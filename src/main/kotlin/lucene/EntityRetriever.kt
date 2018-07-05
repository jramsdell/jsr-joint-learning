package lucene

import entity.EntityData
import entity.EntityDatabase
import lucene.containers.EntityContainer
import lucene.indexers.IndexFields
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import utils.parallel.pmap
import java.io.File


class EntityRetriever(val db: EntityDatabase,
                      val indexSearcher: IndexSearcher,
                      queries: List<Pair<String, TopDocs>>,
                      qrelLoc: String, val paragraphRetrieve: ParagraphRetriever) {

    private val relevancies =
            if (qrelLoc == "") null
            else
                File(qrelLoc)
                    .bufferedReader()
                    .readLines()
                    .map { it.split(" ").let { it[0] to cleanQrelEntry(it[2]) } }
                    .toSet()



    val entityContainers =
            queries
                .withIndex().pmap {index ->
                val (query, tops) = index.value
//                val entityNames = getCandidatesFromQuery(query) + getCandidateEntityNames(tops) // skip query for now
                    val entityNames = getCandidateEntityNames(tops, index.index)
                    val entities = getCandidateEntityData(entityNames)
                    entities.map { entity: EntityData ->
                    EntityContainer(
                            name = entity.name,
                            qid = index.index + 1,
                            docId = entity.docId,
                            searcher = db.searcher,
                            isRelevant = relevancies?.contains(query.split("/").first() to entity.name.toLowerCase()) ?: false
                    )
                }.toList()
            }

    private fun filterNeighbors(containers: List<EntityContainer>): List<EntityContainer> {
        val nearby = HashSet<Int>()
        containers.forEachIndexed { index, entityContainer ->
            if (entityContainer.isRelevant) {
                nearby += Math.max(index - 1, 0)
                nearby += Math.min(index + 1, containers.size)
                nearby += index
            }
        }
        return containers.filterIndexed { index, paragraphContainer ->  index in nearby }
    }


    private fun getCandidateEntityNames(tops: TopDocs, index: Int): List<String> {
        val seen = HashSet<String>()
        val entities = paragraphRetrieve
            .paragraphContainers[index].flatMap { pC -> pC.doc().get(IndexFields.FIELD_NEIGHBOR_ENTITIES.field).split(" ") }
//            .paragraphContainers[index].flatMap { pC -> pC.doc.get(IndexFields.FIELD_ENTITIES.field).split(" ") }
            .filter { entity -> seen.add(entity.toUpperCase()) }
//            tops.scoreDocs.flatMap {  scoreDoc ->
//                val doc = indexSearcher.doc(scoreDoc.doc)
//                doc.getValues("spotlight").toList() }
        return entities
    }

    private fun getCandidateEntityData(entities: List<String>): Sequence<EntityData> {
        val seen = HashSet<String>()
        val result = entities.toSortedSet()
            .asSequence()
            .mapNotNull(db::getEntityDocId)
            .map(db::getEntityByID)
            .filter { doc -> seen.add(doc.name) }
        return result

    }

    private fun getCandidatesFromQuery(query: String) =
        db.getEntityDocuments(query, 5)
            .map { doc -> doc.get("abstract") }

    private fun cleanQrelEntry(entry: String) =
            entry.replace("enwiki:", "")
                .replace("%20", "_")
                .toLowerCase()

}