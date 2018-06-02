package utils.stats

import utils.misc.identity

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