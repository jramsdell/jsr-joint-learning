package lucene

import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import lucene.containers.*
import lucene.indexers.IndexFields
import lucene.indexers.boostedTermQuery
import lucene.indexers.termQuery
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import org.apache.lucene.util.QueryBuilder
import utils.lucene.getIndexSearcher
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import utils.AnalyzerFunctions
import utils.lucene.getTypedSearcher
import utils.misc.PID
import utils.misc.identity
import utils.misc.mapOfLists
import utils.misc.sharedRand
import utils.parallel.pmap
import utils.stats.countDuplicates
import utils.stats.takeMostFrequent
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
                        val isHomogenous: Boolean,
                        val contextEntitySearcher: ContextEntitySearcher) {


    val originParagraphSearcher = getTypedSearcher<IndexType.PARAGRAPH>("/home/jsc57/projects/jsr-joint-learning/index")
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
//        var tokens = AnalyzerFunctions.createTokenList(query, useFiltering = true,
//                analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)

//        if (tokens.size == 1) {
//            val topParagraph = AnalyzerFunctions.createQuery(query, IndexFields.FIELD_UNIGRAM.field, true, AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)
//                .run { paragraphSearcher.search(this, 1).scoreDocs.firstOrNull()?.let { paragraphSearcher.getIndexDoc(it.doc) } }
//            if (topParagraph != null) {
//                tokens = tokens + topParagraph.unigrams().split(" ").countDuplicates().maxBy { it.value }!!.key
//            }
//        }

//        val q = FieldQueryFormatter()
//            .addWeightedQueryTokens(query, IndexFields.FIELD_BIGRAM)
//            .createBooleanQuery()

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
        val pids = sections.flatMap { sContainer -> sContainer
            .doc()
            .paragraphs().split(" ") }
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

    private fun createOriginParagraphContainers(query: String, qid: Int): List<ParagraphContainer> {
        val q = AnalyzerFunctions.createQuery(query, IndexFields.FIELD_UNIGRAM.field, useFiltering = true,
                analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)

        originParagraphSearcher.search(q, 100).scoreDocs.mapIndexed { index, sd ->
            val doc = originParagraphSearcher.getIndexDoc(sd.doc)
            ParagraphContainer(
                    query = query,
                    qid = qid,
                    name = doc.pid(),
                    searcher = originParagraphSearcher,
                    docId = doc.docId,
                    isRelevant = paragraphRelevancies[query]?.get(doc.pid().toLowerCase()) ?: 0,
                    index = index,
                    score = 0.0,
                    docType = IndexType.PARAGRAPH::class.java
            )
        }.run {
            return this
        }
    }

    private fun createContextEntityContainers(query: String, qid: Int,
                                                     paragraphs: List<ParagraphContainer>): List<ContextEntityContainer> {

//        val entityNames = paragraphs.flatMap { pContainer -> pContainer.doc().spotlightEntities().split(" ") }
//            .toSet()
//            .toList()

        val entityNames = paragraphs.flatMap { pContainer ->
            val unigramQuery = pContainer.doc().bigrams()
                .split(" ")
                .countDuplicates()
                .map { IndexFields.FIELD_BIGRAM.boostedTermQuery(it.key, it.value.toDouble()) }
                .fold(BooleanQuery.Builder()) { builder, q -> builder.add(q, BooleanClause.Occur.SHOULD)}
                .build()
//                .toList()
//                .sortedByDescending { it.second }
//                .take(5)
//                .map { it.first }
//                .joinToString(" ")
//                .run { AnalyzerFunctions.createQuery(this, IndexFields.FIELD_BIGRAM.field) }
            pContainer.doc()
                .load(IndexFields.FIELD_ENTITIES_EXTENDED)
                .split(" ")
                .countDuplicates()
                .map { (entityName, freq) ->
                    entityName to BoostQuery(unigramQuery, freq.toFloat())
                }

//            pContainer.doc()
//                .spotlightEntities()
//                .split(" ")
//                .map { entityName ->
//                    entityName to unigramQuery
//                }
        }.groupBy { it.first }
            .mapValues { it.value.fold(BooleanQuery.Builder()) { builder, q -> builder.add(q.second, BooleanClause.Occur.SHOULD)}.build() }

//        val queryF = AnalyzerFunctions.createQuery(query, IndexFields.FIELD_UNIGRAM.field, useFiltering = true, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)
        val seen = HashSet<Int>()
        val entityScoreDocs = entityNames
            .filter { it.key != "" }
            .mapNotNull { (k,query) ->
//            val q = AnalyzerFunctions.createQuery(entity, IndexFields.FIELD_NAME.field)
            val q = BooleanQuery.Builder()
                .add(query, BooleanClause.Occur.SHOULD)
                .add(IndexFields.FIELD_NAME.termQuery(k.toLowerCase()), BooleanClause.Occur.FILTER)
                .build()
            contextEntitySearcher.search(q, 5)?.scoreDocs?.toList()
        }.flatten()
            .filter { it.score > 0.0f && seen.add(it.doc) }


        val entities = entityScoreDocs
            .shuffled(sharedRand)
            .mapIndexed { index, sd ->
                val doc = contextEntitySearcher.getIndexDoc(sd.doc)
                ContextEntityContainer(
                        query = query,
                        qid = if (isHomogenous) qid else qid + 1000,
                        name = doc.name(),
                        searcher = contextEntitySearcher,
                        docId = doc.docId,
                        isRelevant = entityRelevancies[query]?.get(doc.name().toLowerCase()) ?: 0,
                        index = index,
                        score = 0.0,
                        docType = IndexType.CONTEXT_ENTITY::class.java
                )
            }
        return entities
    }

    private fun createQueryContainer(query: String, queryId: String, qid: Int): QueryContainer {
        val sections = createSectionContainers(queryId, qid)
        val paragraphs = createParagraphContainersFromSections(queryId, qid, sections)
        val entities = createEntityContainersFromParagraphs(queryId, qid, paragraphs)
//        val contextEntities = createContextEntityContainers(queryId, qid, paragraphs)
//        val originParagraphs = createOriginParagraphContainers(queryId, qid)
        val qd = QueryData(
               entitySearcher = entitySearcher,
                paragraphSearcher = paragraphSearcher,
                sectionSearcher = sectionSearcher,
                contextEntitySearcher = contextEntitySearcher,
                originSearcher = originParagraphSearcher,
                originParagraphContainers = emptyList(),
                paragraphContainers = paragraphs,
                sectionContainers = sections,
                entityContainers = entities,
                contextEntityContainers = emptyList(),
                isJoint = false,
                queryString = query
        )

        return QueryContainer(
                query = queryId,
                paragraphs = paragraphs,
                originParagraphs = emptyList(),
                entities = entities,
                sections = sections,
                contextEntities = emptyList(),
                queryData = qd,
                jointDistribution = JointDistribution.createEmpty()
        )
    }


}
