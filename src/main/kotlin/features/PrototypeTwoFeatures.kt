package features

import info.debatty.java.stringsimilarity.Jaccard
import info.debatty.java.stringsimilarity.JaroWinkler
import info.debatty.java.stringsimilarity.NormalizedLevenshtein
import info.debatty.java.stringsimilarity.SorensenDice
import info.debatty.java.stringsimilarity.interfaces.StringDistance
import language.GramStatType
import language.KotlinGramAnalyzer
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import utils.AnalyzerFunctions
import utils.CONTENT
import utils.defaultWhenNotFinite
import kotlin.math.ln

/**
 * Function: addStringDistanceFunction
 * Description: In this method, I try to the distance (or similarity) between the terms (after splitting)
 *              and the entities in each document.
 * @params dist: StingDistance interface (from debatty stringsimilarity library)
 */
fun featAddStringDistanceFunction(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                                  dist: StringDistance): List<Double> {
//        val tokens = retrieveSequence(query)
    val tokens = AnalyzerFunctions.createTokenList(query, useFiltering = true)

    // Map this over the score docs, taking the average similarity between query tokens and entities
    return tops.scoreDocs
        .map { scoreDoc ->
            //            val doc = formatter.indexSearcher.doc(scoreDoc.doc)
            val doc = indexSearcher.doc(scoreDoc.doc)
            val entities = doc.getValues("spotlight").map { it.replace("_", " ") }
            if (entities.isEmpty()) 0.0 else
            // This is the actual part where I average the results using the distance function
                tokens.flatMap { q -> entities.map { e -> 1 - dist.distance(q, e)  } }
                    .average()
                    .defaultWhenNotFinite(0.0)
        }
}

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

/**
 * Func: featStringSimilarityComponent
 * Desc: Combines Jaccard Similarity, JaroWinkler Similarity, NormalizedLevenshstein, and SorensenDice coefficient
 *       by taking a weighted combined of these scores. The scores evaluate the similarity of the query's terms to
 *       that of the spotlight entities in each of the documents.
 */
fun featStringSimilarityComponent(query: String, tops: TopDocs, indexSearcher: IndexSearcher): List<Double> {
    val weights = listOf(0.540756, 0.0, 0.0605, -0.3986067)
    val sims = listOf<StringDistance>(Jaccard(), JaroWinkler(), NormalizedLevenshtein(), SorensenDice())
    val simTrials = weights.zip(sims)

    // Apply list of string similarity functions to query and documents get results
    val simResults = simTrials.map { (weight, sim) ->
        featAddStringDistanceFunction(query, tops, indexSearcher, sim)
            .map { score -> score * weight }
    }

    // Fold each string similarity function's document scores into a single list of document scores
    return simResults.reduce { acc, scores -> acc.zip(scores).map { (l1, l2) -> l1 + l2 } }
}




/**
 * Func: featSDM
 * Desc: My best attempt at an SDM model (using Dirichlet smoothing). The individual components have
 *       already been weighted (see training examples) and the final score is a weighted combination
 *       of unigram, bigram, and windowed bigram.
 */
fun featSDM(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
            gramAnalyzer: KotlinGramAnalyzer, alpha: Double,
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



