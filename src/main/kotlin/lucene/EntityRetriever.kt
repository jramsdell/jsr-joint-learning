package lucene

import entity.EntityData
import entity.EntityDatabase
import lucene.containers.EntityContainer
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import utils.parallel.pmap
import java.io.File


class EntityRetriever(val db: EntityDatabase,
                      val indexSearcher: IndexSearcher,
                      queries: List<Pair<String, TopDocs>>,
                      qrelLoc: String) {

    private val relevancies =
            if (qrelLoc == "") null
            else
                File(qrelLoc)
                    .bufferedReader()
                    .readLines()
                    .map { it.split(" ").let { it[0] to cleanQrelEntry(it[2]) } }
                    .toSet()




    val entityContainers =
            queries.withIndex().pmap {index ->
                val (query, tops) = index.value
//                val entityNames = getCandidatesFromQuery(query) + getCandidateEntityNames(tops) // skip query for now
                val entityNames = getCandidateEntityNames(tops)
                val entities = getCandidateEntityData(entityNames)
                entities.map { entity: EntityData ->
                    EntityContainer(
                            name = entity.name,
                            qid = index.index + 1,
                            docId = entity.docId,
                            doc = db.searcher.doc(entity.docId),
                            isRelevant = relevancies?.contains(query to entity.name) ?: false
                    )
                }
            }.toList()

    private fun getCandidateEntityNames(tops: TopDocs) =
            tops.scoreDocs.flatMap {  scoreDoc ->
                val doc = indexSearcher.doc(scoreDoc.doc)
                doc.getValues("spotlight").toList() }

    private fun getCandidateEntityData(entities: List<String>) =
            entities.toSortedSet()
                .mapNotNull(db::getEntityDocId)
                .map(db::getEntityByID)

    private fun getCandidatesFromQuery(query: String) =
        db.getEntityDocuments(query, 100)
            .map { doc -> doc.get("name") }

    private fun cleanQrelEntry(entry: String) =
            entry.replace("enwiki:", "")
                .replace("%20", "_")

}