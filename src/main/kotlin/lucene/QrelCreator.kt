package lucene

import lucene.indexers.IndexFields
import org.apache.lucene.search.IndexSearcher
import utils.AnalyzerFunctions
import utils.lucene.searchFirstOrNull
import utils.misc.groupOfSetsFlattened
import utils.misc.mapOfSets
import java.io.File
import java.util.*


class QrelCreator(paragraphQrel: String, entityQrel: String, val indexSearcher: IndexSearcher) {

    // If a qrel filepath was given, reads file and creates a set of lucene/paragraph pairs for relevancies
    private val paragraphRelevancies =
                File(paragraphQrel)
                    .bufferedReader()
                    .readLines()
                    .mapOfSets { it.split(" ").let { it[0] to it[2] } }

    private val entityRelevancies =
                File("/home/jsc57/data/benchmark/benchmarkY1/benchmarkY1-train/train.pages.cbor-hierarchical.entity.qrels")
                    .bufferedReader()
                    .readLines()
                    .map { it.split(" ").let { it[0].split("/").first() to cleanQrelEntry(it[2]) } }
                    .toSet()

    private val entityRelevanciesHierarchical =
            File("/home/jsc57/data/benchmark/benchmarkY1/benchmarkY1-train/train.pages.cbor-hierarchical.entity.qrels")
                .bufferedReader()
                .readLines()
                .map { it.split(" ").let { it[0] to cleanQrelEntry(it[2]) } }
                .toSet()

    private fun cleanQrelEntry(entry: String) =
            entry.replace("enwiki:", "")
                .replace("%20", "_")

    /**
     * Func: writeEntityQrelsUsingParagraphQrels
     * Desc: Derives a section-level and page-level entity qrel file using
     *       entities that are linked to relevant paragraphs.
     *
     *       Results are saved to entity_section.qrel and entity_page.qrel
     */


    fun writeEntityQrelsUsingParagraphQrels() {
        val relevantEntities = createRelevantEntities()
            .mapValues { (query, relEntities) ->
                val page = query.split("/").first()
                relEntities.filter { entity -> page to entity in entityRelevancies }
                    .toSet() }
            .toSortedMap()

        val sectionQrelFile = File("entity_section.qrel").bufferedWriter()
        val pageQrelFile = File("entity_page.qrel").bufferedWriter()

        val pageLevel = relevantEntities
            .entries
            .map { (query, rels) -> query.split("/").first() to rels.toList() }
            .groupOfSetsFlattened()
            .toSortedMap()


        val getFormattedString = { entityQrels: SortedMap<String, Set<String>> ->
            entityQrels
                .flatMap { (query: String, rels: Set<String>) ->
                    rels.map { relEntity: String ->  "$query 0 $relEntity 1"} }
                .joinToString("\n")
                .replace("_", "%20")
        }

        sectionQrelFile.write(getFormattedString(relevantEntities))
        pageQrelFile.write(getFormattedString(pageLevel))
        sectionQrelFile.close()
        pageQrelFile.close()
    }

    /**
     * Func: createRelevantEntities
     * Desc: Returns a mapping of queries to sets of relevant entity IDs.
     *       Does so by extracting entities linked to relevant paragraphs.
     *
     * @see relevancies (a map of queries to sets of relevant paragraphs)
     * @return[Set<String>] Set of entity IDs linked to paragraphs
     */
    private fun createRelevantEntities(): Map<String, Set<String>> {
        return paragraphRelevancies.mapValues { (_, relevantParagraphs) ->
            queryPIDS(relevantParagraphs.toList()) }
    }

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
                    doc.getValues(IndexFields.FIELD_ENTITIES.field).toList() }
                .toSet()


}