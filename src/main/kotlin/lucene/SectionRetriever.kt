package lucene

import lucene.containers.*
import lucene.indexers.IndexFields
import lucene.indexers.getString
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import utils.AnalyzerFunctions
import utils.misc.mapOfLists
import utils.parallel.pmap
import java.io.File


class SectionRetriever(val sectionSearcher: SectionSearcher,
                       val indexSearcher: IndexSearcher,
                       queries: List<Pair<String, TopDocs>>,
                       qrelLoc: String, val paragraphRetrieve: ParagraphRetriever) {

    private val relevancies =
            if (qrelLoc == "") null
            else
                File(qrelLoc)
                    .bufferedReader()
                    .readLines()
                    .mapOfLists { it.split(" ").let { it[0] to (it[2] to it[3].toInt()) } }
                    .mapValues { it.value.toMap() }
//                    .toSet()



    val sectionContainers =
            queries
                .withIndex().pmap {index ->
                val (query, tops) = index.value
                    val seen = HashSet<Int>()
                    val entityNames = (getCandidateEntityNames(tops, index.index) )
//                            doSectionQuery(query))
                    val sections = getCandidateEntityData(entityNames).toList()
                        .filter { it.second != "" && seen.add(it.first) }
                    sections
                        .mapIndexed { eIndex: Int, (docId, sectionId) ->
                            DocContainer.createDocumentContainer<IndexType.SECTION>(
                                    name = sectionId,
                                    qid = index.index + 5001,
                                    docId = docId,
                                    searcher = sectionSearcher,
                                    index = eIndex,
                                    query = query,
//                            isRelevant = relevancies?.contains(query.split("/").first() to entity.name.toLowerCase()) ?: false
                                    isRelevant = relevancies?.get(query.split("/").first())?.get(sectionId) ?: 0
                            )
                        }.toList()
                }

//    private fun filterNeighbors(containers: List<EntityContainer>): List<EntityContainer> {
//        val nearby = HashSet<Int>()
//        containers.forEachIndexed { index, entityContainer ->
//            if (entityContainer.isRelevant) {
//                nearby += Math.max(index - 1, 0)
//                nearby += Math.min(index + 1, containers.size)
//                nearby += index
//            }
//        }
//        return containers.filterIndexed { index, paragraphContainer ->  index in nearby }
//    }


    private fun getCandidateEntityNames(tops: TopDocs, index: Int): List<String> {
        val entities = paragraphRetrieve
            .paragraphContainers[index].map { pC -> pC.doc().get(IndexFields.FIELD_PID.field)}
        return entities
    }

    private fun getCandidateEntityData(entities: List<String>): Sequence<Pair<Int, String>> {
        val result = entities.toSortedSet()
            .asSequence()
            .mapNotNull { pid ->
                val q = AnalyzerFunctions.createQuery(pid, field = IndexFields.FIELD_CHILDREN_IDS.field)
                val secDocs = sectionSearcher.search(q, 1).scoreDocs
                if (secDocs.size > 1) println("Uh oh: ${secDocs.size}")
                secDocs.firstOrNull()
            }
            .map { it.doc to IndexFields.FIELD_SECTION_ID.getString(sectionSearcher.doc(it.doc)) }
        return result
    }

    private fun doSectionQuery(query: String): List<String> {
        val q = AnalyzerFunctions.createQuery(query, IndexFields.FIELD_UNIGRAM.field,
                analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED,
                useFiltering = true)

        val results = sectionSearcher.search(q, 20)
        return results.scoreDocs.map { sectionSearcher.getIndexDoc(it.doc).id() }
    }


}