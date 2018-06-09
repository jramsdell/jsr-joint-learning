package lucene.indexers

import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import lucene.containers.LuceneDocumentContainer
import org.apache.lucene.index.IndexWriterConfig
import org.json.JSONObject
import utils.lucene.getIndexWriter
import utils.lucene.paragraphs
import utils.parallel.asIterable
import utils.parallel.forEachParallel
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.StreamSupport


class IndexerStream(corpusLocs: List<String>, val chunkSize: Int = 1000) {
    val pageIndexers = ArrayList<Indexer>()
    val paragraphIndexers = ArrayList<Indexer>()
    val pageCounter = AtomicInteger()
    val paragraphCounter = AtomicInteger()
    val pageIndex = getIndexWriter("extractions/page", mode = IndexWriterConfig.OpenMode.CREATE)
    val paragraphIndex = getIndexWriter("extractions/paragraph", mode = IndexWriterConfig.OpenMode.CREATE)

    val corpusStreams = corpusLocs.map { corpusLoc ->
        File(corpusLoc)
            .inputStream()
            .buffered()
    }

//    private inline fun<reified A> getStream(corpusLoc: String) = when (A::class) {
//        Data.Page::class -> fromPageCorpus(corpusLoc)
//        Data.Paragraph::class -> fromParagraphCorpus(corpusLoc)
//        else                  -> throw IllegalArgumentException("Unknown type")
//
//    }

//    @Suppress("UNCHECKED_CAST")
//    private inline fun<reified A> getStream(corpusLoc: String): Iterable<A> = when (A::class) {
//            Data.Page::class      -> fromPageCorpus(corpusLoc)
//            Data.Paragraph::class -> fromParagraphCorpus(corpusLoc)
//            else                  -> throw IllegalArgumentException("Unknown type")
//        } as Iterable<A>

    fun addPageIndexer() {
        val funcs =
                listOf(Indexer::extractMetaData, Indexer::extractMetaData)
        pageIndexers.add(Indexer(funcs))
    }

    fun addParagraphIndexer() {
        val funcs =
                listOf(Indexer::extractGram)
        paragraphIndexers.add(Indexer(funcs))

    }


    fun processPageIndexers(chunk: Iterable<Data.Page>) {
        val docs =
                chunk.map { page -> LuceneDocumentContainer(page = page) }
        pageIndexers.forEach { pageIndexer -> pageIndexer.doExtract(docs) }
        docs.forEachParallel { doc -> pageIndex.addDocument(doc.doc) }
        Thread( { pageIndex.commit() } ).run()
        println("Pages: ${pageCounter.addAndGet(docs.size)}")
    }

    fun processParagraphIndexers(chunk: Iterable<Data.Page>) {
        val docs =
                chunk.flatMap { page ->
                    page.paragraphs().map { paragraph ->
                        LuceneDocumentContainer(page = page, paragraph = paragraph)
                    }
                }
        paragraphIndexers.forEach { pageIndexer -> pageIndexer.doExtract(docs) }
//        paragraphIndex.addDocuments(docs.map { it.doc })
        docs.forEachParallel { doc -> paragraphIndex.addDocument(doc.doc) }
        println("Paragraphs: ${paragraphCounter.addAndGet(docs.size)}")
        Thread( { paragraphIndex.commit() } ).run()
    }


    fun run() {
        corpusStreams.forEach { corpusStream ->
            DeserializeData.iterAnnotations(corpusStream)
                .asSequence()
                .chunked(chunkSize)
                .forEach { chunk ->
                    processPageIndexers(chunk)
                    processParagraphIndexers(chunk)
                }
        }
        paragraphIndex.close()
        pageIndex.close()
    }

}



