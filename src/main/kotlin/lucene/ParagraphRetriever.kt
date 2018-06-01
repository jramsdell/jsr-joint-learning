package lucene

import lucene.containers.ParagraphContainer
import lucene.containers.QueryContainer
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import utils.lucene.getIndexSearcher
import utils.misc.PID
import utils.parallel.pmap
import java.io.File


class ParagraphRetriever(val indexSearcher: IndexSearcher,
                         queries: List<Pair<String, TopDocs>>,
                         qrelLoc: String) {

    // If a qrel filepath was given, reads file and creates a set of lucene/paragraph pairs for relevancies
    private val relevancies =
            if (qrelLoc == "") null
            else
                File(qrelLoc)
                    .bufferedReader()
                    .readLines()
                    .map { it.split(" ").let { it[0] to it[2] } }
                    .toSet()

    // Maps queries into lucene containers (stores paragraph and feature information)
    val paragraphContainers =
            queries.withIndex().pmap {index ->
                val (query, tops) = index.value
                tops.scoreDocs.map { sc ->
                    val pid = indexSearcher.doc(sc.doc).get(PID)

                    ParagraphContainer(
                            pid = pid,
                            qid = index.index + 1,
                            isRelevant = relevancies?.run { contains(Pair(query, pid)) } ?: false,
                            docId = sc.doc,
                            doc = indexSearcher.doc(sc.doc),
                            features = arrayListOf())
                }
            }

}
