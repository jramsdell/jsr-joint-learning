package lucene

import lucene.containers.ParagraphContainer
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import utils.AnalyzerFunctions
import utils.misc.PID
import utils.misc.mapOfSets
import utils.parallel.pmap
import java.io.File


class ParagraphRetriever(val indexSearcher: IndexSearcher,
                         queries: List<Pair<String, TopDocs>>,
                         qrelLoc: String, includeRelevant: Boolean = false) {

    // If a qrel filepath was given, reads file and creates a set of lucene/paragraph pairs for relevancies
    private val relevancies =
            if (qrelLoc == "") null
            else
                File(qrelLoc)
                    .bufferedReader()
                    .readLines()
                    .mapOfSets { it.split(" ").let { it[0] to it[2] } }

    // Maps queries into lucene containers (stores paragraph and feature information)
    val paragraphContainers =
            queries.withIndex().pmap { index ->
                val (query, tops) = index.value
                val relevantToQuery = relevancies?.get(query) ?: emptySet()

                // bad
//                if (relevancies!!.get(query) == null) {
//                    println(query)
//                }

                val containers = tops.scoreDocs.map { sc ->
                    createParagraphContainer(sc.doc, index.index, query, relevantToQuery, sc.score)
                }

                if (includeRelevant && relevancies != null) include(containers, index.index, query,  relevantToQuery)
                else containers
            }

    private fun include(containers: List<ParagraphContainer>, index: Int, query: String,
                                          relevantToQuery: Set<String>): List<ParagraphContainer> {
        val missingRelevantParagraphs = relevantToQuery - containers.map { p -> p.pid }.toSet()
        val retrievedRelevantParagraphs = retrieveParagraphs(missingRelevantParagraphs.toList())
            .map { docId -> createParagraphContainer(docId, index, query, relevantToQuery, 0.0f) }
        return containers + retrievedRelevantParagraphs
    }

    private fun createParagraphContainer(docId: Int, index: Int,
                                         query: String, relevantToQuery: Set<String>, score: Float): ParagraphContainer {
        val doc = indexSearcher.doc(docId)
        val pid = doc.get(PID)
        return ParagraphContainer(
                pid = pid,
                qid = index + 1,
                isRelevant = relevantToQuery.contains(pid),
                query = query,
                docId = docId,
                doc = doc,
                score = score.toDouble(),
                features = arrayListOf())
    }

    private fun retrieveParagraphs(pids: List<String>): List<Int> =
            pids.mapNotNull { pid ->
                val query = AnalyzerFunctions.createQuery(pid, field = PID)
                indexSearcher.search(query, 1)
                    .scoreDocs
                    .firstOrNull()
                    ?.doc
            }

}
