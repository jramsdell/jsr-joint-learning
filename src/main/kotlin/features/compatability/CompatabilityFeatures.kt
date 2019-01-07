package features.compatability

import features.compatability.CompatabilityFeatureType.ENTITY_TO_PARAGRAPH
import lucene.NormType
import lucene.NormType.*
import features.shared.SharedFeature
import language.GramAnalyzer
import language.GramStatType
import lucene.FeatureType
import lucene.FieldQueryFormatter
import lucene.KotlinRanklibFormatter
import lucene.containers.*
import utils.AnalyzerFunctions
import utils.AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED
import utils.stats.countDuplicates
import utils.stats.takeMostFrequent
import lucene.containers.FeatureEnum.*
import lucene.indexers.IndexFields
import lucene.indexers.boostedTermQuery
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.BoostQuery
import org.apache.lucene.search.DisjunctionMaxQuery
import org.apache.lucene.search.similarities.LMDirichletSimilarity
import utils.misc.toArrayList
import utils.stats.normZscore
import utils.stats.normalize

sealed class CompatabilityFeatureType {
    object ENTITY_TO_PARAGRAPH : CompatabilityFeatureType()
}

data class GramContainer<A: CompatabilityFeatureType>(val matrices: ArrayList<GramMatrix<A>>) {
    val nRows = matrices.first().nRows
    val nCols = matrices.first().nCols

    val finalMatrix = (0 until nRows).map { (0 until nCols).map { 0.0 }.toArrayList() }.toArrayList()

    fun updateFinalMatrix(weights: List<Double>) {
        var total = 0.0
        (0 until nRows).forEach { row ->
            (0 until nCols).forEach { column ->
                val newScore = weights.withIndex().sumByDouble { (matrixIndex, weight) -> matrices[matrixIndex][row, column] * weight }.run { Math.exp(this) }
                finalMatrix[row][column] = newScore
                total += newScore
            }
        }

        (0 until nRows).forEach { row ->
            (0 until nCols).forEach { column ->
                finalMatrix[row][column] = finalMatrix[row][column] / total
            }
        }
    }
}

data class GramMatrix<A: CompatabilityFeatureType>(val nRows: Int, val nCols: Int, val compatType: A) {
    private val matrix = (0 until nRows).map { (0 until nCols).map { 0.0 }.toArrayList() }.toArrayList()
    operator fun get(i1: Int, i2: Int): Double = matrix[i1][i2]
    operator fun set(i1: Int, i2: Int, v: Double) { matrix[i1][i2] = v }
    fun normalizeByRowZscore() = (0 until nRows).forEach { row -> matrix[row].normZscore() }
    fun flatten() = matrix.flatten()
}

object CompatabilityFeatures {


    private fun entityToPassageAbstractGrams(qd: QueryData, gramMatrix: GramMatrix<ENTITY_TO_PARAGRAPH>, field: IndexFields): Unit = with(qd) {
        val filteredCounts = { grams: String ->
            grams.split(" ")
                .filter { it != " " }
                .countDuplicates()
                .normalize()
        }

        val passageGrams = qd.paragraphContainers.map { it.index to filteredCounts(it.doc().load(field)) }

        qd.entityContainers.map { eC ->
            val entityGrams = filteredCounts(eC.doc().load(field))
            passageGrams.forEach { (passageIndex, pGrams) ->
                val score = entityGrams.keys.intersect(pGrams.keys)
                    .sumByDouble { key -> Math.log(pGrams[key]!! * entityGrams[key]!!) }

                gramMatrix[eC.index, passageIndex] = score
            }
        }
    }

    private fun entityToPassageLinkFreqs(qd: QueryData, gramMatrix: GramMatrix<ENTITY_TO_PARAGRAPH>): Unit = with(qd) {
        val filteredCounts = { grams: String ->
            grams.split(" ")
                .filter { it != " " }
                .countDuplicates()
                .normalize()
        }

        val passageEntities = qd.paragraphContainers.map { it.index to filteredCounts(it.doc().entities()) }

        qd.entityContainers.map { eC ->

            passageEntities.forEach { (passageIndex, pEntities) ->
                gramMatrix[eC.index, passageIndex] = pEntities[eC.name] ?: 0.00000001
            }
        }
    }

//
//    fun addContextWindowed(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
//            fmt.addFeature3(ENTITY_CONTEXT_WINDOWED, wt, norm) { qd, sf -> queryContext(qd, sf, IndexFields.FIELD_WINDOWED_BIGRAM) }

    fun addEToPAbstractUnigrams(qd: QueryData, gramMatrix: GramMatrix<ENTITY_TO_PARAGRAPH>) = entityToPassageAbstractGrams(qd, gramMatrix, IndexFields.FIELD_UNIGRAM)
    fun addEToPAbstractBigrams(qd: QueryData, gramMatrix: GramMatrix<ENTITY_TO_PARAGRAPH>) = entityToPassageAbstractGrams(qd, gramMatrix, IndexFields.FIELD_BIGRAM)
    fun addEToPPassageLinkFreqs(qd: QueryData, gramMatrix: GramMatrix<ENTITY_TO_PARAGRAPH>) = entityToPassageLinkFreqs(qd, gramMatrix)


}
