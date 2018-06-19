package lucene.parsers

import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import language.GramAnalyzer
import language.GramIndexer.Companion.getGrams
import lucene.parsers.ExtractorType.*
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

enum class ExtractorType {
    EXTRACT_PARAGRAPH,
    EXTRACT_PAGE
}

open class Extractor<T>(outLoc: String,
                           val extractFun: Extractor<T>.(T) -> String, val debug: Boolean = false) {

    val extractors = ArrayList<Extractor<String>>()

//    private val out = GZIPOutputStream(File("extractions/$outLoc.gz").outputStream())
    private val out = File("extractions/$outLoc.tsv").outputStream()
        .bufferedWriter()
        .apply { write("") }

    fun doExtract(iterable: Iterable<T>) {
        iterable.pmap { item: T ->
            extractFun(item)
        }.forEach { result ->
            out.write(result)
        }
//        out.append(result.joinToString(""))
        out.flush()
//        extractors.forEach { extractor -> extractor.doExtract(result) }
    }


}

fun Extractor<Data.Page>.extractGram(page: Data.Page): String =
    page.paragraphs().map { paragraph ->
        val id = paragraph.paraId
        val content = paragraph.textOnly
        val grams = getGramsFromContent(content)
        "$id\t$grams\n"
    }
        .joinToString("")

fun Extractor<String>.extractGram(result: String): String =
        result.split("\n").map { line ->
            val (id, content) = line.split("\t")
            val grams = getGramsFromContent(content)
            "$id\t$grams\n"
        }
            .joinToString("")



fun Extractor<Data.Page>.extractMetadata(page: Data.Page): String {
    val id = page.pageName.replace(" ", "_")
    val inlinks = page.pageMetadata.inlinkIds.joinToString(" ")
    val outlinks = page.outlinks().joinToString(" ")
    val categories = page.pageMetadata.categoryIds.joinToString(" ")
    val disambiguations = page.pageMetadata.disambiguationNames.joinToString(" ")
    val redirects = page.pageMetadata.redirectNames.joinToString(" ")
    val results = listOf(inlinks, outlinks, categories, disambiguations, redirects)
    return "$id\t${results.joinToString("\t")}\n"
}

fun Extractor<Data.Page>.extractAbstractAndGrams(page: Data.Page): String {
    val id = page.pageName.replace(" ", "_")
    val abstract = page.paragraphs()
        .take(3)
        .map { paragraph -> paragraph.textOnly.replace("\n", " ") }
        .joinToString(" ")
    val grams = getGramsFromContent(abstract)
    return "$id\t$abstract\t$grams\n"
}



private fun getGramsFromContent(content: String): String =
    getGrams(content).toList().map { it.joinToString(" ") }.joinToString("\t")







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