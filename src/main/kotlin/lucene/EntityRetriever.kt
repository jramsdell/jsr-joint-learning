package lucene

import entity.EntityData
import entity.EntityDatabase
import lucene.containers.*
import lucene.indexers.IndexFields
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import utils.misc.mapOfLists
import utils.parallel.pmap
import java.io.File


class EntityRetriever(val entitySearcher: EntitySearcher,
                      val paragraphSearcher: ParagraphSearcher,
                      queries: List<Pair<String, TopDocs>>,
                      qrelLoc: String, val paragraphRetrieve: ParagraphRetriever) {

    private val relevancies =
            if (qrelLoc == "") null
            else
                File(qrelLoc)
                    .bufferedReader()
                    .readLines()
                    .mapOfLists { it.split(" ").let { it[0] to (cleanQrelEntry(it[2]) to it[3].toInt()) } }
                    .mapValues { it.value.toMap() }
//                    .toSet()



    val entityContainers =
            queries
                .withIndex().pmap {index ->
                val (query, tops) = index.value
//                val entityNames = getCandidatesFromQuery(query) + getCandidateEntityNames(tops) // skip query for now
                    val entityNames = getCandidateEntityNames(tops, index.index)
                    getCandidateEntityData(entityNames)
                        .mapIndexed { eIndex: Int, (name, docId) ->
                    DocContainer.createDocumentContainer<IndexType.ENTITY>(
                            name = name,
                            qid = index.index + 1,
                            docId = docId,
                            searcher = entitySearcher,
                            index = eIndex,
                            query = query,
//                            isRelevant = relevancies?.contains(query.split("/").first() to entity.name.toLowerCase()) ?: false
                            isRelevant = relevancies?.get(query.split("/").first())?.get(name.toLowerCase()) ?: 0
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
        val seen = HashSet<String>()
        val entities = paragraphRetrieve
//            .paragraphContainers[index].flatMap { pC -> pC.doc().get(IndexFields.FIELD_ENTITIES.field).split(" ") }
            .paragraphContainers[index].flatMap { pC -> pC.doc().neighborEntities().split(" ") }
//            .paragraphContainers[index].flatMap { pC -> pC.doc.get(IndexFields.FIELD_ENTITIES.field).split(" ") }
            .filter { entity -> seen.add(entity.toUpperCase()) }
//            tops.scoreDocs.flatMap {  scoreDoc ->
//                val doc = indexSearcher.doc(scoreDoc.doc)
//                doc.getValues("spotlight").toList() }
        return entities
    }

    private fun getCandidateEntityData(entities: List<String>): Sequence<Pair<String, Int>> {
        val seen = HashSet<String>()
        val result = entities.toSortedSet()
            .asSequence()
            .mapNotNull { entitySearcher.getDocumentByField(it) }
            .map { it.name() to it.docId }
            .filter { (name, _) -> seen.add(name) }
        return result

    }

//    private fun getCandidatesFromQuery(query: String) =
//        db.getEntityDocuments(query, 5)
//            .map { doc -> doc.get("abstract") }
//
    private fun cleanQrelEntry(entry: String) =
            entry.replace("enwiki:", "")
                .replace("%20", "_")
                .toLowerCase()

}