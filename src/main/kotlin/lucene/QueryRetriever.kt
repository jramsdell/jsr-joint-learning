@file:JvmName("KotQueryRetriever")
package lucene

import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import lucene.containers.EntityContainer
import lucene.containers.ParagraphContainer
import lucene.containers.QueryContainer
import lucene.containers.SectionContainer
import lucene.indexers.IndexFields
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.search.*
import utils.lucene.getIndexSearcher
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import utils.AnalyzerFunctions
import utils.misc.PID
import utils.parallel.pmap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Class: QueryRetriever
 * Description: Used to make queries (using BM25) and to parse the results of queries.
 *              Takes an IndexSearcher (or path to an index) for the Lucene index it will lucene with.
 */
class QueryRetriever(val indexSearcher: IndexSearcher, val takeSubset: Boolean = false) {
    constructor(indexPath: String) : this(getIndexSearcher(indexPath))

    val analyzer = StandardAnalyzer()


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
    fun getPageQueries(queryLocation: String, doBoostedQuery: Boolean = false): List<Pair<String, TopDocs>> =
            DeserializeData.iterableAnnotations(File(queryLocation).inputStream())
                .pmap { page ->
                    val queryId = page.pageId
                    val queryStr = createQueryString(page, emptyList())
//                    queryId to indexSearcher.search(createQuery(queryStr), 100) }
                    val result = if (!doBoostedQuery) {
                        indexSearcher
                            .search(AnalyzerFunctions.createQuery(queryStr, useFiltering = true), 100)
                    } else { doBoost(queryStr, isPage = true) }
                    queryId to result
                }
                .toList()


    /**
     * Function: getSectionQueries
     * Description: Given a lucene location (.cbor file), queries Lucene index with page name and section names.
     * @return List of pairs (lucene string and the Top 100 documents obtained by doing the lucene)
     */
    fun getSectionQueries(queryLocation: String, doBoostedQuery: Boolean = false): List<Pair<String, TopDocs>> {
        val seen = ConcurrentHashMap<String, String>()
        val replaceNumbers = """(\d+|enwiki:)""".toRegex()
        val counter = AtomicInteger()

        return DeserializeData.iterableAnnotations(File(queryLocation).inputStream())
            .run { if (takeSubset) take(10) else this }
            .flatMap { page ->
//                (page.flatSectionPaths())
                (page.flatSectionPaths().apply { this.add(kotlin.collections.emptyList()) })
                    .pmap { sectionPath ->
                        val queryId = Data.sectionPathId(page.pageId, sectionPath)
                        var queryStr = createQueryString(page, sectionPath)
                        queryStr = queryStr.replace(replaceNumbers, queryStr)      // remove numbers/enwiki:

                        val query = if (!doBoostedQuery) {
                            indexSearcher
                                .search(AnalyzerFunctions.createQuery( queryStr, field = IndexFields.FIELD_TEXT.field, useFiltering = false), 100)

                        } else { doBoost(queryStr) }




                        val result = queryId to query
                        result.takeUnless {seen.put(queryId, "") != null}   // remove duplicates
                    }.apply { println(counter.incrementAndGet()) }
            }.filterNotNull()
    }


