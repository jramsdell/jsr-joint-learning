package learning.optimization

import org.apache.commons.math3.distribution.NormalDistribution

//import utils.
import utils.*
import utils.misc.toArrayList
import utils.misc.toHashMap
import utils.parallel.pmap
import utils.stats.cosine
import utils.stats.normalize
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sign

private data class StepResult(
        val feature: Int,
        val step: Double,
        val gradient: Double
)

private fun List<Double>.transform() = this

private fun perturbWeights(weights: List<Double>): List<Double>  {
    val normalDistribution = NormalDistribution(0.0, 0.1)
    val perturbed = weights.map { (it * normalDistribution.sample()) * 0.5 + it * 0.5 }
    return perturbed
}



class SimpleDescent(val nFeatures: Int, val scoreFun: (List<Double>) -> Double, val onlyPos: Boolean = false, val useDist: Boolean = true, val endFun: ((List<Double>) -> Unit)? = null, val winnow: Boolean = true) {
//    var weights = (0 until nFeatures).map { -Math.random() }.normalize()
    var debug: Boolean = true
    var weights = (0 until nFeatures).map { 1.0 }.transform()
    val converged = AtomicBoolean(false)
    private val priorities = PriorityQueue<StepResult>(kotlin.Comparator { t1, t2 -> -compareValues(t1.gradient, t2.gradient)  })
    var lastStep = (0 until nFeatures).map { it to 1.0 }.toHashMap()
    var curScore = 0.0
    var curStep = 0
//    var nIterations = 1.0

    fun moveCloser(index: Int): Double {
        val w = weights.toArrayList()
        val base = scoreFun(w.toList())
        val step = 0.01
        w[index] += step
        val v1 = scoreFun(w.toList())
        val d1 = (base - v1) / step

        return if (d1 == 0.0) weights[index] else (if (d1 < 0.0) weights[index] / (d1) else weights[index] / -d1 )
    }

