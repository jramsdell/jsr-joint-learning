package features.document

import language.GramAnalyzer
import language.GramStatType
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import utils.AnalyzerFunctions
import utils.CONTENT

/**
 * Func: featSDM
 * Desc: My best attempt at an SDM model (using Dirichlet smoothing). The individual components have
 *       already been weighted (see training examples) and the final score is a weighted combination
 *       of unigram, bigram, and windowed bigram.
 */
fun featSDM(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
            gramAnalyzer: GramAnalyzer, alpha: Double,
            gramType: GramStatType? = null): List<Double> {

    // Parse query and retrieve a language model for it
    val tokens = AnalyzerFunctions.createTokenList(query, useFiltering = true)
    val cleanQuery = tokens.toList().joinToString(" ")
    val queryCorpus = gramAnalyzer.getCorpusStatContainer(cleanQuery)

    return tops.scoreDocs.map { scoreDoc ->
        val doc = indexSearcher.doc(scoreDoc.doc)
        val text = doc.get(CONTENT)

        // Generate a language model for the given document's text
        val docStat = gramAnalyzer.getLanguageStatContainer(text)
        val (uniLike, biLike, windLike) = gramAnalyzer.getQueryLikelihood(docStat, queryCorpus, alpha)

        // If gram type is given, only return the score of a particular -gram method.
        // Otherwise, used the weights that were learned and combine all three types into a score.
        val weights = listOf(0.9285990421606605, 0.070308081629, -0.0010928762)
        when (gramType) {
            GramStatType.TYPE_UNIGRAM -> uniLike
            GramStatType.TYPE_BIGRAM -> biLike
            GramStatType.TYPE_BIGRAM_WINDOW -> windLike
            else -> uniLike * weights[0] + biLike * weights[1] + windLike * weights[2]
        }
    }
}
