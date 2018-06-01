package lucene.containers

import org.apache.lucene.search.TopDocs

/**
 * Class: QueryContainer
 * Description: One is created for each of the lucene strings in the lucene .cbor file.
 *              Stores corresponding lucene string and TopDocs (obtained from BM25)
 */
data class QueryContainer(val query: String, val tops: TopDocs, val paragraphs: List<ParagraphContainer>,
                          val queryData: QueryData)
