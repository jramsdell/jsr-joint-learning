@file:JvmName("KotQueryRetriever")
package lucene

import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import lucene.containers.FieldNames
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.search.*
import utils.lucene.getIndexSearcher
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import utils.AnalyzerFunctions
import utils.misc.PID
import utils.parallel.pmap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Class: QueryRetriever
 * Description: Used to make queries (using BM25) and to parse the results of queries.
 *              Takes an IndexSearcher (or path to an index) for the Lucene index it will lucene with.
 */
class QueryRetriever(val indexSearcher: IndexSearcher, val takeSubset: Boolean = false) {
    constructor(indexPath: String) : this(getIndexSearcher(indexPath))

    val analyzer = StandardAnalyzer()


    /**
     * Function: createQueryString
     * Description: Returns string to be used in querying.
     * @param sectionPath: If non-empty, collapses sections into a single lucene string
     */
    fun createQueryString(page: Data.Page, sectionPath: List<Data.Section>): String =
            page.pageName + sectionPath.joinToString { section -> " " + section.heading  }


    /**
     * Function: getPageQueries
     * Description: Given a lucene location (.cbor file), queries Lucene index with page names.
     * @return List of pairs (lucene string and the Top 100 documents obtained by doing the lucene)
     */
    fun getPageQueries(queryLocation: String): List<Pair<String, TopDocs>> =
            DeserializeData.iterableAnnotations(File(queryLocation).inputStream())
                .map { page ->
                    val queryId = page.pageId
                    val queryStr = createQueryString(page, emptyList())
//                    queryId to indexSearcher.search(createQuery(queryStr), 100) }
                    val booleanQuery = indexSearcher
                        .search(AnalyzerFunctions.createQuery(queryStr, useFiltering = true), 100)
                    queryId to booleanQuery }
                .toList()


    /**
     * Function: getSectionQueries
     * Description: Given a lucene location (.cbor file), queries Lucene index with page name and section names.
     * @return List of pairs (lucene string and the Top 100 documents obtained by doing the lucene)
     */
    fun getSectionQueries(queryLocation: String, doBoostedQuery: Boolean = false): List<Pair<String, TopDocs>> {
        val seen = ConcurrentHashMap<String, String>()
        val replaceNumbers = """(\d+|enwiki:)""".toRegex()
        val counter = AtomicInteger()

        return DeserializeData.iterableAnnotations(File(queryLocation).inputStream())
            .run { if (takeSubset) take(10) else this }
            .flatMap { page ->
                page.flatSectionPaths()
                    .pmap { sectionPath ->
                        val queryId = Data.sectionPathId(page.pageId, sectionPath)
                        var queryStr = createQueryString(page, sectionPath)
                        queryStr = queryStr.replace(replaceNumbers, queryStr)      // remove numbers/enwiki:

                        val query = if (!doBoostedQuery) {
                            indexSearcher
                                .search(AnalyzerFunctions.createQuery(queryStr, useFiltering = false), 100)

                        } else {
//                            val weights = listOf(0.9346718895308014 , 0.049745249968994265 , 0.015582860500204451 )
                            val weights = listOf(0.9346718895308014 , 0.04971515179492379 , 0.015612958674274948 )
                            val terms = AnalyzerFunctions.createTokenList(queryStr, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED,
                                    useFiltering = true)
                            FieldQueryFormatter()
                                .addWeightedQueryTokens(terms, FieldNames.FIELD_UNIGRAMS, weights[0])
                                .addWeightedQueryTokens(terms, FieldNames.FIELD_BIGRAMS, weights[1])
                                .addWeightedQueryTokens(terms, FieldNames.FIELD_WINDOWED_BIGRAMS, weights[2])
                                .createBooleanQuery()
                                .run {
                                    indexSearcher.search(this, 100) }
//                        val query = FieldQueryFormatter().doSectionQueries(queryStr).createBooleanQuery()
//                            .run { indexSearcher.search(this, 100) }
                        }




                        val result = queryId to query
                        result.takeUnless {seen.put(queryId, "") != null}   // remove duplicates
                    }.apply { println(counter.incrementAndGet()) }
            }.filterNotNull()
    }


    /**
     * Function: writeRankingsToFile
     * Description: Writes formatted lucene results to a file (for use with trec_eval)
     */
    private fun writeRankingsToFile(tops: TopDocs, queryId: String, writer: BufferedWriter, queryNumber: Int) {
        (0 until tops.scoreDocs.size).forEach { index ->
            val sd = tops.scoreDocs[index]
            val doc = indexSearcher.doc(sd.doc)
            val paragraphid = doc.get(PID)
            val score = sd.score
            val searchRank = index + 1

            writer.write("$queryId Q$queryNumber $paragraphid $searchRank $score Query\n")
        }
    }

    /**
     * Function: writeQueriesToFile
     * Description: For each pair of lucene name and top 100 documents, write the results to a file.
     * @see writeRankingsToFile
     */
    fun writeQueriesToFile(queries: List<Pair<String, TopDocs>>, out: String = "results.txt") {
        val writer = File(out).bufferedWriter()
        queries.forEachIndexed { index, (query, tops) -> writeRankingsToFile(tops, query, writer, index)}
        writer.flush()
        writer.close()
    }
}

