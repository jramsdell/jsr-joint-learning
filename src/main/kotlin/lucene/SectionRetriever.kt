package lucene

import lucene.containers.DocContainer
import lucene.containers.EntityContainer
import lucene.containers.IndexType
import lucene.containers.SectionContainer
import lucene.indexers.IndexFields
import lucene.indexers.getString
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import utils.AnalyzerFunctions
import utils.misc.mapOfLists
import utils.parallel.pmap
import java.io.File


class SectionRetriever(val sectionSearcher: IndexSearcher,
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
//                val entityNames = getCandidatesFromQuery(query) + getCandidateEntityNames(tops) // skip query for now
                    val entityNames = getCandidateEntityNames(tops, index.index)
                    val sections = getCandidateEntityData(entityNames).toList()
                    sections
                        .mapIndexed { eIndex: Int, (docId, sectionId) ->
                            DocContainer.createDocumentContainer<IndexType.SECTION>(
                                    name = sectionId,
                                    qid = index.index + 1,
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
                sectionSearcher.search(q, 1).scoreDocs.firstOrNull() }
            .map { it.doc to IndexFields.FIELD_SECTION_ID.getString(sectionSearcher.doc(it.doc)) }
        return result

    }


}