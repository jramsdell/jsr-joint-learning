package lucene

import lucene.containers.ParagraphContainer
import lucene.containers.QueryContainer
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import utils.AnalyzerFunctions
import utils.lucene.getIndexSearcher
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

                val containers = tops.scoreDocs.map { sc ->
//                    val doc = indexSearcher.doc(sc.doc)
//                    val pid = doc.get(PID)
                    createParagraphContainer(sc.doc, index.index, relevantToQuery)

//                    ParagraphContainer(
//                            pid = pid,
//                            qid = index.index + 1,
////                            isRelevant = relevancies?.run { contains(Pair(query, pid)) } ?: false,
//                            isRelevant = relevantToQuery.contains(pid),
//                            docId = sc.doc,
//                            doc = doc,
//                            features = arrayListOf())
                }

                if (includeRelevant && relevancies != null) include(containers, index.index, relevantToQuery)
                else containers
            }

    private fun include(containers: List<ParagraphContainer>, index: Int,
                                          relevantToQuery: Set<String>): List<ParagraphContainer> {
        val missingRelevantParagraphs = relevantToQuery - containers.map { p -> p.pid }.toSet()
        val retrievedRelevantParagraphs = retrieveParagraphs(missingRelevantParagraphs.toList())
            .map { docId -> createParagraphContainer(docId, index, relevantToQuery) }
        return containers + retrievedRelevantParagraphs
    }

    private fun createParagraphContainer(docId: Int, index: Int, relevantToQuery: Set<String>): ParagraphContainer {
        val doc = indexSearcher.doc(docId)
        val pid = doc.get(PID)
        return ParagraphContainer(
                pid = pid,
                qid = index + 1,
                isRelevant = relevantToQuery.contains(pid),
                docId = docId,
                doc = doc,
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
