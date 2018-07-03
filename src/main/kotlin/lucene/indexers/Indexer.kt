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
import utils.lucene.getSectionLevels
import utils.stats.countDuplicates
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
    val entities = doc.paragraph!!.entitiesOnly.map(::cleanEntity).joinToString(" ")
    doc.lock.withLock {
        FIELD_PID.setTextField(doc.doc, doc.paragraph!!.paraId)
        FIELD_TEXT.setTextField(doc.doc, content)
        FIELD_ENTITIES.setTextField(doc.doc, entities)
        FIELD_UNIGRAM.setTextField(doc.doc, unigrams)
        FIELD_BIGRAM.setTextField(doc.doc, bigrams)
        FIELD_WINDOWED_BIGRAM.setTextField(doc.doc, windowed)
    }
}


private fun cleanEntity(entry: String) =
    entry.replace("enwiki:", "")
        .replace("%20", "_")
        .replace(" ", "_")



fun Indexer.extractMetaData(doc: LuceneDocumentContainer) {
    val id = doc.page.pageName.replace(" ", "_")
    val inlinks = doc.page.pageMetadata.inlinkIds
        .map(::cleanEntity)
        .joinToString(" ")

    val outlinks = doc.page.outlinks()
        .map(::cleanEntity)
        .joinToString(" ")

    val categories = doc.page.pageMetadata.categoryIds
        .map(::cleanEntity)
        .map { it.replace("[^ ]*:".toRegex(), "") }
        .joinToString(" ")

    val disambiguations = doc.page.pageMetadata.disambiguationNames
        .map { it.replace(" (disambiguation)", "").replace(" ", "_") }
        .joinToString(" ")

    val redirects = doc.page.pageMetadata.redirectNames
        .filter { !it.startsWith("Category:") }
        .joinToString(" ")
//    val results = listOf(inlinks, outlinks, categories, disambiguations, redirects)

    val convertToUnigrams = { text: String ->
        AnalyzerFunctions.createTokenList(text.replace("_", " "), AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)
            .filter { it.length > 2 }
            .joinToString(" ")
    }

    doc.lock.withLock {
        FIELD_NAME.setTextField(doc.doc, id)
        FIELD_NAME_UNIGRAMS.setTextField(doc.doc, convertToUnigrams(id))
    }
    FIELD_INLINKS.setTextField(doc.doc, inlinks)
    FIELD_INLINKS_UNIGRAMS.setTextField(doc.doc, convertToUnigrams(inlinks))
    FIELD_OUTLINKS.setTextField(doc.doc, outlinks)
    FIELD_OUTLINKS_UNIGRAMS.setTextField(doc.doc, convertToUnigrams(outlinks))
    FIELD_CATEGORIES.setTextField(doc.doc, categories)
    FIELD_CATEGORIES_UNIGRAMS.setTextField(doc.doc, convertToUnigrams(categories))
    FIELD_DISAMBIGUATIONS.setTextField(doc.doc, disambiguations)
    FIELD_DISAMBIGUATIONS_UNIGRAMS.setTextField(doc.doc, convertToUnigrams(disambiguations))
    FIELD_REDIRECTS.setTextField(doc.doc, redirects)
    FIELD_REDIRECTS_UNIGRAMS.setTextField(doc.doc, convertToUnigrams(redirects))
}


fun Indexer.extractEntityTextAndGrams(doc: LuceneDocumentContainer) {
    val text = doc.page.paragraphs()
        .asSequence()
        .filter { it.textOnly.length > 100 && !it.textOnly.contains(":") && !it.textOnly.contains("•")}
        .map { it.textOnly }
        .joinToString("\n")
//        .take(2)
//        .map { paragraph -> paragraph.textOnly.replace("\n", " ") }
//        .joinToString(" ")
    val (unigrams, bigrams, windowed) = getGramsFromContent(text)
    FIELD_UNIGRAM.setTextField(doc.doc, unigrams)
    FIELD_BIGRAM.setTextField(doc.doc, bigrams)
    FIELD_WINDOWED_BIGRAM.setTextField(doc.doc, windowed)
//    FIELD_TEXT.setTextField(doc.doc, text)
}

fun Indexer.addEntityHeaders(doc: LuceneDocumentContainer) {
//    val sections = doc.page.childSections.map { it.heading }.joinToString("\n")
    val sections = doc.page.getSectionLevels().joinToString("\n")
    val (unigrams, bigrams, windowed) = getGramsFromContent(sections)
    FIELD_SECTION_UNIGRAM.setTextField(doc.doc, unigrams)
    FIELD_SECTION_TEXT.setTextField(doc.doc, sections)
}





fun getGramsFromContent(content: String): List<String> =
    getGrams(content).toList()
        .map { grams ->
            val top20 = grams.countDuplicates().entries.sortedByDescending { it.value }.take(20)
            top20.map { (gram, count) -> (gram + " ").repeat(count) }
        }
        .map { it.joinToString(" ") }






