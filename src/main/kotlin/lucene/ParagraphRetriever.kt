package lucene

import lucene.containers.DocContainer
import lucene.containers.IndexType.*
import lucene.indexers.IndexFields
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import utils.AnalyzerFunctions
import utils.lucene.searchFirstOrNull
import utils.misc.PID
import utils.misc.mapOfLists
import utils.parallel.pmap
import java.io.File
import java.util.*


/**
 * Class: ParagraphRetriever
 * Desc: This class retrieves candidate paragraphs from query results (see [QueryRetriever]) obtained via BM25.
 *
 * @param[indexSearcher] IndexSearcher pointing to Lucene corpus of paragraphs
 * @param[queries] A paired list of query strings and of [TopDocs] retrieves via BM25 search.
 * @param[qrelLoc] Location of .qrel file (optional): used to determine if paragraphs are relevant to query
 */
class ParagraphRetriever(val indexSearcher: IndexSearcher,
                         queries: List<Pair<String, TopDocs>>,
                         qrelLoc: String, includeRelevant: Boolean = false,
                         doFiltered: Boolean = false) {

    // If a qrel filepath was given, reads file and creates a set of lucene/paragraph pairs for relevancies
    private val relevancies =
            if (qrelLoc == "") null
            else
                File(qrelLoc)
                    .bufferedReader()
                    .readLines()
//                    .mapOfSets { it.split(" ").let { it[0] to it[2] } }
                    .mapOfLists { it.split(" ").let { it[0] to (it[2] to it[3].toInt()) } }
                    .mapValues { it.value.toMap() }



    // Maps queries into lucene containers (stores paragraph and feature information)
    val paragraphContainers =
            queries.withIndex().pmap { index ->
                val (query, tops) = index.value
//                val relevantToQuery = relevancies?.get(query) ?: emptySet()
                val relevantToQuery = relevancies?.get(query) ?: emptyMap()
                val seen = HashSet<String>()

                val containers = tops.scoreDocs.mapIndexed { pIndex, sc ->
                    createParagraphContainer(pIndex, sc.doc, index.index, query, relevantToQuery, sc.score) }
//                    .filter { seen.add(it.pid) }

                containers
//                if (includeRelevant && relevancies != null) include(containers, index.index, query,  relevantToQuery)
//                else containers

            }.let { result ->  if (doFiltered) result.map(this::filterNeighbors) else result }


    private fun filterNeighbors(containers: List<DocContainer<PARAGRAPH>>): List<DocContainer<PARAGRAPH>> {
        val nearby = HashSet<Int>()
        containers.forEachIndexed { index, paragraphContainer ->
            if (paragraphContainer.isRelevant > 0) {
                nearby += Math.max(index - 1, 0)
                nearby += Math.max(index - 2, 0)
                nearby += Math.min(index + 1, containers.size)
                nearby += Math.min(index + 2, containers.size)
                nearby += index
            }
        }
        return containers.filterIndexed { index, paragraphContainer ->  index in nearby }
    }

//    private fun include(containers: List<ParagraphContainer>, index: Int, query: String,
//                        relevantToQuery: Map<String, Int>): List<ParagraphContainer> {
//        val missingRelevantParagraphs = relevantToQuery - containers.map { p -> p.pid }.toSet()
//        val retrievedRelevantParagraphs = retrieveParagraphs(missingRelevantParagraphs.toList())
//            .mapIndexed { pIndex, docId -> createParagraphContainer(pIndex, docId, index, query, relevantToQuery, 0.0f) }
//        return containers + retrievedRelevantParagraphs
//    }

    /**
     * Func: createParagraphContainer
     * Desc: Creates a paragraph container given the current query result.
     *       This represents a candidate document retrieved via BM25.
     *
     * @param[docId] ID of the paragraph document stored in the Lucene corpus
     * @param[index] Query index (i.e. what # query does this paragraph belong to)
     * @param[query] Query string (that was used to do a BM25 search to retrieve candidates)
     * @param[relevantToQuery] A set of PIDs that are relevant to the query (based on parsed qrel file)
     * @param[score] The score for this paragraph document when it was retrieved using BM25
     *
     * @return[ParagraphContainer] Candidate paragraph document retrieved via BM25
     */
    private fun createParagraphContainer(pIndex: Int, docId: Int, index: Int,
                                         query: String, relevantToQuery: Map<String, Int>, score: Float)
            : DocContainer<PARAGRAPH> {
        val doc = indexSearcher.doc(docId)
        val pid = doc.get(PID)
        return DocContainer.createDocumentContainer(
                name = pid,
                qid = index + 1,
//                isRelevant = relevantToQuery.contains(pid),
                isRelevant = relevantToQuery[pid] ?: 0,
                query = query,
                docId = docId,
                searcher = indexSearcher,
                score = score.toDouble(),
                index = pIndex)
    }

    private fun retrieveParagraphs(pids: List<String>): List<Int> =
            pids.mapNotNull { pid ->
                val query = AnalyzerFunctions.createQuery(pid, field = PID)
                indexSearcher.search(query, 1)
                    .scoreDocs
                    .firstOrNull()
                    ?.doc
            }

    /**
     * Func: writeEntityQrelsUsingParagraphQrels
     * Desc: Derives a section-level and page-level entity qrel file using
     *       entities that are linked to relevant paragraphs.
     *
     *       Results are saved to entity_section.qrel and entity_page.qrel
     */
//    fun writeEntityQrelsUsingParagraphQrels() {
//        val relevantEntities = createRelevantEntities().toSortedMap()
//        val sectionQrelFile = File("entity_section.qrel").bufferedWriter()
//        val pageQrelFile = File("entity_page.qrel").bufferedWriter()
//
//        val pageLevel = relevantEntities
//            .entries
//            .map { (query, rels) -> query.split("/").first() to rels.toList() }
//            .groupOfSetsFlattened()
//            .toSortedMap()
//
//
//        val getFormattedString = { entityQrels: SortedMap<String, Set<String>> ->
//            entityQrels
//                .flatMap { (query: String, rels: Set<String>) ->
//                    rels.map { relEntity: String ->  "$query 0 $relEntity 1"} }
//                .joinToString("\n")
//        }
//
//        sectionQrelFile.write(getFormattedString(relevantEntities))
//        pageQrelFile.write(getFormattedString(pageLevel))
//        sectionQrelFile.close()
//        pageQrelFile.close()
//    }

    /**
     * Func: createRelevantEntities
     * Desc: Returns a mapping of queries to sets of relevant entity IDs.
     *       Does so by extracting entities linked to relevant paragraphs.
     *
     * @see relevancies (a map of queries to sets of relevant paragraphs)
     * @return[Set<String>] Set of entity IDs linked to paragraphs
     */
//    private fun createRelevantEntities(): Map<String, Set<String>> {
//        if (relevancies == null)
//            return emptyMap()
//        return relevancies.mapValues { (_, relevantParagraphs) ->
//            queryPIDS(relevantParagraphs.toList()) }
//    }

    /**
     * Func: queryPIDS
     * Desc: Retrieves paragraphs from Lucene corpus using a list of PIDs and then
     *       extracts list of entities linked to these paragraphs.
     *
     * @return[Set<String>] Set of entity IDs linked to paragraphs
     */
    private fun queryPIDS(pids: List<String>) =
            pids
                // Retrieve paragraph document IDs from index based on their PIDs
                .mapNotNull { pid: String ->
                    val query = AnalyzerFunctions.createQuery(pid, field = IndexFields.FIELD_PID.field)
                    indexSearcher.searchFirstOrNull(query)?.doc }

                // Retrieve document from index and get spotlight/tagme entities
                .flatMap {  docId: Int ->
                    val doc = indexSearcher.doc(docId)
                    doc.get(IndexFields.FIELD_ENTITIES.field).split(" ") }
                //    doc.getValues(IndexFields.FIELD_ENTITIES.field).toList() }
                .toSet()




}
