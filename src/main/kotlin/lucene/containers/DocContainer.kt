package lucene.containers

import lucene.indexers.IndexFields
import lucene.indexers.getList
import lucene.indexers.getString
import org.apache.lucene.document.Document
import org.apache.lucene.search.IndexSearcher
import java.lang.ref.WeakReference
import kotlin.properties.Delegates

class DocContainer<out A: IndexType>(val name: String,
                                     val qid: Int,
                                     val isRelevant: Int,
                                     val docId: Int,
                                     val searcher: IndexSearcher,
                                     val index: Int,
                                     var score: Double = 0.0,
                                     val query: String,
                                     private val docType: Class<A>) {
    val features = ArrayList<FeatureContainer>()

    // Adjust the paragraph's score so that it is equal to the weighted sum of its features.
//    fun doc(): Document = searcher.doc(docId)
//    val doc: Document by Delegates.observable()
    private var docRef: WeakReference<IndexDoc<A>> = WeakReference<IndexDoc<A>>(null)
    fun doc(): IndexDoc<A> {
        return docRef.get() ?: let {
            val d = IndexDoc<A>(searcher.doc(docId), docId)
            docRef = WeakReference(d)
            d
        }
    }


    fun rescore() {
        score = features.sumByDouble(FeatureContainer::getAdjustedScore)
    }


    override fun toString(): String {
                return "$isRelevant qid:$qid " +
                    (1..features.size).zip(features)
                        .joinToString(separator = " ") { (id,feat) -> "$id:$feat" } +
                    "#$name #${docType.typeName.split("$").last()}"
    }

    companion object {
        inline fun<reified T: IndexType> createDocumentContainer(
                name: String,
                qid: Int,
                isRelevant: Int,
                docId: Int,
                searcher: IndexSearcher,
                index: Int,
                score: Double = 0.0,
                query: String): DocContainer<T> {

            return DocContainer(name, qid, isRelevant, docId, searcher, index, score, query,
                    docType = T::class.java)
        }

    }


}

typealias ParagraphContainer = DocContainer<IndexType.PARAGRAPH>
typealias EntityContainer = DocContainer<IndexType.ENTITY>
typealias SectionContainer = DocContainer<IndexType.SECTION>


