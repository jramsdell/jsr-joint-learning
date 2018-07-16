package lucene

import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import lucene.containers.*
import lucene.indexers.IndexFields
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.search.*
import utils.lucene.getIndexSearcher
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import utils.AnalyzerFunctions
import utils.misc.PID
import utils.misc.identity
import utils.misc.mapOfLists
import utils.misc.sharedRand
import utils.parallel.pmap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Class: QueryRetriever
 * Description: Used to make queries (using BM25) and to parse the results of queries.
 *              Takes an IndexSearcher (or path to an index) for the Lucene index it will lucene with.
 */
class CombinedRetriever(val paragraphSearcher: ParagraphSearcher,
                        val entitySearcher: EntitySearcher,
                        val sectionSearcher: SectionSearcher,
                        paragraphQrelLoc: String = "",
                        entityQrelLoc: String = "",
                        sectionQrelLoc: String = "",
                        val limit: Int? = null,
                        val isHomogenous: Boolean) {


    private val paragraphRelevancies =
            getRelevancies(paragraphQrelLoc)

    private val entityRelevancies =
            getRelevancies(entityQrelLoc, this::cleanQrelEntry)

    private val sectionRelevancies =
            getRelevancies(sectionQrelLoc)


    private fun getRelevancies(fileLoc: String, f: (String) -> String = ::identity) =
        if (fileLoc == "") emptyMap()
        else
            File(fileLoc)
                .bufferedReader()
                .readLines()
                .mapOfLists { it.split(" ").let { it[0] to (f(it[2]) to it[3].toInt()) } }
                .mapValues { it.value.toMap() }


    private fun cleanQrelEntry(entry: String) =
            entry.replace("enwiki:", "")
                .replace("%20", "_")
                .toLowerCase()

    /**
     * Function: createQueryString
     * Description: Returns string to be used in querying.
     * @param sectionPath: If non-empty, collapses sections into a single lucene string
     */
    fun createQueryString(page: Data.Page, sectionPath: List<Data.Section>): String =
            page.pageName + sectionPath.joinToString { section -> " " + section.heading  }


    /**
     * Function: getPageQueries
     * Description: Given a lucene location (.cbor file), queries Lucene index with page names.
     * @return List of pairs (lucene string and the Top 100 documents obtained by doing the lucene)
     */
    fun createQueryContainers(queryLocation: String, doBoostedQuery: Boolean = false) =
        DeserializeData.iterableAnnotations(File(queryLocation).inputStream())
            .run { limit?.let { this.take(limit) } ?: this }
            .withIndex()
            .pmap { indObject ->
                val index = indObject.index
                val page = indObject.value
                val queryStr = createQueryString(page, emptyList())
                createQueryContainer(queryStr, page.pageId, index)
            }



    private fun createSectionContainers(query: String, qid: Int): List<SectionContainer> {
        val q = AnalyzerFunctions.createQuery(query, field = IndexFields.FIELD_UNIGRAM.field,
                analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED,
                useFiltering = true)

        val sections = sectionSearcher.search(q, 100).scoreDocs
            .filter { sd -> sectionSearcher.getIndexDoc(sd.doc).id() != ""  }
            .shuffled(sharedRand)
            .mapIndexed { index, sd ->
                val doc = sectionSearcher.getIndexDoc(sd.doc)
                SectionContainer(
                        query = query,
                        docType = IndexType.SECTION::class.java,
                        score = 0.0,
                        index = index,
                        isRelevant = sectionRelevancies[query]?.get(doc.id()) ?: 0,
                        docId = sd.doc,
                        searcher = sectionSearcher,
                        name = doc.id(),
                        qid = if (isHomogenous) qid else qid + 5000
                )
            }

        return sections
    }

    private fun createParagraphContainersFromSections(query: String, qid: Int,
                                              sections: List<SectionContainer>): List<ParagraphContainer> {
        val pids = sections.flatMap { sContainer -> sContainer.doc().paragraphs().split(" ") }
            .toSet()
            .toList()

        val seen = HashSet<Int>()
        val paragraphScoreDocs = pids.mapNotNull { pid ->
            val q = AnalyzerFunctions.createQuery(pid, IndexFields.FIELD_PID.field)
            paragraphSearcher.search(q, 1).scoreDocs.firstOrNull()
        }.filter { seen.add(it.doc) }

        val paragraphs = paragraphScoreDocs
            .shuffled(sharedRand)
            .mapIndexed { index, sd ->
            val doc = paragraphSearcher.getIndexDoc(sd.doc)
            ParagraphContainer(
                    query = query,
                    qid = qid,
                    name = doc.pid(),
                    searcher = paragraphSearcher,
                    docId = doc.docId,
                    isRelevant = paragraphRelevancies[query]?.get(doc.pid()) ?: 0,
                    index = index,
                    score = 0.0,
                    docType = IndexType.PARAGRAPH::class.java
            )
        }

        return paragraphs
    }

    private fun createEntityContainersFromParagraphs(query: String, qid: Int,
                                              paragraphs: List<ParagraphContainer>): List<EntityContainer> {

        val entityNames = paragraphs.flatMap { pContainer -> pContainer.doc().spotlightEntities().split(" ") }
            .toSet()
            .toList()

        val entityScoreDocs = entityNames.mapNotNull { entity ->
            val q = AnalyzerFunctions.createQuery(entity, IndexFields.FIELD_NAME.field)
            entitySearcher.search(q, 1).scoreDocs.firstOrNull()
        }

        val entities = entityScoreDocs
            .shuffled(sharedRand)
            .mapIndexed { index, sd ->
            val doc = entitySearcher.getIndexDoc(sd.doc)
            EntityContainer(
                    query = query,
                    qid = if (isHomogenous) qid else qid + 1000,
                    name = doc.name(),
                    searcher = entitySearcher,
                    docId = doc.docId,
                    isRelevant = entityRelevancies[query]?.get(doc.name().toLowerCase()) ?: 0,
                    index = index,
                    score = 0.0,
                    docType = IndexType.ENTITY::class.java
            )
        }
        return entities
    }

    private fun createQueryContainer(query: String, queryId: String, qid: Int): QueryContainer {
        val sections = createSectionContainers(queryId, qid)
        val paragraphs = createParagraphContainersFromSections(queryId, qid, sections)
        val entities = createEntityContainersFromParagraphs(queryId, qid, paragraphs)
        val qd = QueryData(
               entitySearcher = entitySearcher,
                paragraphSearcher = paragraphSearcher,
                sectionSearcher = sectionSearcher,
                paragraphContainers = paragraphs,
                sectionContainers = sections,
                entityContainers = entities,
                isJoint = false,
                queryString = query
        )

        return QueryContainer(
                query = queryId,
                paragraphs = paragraphs,
                entities = entities,
                sections = sections,
                queryData = qd,
                jointDistribution = JointDistribution.createEmpty()
        )
    }


}
