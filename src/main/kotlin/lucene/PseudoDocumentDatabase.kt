package lucene

import experiment.KotlinRanklibFormatter
import lucene.containers.*
import lucene.indexers.IndexFields
import lucene.indexers.getString
import lucene.indexers.setTextField
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.TextField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.search.IndexSearcher
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap
import utils.lucene.getIndexWriter
import utils.lucene.getTypedSearcher
import java.io.File


data class PseudoDatabase(
//                          var paragraphSearcher: ParagraphSearcher,
                          var entitySearcher: EntitySearcher
//                          var sectionSearcher: SectionSearcher)
)

object PseudoDocumentDatabase {
    val base = "pseudo/"
//    val databases = ConcurrentHashMap<Int, PseudoDatabase>()
    init {
        File(base).mkdir()
    }



//    fun buildDatabases() {
//        formatter.queryContainers.forEachIndexed { index, qContainer ->
//            databases[index] = buildDatabase(index, qContainer)
//        }
//    }

    fun buildDatabase(index: String,
                      pContainers: List<ParagraphContainer>,
                      sContainers: List<SectionContainer>,
                      eContainers: List<EntityContainer>): PseudoDatabase {
        File("$base/${index}/").mkdir()

//        val pSearcher = addWriter("$base/$index/paragraph", pContainers)
        val eSearcher = addWriter("$base/$index/entity", eContainers)
//        val sSearcher = addWriter("$base/$index/section", sContainers)

        return PseudoDatabase(
                entitySearcher = eSearcher)
//                paragraphSearcher = pSearcher,
//                sectionSearcher = sSearcher )
    }

    inline fun<reified T: IndexType> addWriter(writerLoc: String, docs: List<DocContainer<T>>): TypedSearcher<T> {
        val writer = getIndexWriter(writerLoc, IndexWriterConfig.OpenMode.CREATE)
        docs.forEach { container ->
            val oDoc = container.searcher.doc(container.docId)
            val nDoc = Document()
            IndexFields.FIELD_NAME.setTextField(nDoc, IndexFields.FIELD_NAME.getString(oDoc))
            IndexFields.FIELD_UNIGRAM.setTextField(nDoc, IndexFields.FIELD_UNIGRAM.getString(oDoc))
            IndexFields.FIELD_BIGRAM.setTextField(nDoc, IndexFields.FIELD_BIGRAM.getString(oDoc))
            IndexFields.FIELD_WINDOWED_BIGRAM.setTextField(nDoc, IndexFields.FIELD_WINDOWED_BIGRAM.getString(oDoc))
            nDoc.add(TextField("did", "${container.docId}", Field.Store.YES))
//            doc.add(TextField(field, fieldValue, Field.Store.YES))

            writer.addDocument(nDoc)
        }
        writer.commit()
        writer.close()
        return getTypedSearcher<T>(writerLoc)
    }

}