package features.document

import lucene.containers.QueryData
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import org.apache.lucene.search.similarities.BasicStats
import org.apache.lucene.search.similarities.Similarity
import org.apache.lucene.search.similarities.SimilarityBase
import utils.AnalyzerFunctions

enum class Similarities(val similarity: Similarity) {
//    TFIDF(TFIDFSimilarity())
}

val TFIDF = object : SimilarityBase() {
    override fun score(stats: BasicStats, freq: Float, docLen: Float): Float {
        val idf = stats
            .run { Math.log10(numberOfDocuments.toDouble() / totalTermFreq) }
            .toFloat()

        val tf = stats.docFreq / docLen
        return idf * tf
    }

    override fun toString(): String {
        return "hi"
    }

}


//fun featAddLuceneSimilarity(qd: QueryData, similarity: Similarity): List<Double> = with(qd) {
//    indexSearcher.setSimilarity(similarity)
//    return tops.scoreDocs
//        .map { scoreDoc ->
//            indexSearcher.explain(queryBoolean, scoreDoc.doc).value.toDouble()
//        }
//}

/**
 * Func: featSplitSim
 * Desc: Given a feature that scores according to query and TopDocs, reweights score based
 *       on section.
 */
fun featSplitSim(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                 func: (String, TopDocs, IndexSearcher) -> List<Double>,
                 secWeights: List<Double> = listOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0)): List<Double> {

    // Splits query into sections and turns into filtered token list
    val sections = query.split("/")
        .map { section -> AnalyzerFunctions
            .createTokenList(section, useFiltering = true)
            .joinToString(" ")}
        .toList()

    // Given section weights, applies scoring function to each section
    val results = secWeights.zip(sections)
        .filter { (weight, section) -> weight != 0.0 }
        .map { (weight, section) ->
            func(section, tops, indexSearcher).map { result -> result * weight}}

    // Folds each list of section scores (for each document) into a single list of scores for each document
    val finalList = try{ results.reduce { acc, list ->  acc.zip(list).map { (l1, l2) -> l1 + l2 }} }
    catch(e: UnsupportedOperationException) { (0 until tops.scoreDocs.size).map { 0.0 }}
    return finalList
}

/**
 * Func: featSectionComponent
 * Desc: Reweights BM25 score of sections in query and returns a score that is a sum of these reweighted scores.
 */
fun featSectionComponent(query: String, tops: TopDocs, indexSearcher: IndexSearcher): List<Double> {
    // Parse into sections and turn into a list of boolean queries
    val termQueries = query.split("/")
        .map { section -> AnalyzerFunctions
            .createTokenList(section, useFiltering = true)
            .joinToString(" ")}
        .map { section -> AnalyzerFunctions.createQuery(section)}
        .toList()

    // Zip section queries with weights
    val weights = listOf(0.200983, 0.099785, 0.223777, 0.4754529531)
    val validQueries = weights.zip(termQueries)

    // Apply BM25 to each section and sum up the results according to weights on each section
    return tops.scoreDocs
        .map { scoreDoc ->
            validQueries.map { (weight, boolQuery) ->
                indexSearcher.explain(boolQuery, scoreDoc.doc).value.toDouble() * weight }
                .sum()
        }
}

