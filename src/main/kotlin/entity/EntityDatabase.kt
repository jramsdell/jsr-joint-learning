package entity

import language.GramIndexer
import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarStyle
import org.apache.lucene.document.*
import org.apache.lucene.index.MultiFields
import org.apache.lucene.queries.function.docvalues.DoubleDocValues
import org.apache.lucene.queries.function.docvalues.LongDocValues
import org.apache.lucene.search.IndexSearcher
import org.json.JSONException
import utils.*
import java.net.URISyntaxException
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.StreamSupport
import kotlin.coroutines.experimental.buildIterator
import kotlin.coroutines.experimental.buildSequence

data class EntityData(
        val abstract: String?,
        val rdf: Set<String> ) {

    companion object {
        fun emptyEntity(): EntityData {
            return EntityData("", emptySet())
        }
    }
}


class EntityDatabase(dbLoc: String = "") {
    val searcher = getIndexSearcher(dbLoc)


    fun getEntity(entity: String) =
        searcher.search(AnalyzerFunctions.createQuery(entity, field = "name"), 1).scoreDocs
            .firstOrNull()
            ?.let { sc ->
                val doc = searcher.doc(sc.doc)
                val abstract = doc.get("abstract")
                val rdf = doc.getValues("rdf")
                return EntityData(abstract, rdf.toSet())
            } ?: EntityData.emptyEntity()

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