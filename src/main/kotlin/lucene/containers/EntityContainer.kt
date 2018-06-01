package lucene.containers

import org.apache.lucene.document.Document
import java.util.ArrayList

data class EntityContainer(
        val name: String,
        val qid: Int,
        val docId: Int,
        val isRelevant: Boolean,
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

}

