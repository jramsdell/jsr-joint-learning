package lucene.containers

import lucene.indexers.IndexFields
import org.apache.lucene.index.IndexReader
import org.apache.lucene.search.IndexSearcher
import utils.AnalyzerFunctions


class TypedSearcher<out A: IndexType>(r: IndexReader?, private val typeClass: Class<A>) : IndexSearcher(r) {

    companion object {
        inline fun<reified C: IndexType> createTypedSearcher(r: IndexReader) =
                TypedSearcher(r, C::class.java)

        inline fun<reified C: IndexType> createTypedSearcher(r: IndexSearcher) =
                TypedSearcher(r.indexReader, C::class.java)
    }

    fun getDocumentByField(id: String): IndexDoc<A>? {
        val field = when (typeClass) {
            IndexType.ENTITY::class.java -> IndexFields.FIELD_NAME.field
            IndexType.PARAGRAPH::class.java -> IndexFields.FIELD_PID.field
            IndexType.SECTION::class.java -> IndexFields.FIELD_SECTION_ID.field
            else -> "text"
        }
        val q = AnalyzerFunctions.createQuery(id, field = field)
        val sc = search(q, 1).scoreDocs.firstOrNull()
        return sc?.let { IndexDoc(doc(sc.doc), sc.doc) }
    }


    fun getIndexDoc(id: Int) = IndexDoc<A>(doc(id), id)
}

typealias EntitySearcher = TypedSearcher<IndexType.ENTITY>
typealias ParagraphSearcher = TypedSearcher<IndexType.PARAGRAPH>
typealias SectionSearcher = TypedSearcher<IndexType.SECTION>

