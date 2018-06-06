package lucene.containers
import utils.stats.sanitizeDouble

/**
 * Desc: Represents a paragraph that has been scored by a feature.
 * @weight: The amount to adjust the feature's score (used when re-ranking)
 */
data class FeatureContainer(val score: Double, val weight: Double, val type: FeatureEnum) {
    fun getAdjustedScore(): Double = sanitizeDouble(score * weight)
    override fun toString(): String = getAdjustedScore().toString()
}
