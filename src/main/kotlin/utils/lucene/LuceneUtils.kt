package utils.lucene

import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.index.*
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.search.TopDocs
import org.apache.lucene.store.FSDirectory
import utils.misc.identity
import utils.parallel.asIterable
import utils.stats.normalize
import java.lang.IllegalStateException
import java.nio.file.Paths
import kotlin.coroutines.experimental.buildIterator

//// Retrieves an index searcher (I use this everywhere so might as well put it here)
fun getIndexSearcher(indexLocation: String): IndexSearcher {
    val indexPath = Paths.get(indexLocation)
    val indexDir = FSDirectory.open(indexPath)
    val indexReader = DirectoryReader.open(indexDir)
    return IndexSearcher(indexReader)
}

fun getIndexWriter(indexLocation: String, mode: IndexWriterConfig.OpenMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND): IndexWriter {
    val indexPath = Paths.get(indexLocation)
    val indexDir = FSDirectory.open(indexPath)
    val conf = IndexWriterConfig(StandardAnalyzer())
        .apply { openMode = mode }
    return IndexWriter(indexDir, conf)
}

fun getFieldIterator(field: String, indexReader: IndexReader): Iterable<String> {
    val fields = MultiFields.getFields(indexReader)
    val spotLightTerms = fields.terms(field)
//        val numTerms = 2100000 // Hard coding number of entities for progress bar (no easy way to count this)
    val termIterator = spotLightTerms.iterator()

    // Build a sequence that lets us iterate over terms in chunks and run them in parallel
    val termSeq = buildIterator<String> {
        while (true) {
            val bytesRef = termIterator.next() ?: break
            yield(bytesRef.utf8ToString())
        }
    }

    return termSeq.asIterable()
}

fun Document.splitAndCount(field: String) =
        get(field)
            ?.split(" ")
            ?.groupingBy(::identity)
            ?.eachCount() ?: emptyMap()

fun TopDocs.docs(indexSearcher: IndexSearcher): List<Pair<Document, Int>> =
        scoreDocs.map { scoreDoc ->  indexSearcher.doc(scoreDoc.doc) to scoreDoc.doc }

fun Document.getOrDefault(field: String, default: String = "") =
         get(field) ?: default
//    try { get(field) ?: default }
//    catch (e: IllegalStateException) { default }

fun Document.getValuesOrDefault(field: String) = getValues(field).toList()
//        try { getValues(field).toList() }
//        catch (e: IllegalStateException) { emptyList<String>() }

fun IndexSearcher.explainScore(query: Query, doc: Int) = explain(query, doc).value.toDouble()

fun IndexSearcher.searchFirstOrNull(query: Query) =
        search(query, 1)
            .scoreDocs
            .firstOrNull()

