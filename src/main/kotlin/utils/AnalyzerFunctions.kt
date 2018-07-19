package utils

import lucene.containers.TypedSearcher
import lucene.indexers.IndexFields
import org.apache.lucene.analysis.CharArraySet
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.index.Term
import org.apache.lucene.queryparser.xml.builders.BooleanQueryBuilder
import org.apache.lucene.search.*
import utils.misc.removeAll
import utils.stats.countDuplicates
import utils.stats.normalize
import java.io.File
import java.io.StringReader
import java.lang.Double.max
import java.lang.Double.sum
import kotlin.coroutines.experimental.buildSequence

private fun buildStopWords(): CharArraySet {
    val stops = CharArraySet.copy(EnglishAnalyzer.getDefaultStopSet())
     stops.addAll( File("resources/aggressive_stops.txt").readLines() )
    return stops
}


data class WeightedTermData(
        val weightedTerms: Map<String, Double>,
        val field: String,
        val addiitonalWeight: Double )

/**
 * Static Class: AnalyzerFunctions
 * Desc: Contains a collection of tokenizing / lucene building functions used by other scripts.
 */
object AnalyzerFunctions {
    private val standardAnalyzer = StandardAnalyzer()
    private val englishAnalyzer = EnglishAnalyzer()
    private val englishStopped = EnglishAnalyzer(buildStopWords())
    private val standardAnalyzerStopped = StandardAnalyzer(buildStopWords())

    enum class AnalyzerType { ANALYZER_STANDARD, ANALYZER_ENGLISH, ANALYZER_ENGLISH_STOPPED,
    ANALYZER_STANDARD_STOPPED}

    /**
     * Class: createTokenSequence
     * Description: Given a lucene string, tokenizes it and returns a list of String tokens
     * @param analyzerType: Type of analyzer (english or standard)
     * @param useFiltering: If true, filter out numbers, enwiki: and other noise from lucene
     * @return Sequence<String>
     * @see AnalyzerType
     */
    fun createTokenList(query: String,
                        analyzerType: AnalyzerType = AnalyzerType.ANALYZER_STANDARD,
                        useFiltering: Boolean = false): List<String> {
        val analyzer = when (analyzerType) {
            AnalyzerType.ANALYZER_STANDARD -> standardAnalyzer
            AnalyzerType.ANALYZER_ENGLISH  -> englishAnalyzer
            AnalyzerType.ANALYZER_ENGLISH_STOPPED  -> englishStopped
            AnalyzerType.ANALYZER_STANDARD_STOPPED  -> standardAnalyzerStopped
        }

        val replaceNumbers = """(\d+|enwiki:)""".toRegex()
        val finalQuery =
                if (useFiltering) query.replace(replaceNumbers, "").replace("/", " ")
                else query


        val tokens = ArrayList<String>()
        val tokenStream = analyzer.tokenStream("text", StringReader(finalQuery)).apply { reset() }
        while (tokenStream.incrementToken()) {
            tokens.add(tokenStream.getAttribute(CharTermAttribute::class.java).toString())
        }
        tokenStream.end()
        tokenStream.close()

        return tokens
    }


    /**
     * Class: createQuery
     * Description: Given a lucene string, will create a boolean lucene by breaking it into tokens.
     * @return BooleanQuery: (tokens joined with OR clauses)
     */
    fun createQuery(query: String,
                    field: String = "text",
                    useFiltering: Boolean = false,
                    analyzerType: AnalyzerType = AnalyzerType.ANALYZER_STANDARD,
                    must: Boolean = false): BooleanQuery {

        return createTokenList(query, analyzerType, useFiltering)
            .map { token -> TermQuery(Term(field, token)) }
            .run { buildBooleanQuery(this, must = must) }
    }

    fun createWeightedTermsQuery(terms: List<String>, field: String = "text",
                                 weight: Double = 1.0): BooleanQuery =
            weightedTermQueries(terms, field, weight)
                .fold(BooleanQuery.Builder(),
                        { builder, termQuery -> builder.add(termQuery, BooleanClause.Occur.SHOULD) })
                .build()

    fun weightedTermQueries(terms: List<String>, field: String = "text",
                                 weight: Double = 1.0): List<Query> =
            terms.map { term -> boostedTermQuery(field, term, weight) }

    fun boostedTermQuery(field: String, term: String, weight: Double) =
            BoostQuery(TermQuery(Term(field, term)), weight.toFloat())


    /**
     * Class: createQueryList
     * Description: As createQuery, except that it returns a list of boolean queries (one for each token)
     */
    fun createQueryList(query: String,
                    field: String = "text",
                    useFiltering: Boolean = false,
                    analyzerType: AnalyzerType = AnalyzerType.ANALYZER_STANDARD): List<BooleanQuery> {

        return createTokenList(query, analyzerType, useFiltering)
            .map { token -> TermQuery(Term(field, token))}
            .map { termQuery -> BooleanQuery.Builder().add(termQuery, BooleanClause.Occur.SHOULD).build()}
            .toList()
    }

    fun buildBooleanQuery(queries: List<Query>, must: Boolean = false) =
            queries.fold(BooleanQuery.Builder(),
                    { builder, termQuery -> builder.add(termQuery, if (must) BooleanClause.Occur.MUST else BooleanClause.Occur.SHOULD) })
                .build()

    fun splitSections(query: String, analyzerType: AnalyzerType = AnalyzerType.ANALYZER_STANDARD) =
            query.split("/")
                .map { section -> AnalyzerFunctions
                    .createTokenList(section, useFiltering = true, analyzerType = analyzerType) }


    fun doExpandedQuery(query: String, analyzerType: AnalyzerType, useFiltering: Boolean,
                        originSearcher: TypedSearcher<*>,
                        originField: IndexFields, targetField: IndexFields): BooleanQuery {

//        val qtkens = AnalyzerFunctions.createQueryList(query, originField.field, useFiltering, analyzerType)
        val tlist = AnalyzerFunctions.createTokenList(query, analyzerType, useFiltering)
            .toSet()
        val q = createQuery(query, originField.field, useFiltering, analyzerType)
        val results = HashMap<String, Double>()
        originSearcher.search(q, 100).scoreDocs.map { sd ->
            originSearcher.getIndexDoc(sd.doc).load(originField)
                .split(" ")
                .countDuplicates()
                .forEach { (k,v) -> results.merge(k, v * sd.score.toDouble(), ::sum) }
//                .forEach { (k,v) -> results.merge(k, v.toDouble(), ::sum) }
        }

        val builder = BooleanQuery.Builder()

//        results
//            .toList()
//            .sortedBy { it.second }
//            .filter { it.first !in tlist }
//            .take(5)
//            .forEach { (k,v) ->
//                val tq = BoostQuery(TermQuery(Term(targetField.field, k)), -v.toFloat())
//                builder.add(tq, BooleanClause.Occur.SHOULD)
//            }

        results
            .normalize()
            .toList()
            .sortedBy { it.second }
            .filter { it.first !in tlist }
            .take(5)
            .forEach { (k,v) ->
                val tq = BoostQuery(TermQuery(Term(targetField.field, k)), v.toFloat())
                builder.add(tq, BooleanClause.Occur.SHOULD)
            }

        return builder.build()
    }


}