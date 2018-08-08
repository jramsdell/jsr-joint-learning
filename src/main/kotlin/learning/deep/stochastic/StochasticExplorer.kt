package learning.deep.stochastic

import org.apache.commons.math3.distribution.BetaDistribution
import org.apache.commons.math3.distribution.NormalDistribution
import utils.misc.sharedRand
import utils.stats.normalize
import utils.stats.normalize2
import utils.stats.sd
import utils.stats.weightedPick
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sign


var candidates = ArrayList<Double>().apply { addAll((0 until 10).map { 999.0 }) }
var counter = 0
fun runRewards(cover: NormalCover, nRewards: Int, rewardMult: Double = 1.0) {
    (0 until nRewards).map {
        val ball = cover.draw()
        val nearTarget = (ball.location - 0.5).absoluteValue
        if (candidates.isEmpty()) {
            candidates.add(nearTarget)
            ball.reward(1.0)
//        } else if (ThreadLocalRandom.current().nextDouble() >= (nearTarget / candidates.average()).pow(1.0)) {
        } else if (sharedRand.nextDouble() >= (nearTarget / candidates.average()).pow(1.0)) {
            if (candidates.size >= 10) {
                candidates[counter % 10] = nearTarget
            } else {
                candidates.add(nearTarget)
            }
            counter += 1
            ball.reward(1.0)
        } else {
            ball.penalize(1.0)
        }

        ball.getParam()
    }.average().run { println("Average draw: $this") }

}

//fun NormalDistribution.getInvDist(point: Double) =
//        if (point > mean) 0.5 - probability(mean, point)
//        else 0.5 - probability(point, mean)

fun NormalDistribution.getInvDist(point: Double): Double {
    val dist = (point - mean).absoluteValue
    val p1 = probability(mean - dist, mean + dist)
//    val p2 = probability(mean - dist, mean)
    return 1.0 - p1
}

fun probOfGen() {
    val mean = 0.5
    val std = 0.05
    val point = 0.4
    val myDist = NormalDistribution(mean, std)

    println(myDist.getInvDist(0.55))



}

fun closedForm(x: Pair<Double, Double>, y: Pair<Double, Double>, n: Double): Double {
    x.let { (x1, x2) ->
        y.let { (y1, y2) ->
            val d = x1 * y1
            val c = x2 * y1 + y2

            return (c/(d - 1.0)) * (d.pow(n) - 1.0)
        }
    }

}

fun getCovariances(a1: List<Double>, a2: List<Double>): Double {
    val a1Mean = a1.average()
    val a2Mean = a2.average()

    val a1Sd = a1.sd()
    val a2Sd = a2.sd()

    val total = a1.zip(a2).map { (it.first - a1Mean) * (it.second - a2Mean) }.sum()
    return total / (a1Sd * a2Sd)
}


fun getCommutator(n: Double): Pair<Double, Double> {
    return n to n - 1.0
}

class MyAnalyzer() {
    val commutators = ArrayList<Pair<Double, Double>>()

    fun addCommutator(n: Double): MyAnalyzer {
        commutators.add(getCommutator(n))
        return this
    }

    fun composeCommutators(): Pair<Double, Double> {
        var base = commutators[0]
        commutators.drop(1).forEach { (b1, b2) ->
            base = base.let { (a1, a2) -> a1 * b1 to a2 * b1 + b2 }
        }
        return base
    }

    fun getFormula(commutator: Pair<Double, Double>): (Double) -> Pair<Double, Double> {
        val formula = { n: Double ->
            commutator.let { (d, c) ->
                d.pow(n) to c/(d - 1.0) * (d.pow(n) - 1.0)
            }
        }

        return formula
    }

    fun getCosSin(result: Double): Pair<Double, Double> {
        return Math.cos(result) to Math.sin(result)
    }

    fun analyzeResults() {
        val commutator = composeCommutators()
        val formula = getFormula(commutator)
        var step = 1.0
        var totals = ArrayList<List<Double>>()
        (0 until 10).forEach {
            val cur = formula(step)

//            val result = commutators.map { (d, c) -> ((cur.second * c) % d)  }
//            val result = commutators.map { (d, c) -> ((cur.second * c) % d) / d  }
//            val result = commutators.map { (d, c) -> ((((cur.second * c)  % d ) ) )   }
            val result = commutators.map { (d, c) ->
                getCosSin((cur.second * c)  / d ).first + 1.0
                    }.normalize()
            totals.add(result)
//            val result = commutators.map { (d, c) -> ((cur.second / cur.first - c ) % d) / d  }
//            val sumTotal = result.sum()
//            val resultString = result.map { it.first.toString().take(6) to it.second.toString().take(6) }.joinToString(", ")
//            val resultString = result.joinToString(", ")
            val resultString = result.map { it.toString().take(6) }.joinToString(", ")
//            println(resultString + " : $step ")
            step += 0.000000001
        }

        totals.reduce { acc, list -> acc.zip(list).map { (v1, v2) -> v1 + v2 }  }
            .map { it / totals.size }
            .apply { println(this) }

    }

}


fun main(args: Array<String>) {

    val others = (100 until 110).map { it / 1000.0 }
    others.forEach {
        val analyzer = MyAnalyzer()
        analyzer
            .addCommutator(sharedRand.nextDouble() * it)
            .addCommutator(sharedRand.nextDouble() * it)
            .addCommutator(sharedRand.nextDouble() * it )
            .addCommutator(sharedRand.nextDouble() * it )
            .analyzeResults()

    }


//    val x = getCommutator(2.0)
//    val y = getCommutator(8.0)
////    val z = getCommutator(8.0)
////    val a = y.let { (y1, y2) -> z.let { (z1, z2) -> z1 * y1 to y2 * z1 + z2 }}
//    var cur = 1.0
//    val results = (0 until 11).map {
//        val result = closedForm(x, y, cur)
////        val rem1 = ((result - x.second) % x.first) / x.first
////        val rem2 = ((result - y.second) % y.first) / y.first
//        val rem1 = ((result ) % x.first) / x.first
//        val rem2 = ((result) % y.first) / y.first
////        val rem1 = (((result - x.second) / x.first) % x.first) / (x.first - 0.5)
////        val rem2 = (((result - y.second) / y.first) % y.first) / (y.first - 0.5)
////        val f1 = Math.sin(rem1) to Math.cos(rem1)
////        val f2 = Math.sin(rem2) to Math.cos(rem2)
////        val f1 = Math.atan(rem1)
////        val f2 = Math.atan(rem2)
//        val f1 = rem1
//        val f2 = rem2
//        println("($f1, $f2) : $result : $cur")
////        val dX = (result % 2) + result.rem(2)
////        val dY = (result % 3) + result.rem(3)
////        println("($dX, $dY) : $cur ")
//        cur += 0.001
//    }
//
////    println(getCovariances(results.first, results.second))



}