package lucene

import edu.unh.cs.treccar_v2.read_data.DeserializeData
import entity.SpotlightEntityLinker
import language.GramIndexer
import org.apache.lucene.document.*
import org.apache.lucene.index.IndexableFieldType
import utils.*
import java.io.BufferedInputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.StreamSupport
import kotlin.coroutines.experimental.buildIterator


class LuceneIndexer(val indexLoc: String, val corpusLoc: String, val serverLocation: String) {
    val indexWriter = getIndexWriter(indexLoc)
    val linker = SpotlightEntityLinker(serverLocation)
    val gramIndexer = GramIndexer()

    private fun iterWrapper(f: BufferedInputStream): Iterable<Pair<String, String>> {
        val iter = DeserializeData.iterParagraphs(f)
        val iterWrapper = buildIterator<Pair<String,String>>() {
            while (iter.hasNext()) {
                val nextPar = iter.next()

                yield(nextPar.paraId to nextPar.textOnly)
            }
        }
        return Iterable { iterWrapper }
    }

//    fun addDocument(para: Data.Paragraph) {
    fun addDocument(paraID: String, paraText: String) {
        val doc = Document()
//        doc.add(StringField(PID, para.paraId, Field.Store.YES))
//        doc.add(TextField(CONTENT, para.textOnly, Field.Store.YES))
        doc.add(StringField(PID, paraID, Field.Store.YES))
        doc.add(TextField(CONTENT, paraText, Field.Store.YES))

        // Add annotations
        linker.queryServer(paraText)
            .forEach { entity: String ->
                doc.add(StringField("spotlight", entity, Field.Store.YES))
            }
//        doc.add(DoubleDocValuesField("confidence", 1.0))

        // Add bigrams
        gramIndexer.index(doc, paraText)

        indexWriter.addDocument(doc)
    }

    fun index() {
        val fStream = File(corpusLoc).inputStream().buffered()
        val ip = iterWrapper(fStream)
        val counter = AtomicInteger(0)

//        ip.forEachParallel { paragraph: Data.Paragraph ->
//            addDocument(paragraph)
        StreamSupport.stream(ip.spliterator(), true).forEach { (paraID, paraText)  ->
            addDocument(paraID, paraText)
            if (counter.incrementAndGet() % 10000 == 0) {
                println(counter)
                indexWriter.commit()
            }
        }


    }
}