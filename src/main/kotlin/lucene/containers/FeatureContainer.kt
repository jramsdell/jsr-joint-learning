package lucene.containers
import utils.stats.sanitizeDouble

/**
 * Desc: Represents a paragraph that has been scored by a feature.
 * @weight: The amount to adjust the feature's score (used when re-ranking)
 */
data class FeatureContainer(var score: Double, var unnormalizedScore: Double = 0.0, val weight: Double, val type: FeatureEnum) {
//    fun getAdjustedScore(): Double = sanitizeDouble(score * weight)
    fun getAdjustedScore(): Double = score * weight
    fun getUnormalizedAdjusted(): Double = unnormalizedScore * weight
    override fun toString(): String = getAdjustedScore().toString()
}
