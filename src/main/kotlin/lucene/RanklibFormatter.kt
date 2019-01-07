package lucene

import features.shared.SharedFeature
import learning.deep.stochastic.GaussianTrie
import lucene.containers.*
import lucene.indexers.IndexFields
import java.io.File
import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarStyle
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.TermQuery
import utils.*
import utils.lucene.getTypedSearcher
import utils.misc.filledArray
import utils.parallel.forEachParallelQ
import utils.stats.countDuplicates
import utils.stats.defaultWhenNotFinite
import java.lang.Double.sum
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


// .18 / .13
// 5859
//MAP on training data: 0.3467
//MAP on validation data: 0.2412

// 1777 / 1301


// 0.6618 / 0.4096

// MAP on training data: 0.3231
//MAP on validation data: 0.3105

// 0.2390 / 0.1692


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
    CONTEXT_ENTITY,
    ENTITY_TO_PARAGRAPH,
    SHARED,
    PARAGRAPH_FUNCTOR,
    SECTION
}

@Suppress("UNCHECKED_CAST")
/**
 * Class: lucene.KotlinRanklibFormatter
 * Description: Used to apply scoring functions to queries (from .cbor file) and print results as features.
 *              The results file is compatible with RankLib.
 * @param queries: List of lucene string/Topdocs (obtained by QueryRetriever)
 * @param paragraphQrelLoc: Location of the .qrels file (if none is given, then paragraphs won't be marked as relevant)
 * @param indexSearcher: An IndexSearcher for the Lucene index directory we will be querying.
 */