    fun doBoost(queryStr: String, isPage: Boolean = false): TopDocs {
//                            val weights = listOf(0.9346718895308014 , 0.049745249968994265 , 0.015582860500204451 )
        val weights =
//                if (isPage) listOf(0.9513710217127973 , 0.02830263512128421 , 0.02032634316591837 )
                 listOf(0.9346718895308014 , 0.04971515179492379 , 0.015612958674274948 )
//        listOf(0.975344486511033 ,0.0025553537817999484 ,0.0014602021610285421 ,0.02063995754613844 )
//        listOf(
//                0.2931706701610092 ,0.2694585839132235 ,-0.03311896562582617 ,0.2423680843314018 ,0.1570971513118776 ,0.004786544656661633
//        )

        val terms = AnalyzerFunctions.createTokenList(queryStr, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED,
                useFiltering = true)
        var i = 0
        val results = FieldQueryFormatter()
            .addWeightedQueryTokens(terms, IndexFields.FIELD_NEIGHBOR_UNIGRAMS, 1.0)
//            .addWeightedQueryTokens(terms, IndexFields.FIELD_NEIGHBOR_BIGRAMS, weights[i++])
//            .addWeightedQueryTokens(terms, IndexFields.FIELD_NEIGHBOR_WINDOWED, weights[i++])
//            .addWeightedQueryTokens(terms, IndexFields.FIELD_JOINT_UNIGRAMS, weights[i++])
//            .addWeightedQueryTokens(terms, IndexFields.FIELD_JOINT_BIGRAMS, weights[i++])
//            .addWeightedQueryTokens(terms, IndexFields.FIELD_JOINT_WINDOWED, weights[i++])
//            .addWeightedQueryTokens(terms, IndexFields.FIELD_NEIGHBOR_UNIGRAMS, weights[i++])
//            .addWeightedQueryTokens(terms, IndexFields.FIELD_UNIGRAM, weights[0])
//            .addWeightedQueryTokens(terms, IndexFields.FIELD_BIGRAM, weights[1])
//            .addWeightedQueryTokens(terms, IndexFields.FIELD_WINDOWED_BIGRAM, weights[2])
            .createBooleanQuery()
            .run {
                indexSearcher.search(this, 100) }
        return results!!
    }


    /**
     * Function: writeRankingsToFile
     * Description: Writes formatted lucene results to a file (for use with trec_eval)
     */
    private fun writeRankingsToFile(tops: TopDocs, queryId: String, writer: BufferedWriter, queryNumber: Int) {
        (0 until tops.scoreDocs.size).forEach { index ->
            val seen = HashSet<String>()
            val sd = tops.scoreDocs[index]
            val doc = indexSearcher.doc(sd.doc)
            val paragraphid = doc.get(PID)
            val score = sd.score
            val searchRank = index + 1

            if (seen.add(paragraphid)) {
                writer.write("$queryId Q$queryNumber $paragraphid $searchRank $score Query\n")
            }
        }
    }

    /**
     * Function: writeQueriesToFile
     * Description: For each pair of lucene name and top 100 documents, write the results to a file.
     * @see writeRankingsToFile
     */
    fun writeQueriesToFile(queries: List<Pair<String, TopDocs>>, out: String = "results.txt") {
        val writer = File(out).bufferedWriter()
        queries.forEachIndexed { index, (query, tops) -> writeRankingsToFile(tops, query, writer, index)}
        writer.flush()
        writer.close()
    }

    fun writeEntitiesToFile(queries: List<QueryContainer>) {
        val writer = File("entity_results.run").bufferedWriter()
        queries.forEach { container ->
            val seen = HashSet<String>()
            val query = container.query
            container.entities.forEach(EntityContainer::rescore)
            container.entities
                .filter { entity -> seen.add(entity.name) }
                .sortedByDescending(EntityContainer::score)
                .forEachIndexed { index, entity ->
                    val id = "enwiki:" + entity.name.replace("_", "%20")
                    writer.write("${query} Q0 $id ${index + 1} ${entity.score} Entity\n")
                }
        }
        writer.flush()
        writer.close()
    }

    fun writeParagraphsToFile(queries: List<QueryContainer>) {
        val writer = File("paragraph_results.run").bufferedWriter()
        queries.forEach { container ->
            val seen = HashSet<String>()
            val query = container.query
            container.paragraphs.forEach(ParagraphContainer::rescore)
            container.paragraphs
                .filter { paragraph -> seen.add(paragraph.name) }
                .sortedByDescending(ParagraphContainer::score)
                .forEachIndexed { index, paragraph ->
                    writer.write("${query} Q0 ${paragraph.name} ${index + 1} ${paragraph.score} Paragraph\n")
                }
        }
        writer.flush()
        writer.close()
    }

    fun writeSectionsToFile(queries: List<QueryContainer>) {
        val writer = File("section_results.run").bufferedWriter()
        queries.forEach { container ->
            val seen = HashSet<String>()
            val query = container.query
            container.sections.forEach(SectionContainer::rescore)
            container.sections
                .filter { section -> seen.add(section.name) }
                .sortedByDescending(SectionContainer::score)
                .forEachIndexed { index, section ->
                    writer.write("${query} Q0 ${section.name} ${index + 1} ${section.score} Section\n")
                }
        }
        writer.flush()
        writer.close()
    }

}

