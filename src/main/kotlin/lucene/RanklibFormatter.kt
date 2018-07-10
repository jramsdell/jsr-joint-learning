package experiment

import entity.EntityDatabase
import features.shared.SharedFeature
import lucene.*
import lucene.containers.*
import lucene.indexers.IndexFields
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import java.io.File
import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarStyle
import utils.*
import utils.lucene.getIndexSearcher
import utils.misc.CONTENT
import utils.misc.filledArray
import utils.parallel.forEachParallel
import utils.parallel.forEachParallelQ
import utils.parallel.pmap
import utils.stats.normalize
import java.lang.Double.sum
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


// .18 / .13
// 5859
//MAP on training data: 0.3467
//MAP on validation data: 0.2412

// 1777 / 1301


//MAP on training data: 0.4345
//MAP on validation data: 0.3569


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

enum class FeatureType {
    PARAGRAPH,
    PARAGRAPH_TO_ENTITY,
    ENTITY,
    ENTITY_TO_PARAGRAPH,
    SHARED,
    PARAGRAPH_FUNCTOR
}

/**
 * Class: lucene.KotlinRanklibFormatter
 * Description: Used to apply scoring functions to queries (from .cbor file) and print results as features.
 *              The results file is compatible with RankLib.
 * @param queries: List of lucene string/Topdocs (obtained by QueryRetriever)
 * @param paragraphQrelLoc: Location of the .qrels file (if none is given, then paragraphs won't be marked as relevant)
 * @param indexSearcher: An IndexSearcher for the Lucene index directory we will be querying.
 */
