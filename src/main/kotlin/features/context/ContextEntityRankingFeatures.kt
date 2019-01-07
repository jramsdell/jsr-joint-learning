package features.context

import lucene.NormType
import features.shared.SharedFeature
import lucene.FieldQueryFormatter
import lucene.KotlinRanklibFormatter
import lucene.containers.FeatureEnum
import lucene.containers.QueryData
import lucene.indexers.IndexFields
import utils.AnalyzerFunctions
import utils.lucene.explainScore


object ContextEntityRankingFeatures {
    private fun queryField(qd: QueryData, sf: SharedFeature, field: IndexFields): Unit = with(qd) {

        val tokens = AnalyzerFunctions.createTokenList(queryString, useFiltering = true,
                analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)
        val fq = FieldQueryFormatter()
        val fieldQuery = fq.addWeightedQueryTokens(tokens, field).createBooleanQuery()

        contextEntityContainers.forEachIndexed { index, container ->
            val score = contextEntitySearcher.explainScore(fieldQuery, container.docId)
            sf.entityScores[index] = score
        }
    }

    fun addUnigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = NormType.ZSCORE) =
            fmt.addFeature3(FeatureEnum.CONTEXT_ENTITY_UNIGRAM, wt, norm) { qd, sf ->
                queryField(qd, sf, IndexFields.FIELD_UNIGRAM) }

    fun addBigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = NormType.ZSCORE) =
            fmt.addFeature3(FeatureEnum.CONTEXT_ENTITY_UNIGRAM, wt, norm) { qd, sf ->
                queryField(qd, sf, IndexFields.FIELD_BIGRAM) }

    fun addWindowed(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = NormType.ZSCORE) =
            fmt.addFeature3(FeatureEnum.CONTEXT_ENTITY_UNIGRAM, wt, norm) { qd, sf ->
                queryField(qd, sf, IndexFields.FIELD_WINDOWED_BIGRAM) }

}