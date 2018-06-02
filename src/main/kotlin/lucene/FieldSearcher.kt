package lucene

import language.GramAnalyzer
import lucene.containers.FieldNames
import lucene.containers.FieldNames.*
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.search.TermQuery
import utils.AnalyzerFunctions
import utils.AnalyzerFunctions.AnalyzerType.*
import utils.WeightedTermData
import utils.stats.normalize
import utils.stats.takeMostFrequent


class FieldQueryFormatter() {
    val queries: ArrayList<Query>  = ArrayList()

    private fun parseUnigrams(tokens: List<String>) =
        GramAnalyzer.countUnigrams(tokens)
            .takeMostFrequent(15)
            .keys.toList()
//            .normalize()

    private fun parseBigrams(tokens: List<String>) =
            GramAnalyzer.countBigrams(tokens)
                .takeMostFrequent(15)
                .keys.toList()
//                .normalize()

    private fun parseWindowedBigrams(tokens: List<String>) =
            GramAnalyzer.countWindowedBigrams(tokens)
                .takeMostFrequent(15)
                .keys.toList()
//                .normalize()

    fun addWeightedQueryTokens(tokens: List<String>, fieldName: FieldNames, weight: Double = 1.0): FieldQueryFormatter {

        val result = when (fieldName) {
            FIELD_BIGRAMS -> parseBigrams(tokens)
            FIELD_WINDOWED_BIGRAMS -> parseWindowedBigrams(tokens)
            else -> parseUnigrams(tokens)
        }

        queries += AnalyzerFunctions.weightedTermQueries(result, fieldName.field, weight)
        return this
    }

    fun addNormalQuery(text: String, fieldName: FieldNames, weight: Double) {
        AnalyzerFunctions.createTokenList(text, useFiltering = true)
            .mapTo(queries) { token -> AnalyzerFunctions.boostedTermQuery(fieldName.name, token, weight) }
    }

    fun createBooleanQuery(): BooleanQuery =
            AnalyzerFunctions.buildBooleanQuery(queries)




}