package features.section

import experiment.KotlinRanklibFormatter
import experiment.NormType
import features.shared.SharedFeature
import lucene.FieldQueryFormatter
import lucene.containers.FeatureEnum
import lucene.containers.QueryData
import lucene.indexers.IndexFields
import utils.AnalyzerFunctions


object SectionRankingFeatures {

    private fun queryField(qd: QueryData, sf: SharedFeature, field: IndexFields): Unit = with(qd) {
        val tokens = AnalyzerFunctions.createTokenList(queryString, useFiltering = true,
                analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)
        val fq = FieldQueryFormatter()
        val fieldQuery = fq.addWeightedQueryTokens(tokens, field).createBooleanQuery()

        val scores = sectionSearcher.search(fieldQuery, 5000)
            .scoreDocs
            .map { sc -> sc.doc to sc.score.toDouble() }
            .toMap()


        sectionContainers.forEachIndexed { index, container ->
            sf.sectionScores[index] = scores[container.docId] ?: 0.0
        }
    }

    fun addUnigrams(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = NormType.ZSCORE) =
            fmt.addFeature3(FeatureEnum.SECTION_UNIGRAMS, wt, norm) { qd, sf ->
                queryField(qd, sf, IndexFields.FIELD_UNIGRAM) }

    fun addBigrams(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = NormType.ZSCORE) =
            fmt.addFeature3(FeatureEnum.SECTION_BIGRAMS, wt, norm) { qd, sf ->
                queryField(qd, sf, IndexFields.FIELD_BIGRAM) }

    fun addHeading(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = NormType.ZSCORE) =
            fmt.addFeature3(FeatureEnum.SECTION_HEADING, wt, norm) { qd, sf ->
                queryField(qd, sf, IndexFields.FIELD_SECTION_HEADING) }

}