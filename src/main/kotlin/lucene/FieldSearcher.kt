package lucene

import language.GramAnalyzer
import lucene.indexers.IndexFields
import lucene.indexers.IndexFields.*
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.Query
import utils.AnalyzerFunctions
import utils.AnalyzerFunctions.AnalyzerType.*
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

    fun addWeightedQueryTokens(tokens: List<String>, fieldName: IndexFields, weight: Double = 1.0): FieldQueryFormatter {

        val result = when (fieldName) {
            FIELD_BIGRAM, FIELD_NEIGHBOR_BIGRAMS, FIELD_JOINT_BIGRAMS          -> parseBigrams(tokens)
            FIELD_WINDOWED_BIGRAM, FIELD_NEIGHBOR_WINDOWED, FIELD_JOINT_WINDOWED -> parseWindowedBigrams(tokens)
            else                  -> parseUnigrams(tokens)
        }

        queries += AnalyzerFunctions.weightedTermQueries(result, fieldName.field, weight)
        return this
    }

    fun addNormalQuery(text: String, fieldName: IndexFields, weight: Double) {
        AnalyzerFunctions.createTokenList(text, useFiltering = true)
            .mapTo(queries) { token -> AnalyzerFunctions.boostedTermQuery(fieldName.name, token, weight) }
    }

    fun doSectionQueries(text: String, sectionWeights: List<Double>? = null): FieldQueryFormatter {
        val sections = AnalyzerFunctions.splitSections(text, analyzerType = ANALYZER_ENGLISH_STOPPED)
//        val finalSectionWeights = sectionWeights ?: (0 until sections.size).map { 1.0 }
        val finalSectionWeights = listOf(0.10502783449310671 , 0.08903713107747403 , 0.1694307229058893 , 0.46667935754481665 , 0.08491247698935675 , 0.08491247698935675 )
        val gramWeights = listOf(0.9346718895308014 to FIELD_UNIGRAM,
                0.049745249968994265 to FIELD_BIGRAM, 0.015582860500204451 to FIELD_WINDOWED_BIGRAM )

        gramWeights.forEach { (gramWeight, gram) ->
            sections.zip(finalSectionWeights)
                .forEach { (section, sectionWeight) ->
                    addWeightedQueryTokens(section, gram, sectionWeight * gramWeight)
                }
        }
        return this
    }

    fun createBooleanQuery(): BooleanQuery =
            AnalyzerFunctions.buildBooleanQuery(queries)




}