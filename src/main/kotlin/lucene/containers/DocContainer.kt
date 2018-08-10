package lucene.containers

import lucene.indexers.IndexFields
import lucene.indexers.getList
import lucene.indexers.getString
import org.apache.lucene.document.Document
import org.apache.lucene.search.IndexSearcher
import java.lang.ref.WeakReference
import kotlin.properties.Delegates

class DocContainer<out A: IndexType>(var name: String,
                                     val qid: Int,
                                     val isRelevant: Int,
                                     val docId: Int,
                                     val searcher: TypedSearcher<A>,
                                     val index: Int,
                                     var score: Double = 0.0,
                                     val query: String,
                                     var dist: HashMap<String, Double> = HashMap(),
                                     var dist2: HashMap<Int, Double> = HashMap(),
                                     private val docType: Class<A>) {
    var features = ArrayList<FeatureContainer>()

    // Adjust the paragraph's score so that it is equal to the weighted sum of its features.
//    fun doc(): Document = searcher.doc(docId)
//    val doc: Document by Delegates.observable()
//    fun doc(vararg fieldsToLoad: IndexFields): IndexDoc<A> {
//        return docRef.get() ?: let {
//            val d = if (fieldsToLoad.size > 0) {
//                val fields = fieldsToLoad.map { it.field }.toSet()
//                IndexDoc<A>(searcher.doc(docId, fields), docId)
//            } else IndexDoc<A>(searcher.doc(docId), docId)
//            docRef = WeakReference(d)
//            d
//        }
//    }
    fun doc() = searcher.getIndexDoc(docId)

//    fun partialDoc(vararg fieldsToLoad: IndexFields): IndexDoc<A> {
//        val fields = fieldsToLoad.map { it.field }.toSet()
//        return IndexDoc<A>(searcher.doc(docId, fields), docId)
//    }


    fun rescore() {
        score = features.sumByDouble(FeatureContainer::getAdjustedScore)
    }

    fun cloneSelf(newQuery: String, relevant: (String) -> Int, qidNew: Int): DocContainer<A> =
            DocContainer(
                    name = name,
                    qid = qidNew,
                    query = newQuery,
                    searcher = searcher,
                    index = index,
                    score = score,
                    docId = docId,
                    isRelevant = relevant(name),
                    docType = docType
            )


    override fun toString(): String {
                return "$isRelevant qid:$qid " +
                    (1..features.size).zip(features)
                        .joinToString(separator = " ") { (id,feat) -> "$id:$feat" } +
                    " #$name # ${docId} ${docType.typeName.split("$").last()}"
    }

    fun toCustomString(qd: Int): String {
        return "$isRelevant qid:$qd " +
                (1..features.size).zip(features)
                    .joinToString(separator = " ") { (id,feat) -> "$id:$feat" } +
                " #$name # ${docId} ${docType.typeName.split("$").last()}"
    }

    companion object {
        inline fun<reified T: IndexType> createDocumentContainer(
                name: String,
                qid: Int,
                isRelevant: Int,
                docId: Int,
                searcher: TypedSearcher<T>,
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
typealias ContextEntityContainer = DocContainer<IndexType.CONTEXT_ENTITY>
typealias ContextSectionContainer = DocContainer<IndexType.CONTEXT_SECTION>


