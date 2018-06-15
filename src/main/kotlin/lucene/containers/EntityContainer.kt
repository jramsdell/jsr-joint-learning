package lucene.containers

import org.apache.lucene.document.Document
import java.util.ArrayList

data class EntityContainer(
        val name: String,
        val qid: Int,
        val docId: Int,
        var isRelevant: Boolean = false,
        val queryFeatures: ArrayList<FeatureContainer> = arrayListOf(),
        val sharedFeatures: ArrayList<FeatureContainer> = arrayListOf(),
        val documentFeatures: ArrayList<FeatureContainer> = arrayListOf(),
        var score: Double = 0.0,
        val doc: Document) {

    // Adjust the paragraph's score so that it is equal to the weighted sum of its features.
    fun rescoreEntity() {
        score = documentFeatures.sumByDouble(FeatureContainer::getAdjustedScore) +
                sharedFeatures.sumByDouble(FeatureContainer::getAdjustedScore) +
                queryFeatures.sumByDouble(FeatureContainer::getAdjustedScore)
    }

    override fun toString(): String {
        val combinedFeaures = queryFeatures + documentFeatures + sharedFeatures
        return "${if (isRelevant) 1 else 0} qid:${qid + 1000} " +
                (1..combinedFeaures.size).zip(combinedFeaures)
                    .joinToString(separator = " ") { (id,feat) -> "$id:$feat" } + " # entity"
    }

}

