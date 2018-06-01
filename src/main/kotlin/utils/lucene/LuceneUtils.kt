package utils.lucene

import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.store.FSDirectory
import java.lang.IllegalStateException
import java.nio.file.Paths

//// Retrieves an index searcher (I use this everywhere so might as well put it here)
fun getIndexSearcher(indexLocation: String): IndexSearcher {
    val indexPath = Paths.get(indexLocation)
    val indexDir = FSDirectory.open(indexPath)
    val indexReader = DirectoryReader.open(indexDir)
    return IndexSearcher(indexReader)
}

fun getIndexWriter(indexLocation: String): IndexWriter {
    val indexPath = Paths.get(indexLocation)
    val indexDir = FSDirectory.open(indexPath)
    val conf = IndexWriterConfig(StandardAnalyzer())
        .apply { openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND }
    return IndexWriter(indexDir, conf)
}

fun Document.getOrDefault(field: String, default: String = "") =
         get(field) ?: default
//    try { get(field) ?: default }
//    catch (e: IllegalStateException) { default }

fun Document.getValuesOrDefault(field: String) = getValues(field).toList()
//        try { getValues(field).toList() }
//        catch (e: IllegalStateException) { emptyList<String>() }

fun IndexSearcher.explainScore(query: Query, doc: Int) = explain(query, doc).value.toDouble()
