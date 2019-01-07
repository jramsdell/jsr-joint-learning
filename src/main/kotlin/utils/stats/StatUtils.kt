package utils.stats

import utils.misc.identity
import utils.nd4j.softMax
import utils.nd4j.toNDArray
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.absoluteValue
import kotlin.math.pow

fun<K> HashMap<K, Double>.putMin(key: K, v: Double) {
    put(key, if (!containsKey(key)) v else Math.min(get(key)!!, v))
}

fun<K> HashMap<K, Double>.putMax(key: K, v: Double) {
    put(key, if (!containsKey(key)) v else Math.max(get(key)!!, v))
}

fun <A, B: Number>Map<A, B>.normalize(): Map<A, Double> {
    val total = values.sumByDouble { it.toDouble() }
    return mapValues { (_, value) -> (value.toDouble() / total).defaultWhenNotFinite(0.0) }
}

fun <A, B: Number>Map<A, B>.inverseNormalize(): Map<A, Double> {
    val total = values.sumByDouble { it.toDouble() }
    return mapValues { (_, value) -> (total / value.toDouble()).defaultWhenNotFinite(0.0) }.normalize()
}

fun Iterable<Double>.normalize(): List<Double> {
    val items = toList()
    val total = items.sum()
    if (total == 0.0) return items.map { value -> 0.0 }
    return items.map { value -> value / total }
}

fun Iterable<Double>.normalize2(): List<Double> {
    val items = toList()
    val total = items.map { it.absoluteValue }.sum()
    if (total == 0.0) return items.map { value -> 0.0 }
    return items.map { value -> value / total }
}

fun <A, B: Number>Map<A, B>.normalizeZscore(): Map<A, Double> {
    val mean = values.map { it.toDouble() }.average()
    val std = Math.sqrt(values.sumByDouble { Math.pow(it.toDouble() - mean, 2.0) })
    return mapValues { (_, value) -> (value.toDouble() - mean) / std }
}

fun <A, B: Number>Map<A, B>.cosine(): Map<A, Double> {
    val total = values.sumByDouble { it.toDouble().pow(2.0) }
    return mapValues { (_, value) -> (value.toDouble() / total).defaultWhenNotFinite(0.0) }
}

fun List<Double>.cosine(): List<Double> {
    val s1 = this.sumByDouble { it.pow(2.0) }.pow(0.5)
    return this.map { it / s1 }
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

fun ArrayList<Double>.normZscore() {
    val mean = average()
    val std = Math.sqrt(sumByDouble { Math.pow(it - mean, 2.0) })
    forEachIndexed { index, d ->  this[index] = (d - mean) / std}
}

fun<A> Iterable<A>.countDuplicates(): Map<A, Int> =
        groupingBy(::identity)
            .eachCount()

fun Iterable<Double>.sd(): Double {
    val mean = average()
    return Math.sqrt(map { (it - mean).pow(2.0) }.average())
}

fun<A, B: Comparable<B>> Map<A, B>.takeMostFrequent(n: Int): Map<A, B> =
        entries
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key to it.value }
            .toMap()


fun<A> Map<A, Double>.weightedPick(): A {
    var total = 0.0
    val cumSum = map { total += it.value; it.key to total }
    val rValue = ThreadLocalRandom.current().nextDouble(0.0, total)
    return cumSum.find { it.second >= rValue }!!.first
}

fun<A> Map<A, Double>.weightedPicks(nTimes: Int): ArrayList<A> {
    var total = 0.0
    val cumSum = map { total += it.value; it.key to total }
    var picks = (0 until nTimes).map { ThreadLocalRandom.current().nextDouble(0.0, total) }.sorted()
    val draws = ArrayList<A>()
    cumSum.forEach { (a, prob) ->
        if (picks.isEmpty()) {
            return draws
        }
        val lastIndex = picks.withIndex().findLast { pickValue -> prob >= pickValue.value }?.index ?: -1

        if (lastIndex > -1) {
            (0 until lastIndex + 1).forEach { draws.add(a) }
            picks = picks.dropLast(lastIndex + 1)
        }


    }
    return draws
}


fun Double.defaultWhenNotFinite(default: Double = 0.0): Double = if (!isFinite()) default else this
// Convenience function (turns NaN and infinite values into 0.0)
fun sanitizeDouble(d: Double): Double { return if (d.isInfinite() || d.isNaN()) 0.0 else d }

