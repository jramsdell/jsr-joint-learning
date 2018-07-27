package features.general

import features.shared.SharedFeature
import lucene.FieldQueryFormatter
import lucene.containers.DocContainer
import lucene.containers.IndexType
import lucene.containers.QueryData
import lucene.containers.TypedSearcher
import lucene.indexers.IndexFields
import utils.AnalyzerFunctions
import utils.lucene.explainScore


object GeneralRankingFeatures {

    private fun<T: IndexType> queryField(qd: QueryData, docScores: Array<Double>,
                                         queryField: IndexFields, searcher: TypedSearcher<T>,
                                         containers: List<DocContainer<T>>): Unit = with(qd) {

        val tokens = AnalyzerFunctions.createTokenList(queryString, useFiltering = true,
                analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)
        val fq = FieldQueryFormatter()
        val fieldQuery = fq.addWeightedQueryTokens(tokens, queryField).createBooleanQuery()

        containers.forEach { container ->
            val score = searcher.explainScore(fieldQuery, container.docId)
            docScores[container.index] = score
        }
    }

}