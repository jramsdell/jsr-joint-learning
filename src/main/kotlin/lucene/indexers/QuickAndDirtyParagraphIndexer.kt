package lucene.indexers

import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.ByteString
import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.CborDataItemIterator
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import lucene.containers.LuceneDocumentContainer
import org.apache.lucene.index.IndexWriterConfig
import org.json.JSONObject
import utils.parallel.asIterable
import utils.parallel.forEachChunkedParallel
import utils.parallel.forEachParallel
import utils.parallel.forEachParallelQ
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.StreamSupport
import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.UnicodeString
import edu.unh.cs.treccar_v2.read_data.CborListWithHeaderIterator
import entity.SpotlightEntityLinker
import language.GramAnalyzer
import lucene.indexers.*
import org.apache.lucene.document.Document
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.Term
import org.apache.lucene.search.IndexSearcher
import utils.AnalyzerFunctions
import utils.AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED
import utils.lucene.*
import lucene.indexers.IndexFields.*
import org.apache.lucene.analysis.StopwordAnalyzerBase
import utils.AnalyzerFunctions.AnalyzerType.ANALYZER_STANDARD_STOPPED
import utils.misc.toArrayList
import utils.stats.countDuplicates
import utils.stats.takeMostFrequent
import java.lang.Integer.min


class QuickAndDirtyParagraphIndexer() {
    val corpus = "/home/jsc57/data/corpus/paragraphCorpus/dedup.articles-paragraphs.cbor"
    val paragraphCounter = AtomicInteger()
//    val speedy = "/speedy/jsc57/"
    val speedy = "/home/jsc57/data/backup/"
    val paragraphIndex = getIndexWriter("${speedy}extractions/paragraph2", mode = IndexWriterConfig.OpenMode.CREATE)
//    val linker = SpotlightEntityLinker("/home/jsc57/projects/jsr-joint-learning/spotlight_server")
//        .apply { (0 until 100).forEach { queryServer("Test") }   }



    private fun cleanEntity(entry: String) =
            entry.replace("enwiki:", "")
                .replace("%20", "_")
                .replace(" ", "_")



    private fun convertToUnigrams(text: String) =
            text.split(" ")
                .asSequence()
                .map { it.replace("_", " ") }
                .map {  AnalyzerFunctions.createTokenList(it, ANALYZER_ENGLISH_STOPPED) }
                .map { it.joinToString("_") }
                .joinToString(" ")

    fun processParagraphs(paragraph: Data.Paragraph) {
        val doc = Document()
        FIELD_PID.setTextField(doc, paragraph.paraId)
        FIELD_TEXT.setTextField(doc, paragraph.textOnly)
        val entities = paragraph.entitiesOnly
            .map(this::cleanEntity)
            .joinToString(" ")
//        val annotations = linker.queryServer(paragraph.textOnly)
        FIELD_ENTITIES.setTextField(doc, entities)
        FIELD_ENTITIES_UNIGRAMS.setTextField(doc, convertToUnigrams(entities))
//        FIELD_ENTITIES_EXTENDED.setTextField(doc, annotations.joinToString(" "))

        val (unigrams, bigrams, windowed) = getGramsFromContent(paragraph.textOnly)

        FIELD_UNIGRAM.setTextField(doc, unigrams)
        FIELD_BIGRAM.setTextField(doc, bigrams)
        FIELD_WINDOWED_BIGRAM.setTextField(doc, windowed)
        val tokens = AnalyzerFunctions.createTokenList(paragraph.textOnly, ANALYZER_ENGLISH_STOPPED)
        FIELD_TRIGRAM.setTextField(doc, getTrigram(tokens))
        FIELD_TEXT_STEMMED.setTextField(doc, tokens.joinToString(" "))
        FIELD_WINDOWED_BIGRAM_3.setTextField(doc, getWindowed(tokens, 3))
        FIELD_WINDOWED_BIGRAM_4.setTextField(doc, getWindowed(tokens, 4))
        FIELD_WINDOWED_BIGRAM_6.setTextField(doc, getWindowed(tokens, 6))
        FIELD_LETTER_2.setTextField(doc, getLetterGram(tokens, 2))
        FIELD_LETTER_3.setTextField(doc, getLetterGram(tokens, 3))
        FIELD_LETTER_4.setTextField(doc, getLetterGram(tokens, 4))

        paragraphIndex.addDocument(doc)

    }

    fun getWindowed(tokens: List<String>, windowSize: Int): String {
        val windowed = ArrayList<String>()
        (0 until tokens.size).forEach { i ->

            ( i + 1 until min(i + (windowSize + 1), tokens.size)).forEach { j ->
                windowed.add(tokens[i] + tokens[j])
                windowed.add(tokens[j] + tokens[i])
            }
        }
        return windowed.countDuplicates()
            .entries
            .sortedByDescending { it.value }
            .take(20)
            .map { (it.key + " ").repeat(it.value) }
            .joinToString(" ")
    }

    fun getTrigram(tokens: List<String>) =
        tokens.windowed(3, partialWindows = false)
            .map { it.joinToString("") }
            .joinToString(" ")

    fun getLetterGram(tokens: List<String>, gramSize: Int) =
            tokens.flatMap { token ->
                token.windowed(gramSize, partialWindows = false) }
                .joinToString(" ")

    fun run() {
        val corpusStream = File("/home/jsc57/data/corpus/paragraphCorpus/dedup.articles-paragraphs.cbor")
//        val corpusStream = File("/home/jsc57/data/corpus/paragraphCorpus/dedup.articles-paragraphs.cbor")
            .inputStream()
            .buffered()
            DeserializeData.iterableParagraphs(corpusStream)
                .forEachParallelQ(1000, 30) { paragraph: Data.Paragraph ->
                    processParagraphs(paragraph)
                    val result = paragraphCounter.incrementAndGet()
                    if (result % 10000 == 0) {
                        println(result)
                        paragraphIndex.commit()
                    }
                }
        paragraphIndex.commit()
        paragraphIndex.close()
    }

}



