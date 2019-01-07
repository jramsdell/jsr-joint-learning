package lucene.joint

import features.compatability.CompatabilityFeatureType
import features.compatability.CompatabilityFeatureType.ENTITY_TO_PARAGRAPH
import features.compatability.CompatabilityFeatures
import features.compatability.GramContainer
import features.compatability.GramMatrix
import features.entity.EntityRankingFeatures
import lucene.FeatureType
import lucene.NormType
import features.shared.SharedFeature
import learning.deep.stochastic.GaussianTrie
import learning.deep.tensorflow.GraphBuilder
import learning.deep.tensorflow.GraphElement
import learning.optimization.SimpleDescent
import lucene.*
import lucene.containers.*
import lucene.indexers.IndexFields
import java.io.File
import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarStyle
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.TermQuery
import org.nd4j.linalg.api.ndarray.INDArray
import org.tensorflow.Graph
import org.tensorflow.Output
import org.tensorflow.Shape
import utils.lucene.getTypedSearcher
import utils.misc.filledArray
import utils.misc.toArrayList
import utils.nd4j.exp
import utils.nd4j.toNDArray
import utils.parallel.forEachParallelQ
import utils.parallel.pmap
import utils.stats.defaultWhenNotFinite
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock





@Suppress("UNCHECKED_CAST")
/**
 * Class: lucene.KotlinRanklibFormatter
 * Description: Used to apply scoring functions to queries (from .cbor file) and print results as features.
 *              The results file is compatible with RankLib.
 */
