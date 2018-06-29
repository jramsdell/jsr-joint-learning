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
import utils.lucene.getIndexWriter
import utils.lucene.paragraphs
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
import utils.lucene.pageFromCbor


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
                listOf(Indexer::extractEntityTextAndGrams, Indexer::extractMetaData, Indexer::addEntityHeaders)
        pageIndexers.add(Indexer(funcs))
    }

    fun addParagraphIndexer() {
        val funcs =
                listOf(Indexer::extractGram)
        paragraphIndexers.add(Indexer(funcs))

    }


//    fun processPageIndexers(chunk: Iterable<Data.Page>) {
//        val docs =
//                chunk.map { page -> LuceneDocumentContainer(page = page) }
//        pageIndexers.forEach { pageIndexer -> pageIndexer.doExtract(docs) }
//        docs.forEachParallel { doc -> pageIndex.addDocument(doc.doc) }
//        Thread( { pageIndex.commit() } ).run()
//        println("Pages: ${pageCounter.addAndGet(docs.size)}")
//    }

    fun processPageIndexers(page: Data.Page) {
        val doc = LuceneDocumentContainer(page = page)
        pageIndexers.forEach { pageIndexer -> pageIndexer.doExtract(listOf(doc)) }
        pageIndex.addDocument(doc.doc)
    }

//    fun processParagraphIndexers(chunk: Iterable<Data.Page>) {
//        val docs =
//                chunk.flatMap { page ->
//                    page.paragraphs().map { paragraph ->
//                        LuceneDocumentContainer(page = page, paragraph = paragraph)
//                    }
//                }
//        paragraphIndexers.forEach { pageIndexer -> pageIndexer.doExtract(docs) }
////        paragraphIndex.addDocuments(docs.map { it.doc })
////        docs.forEachChunkedParallel(1000) { doc -> paragraphIndex.addDocument(doc.doc) }
//        docs.forEachParallelQ(1000, 30) { doc -> paragraphIndex.addDocument(doc.doc) }
//        println("Paragraphs: ${paragraphCounter.addAndGet(docs.size)}")
//        paragraphIndex.commit()
//        Thread( { paragraphIndex.commit() } ).start()
//    }

    fun processParagraphIndexers(page: Data.Page) {
        val docs =
                page.paragraphs().map { paragraph ->
                    LuceneDocumentContainer(page = page, paragraph = paragraph)
                }
        paragraphIndexers.forEach { pageIndexer -> pageIndexer.doExtract(docs) }
//        paragraphIndex.addDocuments(docs.map { it.doc })
//        docs.forEachChunkedParallel(1000) { doc -> paragraphIndex.addDocument(doc.doc) }
//        docs.forEachParallelQ(100, 5) { doc -> paragraphIndex.addDocument(doc.doc) }
        docs.forEach{ doc -> paragraphIndex.addDocument(doc.doc) }
//        println("Paragraphs: ${paragraphCounter.addAndGet(docs.size)}")
//        paragraphIndex.commit()
//        Thread( { paragraphIndex.commit() } ).start()
    }

//    fun wee() {
//        DeserializeData.
//    }




    fun run() {
        corpusStreams.forEach { corpusStream ->
            DeserializeData.iterableAnnotations(corpusStream)
                .take(1000)
                .forEachParallelQ(2000, 60) { page: Data.Page ->
                    processPageIndexers(page)
                    processParagraphIndexers(page)
                    val result = pageCounter.incrementAndGet()
                    if (result % 10000 == 0) {
                        println(result)
                        paragraphIndex.commit()
                        pageIndex.commit()
                    }
                }
        }
        paragraphIndex.commit()
        pageIndex.commit()
        paragraphIndex.close()
        pageIndex.close()
    }

}



