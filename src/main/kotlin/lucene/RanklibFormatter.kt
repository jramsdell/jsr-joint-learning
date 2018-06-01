package experiment

import com.jsoniter.JsonIterator
import khttp.post
import lucene.*
import lucene.containers.FeatureContainer
import lucene.containers.ParagraphContainer
import lucene.containers.QueryContainer
import lucene.containers.QueryData
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import java.io.File
import java.util.*
import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarStyle
import utils.*
import utils.lucene.getIndexSearcher
import utils.misc.CONTENT
import utils.misc.PID
import utils.parallel.pmap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


/**
 * Enum: lucene.NormType
 * Description: Determines if the the values of an added feature should be normalized
 */
enum class NormType {
    NONE,           // No normalization should be used
    SUM,            // Value is divided by total sum of all values in lucene
    ZSCORE,         // Zscore is calculated for all values in lucene
    LINEAR          // Value is normalized to: (value - min) / (max - min)
}

/**
 * Class: lucene.KotlinRanklibFormatter
 * Description: Used to apply scoring functions to queries (from .cbor file) and print results as features.
 *              The results file is compatible with RankLib.
 * @param queries: List of lucene string/Topdocs (obtained by QueryRetriever)
 * @param qrelLoc: Location of the .qrels file (if none is given, then paragraphs won't be marked as relevant)
 * @param indexSearcher: An IndexSearcher for the Lucene index directory we will be querying.
 */
class KotlinRanklibFormatter(queryLocation: String,
                             qrelLoc: String, val indexSearcher: IndexSearcher) {

    /**
     * @param indexLoc: A string pointing to location of index (used to create IndexSearcher if none is given)
     */
    constructor(queryLocation: String, qrelLoc: String, indexLoc: String) :
            this(queryLocation, qrelLoc, getIndexSearcher(indexLoc))


    val queryRetriever = QueryRetriever(indexSearcher)
    val queries = queryRetriever.getSectionQueries(queryLocation)

    // If a qrel filepath was given, reads file and creates a set of lucene/paragraph pairs for relevancies
    private val relevancies =
            if (qrelLoc == "") null
            else
                File(qrelLoc)
                    .bufferedReader()
                    .readLines()
                    .map { it.split(" ").let { it[0] to it[2] } }
                    .toSet()

    // Maps queries into lucene containers (stores paragraph and feature information)
    private val queryContainers =
//        queries.withIndex().map {index,  (query, tops) ->
            queries.withIndex().pmap {index ->
                val (query, tops) = index.value
            val containers = tops.scoreDocs.map { sc ->
                val pid = indexSearcher.doc(sc.doc).get(PID)

                ParagraphContainer(
                        pid = pid,
                        qid = index.index + 1,
                        isRelevant = relevancies?.run { contains(Pair(query, pid)) } ?: false,
                        docId = sc.doc,
                        features = arrayListOf())
            }
            QueryContainer(query = query, tops = tops, paragraphs = containers,
            queryData = createQueryData(query, tops))
        }.toList()



    /**
     * Function: normSum
     * Description: Normalizes list of doubles by each value equal to itself divided by the total
     * @see NormType
     */
    private fun normSum(values: List<Double>): List<Double> =
            values.sum()
                .let { total -> values.map { value ->  value / total } }


    /**
     * Function: normZscore
     * Description: Calculates zscore for doubles in list
     * @see NormType
     */
    private fun normZscore(values: List<Double>): List<Double> {
        val mean = values.average()
        val std = Math.sqrt(values.sumByDouble { Math.pow(it - mean, 2.0) })
        return values.map { ((it - mean) / std) }
    }

    /**
     * Function: normLinear
     * Description: Linearizes each value: (value - min) / (max - min)
     * @see NormType
     */
    private fun normLinear(values: List<Double>): List<Double> {
        val minValue = values.min()!!
        val maxValue = values.max()!!
        return values.map { value -> (value - minValue) / (maxValue - minValue) }
    }


    /**
     * Function: normalizeResults
     * Description: Normalizes a list of doubles according to the lucene.NormType
     * @see NormType
     */
    private fun normalizeResults(values: List<Double>, normType: NormType): List<Double> {
        return when (normType) {
            NormType.NONE   -> values
            NormType.SUM    -> normSum(values)
            NormType.ZSCORE -> normZscore(values)
            NormType.LINEAR -> normLinear(values)
        }
    }

    private fun createQueryData(query: String, tops: TopDocs): QueryData {
        val booleanQuery = AnalyzerFunctions.createQuery(query, CONTENT)
        val booleanQueryTokens = AnalyzerFunctions.createQueryList(query, CONTENT)
        val tokens = AnalyzerFunctions.createTokenList(query)

        // Prefetch document entities
        val documentEntities = tops.scoreDocs.map { scoreDoc ->
            indexSearcher.doc(scoreDoc.doc).getValues("spotlight").toList()
        }

        val candidateEntities = documentEntities
            .flatten()
            .toSet()
            .toList()
            .sorted()
            .map { it to ArrayList<Double>() }

        val data = QueryData(queryString = query, tops = tops, queryBoolean = booleanQuery,
                queryBooleanTokens = booleanQueryTokens, queryTokens = tokens, indexSearcher = indexSearcher,
                queryEntities = retrieveTagMeEntities(query),
                documentEntities = documentEntities,
                candidateEntities = candidateEntities)
        return data
    }

    /**
     * Function: addFeature
     * Description: Accepts a functions that take a string (lucene string) and TopDocs (from BM25 lucene)
     *              The function must return a list of Doubles in the same order as the documents they score
     *              The values are stored as a feature for later reranking or for creating a RankLib file.
     *
     * Note: The function, f, is mapped onto all of the queries in parallel. Make sure it is thread-safe.
     *
     * @param f: Function (or method reference) that scores each document in TopDocs and returns it as a list of doubles
     * @param normType: lucene.NormType determines the type of normalization (if any) to apply to the new document scores.
     * @param weight: The final list of doubles is multiplies by this weight
     */
    fun addFeature(f: (String, TopDocs, IndexSearcher) -> List<Double>, weight:Double = 1.0,
                   normType: NormType = NormType.NONE) {

        val bar = ProgressBar("lucene.Feature Progress", queryContainers.size.toLong(), ProgressBarStyle.ASCII)
        bar.start()
        val lock = ReentrantLock()
        val curSim = indexSearcher.getSimilarity(true)

        queryContainers
            .pmap { (query, tops, paragraphs, _) ->
                    // Using scoring function, score each of the paragraphs in our lucene result
                    val featureResult: List<Double> =
                            f(query, tops, indexSearcher).run { normalizeResults(this, normType) }

                    lock.withLock { bar.step() }
                    featureResult.zip(paragraphs) } // associate the scores with their corresponding paragraphs
            .forEach { results ->
                results.forEach { (score, paragraph) ->
                                   paragraph.features += FeatureContainer(score, weight)
                }}
        bar.stop()
        indexSearcher.setSimilarity(curSim)
    }

    fun addFeature2(f: (QueryData) -> List<Double>, weight:Double = 1.0,
                   normType: NormType = NormType.NONE) {

        val bar = ProgressBar("lucene.Feature Progress", queryContainers.size.toLong(), ProgressBarStyle.ASCII)
        bar.start()
        val lock = ReentrantLock()
        val curSim = indexSearcher.getSimilarity(true)

        queryContainers
            .pmap { (_, _, paragraphs, queryData) ->
                // Using scoring function, score each of the paragraphs in our lucene result
                val featureResult: List<Double> =
                        f(queryData).run { normalizeResults(this, normType) }

                lock.withLock { bar.step() }
                featureResult.zip(paragraphs) } // associate the scores with their corresponding paragraphs
            .forEach { results ->
                results.forEach { (score, paragraph) ->
                    paragraph.features += FeatureContainer(score, weight)
                }}
        bar.stop()
        indexSearcher.setSimilarity(curSim)
    }


    private fun bm25(query: String, tops:TopDocs, indexSearcher: IndexSearcher): List<Double> {
        return tops.scoreDocs.map { it.score.toDouble() }
    }

    /**
     * Function: addBM25
     * Description: Adds results of the BM25 lucene as a feature. Since the scores are already contained in the TopDocs,
     *              this simply extracts them as a list of doubles.
     * @see addFeature
     */
    fun addBM25(weight: Double = 1.0, normType: NormType = NormType.NONE) =
            addFeature(this::bm25, weight = weight, normType = normType)

    /**
     * Function: rerankQueries
     * Description: Sums current weighted features together and reranks documents according to their new scores.
     * @see addFeature
     */
    fun rerankQueries() =
        queryContainers.forEach { queryContainer ->
            queryContainer.paragraphs
                .onEach(ParagraphContainer::rescoreParagraph)
                .sortedByDescending(ParagraphContainer::score)
                .forEachIndexed { index, paragraph ->
                    queryContainer.tops.scoreDocs[index].doc = paragraph.docId
                    queryContainer.tops.scoreDocs[index].score = paragraph.score.toFloat()
                }
        }


    /**
     * Function: writeToRankLibFile
     * Desciption: Writes features to a RankLib-compatible file.
     * @param outName: Name of the file to write the results to.
     */
    fun writeToRankLibFile(outName: String) {
        queryContainers
                .flatMap { queryContainer -> queryContainer.paragraphs  }
                .joinToString(separator = "\n", transform = ParagraphContainer::toString)
                .let { File(outName).writeText(it) }
    }

    /**
     * Function: writeQueriesToFile
     * Desciption: Uses lucene formatter to write current queries to trec-car compatible file
     * @param outName: Name of the file to write the results to.
     */
    fun writeQueriesToFile(outName: String) {
        queryRetriever.writeQueriesToFile(queries, outName)
    }
}