open class KotlinRanklibFormatter(paragraphQueryLoc: String,
                             paragraphIndexLoc: String,
                             paragraphQrelLoc: String,
                             entityIndexLoc: String,
                             entityQrelLoc: String = "",
                             sectionIndexLoc: String = "",
                             sectionQrelLoc: String = "",
                             contextEntityLoc: String = "",
                             contextSectionLoc: String = "",
                             omitArticleLevel: Boolean = false
                             ) {

    /**
     * @param indexLoc: A string pointing to location of index (used to create IndexSearcher if none is given)
     */
//    constructor(queryLocation: String, qrelLoc: String, indexLoc: String) :
//            this(queryLocation, qrelLoc, getIndexSearcher(indexLoc))


    var useJointDist = false
    val useSavedFeatures = false
    var limit: Int? = 2
    val isHomogenous = false
    val nThreads = 10
    val paragraphSearcher = getTypedSearcher<IndexType.PARAGRAPH>(paragraphIndexLoc)
    val entitySearcher = getTypedSearcher<IndexType.ENTITY>(entityIndexLoc)
    val sectionSearcher  =
            if (sectionIndexLoc == "") paragraphSearcher as SectionSearcher
            else getTypedSearcher<IndexType.SECTION>(sectionIndexLoc)
    val contextEntitySearcher =  getTypedSearcher<IndexType.CONTEXT_ENTITY>(contextEntityLoc)

    val contextSectionSearcher =
            if (contextSectionLoc == "") paragraphSearcher as TypedSearcher<IndexType.CONTEXT_SECTION> else
            getTypedSearcher<IndexType.CONTEXT_SECTION>(contextSectionLoc)

    val ranklibWriter = RanklibWriter(this, omitArticleLevel)
//        .apply {
//            rebuildSectionQrels(entityQrelLoc, paragraphQrelLoc)
//            println("GOt it")
//        }

    val queryContainers = createQueryContainers( paragraphQueryLoc, paragraphQrelLoc, entityQrelLoc, sectionQrelLoc, isHomogenous)

//    val featureDatabase = FeatureDatabase2()

    private fun createQueryContainers(queryLocation: String,
                                      paragraphQrelLoc: String, entityQrelLoc: String, sectionQrelLoc: String,
                                      isHomogenous: Boolean): List<QueryContainer> {
        val retriever = CombinedRetriever(
                paragraphSearcher = paragraphSearcher,
                sectionSearcher = sectionSearcher,
                contextSectionSearcher = contextSectionSearcher,
                entitySearcher = entitySearcher,
                limit = limit,
                entityQrelLoc = entityQrelLoc,
                paragraphQrelLoc = paragraphQrelLoc,
                sectionQrelLoc = sectionQrelLoc,
                isHomogenous = isHomogenous,
                contextEntitySearcher = contextEntitySearcher
        )

        return retriever.createQueryContainers(queryLocation)
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
    fun normalizeResults(values: List<Double>, normType: NormType): List<Double> {
        return when (normType) {
            NormType.NONE   -> values
            NormType.SUM    -> normSum(values)
            NormType.ZSCORE -> normZscore(values)
            NormType.LINEAR -> normLinear(values)
        }.map { it.defaultWhenNotFinite(0.0) }
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
//        val featureMap = if (useSavedFeatures) featureDatabase.getFeature(featureEnum) else null
        val bar = ProgressBar("Feature Progress", queryContainers.size.toLong(), ProgressBarStyle.ASCII)
        bar.start()
        val lock = ReentrantLock()
//        val curSim = paragraphSearcher.getSimilarity(true)
//        val curSimEntity = entitySearcher.getSimilarity(true)

        queryContainers.forEachParallelQ(60, nThreads) { qc ->

            // Apply feature and update counter
            var sf = SharedFeature(paragraphScores = filledArray(qc.paragraphs.size, 0.0),
                    entityScores = filledArray(qc.entities.size, 0.0),
                    sectionScores = filledArray(qc.sections.size, 0.0))


            if (featureEnum.type == FeatureType.PARAGRAPH_FUNCTOR) {
                qc.paragraphs.forEachIndexed { index, paragraphContainer ->
                    sf.paragraphScores[index] = paragraphContainer.isRelevant.toDouble()
                }

                qc.entities.forEachIndexed { index, entityContainer ->
                    sf.entityScores[index] = entityContainer.isRelevant.toDouble()
                }
            }

//            if (featureMap != null)
//                sf = featureMap[qc.query]!!
//            else
            f(qc.queryData, sf)

//            if (!useSavedFeatures) {
//                featureDatabase.storeFeature(qc.query,featureEnum, sf.makeCopy())
//            }


            // Turn paragraph features into entity features
            if (featureEnum.type == FeatureType.PARAGRAPH && useJointDist) {
                sf.paragraphScores.forEachIndexed { index, score ->
                    qc.jointDistribution.parToEnt[index]!!
                        .entries
                        .forEach { (entityIndex, freq) ->
                            sf.entityScores[entityIndex] += freq * sf.paragraphScores[index]
                        }

                    qc.jointDistribution.parToSec[index]!!
                        .entries
                        .forEach { (secIndex, freq) ->
                            sf.sectionScores[secIndex] += freq * sf.paragraphScores[index]
                        }
                }

            }

            // Turn section features into other features
            if (featureEnum.type == FeatureType.SECTION && useJointDist) {

                sf.sectionScores.forEachIndexed { index, score ->
                    qc.jointDistribution.secToPar[index]!!
                        .entries
                        .forEach { (parIndex, freq) ->
                            sf.paragraphScores[parIndex] += freq * sf.sectionScores[index]
                        }

                }

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

                sf.paragraphScores.forEachIndexed { index, score ->
                    qc.jointDistribution.parToSec[index]!!
                        .entries
                        .forEach { (secIndex, freq) ->
                            sf.sectionScores[secIndex] += freq * sf.paragraphScores[index]
                        }
                }

            }



            addBothScores(qc, sf, weight, normType, featureEnum)
            lock.withLock { bar.step() }
        }
        bar.stop()
//        paragraphSearcher.setSimilarity(curSim)
//        entitySearcher.setSimilarity(curSimEntity)
    }



    private fun addBothScores(qc: QueryContainer, sf: SharedFeature, weight: Double, normType: NormType,
                              featureEnum: FeatureEnum) {


        val normEntities = normalizeResults(sf.entityScores, normType)
        val normParagraphs = normalizeResults(sf.paragraphScores, normType)
        val normSection = normalizeResults(sf.sectionScores, normType)

        qc.entities.forEachIndexed { index, eContainer ->
            val score = normEntities[index]
            val unnormalizedScore = sf.entityScores[index]
            eContainer.features.add(FeatureContainer(score, unnormalizedScore, weight, featureEnum))
        }

        qc.paragraphs.forEachIndexed { index, pContainer ->
            val score = normParagraphs[index]
            val unnormalizedScore = sf.paragraphScores[index]
            pContainer.features.add(FeatureContainer(score, unnormalizedScore, weight, featureEnum))
        }

        qc.sections.forEachIndexed { index, sContainer ->
            val score = normSection[index]
            val unnormalizedScore = sf.sectionScores[index]
            sContainer.features.add(FeatureContainer(score, unnormalizedScore, weight, featureEnum))
        }

    }

    fun doJointDistribution() {
        if (!useJointDist)
            return

        queryContainers.forEachParallelQ(60, nThreads) { qContainer ->
            //                val jointDistribution = JointDistribution.createFromFunctor(qContainer.queryData)
                val jointDistribution =  JointDistribution.createJointDistribution(qContainer.queryData)
//            val jointDistribution =  JointDistribution.createNew(qContainer.queryData)
//            val jointDistribution =  JointDistribution.createMonoidDistribution(qContainer.query, qContainer.queryData)
            qContainer.jointDistribution = jointDistribution
//            analyzeStuff(qContainer.queryData)
        }
    }


    fun doPullback(field: IndexFields) {
//        queryContainers.forEach { qContainer ->
//            qContainer.entities.forEach { eContainer ->
//                val unigrams = eContainer.doc().unigrams().split(" ")
//                    .flatMap { it.windowed(2) }
//                val umap = unigrams.countDuplicates().mapValues { it.value.toDouble() }
//                umap.forEach { (unigram, freq) -> eContainer.dist.merge(unigram, freq, ::sum) }
//            }

        queryContainers.forEach { qContainer ->
            qContainer.paragraphs.forEach { pContainer ->
//                val unigrams = pContainer.doc().unigrams().split(" ")
                val text = pContainer.doc().text()
                val unigrams = AnalyzerFunctions.createTokenList(text, AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)
                val umap = unigrams.countDuplicates().mapValues { it.value.toDouble() }
                umap.forEach { (unigram, freq) -> pContainer.dist.merge(unigram, freq, ::sum) }
            }

            val eIds = qContainer.entities.mapIndexed { index, docContainer -> docContainer.name to index  }
                .toMap()

            val pIds = qContainer.paragraphs.mapIndexed { index, docContainer -> docContainer.name to index  }
                .toMap()

//            val sIds = qContainer.sections.mapIndexed { index, docContainer -> docContainer.name to index  }
//                .toMap()

//            qContainer.paragraphs.forEach { pContainer ->
//                val entities = pContainer.doc().entities().split(" ")
//                    entities.mapNotNull { entity -> eIds[entity] }
//                        .map { eIndex ->
//                            val entity = qContainer.entities[eIndex]
//                            entity.dist.forEach { (unigram, freq) -> pContainer.dist.merge(unigram, freq, ::sum) }
//                        }
//            }

            qContainer.sections.forEach { sContainer: SectionContainer ->
                val paragraphs = sContainer.doc().paragraphs().split(" ")
                paragraphs.mapNotNull { paragraph: String -> pIds[paragraph] }
                    .map { pIndex: Int ->
                        val paragraph = qContainer.paragraphs[pIndex]
                        paragraph.dist.forEach { (unigram, freq) -> sContainer.dist.merge(unigram, freq, ::sum) }
                    }
            }

//            qContainer.sections.forEach { sContainer ->
//                sContainer.dist = sContainer.dist.normalize().toList().toHashMap()
//            }
//
//            qContainer.paragraphs.forEach { sContainer ->
//                sContainer.dist = sContainer.dist.normalize().toList().toHashMap()
//            }


        }
    }

    fun initialize() {
//        doPullback(IndexFields.FIELD_UNIGRAM)
        doJointDistribution()
    }
    fun finish() {
//        val t = Trie<QueryContainer>(curKey = "")
//        queryContainers.forEach { qc ->
//            val path = qc.query.split("/")
//            t.add(path, qc)
//        }
//        val g = GaussianTrie(t, nFeatures = queryContainers.first().paragraphs.first().features.size)
////        StochasticUtils.scoreSections(g.tries)
////        StochasticUtils.filterBySections(g.tries)
//        g.descent.search()
//        System.exit(0)

    }

    fun finishQuery() {
//        val t = Trie<QueryContainer>(curKey = "")
//        queryContainers.forEach { qc ->
//            val path = qc.query.split("/")
//            t.add(path, qc)
//        }
//        println(GaussianTrie(t).getMAP(OptimalWeights.ONLY_ENTITY_WITH_NO_SHARED.weights))

//        t.traverseBottomUp { path, curNodeKey, d, children ->
//            d.paragraphs.forEachIndexed { pIndex, p ->
//                p.rescore()
//                var score = p.score
//                var scoreMin = p.score
//                p.features.clear()
//                if (children.isNotEmpty()) {
//                    children.forEach { child ->
//                        child.data?.let { qc ->
//                            score = Math.max(score, qc.paragraphs[pIndex].score)
//                        }
//                    }
//                }
//                p.features.add(FeatureContainer(score, score, 1.0, FeatureEnum.ENTITY_SECTIONS_FIELD))
//            }
//        }

//        t.traverseBottomUp { path, curNodeKey, d, children ->
//            if (children.isNotEmpty())  {
//                val nParagraphs = d.paragraphs.size
//                val nFeatures = d.paragraphs.first().features.size
//
//                (0 until nFeatures).forEach { fIndex ->
//                    (0 until nParagraphs).forEach { pIndex ->
//                        val dFeat = d.paragraphs[pIndex].features[fIndex]
////                        dFeat.score = 0.0
////                        dFeat.unnormalizedScore = 0.0
//                        children.forEach { child ->
//                            child.data?.let { qc ->
//                                val cFeat = qc.paragraphs[pIndex].features[fIndex]
////                                dFeat.score += cFeat.score
////                                dFeat.unnormalizedScore += cFeat.unnormalizedScore
//                                dFeat.score = Math.max(dFeat.score, cFeat.score)
//                                dFeat.unnormalizedScore = Math.max(dFeat.unnormalizedScore, cFeat.unnormalizedScore)
//                            }
//                        }
//                    }
//                }
//            }
//        }
    }

    fun rebuildSectionQrels(entityQrel: String, paraQrel: String) {
        val lines = File(paraQrel).readLines().flatMap { line ->
            val elements = line.split(" ")
            val query = elements[0]
            val pid = elements[2]
            val q = BooleanQuery.Builder()
                .add(TermQuery(Term(IndexFields.FIELD_PID.field, pid)), BooleanClause.Occur.MUST)
                .build()

            val results = paragraphSearcher.search(q, 1)
                .scoreDocs
                .firstOrNull()
                ?.let { sd ->
                    paragraphSearcher.getIndexDoc(sd.doc)
                        .spotlightEntities()
                        .split(" ")
                        .distinct()
                }
            results?.map { entity ->
                query to entity
            } ?: emptyList()
        }


        val f = File("hierarchical_entity.qrels").bufferedWriter()
        lines.forEach { (query, entity) ->
            val e = entity.replace("_", "%20")
            f.write("$query 0 $e 1\n")
        }
        f.close()

    }


}



