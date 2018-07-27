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



class QuickAndDirtyParagraphIndexer() {
    val corpus = "/home/jsc57/data/corpus/paragraphCorpus/dedup.articles-paragraphs.cbor"
    val paragraphCounter = AtomicInteger()
//    val speedy = "/speedy/jsc57/"
    val speedy = "/home/jsc57/data/backup/"
    val paragraphIndex = getIndexWriter("${speedy}extractions/paragraph", mode = IndexWriterConfig.OpenMode.CREATE)



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
        FIELD_ENTITIES.setTextField(doc, entities)
        FIELD_ENTITIES_UNIGRAMS.setTextField(doc, convertToUnigrams(entities))

        val (unigrams, bigrams, windowed) = getGramsFromContent(paragraph.textOnly)

        FIELD_UNIGRAM.setTextField(doc, unigrams)
        FIELD_BIGRAM.setTextField(doc, bigrams)
        FIELD_WINDOWED_BIGRAM.setTextField(doc, windowed)

        paragraphIndex.addDocument(doc)

    }

    fun run() {
        val corpusStream = File("/home/jsc57/data/corpus/paragraphCorpus/dedup.articles-paragraphs.cbor")
            .inputStream()
            .buffered()
            DeserializeData.iterableParagraphs(corpusStream)
                .forEachParallelQ(1000, 10) { paragraph: Data.Paragraph ->
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



