package lucene.containers

import lucene.indexers.IndexFields
import org.apache.lucene.document.Document
import org.apache.lucene.search.IndexSearcher

/**
 * Class: ParagraphContainer
 * Description: Represents a scored paragraph (from TopDocs).
 * @param pid: Paragraph Id (obtained from Lucene index)
 * @param qid: Query ID (index of lucene that yielded the TopDocs)
 * @param isRelevant: whether or not this is a relevant paragraph (obtained from qrels)
 * @param features: Scores (from scoring functions) add added to this array for use in reweighting and rescoring
 * @param docId: Document id that this paragraph belongs to
 * @param score: Used when rescoring and reweighting the features
 */
data class ParagraphContainer(val pid: String,
                              val qid: Int,
                              val isRelevant: Boolean,
                              val features: ArrayList<FeatureContainer>,
                              val queryFeatures: ArrayList<FeatureContainer> = ArrayList(),
                              val entityFeatures: ArrayList<FeatureContainer> = ArrayList(),
                              val sharedFeatures: ArrayList<FeatureContainer> = ArrayList(),
                              val docId: Int,
                              val searcher: IndexSearcher,
                              val index: Int,
//                              val doc: Document,
                              var score: Double = 0.0,
                              val query: String) {

    // Adjust the paragraph's score so that it is equal to the weighted sum of its features.
    fun doc(): Document = searcher.doc(docId)
    fun rescoreParagraph() {
        score = queryFeatures.sumByDouble(FeatureContainer::getAdjustedScore) +
                entityFeatures.sumByDouble(FeatureContainer::getAdjustedScore) +
                sharedFeatures.sumByDouble(FeatureContainer::getAdjustedScore)

    }

    // Convenience override: prints RankLib compatible lines
//    override fun toString(): String =
//            "${if (isRelevant) 1 else 0} qid:$qid " +
//                    (1..features.size).zip(features)
//                        .joinToString(separator = " ") { (id,feat) -> "$id:$feat" } +
//                    " #docid=$pid"

    override fun toString(): String {
        val combinedFeaures = queryFeatures + entityFeatures + sharedFeatures
        return "${if (isRelevant) 1 else 0} qid:$qid " +
                    (1..combinedFeaures.size).zip(combinedFeaures)
                        .joinToString(separator = " ") { (id,feat) -> "$id:$feat" } +
                    " #$query#$pid"
//        " #$query#$pid#${doc.get(IndexFields.FIELD_TEXT.field).replace("\n", " ")}"
    }

}
