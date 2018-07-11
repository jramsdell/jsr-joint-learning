package lucene.containers

import org.apache.lucene.document.Document
import org.apache.lucene.search.IndexSearcher


//class EntityContainer(name: String,
//                      qid: Int,
//                      isRelevant: Int,
//                      features: ArrayList<FeatureContainer>,
//                      docId: Int,
//                      searcher: IndexSearcher,
//                      index: Int,
//                      score: Double,
//                      query: String)
//                      : DocumentContainer(name, qid, isRelevant, features, docId, searcher, index, score, query, docType = DocumentContainerType.TYPE_ENTITY) {
//
//}





//data class EntityContainer(
//        val name: String,
//        val qid: Int,
//        val index: Int,
//        val docId: Int,
//        var isRelevant: Int = 0,
//        val queryFeatures: ArrayList<FeatureContainer> = arrayListOf(),
//        val sharedFeatures: ArrayList<FeatureContainer> = arrayListOf(),
//        val documentFeatures: ArrayList<FeatureContainer> = arrayListOf(),
//        var score: Double = 0.0,
//        val searcher: IndexSearcher
////        val doc: Document
//) {
//    fun doc(): Document = searcher.doc(docId)
////    val doc: Document get() = searcher.doc(docId)
////    get() = Document()
//
//    // Adjust the paragraph's score so that it is equal to the weighted sum of its features.
//    fun rescoreEntity() {
//        score = documentFeatures.sumByDouble(FeatureContainer::getAdjustedScore) +
//                sharedFeatures.sumByDouble(FeatureContainer::getAdjustedScore) +
//                queryFeatures.sumByDouble(FeatureContainer::getAdjustedScore)
//    }
//
//    override fun toString(): String {
//        val combinedFeaures = queryFeatures + documentFeatures + sharedFeatures
////        return "${if (isRelevant) 1 else 0} qid:${qid + 1000} " +
//                return "$isRelevant qid:${qid + 1000} " +
////                        return "$isRelevant qid:${qid} " +
////                return "${if (isRelevant) 1 else 0} qid:${qid} " +
//                (1..combinedFeaures.size).zip(combinedFeaures)
//                    .joinToString(separator = " ") { (id,feat) -> "$id:$feat" } + " # entity"
//    }
//
//}

