package lucene.indexers

import edu.unh.cs.treccar_v2.read_data.DeserializeData
import entity.SpotlightEntityLinker
import language.GramIndexer
import lucene.containers.FieldNames
import org.apache.lucene.document.*
import utils.lucene.getIndexWriter
import utils.misc.CONTENT
import utils.misc.PID
import java.io.BufferedInputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.stream.StreamSupport
import kotlin.coroutines.experimental.buildIterator


class LuceneIndexer(val indexLoc: String, val corpusLoc: String, val serverLocation: String) {
    val indexWriter = getIndexWriter(indexLoc)
    val linker = SpotlightEntityLinker(serverLocation)
    val gramIndexer = GramIndexer()
    val l = AtomicLong()

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

        // Add annotationks
//        val entities = EntityStats.retrieveTagMeData(paraText) ?: return
//         l.addAndGet(measureTimeMillis { EntityStats.retrieveTagMeData(paraText) } )

    val entities = linker.queryServer(paraText).joinToString(" ")
    doc.add(StringField(FieldNames.FIELD_ENTITIES.field, entities, Field.Store.YES))
//            .forEach { entity: String ->
//                doc.add(StringField("spotlight", entity, Field.Store.YES))
//            }
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
            println(counter.get())
            if (counter.incrementAndGet() % 300 == 0) {
                println(l.get().toDouble() / 1000)
                indexWriter.commit()
            }
        }


    }
}

class Other() {
    fun deserialize(cbor: String) {
        val f = File(cbor).inputStream().buffered(16 * 1024)
        val counter = AtomicInteger()

        DeserializeData.iterableAnnotations(f)
            .take(1)
            .forEach { page ->
                println(page.pageName)
                println(page.pageMetadata)
                page.flatSectionPathsParagraphs().forEach { paragraph ->
                    println(paragraph.paragraph.entitiesOnly)
                }

//                // Extract all of the anchors/entities and add them to database
//                page.flatSectionPathsParagraphs()
//                    .flatMap { psection ->
//                        psection.paragraph.bodies
//                            .filterIsInstance<Data.ParaLink>()
//                            .map { paraLink -> paraLink.anchorText.toLowerCase() to paraLink.page.toLowerCase() } }
//                    .apply(this::addLinks)
            }

    }
}

fun main(args: Array<String>) {
    val o = Other().deserialize("/home/hcgs/Desktop/projects/jsr-joint-learning/data/test200/test200-train/train.pages.cbor")
}