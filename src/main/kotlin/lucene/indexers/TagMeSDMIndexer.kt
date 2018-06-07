package lucene.indexers

import entity.EntityStats
import entity.TagMeData
import language.GramIndexer
import org.apache.lucene.document.*
import utils.misc.CONTENT
import utils.misc.PID
import utils.lucene.getIndexSearcher
import utils.lucene.getIndexWriter
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.StreamSupport
import kotlin.coroutines.experimental.buildIterator

data class ProximityDocument(
        val title: String,
        val abstract: String,
        val pid: String,
        val spot: String,
        val rho: Double,
        val linkProbability: Double,
        val categories: List<String>,
//        val unigram_4: String,
//        val bigram_4: String,
//        val windowed_4: String,
        val unigram_25: String,
        val bigram_25: String,
        val windowed_25: String
        ) {
    companion object {
        fun createProximityDocument(doc: Document): ProximityDocument = with(doc) {
            return ProximityDocument(
                    title = get("title"),
                    abstract = get("abstract"),
                    pid = get("pid"),
                    categories = get("categories").split(" "),
                    linkProbability = get("link_probability").toDouble(),
                    rho = get("rho").toDouble(),
                    spot = get("spot"),
//                    unigram_4 = get("unigram_4"),
//                    bigram_4 = get("bigrams_4"),
//                    windowed_4 = get("bigram_windows_4"),
                    unigram_25 = get("unigram_4"),
                    bigram_25 = get("bigrams_4"),
                    windowed_25 = get("bigram_windows_4")
            )
        }
    }
}



class TagMeSDMIndexer(val indexLoc: String) {
//    val writer = getIndexWriter(indexLoc)
    val entityProximityWriter = getIndexWriter("entity_proximity/")
    val searcher = getIndexSearcher(indexLoc)
    val gramIndexer: GramIndexer = GramIndexer()
    val counter = AtomicInteger()

    fun debugDocuments(){
        val proxSearcher = getIndexSearcher("entity_proximity/")
        val doc = proxSearcher.doc(0)
        println(doc.fields)
        with (ProximityDocument.createProximityDocument(doc)) {
            println("title: $title")
            println("Rho: $rho")
            println("link_prob: $linkProbability")
            println("categories: $categories")
//            println("unigram: $unigram_4")
            println("--------\n")
        }
    }

    fun createDocumentIterator() =
        buildIterator<Document> {
            (0 until searcher.indexReader.maxDoc()).forEach { index ->
                yield(searcher.doc(index))
            }
        }

    fun writeProximityDocument(pid: String, text: String, entity: TagMeData) {
        val entityPID = "${pid}_${entity.title}"
        val doc = Document()
//        gramIndexer.indexBall(doc, text, 4, entity.start, entity.end)
        gramIndexer.indexBall(doc, text, 4, entity.start, entity.end)
//        gramIndexer.index(doc, entity.abstract)

        doc.add(TextField("abstract", entity.abstract, Field.Store.YES))
        doc.add(StringField("pid", entityPID, Field.Store.YES))
        doc.add(StringField("title", entity.title, Field.Store.YES))
        doc.add(StringField("rho", entity.rho.toString(), Field.Store.YES))
        doc.add(StringField("link_probability", entity.linkProbability.toString(), Field.Store.YES))
        doc.add(TextField("spot", entity.spot.toLowerCase(), Field.Store.YES))
        doc.add(TextField("categories", entity.categories.joinToString(" "), Field.Store.YES))

        entityProximityWriter.addDocument(doc)
        counter.incrementAndGet().let { curDocNumber ->
            if (curDocNumber % 10000 == 0) {
                println(curDocNumber)
                entityProximityWriter.commit()
            }
        }
    }

    fun addTagMeLinks(doc: Document) {
        val text = doc.get(CONTENT)
        val pid = doc.get(PID)
        val entities = EntityStats.retrieveTagMeData(text) ?: return
        entities
            .forEach { entity: TagMeData ->
//                doc.add(StringField("tagme", entity.title, Field.Store.YES))
//                writeProximityDocument(pid, text, entity)
            }
    }


    fun doTagMeIndexing() {
        StreamSupport.stream(createDocumentIterator().asSequence().asIterable().spliterator(), true)
            .forEach(this::addTagMeLinks)
        entityProximityWriter.commit()
        entityProximityWriter.close()

    }
}