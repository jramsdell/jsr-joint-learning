package entity

import language.GramIndexer
import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarStyle
import org.apache.lucene.document.*
import org.apache.lucene.index.MultiFields
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import utils.*
import utils.lucene.getIndexSearcher
import utils.lucene.getIndexWriter
import utils.lucene.getOrDefault
import utils.lucene.getValuesOrDefault
import utils.misc.identity
import utils.stats.normalize
import java.net.URISyntaxException
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.StreamSupport
import kotlin.coroutines.experimental.buildSequence

data class EntityData(
        val name: String,
//        val abstract: String?,
//        val rdf: Set<String>,
//        val tfidf: Double,
//        val clarity: Double,
//        val boostedLinkProbability: Double,
//        val queryScope: Double,
//        val id: Int,
        val docId: Int
//        val mutualDependency: Double
//        val unigram: Map<String, Double>,
//        val bigrams: Map<String, Double>,
//        val bigram_windows: Map<String, Double>
) {

    companion object {
//        fun emptyEntity(): EntityData {
//            return EntityData("", emptySet(), get("tfidf").toDouble())
//        }



        fun createEntityData(doc: Document, docId: Int) = doc.run {
            EntityData(
                    name = get("name"),
//                    abstract = get("abstract") ?: "",
//                    rdf = getValues("rdf").toSet(),
//                    tfidf = get("tfidf")?.toDouble() ?: 0.0,
//                    clarity = get("clarity")?.toDouble() ?: 0.0,
//                    boostedLinkProbability = get("boosted_link_probability")?.toDouble() ?: 0.0,
//                    queryScope = get("query_score")?.toDouble() ?: 0.0,
//                    id = get("id")?.toInt() ?: 0,
                    docId = docId
//                    mutualDependency = get("mutual_dependency")?.toDouble() ?: 0.0
//                    unigram = splitAndCount(doc, "unigram"),
//                    bigrams = splitAndCount(doc, "bigrams"),
//                    bigram_windows = splitAndCount(doc, "bigram_windows")
            )
        }

    }
}


class EntityDatabase(dbLoc: String = "") {
    val searcher = getIndexSearcher(dbLoc)


    fun getEntity(entity: String) =
            getEntityDocId(entity)?.let(::getEntityByID)

    fun getEntityByID(id: Int): EntityData {
            val doc = searcher.doc(id)
            return EntityData.createEntityData(doc, id)
    }

    fun getDocumentById(id: Int) = searcher.doc(id)

    fun getEntityDocument(entity: String) =
            getEntityDocId(entity)?.let { searcher.doc(it) }

    fun getEntityDocId(entity: String) =
        searcher.search(AnalyzerFunctions.createQuery(entity, field = "name"), 1)
            .scoreDocs
            .firstOrNull()
            ?.doc


    fun doSearch(query: String, nDocs: Int): TopDocs {
        val booleanQuery = AnalyzerFunctions.createQuery(query, field = "abstract", useFiltering = true)
        return searcher.search(booleanQuery, nDocs)
    }

    fun getEntityDocuments(query: String, nDocs: Int): List<Document> {
        val booleanQuery = AnalyzerFunctions.createQuery(query, field = "abstract", useFiltering = true)
        return searcher.search(booleanQuery, nDocs)
            .scoreDocs
            .map { scoreDoc -> searcher.doc(scoreDoc.doc) }
    }


    private fun addSurfaceForm(entity: String, doc: Document) {
        val surface = EntityStats.doSurfaceFormQuery(entity) ?: return
        with(surface) {
            doc.add(StringField("tfidf", tfidf.toString(), Field.Store.YES))
            doc.add(StringField("clarity", clarity.toString(), Field.Store.YES))
            doc.add(StringField("boosted_link_probability", boostedLinkProbability.toString(), Field.Store.YES))
            doc.add(StringField("query_score", queryScope.toString(), Field.Store.YES))
            doc.add(StringField("mutual_dependency", mutualDependency.toString(), Field.Store.YES))
            doc.add(StringField("id", id.toString(), Field.Store.YES))
        }
    }

    private fun addAndLinkAbstract(entity: String, doc: Document, gramIndexer: GramIndexer) {
        val abstract = KotlinSparql.extractSingleAbstract(entity) ?: ""
        doc.add(TextField("abstract", abstract, Field.Store.YES))
        gramIndexer.index(doc, abstract)
    }


    fun createEntityDatabase(indexSearcher: IndexSearcher) {
        val writer = getIndexWriter("entity_index_2")
        val gramIndexer = GramIndexer()

        val fields = MultiFields.getFields(indexSearcher.indexReader)
        val spotLightTerms = fields.terms("spotlight")
        val numTerms = 2100000
        val termIterator = spotLightTerms.iterator()

        // Build a sequence that lets us iterate over terms in chunks and run them in parallel
        val termSeq = buildSequence<String>() {
            while (true) {
                val bytesRef = termIterator.next() ?: break
                yield(bytesRef.utf8ToString())
            }
        }

        val bar = ProgressBar("Entities Added", numTerms.toLong(), ProgressBarStyle.ASCII)
        bar.start()
        val counter = AtomicInteger()
        StreamSupport.stream(termSeq.asIterable().spliterator(), true).forEach { entity ->
            val doc = Document()
            doc.add(TextField("name", entity, Field.Store.YES))
            val rdf = KotlinSparql.extractTypes(entity)
            rdf.forEach { r ->
                doc.add(StringField("rdf", r, Field.Store.YES))
            }

            // Now add surface form and abstracts
            try {
                addSurfaceForm(entity, doc)
            } catch (e: URISyntaxException) {}
            addAndLinkAbstract(entity, doc, gramIndexer)
            writer.addDocument(doc)
            if (counter.incrementAndGet() % 1000 == 0) {
                writer.commit()
                bar.stepBy(1000)
            }
        }
        bar.stop()
        writer.close()
    }

}