class KotlinRanklibFormatter(paragraphQueryLoc: String,
                             paragraphIndexLoc: String,
                             paragraphQrelLoc: String,
                             entityIndexLoc: String,
                             entityQrelLoc: String = "",
                             proximityIndexLoc: String = "",
                             entityQueryLoc: String = "",
                             includeRelevant: Boolean = false
                             ) {

    /**
     * @param indexLoc: A string pointing to location of index (used to create IndexSearcher if none is given)
     */
//    constructor(queryLocation: String, qrelLoc: String, indexLoc: String) :
//            this(queryLocation, qrelLoc, getIndexSearcher(indexLoc))


    val useJointDist = true
    val paragraphSearcher = getIndexSearcher(paragraphIndexLoc)
    val entitySearcher: IndexSearcher = getIndexSearcher(entityIndexLoc)
    val proximitySearcher: IndexSearcher =
            if (proximityIndexLoc == "") paragraphSearcher
            else getIndexSearcher(proximityIndexLoc)


//    val qrelCreator = QrelCreator(paragraphQrelLoc,
//            "/home/jsc57/data/benchmark/benchmarkY1/benchmarkY1-train/train.pages.cbor-hierarchical.entity.qrels change this",
//            indexSearcher =  paragraphSearcher)
//        .apply { writeEntityQrelsUsingParagraphQrels() }
//        .apply { System.exit(0) }


    val entityDb = EntityDatabase(entityIndexLoc)

    val queryRetriever = QueryRetriever(paragraphSearcher, false)
//    val queries = queryRetriever.getSectionQueries(paragraphQueryLoc, doBoostedQuery = false)
    val queries = queryRetriever.getPageQueries(paragraphQueryLoc, doBoostedQuery = true)
    val paragraphRetriever = ParagraphRetriever(paragraphSearcher, queries, paragraphQrelLoc, includeRelevant,
//            doFiltered = paragraphQrelLoc != "")
            doFiltered = false)
    val entityRetriever = EntityRetriever(entityDb, paragraphSearcher, queries, entityQrelLoc, paragraphRetrieve = paragraphRetriever)
    val featureDatabase = FeatureDatabase2()



    private val queryContainers =
        queries.withIndex().pmap { indexedQuery ->
            val index = indexedQuery.index
            val (query, tops) = indexedQuery.value
            val paragraphContainers = paragraphRetriever.paragraphContainers[index]
            val entityContainers = entityRetriever.entityContainers.get(index)
//            val entityContainers = emptyList<EntityContainer>()

            QueryContainer(
                    query = query,
                    tops = tops,
                    paragraphs = paragraphContainers,
                    entities = entityContainers,
                    queryData = createQueryData(query, tops, paragraphContainers, entityContainers)
            )
        }



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
        val minValue = values.min() ?: return emptyList()
        val maxValue = values.max() ?: return emptyList()
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

    private fun createQueryData(query: String, tops: TopDocs,
                                paragraphContainers: List<ParagraphContainer>,
                                entityContainers: List<EntityContainer>): QueryData {
        val booleanQuery = AnalyzerFunctions.createQuery(query, CONTENT)
        val booleanQueryTokens = AnalyzerFunctions.createQueryList(query, CONTENT)
        val tokens = AnalyzerFunctions.createTokenList(query)


        val data = QueryData(
                queryString = query,
                tops = tops,
                queryBoolean = booleanQuery,
                queryBooleanTokens = booleanQueryTokens,
                queryTokens = tokens,
                paragraphSearcher = paragraphSearcher,
                entitySearcher = entitySearcher,
                proximitySearcher = proximitySearcher,
                entityContainers = entityContainers,
                paragraphContainers = paragraphContainers,
                entityDb = entityDb)
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


    fun addFeature3(featureEnum: FeatureEnum, weight:Double = 1.0,
                    normType: NormType = NormType.NONE, f: (QueryData, SharedFeature) -> Unit) {

        println("Weight was: $weight")
        val bar = ProgressBar("Feature Progress", queryContainers.size.toLong(), ProgressBarStyle.ASCII)
        bar.start()
        val lock = ReentrantLock()
        val curSim = paragraphSearcher.getSimilarity(true)
        val curSimEntity = entitySearcher.getSimilarity(true)

        queryContainers.forEachParallelQ(30, 50) { qc ->

            // Apply feature and update counter
            val sf = SharedFeature(paragraphScores = filledArray(qc.paragraphs.size, 0.0),
                    entityScores = filledArray(qc.entities.size, 0.0))


            if (featureEnum.type == FeatureType.PARAGRAPH_FUNCTOR) {
                qc.paragraphs.forEachIndexed { index, paragraphContainer ->
//                    sf.paragraphScores[index] = paragraphContainer.score
//                    sf.paragraphScores[index] = if (paragraphContainer.isRelevant > 0) 1.0 else 0.0
                    sf.paragraphScores[index] = paragraphContainer.isRelevant.toDouble()
                }

                qc.entities.forEachIndexed { index, entityContainer ->
                    //                    sf.paragraphScores[index] = paragraphContainer.score
//                    sf.entityScores[index] = if (entityContainer.isRelevant > 0) 1.0 else 0.0
                    sf.entityScores[index] = entityContainer.isRelevant.toDouble()
                }
            }

            f(qc.queryData, sf)


            // Turn paragraph features into entity features
            if (featureEnum.type == FeatureType.PARAGRAPH && useJointDist) {
                sf.paragraphScores.forEachIndexed { index, score ->
                    qc.jointDistribution.parToEnt[index]!!
                        .entries
                        .forEach { (entityIndex, freq) ->
                            sf.entityScores[entityIndex] += freq * sf.paragraphScores[index]
                        }
                }
            }

            // Turn entity features into paragraph features
            if (featureEnum.type == FeatureType.ENTITY && useJointDist) {
                sf.entityScores.forEachIndexed { index, score ->
                    qc.jointDistribution.entToPar[index]!!
                        .entries
                        .forEach { (parIndex, freq) ->
                            sf.paragraphScores[parIndex] += freq * sf.entityScores[index]
                        }
                }
            }



            addBothScores(qc, sf, weight, normType, featureEnum)
            lock.withLock { bar.step() }
        }
        bar.stop()
        paragraphSearcher.setSimilarity(curSim)
        entitySearcher.setSimilarity(curSimEntity)
    }



    private fun addBothScores(qc: QueryContainer, sf: SharedFeature, weight: Double, normType: NormType,
                              featureEnum: FeatureEnum) {


        qc.entities
            .zip(sf.entityScores.run { normalizeResults(this, normType) })
            .forEach { (entity, score) ->
                entity.queryFeatures += FeatureContainer(score, weight, featureEnum)}

        qc.paragraphs
            .zip(sf.paragraphScores.run { normalizeResults(this, normType) })
            .forEach { (paragraph, score) ->
                paragraph.queryFeatures += FeatureContainer(score, weight, featureEnum)
            }
    }



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

            queryContainer.entities
                .onEach(EntityContainer::rescoreEntity)
        }


    /**
     * Function: writeToRankLibFile
     * Desciption: Writes features to a RankLib-compatible file.
     * @param outName: Name of the file to write the results to.
     */
    fun writeToRankLibFile(outName: String) {
        val file = File(outName).bufferedWriter()
        val onlyParagraph = File("ony_paragraph.txt").bufferedWriter()
        val onlyEntity = File("ony_entity.txt").bufferedWriter()

//        queryContainers
//                .flatMap { queryContainer -> queryContainer.paragraphs  }
//                .joinToString(separator = "\n", transform = ParagraphContainer::toString)
//                .let { file.write(it + "\n"); onlyParagraph.write(it + "\n") }

        queryContainers
            .flatMap { queryContainer -> queryContainer.entities.map(EntityContainer::toString) + queryContainer.paragraphs.map(ParagraphContainer::toString)  }
            .joinToString(separator = "\n")
            .let { file.write(it + "\n"); }

        queryContainers
            .flatMap { queryContainer -> queryContainer.paragraphs.map(ParagraphContainer::toString)  }
            .joinToString(separator = "\n")
            .let { onlyParagraph.write(it + "\n"); }


        queryContainers
            .flatMap { queryContainer -> queryContainer.entities  }
            .joinToString(separator = "\n", transform = EntityContainer::toString)
            .let { file.append(it); onlyEntity.write(it) }

//        queryContainers.forEach(featureDatabase::writeFeatures)
        featureDatabase.writeFeatures(queryContainers)
        file.close()
        onlyEntity.close()
        onlyParagraph.close()
    }



    /**
     * Function: writeQueriesToFile
     * Desciption: Uses lucene formatter to write current queries to trec-car compatible file
     * @param outName: Name of the file to write the results to.
     */
    fun writeQueriesToFile(outName: String) {
        queryRetriever.writeQueriesToFile(queries, outName)
        queryRetriever.writeEntitiesToFile(queryContainers)
    }
}