    fun getPartialGradient(feature: Int, base: Double): Pair<Double, Double> {
//        val curVal = weights[index]
//        val steps = listOf(-0.00001, -0.0001, -0.001, -0.01, 0.0, -0.05, -0.25, 0.001, 0.0001, 0.00001, 0.01, 0.05, 0.25)
//        val steps = listOf(-0.0001, -0.001, -0.01, 0.0, -0.05, -0.25, 0.001, 0.0001, 0.01, 0.05, 0.25)
//        val steps = listOf(-0.0001, -0.001, -0.01, 0.0, -0.05, 0.001, 0.0001, 0.01, 0.05)
//        val steps = listOf(-0.0001, -0.001, -0.01, 0.0, -0.05, 0.001, 0.0001, 0.01, 0.05, -weights[feature], -(weights[feature] * 2), weights[feature] * 0.5, -weights[feature] * 0.5)
//        val steps = listOf(-0.0001, -0.001, -0.01, 0.0, -0.05, 0.001, 0.0001, 0.01, 0.05, -weights[feature], -(weights[feature] * 2), weights[feature] * 0.5, -weights[feature] * 0.5)
//        val steps = listOf(-0.0001, -0.001, -0.01, -0.05, 0.001, 0.0001, 0.01, 0.05, -weights[feature], -(weights[feature] * 2))
//        val steps = listOf(-0.001, -0.01, -0.05, 0.001, 0.01, 0.05, -weights[feature], -(weights[feature] * 2))
//        val steps = listOf(-0.01, -0.05, 0.01, 0.05, 0.15, -0.15, -(weights[feature] * 2))
        val steps = listOf(0.15, -0.15, -(weights[feature] * 2))
//        val steps = listOf(-0.001, -0.01, -0.05, 0.001, 0.01, 0.05, -weights[feature], -(weights[feature] * 2))
//        val steps = listOf(-0.001, -0.01, -0.05, 0.001, 0.01, 0.05, 0.0, -0.2, 0.2, weights[feature] * 1.1, weights[feature] * 0.9)
//        val steps = listOf(-0.001, -0.01, -0.05, 0.001, 0.01, 0.05, 0.0, -0.2, 0.2, weights[feature] * 1.1, weights[feature] * 0.9, -weights[feature] * 2)
//        val steps = listOf(weights[feature] * 0.6, weights[feature] * 0.1, weights[feature] * 1.1, weights[feature] * 1.4, 0.0, 0.01, -0.01)
//        val steps = listOf(-0.001, -0.01, -0.05, 0.001, 0.01, 0.05, -(weights[feature] * 2))
//        val steps = listOf(-0.001, -0.01, -0.05, 0.001, 0.01, 0.05, -(weights[feature] * 2))
//        val steps = listOf(-0.001, -0.01, -0.05, 0.001, 0.01, 0.05, -weights[feature], -(weights[feature] * 2))
//        val steps = listOf(-0.0001, -0.001, -0.01, 0.0, -0.05, 0.001, 0.0001, 0.01, 0.05, -(weights[feature] * 2))
//        val steps = listOf(-0.0001, -0.001, -0.01, 0.0, -0.05, -0.25, 0.001, 0.0001, 0.01, 0.05, 0.25)
//        val steps = listOf(-0.001, -0.01, 0.0, -0.05, -0.25, 0.001,0.01, 0.05, 0.25)
//            .filter { it.absoluteValue <= lastStep[feature]!!.absoluteValue }

        // change to pmap
        return steps.map { step ->
            val nWeights = weights.mapIndexed { fIndex, value -> if(feature == fIndex) value + step else value    }.transform()
//            step to (scoreFun(nWeights) - base).run { if(step == -weights[feature]) this * ((weights.count { it == 0.0 } + 1.0) / (weights.size.toDouble() + 1.0))   else this } }
        step to (scoreFun(nWeights) - base).run { if(step == -weights[feature]) this * 1.0    else this } }
            .maxBy { it.second }!!
    }

    fun initialize() {
        val base = scoreFun(weights)
        priorities.clear()
        (0 until nFeatures)
            .forEach { feature ->
//                if (weights[feature] != 0.0) {
                    val result = getPartialGradient(feature, base)
                    val stepResult = StepResult(feature, result.first, result.second)
                    priorities.add(stepResult)
//                }
            }
    }

    fun doStep2() {
        curScore = scoreFun(weights)
        val next = priorities.poll()
        if (weights[next.feature] == 0.0) {
            return
        }
        curStep = next.feature

        val result = getPartialGradient(next.feature, curScore)

        lastStep[next.feature] = result.first
        val newResult = StepResult(feature = next.feature, step = result.first, gradient = result.second)
        priorities.add(newResult)

        if (result.second < 0.0)
            return
        weights = weights.mapIndexed { index, value -> if (index == next.feature) value + result.first else value  }
            .transform()

    }


    fun doStep3() {
        curScore = scoreFun(weights)

        val result = (0 until nFeatures).map { it to getPartialGradient(it, curScore) }
            .maxBy { it.second.second }!!


        weights = weights.mapIndexed { index, value -> if (index == result.first) value + result.second.first else value  }
            .transform()

    }

    fun doStep4() {
        weights = (0 until nFeatures).map {moveCloser(it) }
        curScore = scoreFun(weights)

    }




    fun search(iterations: Int = 600, weightUser: ((List<Double>) -> Unit)? = null): List<Double> {
        initialize()
        (0 until iterations)
            .forEach {
                if (!converged.get() && priorities.isNotEmpty()) {

                    doStep3()
                    endFun?.invoke(weights)
                    if ((it % 100 == 99 || winnow == false) && debug) {
                        weightUser?.invoke(weights)
                        val count = weights.count { it != 0.0 }
                        println("$count : $curScore")
                    }
                }
            }

        return weights
    }
}


