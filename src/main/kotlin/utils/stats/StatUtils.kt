package utils.stats

import utils.misc.identity
import java.util.*

fun <A, B: Number>Map<A, B>.normalize(): Map<A, Double> {
    val total = values.sumByDouble { it.toDouble() }
    return mapValues { (_, value) -> (value.toDouble() / total).defaultWhenNotFinite(0.0) }
}

fun Iterable<Double>.normalize(): List<Double> {
    val items = toList()
    val total = items.sum()
    if (total == 0.0) return items.map { value -> 0.0 }
    return items.map { value -> value / total }
}

fun <A, B: Number>Map<A, B>.normalizeZscore(): Map<A, Double> {
    val mean = values.map { it.toDouble() }.average()
    val std = Math.sqrt(values.sumByDouble { Math.pow(it.toDouble() - mean, 2.0) })
    return mapValues { (_, value) -> (value.toDouble() - mean) / std }
}

fun <A, B: Number>Map<A, B>.normalizeRanked(): Map<A, Double> {
    val items = values.size.toDouble()
    return map { it.key to it.value.toDouble() }
        .sortedByDescending { it.second }
        .mapIndexed { index, (k,_) -> k to (items - index)/items   }
        .toMap()
}

fun <A, B: Number>Map<A, B>.normalizeMinMax(): Map<A, Double> {
    val vMax = values.maxBy { it.toDouble() }!!.toDouble()
    val vMin = values.minBy { it.toDouble() }!!.toDouble()
    return mapValues { (it.value.toDouble() - vMin) / (vMax - vMin) }
}

private fun normZscore(values: List<Double>): List<Double> {
    val mean = values.average()
    val std = Math.sqrt(values.sumByDouble { Math.pow(it - mean, 2.0) })
    return values.map { ((it - mean) / std) }
}

fun<A> Iterable<A>.countDuplicates(): Map<A, Int> =
        groupingBy(::identity)
            .eachCount()

fun<A, B: Comparable<B>> Map<A, B>.takeMostFrequent(n: Int): Map<A, B> =
        entries
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key to it.value }
            .toMap()



fun Double.defaultWhenNotFinite(default: Double = 0.0): Double = if (!isFinite()) default else this
// Convenience function (turns NaN and infinite values into 0.0)
fun sanitizeDouble(d: Double): Double { return if (d.isInfinite() || d.isNaN()) 0.0 else d }