class JointRunner(paragraphQueryLoc: String,
                  paragraphIndexLoc: String,
                  paragraphQrelLoc: String,
                  entityIndexLoc: String,
                  entityQrelLoc: String = "",
                  sectionIndexLoc: String = "",
                  sectionQrelLoc: String = "",
                  contextEntityLoc: String = "",
                  contextSectionLoc: String = "",
                  omitArticleLevel: Boolean = false
                             ) : KotlinRanklibFormatter(
        paragraphQueryLoc = paragraphQueryLoc,
        entityQrelLoc = entityQrelLoc,
        paragraphQrelLoc = paragraphQrelLoc,
        entityIndexLoc = entityIndexLoc,
        paragraphIndexLoc = paragraphIndexLoc,
        omitArticleLevel = omitArticleLevel,
        contextEntityLoc = contextEntityLoc,
        sectionQrelLoc = sectionQrelLoc,
        sectionIndexLoc = sectionIndexLoc,
        contextSectionLoc = contextSectionLoc) {

    val graph = Graph()
    val builder = GraphBuilder(graph)


    fun<A: CompatabilityFeatureType> addCompatabilityFeature(f: (QueryData, GramMatrix<A>) -> Unit, compatType: A): List<GramMatrix<A>> {
        val bar = ProgressBar("Feature Progress", queryContainers.size.toLong(), ProgressBarStyle.ASCII)
        bar.start()
        val lock = ReentrantLock()


        val gramMatrices = queryContainers.pmap { qc ->
            val nRows = when(compatType) {
                ENTITY_TO_PARAGRAPH -> qc.entities.size
                else -> 0
            }


            val nCols = when(compatType) {
                ENTITY_TO_PARAGRAPH -> qc.paragraphs.size
                else -> 0
            }

            val gramMatrix = GramMatrix<A>(nRows = nRows, nCols = nCols, compatType = compatType)
            f(qc.queryData, gramMatrix)
            gramMatrix.normalizeByRowZscore()
            lock.withLock { bar.step() }
            gramMatrix
        }
        bar.stop()

        return gramMatrices
    }

    fun retrieveCompatabilityMaps(): List<GramContainer<ENTITY_TO_PARAGRAPH>> {
        val eToPMaps = with(CompatabilityFeatures) {
            listOf(this::addEToPAbstractUnigrams, this::addEToPAbstractBigrams, this::addEToPPassageLinkFreqs)
        }.map { addCompatabilityFeature(it, ENTITY_TO_PARAGRAPH) }

        val nMatrices = eToPMaps.first().size
        val finalMaps = (0 until nMatrices).map { index ->
            val matrices = eToPMaps.map { it[index] }
            GramContainer<ENTITY_TO_PARAGRAPH>(matrices.toArrayList())
        }


        return finalMaps
    }

    fun addEntityFeatures() {
        val norm = NormType.ZSCORE
        var i = 0
        val weights: List<Double>? = null
        EntityRankingFeatures.addTop25Freq(this, wt = weights?.get(i++) ?: 1.0, norm = norm)
        EntityRankingFeatures.addBM25BoostedUnigram(this, wt = weights?.get(i++) ?: 1.0, norm = norm)

//        EntityRankingFeatures.addBM25BoostedBigram(this, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        EntityRankingFeatures.addContextUnigram(this, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        EntityRankingFeatures.addContextBigram(this, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        EntityRankingFeatures.addInlinksField(this, wt = weights?.get(i++) ?: 1.0, norm = norm)

//        EntityRankingFeatures.addCategoriesField(this, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        EntityRankingFeatures.addDisambigField(this, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        EntityRankingFeatures.addInlinksField(this, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        EntityRankingFeatures.addContextUnigram(this, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        EntityRankingFeatures.addContextBigram(this, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        EntityRankingFeatures.addContextWindowed(this, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        EntityRankingFeatures.addOutlinksField(this, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        EntityRankingFeatures.addRedirectField(this, wt = weights?.get(i++) ?: 1.0, norm = norm)
    }

    fun convertCompatabilityMapsToTensor(compats: List<GramContainer<ENTITY_TO_PARAGRAPH>>, weightOutput: GraphElement<Double>) =
        compats.map { compat ->
            val nRows = compat.matrices.first().nRows.toLong()
            val nCols = compat.matrices.first().nCols.toLong()
            val nFeatures = compat.matrices.size.toLong()

            val dArray = compat.matrices.flatMap { matrix -> matrix.flatten() }
                .toDoubleArray()

            val t = builder.constantTensor(builder.getName("tensor"), dArray, longArrayOf(nFeatures, nRows, nCols) )
                .times(weightOutput)
                .sum(0)
            t
        }

    fun convertEntityFeatures(weightOutput: GraphElement<Double>) =
        queryContainers.map { qc ->
            val nFeatures = qc.entities.first().features.size.toLong()
            val nEntities = qc.entities.size.toLong()
            val dArray = (0 until nFeatures.toInt())
                .flatMap { index -> qc.entities.map { it.features[index].score } }
                .toDoubleArray()

            val t = builder.constantTensor(builder.getName("tensor"), dArray, longArrayOf(nFeatures, nEntities) )
                .times(weightOutput)
                .sum(0)
                .lift()
            t
        }

    fun createWeightPlaceholder(name: String, shape: Shape): Output<Double> =
            builder.placeholder<Double>(name, Double::class.javaObjectType, shape)



    fun doTraining() {
        val gramFeatures = retrieveCompatabilityMaps()
        addEntityFeatures()
        val nGramFeatures = gramFeatures.first().matrices.size.toLong()
        val nEntityFeatures = queryContainers.first().entities.first().features.size.toLong()

        val entityWeights = createWeightPlaceholder("weightEntity", Shape.make(nEntityFeatures, 1))
            .run { GraphElement(builder, this) }
        val compatWeights = createWeightPlaceholder("compatWeights", Shape.make(nGramFeatures, 1, 1))
            .run { GraphElement(builder, this) }


        val compatTensors = convertCompatabilityMapsToTensor(gramFeatures, compatWeights)
        val entityTensors = convertEntityFeatures(entityWeights)


        val pushForward = entityTensors.zip(compatTensors)
            .map { (entity, compat) -> entity * compat }

    }


    fun doTraining2() {
        val gramFeatures = retrieveCompatabilityMaps()
        addEntityFeatures()
        val nGramFeatures = gramFeatures.first().matrices.size
        val nEntityFeatures = queryContainers.first().entities.first().features.size

        var gramWeights = gramFeatures.first().matrices.map { 1.0 }
        val featureWeights = queryContainers.first().entities.first().features.map { 1.0 }
        rescoreEntities(featureWeights)
        rescoreGramMatrices(gramWeights, gramFeatures)

        val gramScoreFunction = { weights: List<Double> ->
            rescoreGramMatrices(weights, gramFeatures)
            objectiveFunction(gramFeatures)
        }

        val entityScoreFunction = { weights: List<Double> ->
            rescoreEntities(weights)
            objectiveFunction(gramFeatures)
        }
        println(objectiveFunction(gramFeatures))

        val entityMatrices = createEntityMatrices()
        val scores = scoreEntityMatrices(queryContainers.first().entities.first().features.map { 1.0 }, entityMatrices)



        val entityDescender = SimpleDescent(nFeatures = nEntityFeatures, scoreFun = entityScoreFunction, winnow = false)
        val gramDescender = SimpleDescent(nFeatures = nGramFeatures, scoreFun = gramScoreFunction, winnow = false)
        (0 until 10).forEach {
            entityDescender.search(20) { weights -> println(weights)  }
            println()
            println(getMAP(matrices = gramFeatures))
            println()

            gramDescender.search(10) { weights -> println(weights)  }
            println()
            println(getMAP(matrices = gramFeatures))
            println()



        }
        ranklibWriter.writeParagraphsToFile(queryContainers, false)


//        SimpleDescent(nFeatures = nEntityFeatures, scoreFun = entityScoreFunction, winnow = false)
//            .apply { search(5) { weights -> println(weights) } }
//        println()
//        SimpleDescent(nFeatures = nGramFeatures, scoreFun = gramScoreFunction, winnow = false)
//            .apply { search(5) { weights -> println(weights) } }
//        println()
//        SimpleDescent(nFeatures = nEntityFeatures, scoreFun = entityScoreFunction, winnow = false)
//            .apply { search(5) { weights -> println(weights) } }
//        println()
//        SimpleDescent(nFeatures = nGramFeatures, scoreFun = gramScoreFunction, winnow = false)
//            .apply { search(5) { weights -> println(weights) } }
//        println()
//        SimpleDescent(nFeatures = nEntityFeatures, scoreFun = entityScoreFunction, winnow = false)
//            .apply { search(5) { weights -> println(weights) } }


    }

    fun rescoreParagraphs(qc: QueryContainer, matrix: GramContainer<ENTITY_TO_PARAGRAPH>) {
        qc.paragraphs.forEach { p -> p.score = 0.0 }
        (0 until matrix.nRows).map { entity ->
            (0 until matrix.nCols).map { passage ->
                qc.paragraphs[passage].score += qc.entities[entity].score * matrix.finalMatrix[entity][passage]
            }
        }
    }

    fun objectiveFunction(matrices: List<GramContainer<ENTITY_TO_PARAGRAPH>>): Double {

        return queryContainers
            .withIndex()
            .filter { (_, qc) -> qc.paragraphs.any { it.isRelevant > 0 } }
            .pmap { (index, qc) ->
                rescoreParagraphs(qc, matrices[index])

            qc.paragraphs.filter { p -> p.isRelevant > 0 }
                .map { p -> Math.log(if (p.score == 0.0) 0.00001 else p.score) }.sum()
        }.sumByDouble { it }
    }

    fun createEntityMatrices() =
        queryContainers.map { qc ->
            qc.entities
                .map { it.features.map { it.score } }
                .toNDArray()
        }

    fun scoreEntityMatrices(weights: List<Double>, entityMatrices: List<INDArray>): List<INDArray> {
        val nd = weights.toNDArray()
        return entityMatrices.map { it.mul(nd).exp() }
    }

    fun rescoreGramMatrices(weights: List<Double>, matrices: List<GramContainer<*>>) =
            matrices.withIndex().forEachParallelQ(40) { (index, matrix) ->
                if (queryContainers[index].paragraphs.any { it.isRelevant > 0 })
                    matrix.updateFinalMatrix(weights)
            }

    fun rescoreEntities(weights: List<Double>) = queryContainers.forEach { qc ->
        var total = 0.0
        qc.entities.forEach { entity ->
            val newScore = weights.withIndex().sumByDouble { (fIndex, w) ->
                entity.features[fIndex].score * w
            }.run { Math.exp(this) }

            entity.score = newScore
            total += newScore
        }

        qc.entities.forEach { entity -> entity.score /= total }
    }


    fun getAP(qc: QueryContainer, matrix: GramContainer<ENTITY_TO_PARAGRAPH>): Double {
        rescoreParagraphs(qc, matrix)
        var num = 0.0
        var denom = 0.0
        var score = 0.0
        qc.paragraphs.sortedByDescending { it.score }
            .withIndex()
            .forEach { (rank, paragraph) ->
                if (paragraph.isRelevant > 0) {
                    denom += 1.0
                    num += 1.0
                    score += num / denom
                } else {
                    denom += 1.0
                }
            }

        return score /  Math.max(1.0, qc.nRel)
    }

    fun getMAP(matrices: List<GramContainer<ENTITY_TO_PARAGRAPH>>) =
            queryContainers
                .withIndex()
                .pmap{ (index, qc) -> getAP(qc, matrices[index]) }
                .average()





}



