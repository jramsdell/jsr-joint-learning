package entity

import language.GramIndexer
import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarStyle
import org.apache.lucene.document.*
import org.apache.lucene.index.MultiFields
import org.apache.lucene.search.IndexSearcher
import utils.*
import utils.lucene.getIndexSearcher
import utils.lucene.getIndexWriter
import utils.misc.identity
import utils.stats.normalize
import java.net.URISyntaxException
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.StreamSupport
import kotlin.coroutines.experimental.buildSequence

data class EntityData(
        val abstract: String?,
        val rdf: Set<String>,
        val tfidf: Double,
        val clarity: Double,
        val boostedLinkProbability: Double,
        val queryScope: Double,
        val id: Int,
        val mutualDependency: Double,
        val unigram: Map<String, Double>,
        val bigrams: Map<String, Double>,
        val bigram_windows: Map<String, Double>) {

    companion object {
//        fun emptyEntity(): EntityData {
//            return EntityData("", emptySet(), get("tfidf").toDouble())
//        }

        private fun splitAndCount(doc: Document, field: String) =
            doc.get(field)
                .split(" ")
                .groupingBy(::identity)
                .eachCount()
                .normalize()


        fun createEntityData(doc: Document) = doc.run {
            EntityData(
                    abstract = get("abstract"),
                    rdf = getValues("rdf").toSet(),
                    tfidf = get("tfidf").toDouble(),
                    clarity = get("clarity").toDouble(),
                    boostedLinkProbability = get("boosted_link_probability").toDouble(),
                    queryScope = get("query_score").toDouble(),
                    id = get("id").toInt(),
                    mutualDependency = get("mutual_dependency").toDouble(),
                    unigram = splitAndCount(doc, "unigram"),
                    bigrams = splitAndCount(doc, "bigrams"),
                    bigram_windows = splitAndCount(doc, "bigram_windows")
            )
        }

    }
}


class EntityDatabase(dbLoc: String = "") {
    val searcher = getIndexSearcher(dbLoc)


    fun getEntity(entity: String) =
            getEntityDocId(entity)?.let { docId ->
                val doc = searcher.doc(docId)
                val abstract = doc.get("abstract")
                val rdf = doc.getValues("rdf")
                EntityData.createEntityData(doc)
            }

    fun getEntityDocId(entity: String) =
        searcher.search(AnalyzerFunctions.createQuery(entity, field = "name"), 1)
            .scoreDocs
            .firstOrNull()
            ?.doc


    fun doSearch(query: String) {
        val booleanQuery = AnalyzerFunctions.createQuery(query, field = "abstract")
        searcher.search(booleanQuery, 10)
            .scoreDocs
            .forEach { scoreDoc ->
                val doc = searcher.doc(scoreDoc.doc)
                println(scoreDoc.score)
                println(doc.get("name"))
            }
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

//        // Now do entity linking
//        try {
//            val result = doIORequest { EntityStats.doTagMeQuery(abstract) } ?: return
//            result.forEach { (linkedEntity, rho) ->
//                doc.add(StringField("abstract_entities", linkedEntity, Field.Store.YES))
//                doc.add(StringField("abstract_rhos", rho.toString(), Field.Store.NO))
//            }
//        } catch (e: JSONException) { return }
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
//        termSeq.chunked(1000).forEach { chunk ->
//            chunk.forEachParallel { entity ->
//                val doc = Document()
//                doc.add(TextField("name", entity, Field.Store.YES))
//                val rdf = KotlinSparql.extractTypes(entity)
//                rdf.forEach { r ->
//                    doc.add(StringField("rdf", r, Field.Store.YES))
//                }
//
//                // Now add surface form and abstracts
//                try {
//                    addSurfaceForm(entity, doc)
//                } catch (e: URISyntaxException) {}
//                addAndLinkAbstract(entity, doc, gramIndexer)
//                writer.addDocument(doc)
//            }
//            writer.commit()
//            bar.stepBy(1000)
//        }

//        termSeq.forEach{ entity ->
//            val doc = Document()
//            doc.add(TextField("name", entity, Field.Store.YES))
//            val abstract = KotlinSparql.extractSingleAbstract(entity) ?: ""
//            val rdf = KotlinSparql.extractTypes(entity)
//            gramIndexer.index(doc, abstract)
//            rdf.forEach { r ->
//                doc.add(StringField("rdf", r, Field.Store.NO))
//            }
//            doc.add(TextField("abstract", abstract, Field.Store.YES))
//            bar.step()
//        }
        bar.stop()
        writer.close()
    }

}