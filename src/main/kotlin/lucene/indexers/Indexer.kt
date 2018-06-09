package lucene.indexers

import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import language.GramIndexer.Companion.getGrams
import lucene.containers.LuceneDocumentContainer
import lucene.parsers.ExtractorType.*
import org.apache.lucene.document.Document
import utils.AnalyzerFunctions
import utils.lucene.outlinks
import utils.lucene.paragraphs
import utils.parallel.asIterable
import utils.parallel.forEachChunkedParallel
import utils.parallel.forEachParallel
import utils.parallel.pmap
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream
import lucene.indexers.IndexFields.*
import kotlin.concurrent.withLock


open class Indexer(val extractFuns: List<Indexer.(LuceneDocumentContainer) -> Unit>) {



    fun doExtract(iterable: Iterable<LuceneDocumentContainer>) {
        iterable.forEachParallel { luceneDoc ->
            extractFuns.forEach { extractFun -> extractFun(luceneDoc) }
        }
    }


}

fun Indexer.extractGram(doc: LuceneDocumentContainer) {
    val content = doc.paragraph!!.textOnly
    val (unigrams, bigrams, windowed) = getGramsFromContent(content)
    doc.lock.withLock {
        FIELD_PID.setStringField(doc.doc, doc.paragraph!!.paraId)
        FIELD_TEXT.setTextField(doc.doc, content)
        FIELD_UNIGRAM.setTextField(doc.doc, unigrams)
        FIELD_BIGRAM.setTextField(doc.doc, bigrams)
        FIELD_WINDOWED_BIGRAM.setTextField(doc.doc, windowed)
    }
}




fun Indexer.extractMetaData(doc: LuceneDocumentContainer) {
    val id = doc.page.pageName.replace(" ", "_")
    val inlinks = doc.page.pageMetadata.inlinkIds.joinToString(" ")
    val outlinks = doc.page.outlinks().joinToString(" ")
    val categories = doc.page.pageMetadata.categoryIds.joinToString(" ")
    val disambiguations = doc.page.pageMetadata.disambiguationNames.joinToString(" ")
    val redirects = doc.page.pageMetadata.redirectNames.joinToString(" ")
//    val results = listOf(inlinks, outlinks, categories, disambiguations, redirects)

    doc.lock.withLock {
        FIELD_NAME.setStringField(doc.doc, id)
    }
    FIELD_INLINKS.setTextField(doc.doc, inlinks)
    FIELD_OUTLINKS.setTextField(doc.doc, outlinks)
    FIELD_CATEGORIES.setTextField(doc.doc, categories)
    FIELD_DISAMBIGUATIONS.setTextField(doc.doc, disambiguations)
    FIELD_REDIRECTS.setTextField(doc.doc, redirects)
}

fun Indexer.extractEntityAbstractAndGrams(doc: LuceneDocumentContainer) {
    val abstract = doc.page.paragraphs()
        .take(2)
        .map { paragraph -> paragraph.textOnly.replace("\n", " ") }
        .joinToString(" ")
    val (unigrams, bigrams, windowed) = getGramsFromContent(abstract)
    FIELD_UNIGRAM.setTextField(doc.doc, unigrams)
    FIELD_BIGRAM.setTextField(doc.doc, bigrams)
    FIELD_WINDOWED_BIGRAM.setTextField(doc.doc, windowed)
    FIELD_ABSTRACT.setTextField(doc.doc, abstract)
}



private fun getGramsFromContent(content: String): List<String> =
    getGrams(content).toList().map { it.joinToString(" ") }







//abstract class Extractor(outLoc: String,
//                         val type: ExtractorType,
//                         val chunkSize: Int = 1000,
//                         val debug: Boolean = false) {
//    private val out = File("extractions/$outLoc")
//        .apply { if (!exists()) mkdirs() }
//        .bufferedWriter().
//            apply { write("") }
//
//    private val counter = AtomicInteger()
//
//    private fun fromParagraphCorpus(corpusLoc: String) =
//        File(corpusLoc)
//            .inputStream()
//            .buffered()
//            .run { DeserializeData.iterParagraphs(this) }
//            .asIterable()
//
//    private fun fromPageCorpus(corpusLoc: String) =
//            File(corpusLoc)
//                .inputStream()
//                .buffered()
//                .run { DeserializeData.iterableAnnotations(this) }
//                .asIterable()
//
//    fun extract(corpusLoc: String) {
//        when (type) {
//            EXTRACT_PAGE -> fromPageCorpus(corpusLoc)
//                .let { iterable -> doExtract(iterable, this::pageExtractor) }
//            EXTRACT_PARAGRAPH -> fromParagraphCorpus(corpusLoc)
//                .let { iterable -> doExtract(iterable, this::paragraphExtractor) }
//        }
//    }
//
//
//    private fun<A> doExtract(iterable: Iterable<A>, f: (A) -> String) {
//        iterable.forEachChunkedParallel(chunkSize) { item: A ->
//            val parsedResult = f(item)
//            out.append(parsedResult)
//
//            counter.incrementAndGet().let { iteration ->
//                if (iteration % 10000 == 0) {
//                    if (debug) println(iteration)
//                    out.flush()
//                }
//            }
//        }
//    }
//
//
//    open fun pageExtractor(page: Data.Page): String {
//        return ""
//    }
//
//    open fun paragraphExtractor(paragraph: Data.Paragraph): String {
//        return ""
//    }
//
//    fun close() = out.close()
//
//
//
//
//
//}