private fun retrieveTagMeEntities(content: String): List<Pair<String, Double>> {
    return emptyList()
}

fun retrieveTagMeEntities2(content: String): List<Pair<String, Double>> {
    val tok = "7fa2ade3-fce7-4f4a-b994-6f6fefc7e665-843339462"
    val url = "https://tagme.d4science.org/tagme/tag"
    val p = post(url, data = mapOf(
            "gcube-token" to tok,
            "text" to content
    ))
    val results = JsonIterator.deserialize(p.content).get("annotations")
//    val results = p.jsonObject.getJSONArray("annotations")
    return  results
//        .mapNotNull { result -> (result as JSONObject).run {
//            if (getDouble("rho") <= 0.2) null
//            else getString("title").replace(" ", "_") to getDouble("rho")
//        } }
        .map { result -> result.get("title").toString().replace(" ", "_") to result.get("rho").toDouble()}
        .filter { (_, rho) -> rho > 0.2 }
//        .mapNotNull { result -> result.run {
//            if (get("rho").toDouble() <= 0.2) null
//            else get("title").toString().replace(" ", "_") to get("rho").toDouble()
//        } }
//        .filter { result -> (result as JSONObject).getDouble("rho") > 0.2 }
//        .map { result -> (result as JSONObject) .getString("title")
//            .replace(" ", "_")}
}

//fun main(args: Array<String>) {
//    retrieveTagMeEntities("Computer science is a thing that people who like computers do.")
//        .apply(::println)
//}
