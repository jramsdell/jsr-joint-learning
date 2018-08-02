package learning.deep.stochastic

import org.apache.commons.math3.distribution.BetaDistribution
import org.apache.commons.math3.distribution.NormalDistribution
import utils.misc.sharedRand
import utils.stats.weightedPick
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sign


var candidates = ArrayList<Double>().apply { addAll((0 until 10).map { 999.0 }) }
var counter = 0
fun runRewards(cover: Cover, nRewards: Int, rewardMult: Double = 1.0) {
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

fun main(args: Array<String>) {
    probOfGen()
//    val myDist = NormalDistribution(0.5, 0.05)
//    val results = myDist.sample(100)
//    val av = results.average()
////    val std = Math.sqrt(results.map { (it - av).pow(2.0) }.sum() / 100.0)
////    val std = Math.sqrt(results.map { (it - av).let { it.pow(2.0).times(it.sign)  } }.sum() / 100.0)
//
//    val inter = results.map { (it - av).let { it.pow(2.0).times(it.sign)  } }.sum() / 100.0
//    val std = Math.sqrt(inter.absoluteValue)
//
//    val averageDeviation = results.map { (it - av).let { it.pow(2.0) * it.sign } }.average()
////    val nm = results[5]
////    println(std)
////    println(averageDeviation)
//
//    val myDist2 = NormalDistribution(averageDeviation, std)
//    println(std)
//    println(myDist2.sample(20).toList())
////    println(myDist2.probability(0.01, 0.02))
//    results.forEach {  v ->
//        val diff = (v - av)
////        val diff = (v - av)
//        val chanceOfGenerating = myDist2.probability(diff - std, diff + std)
//        println("$v : $diff : $chanceOfGenerating")
//    }

}