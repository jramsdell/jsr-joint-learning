package learning.deep.stochastic

import learning.L2RModel
import lucene.RanklibReader
import org.apache.commons.math3.distribution.NormalDistribution
import org.nd4j.linalg.api.ndarray.INDArray
import utils.misc.toArrayList
import utils.nd4j.*
import utils.parallel.forEachParallelQ

import utils.parallel.pmap
import utils.stats.defaultWhenNotFinite
import utils.stats.normalize
import utils.stats.sd
import utils.stats.weightedPick
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sign
import kotlin.system.measureTimeMillis


class StochasticMAP(val models: List<L2RModel>) {
    val nFeatures = models.first().features.columns()
    val covers = (0 until nFeatures).map { NormalCover().apply { createBalls() } }

    val nCandidates = 10
    val bestMap = (0 until nCandidates).map { -999999.9 }.toArrayList()
    var iterations = 0
    var mapCounter = 0
    val paramHistory = (0 until nFeatures).map { ArrayList<Double>() }.toArrayList()
    val pHist = ArrayList<List<Double>>()


    fun getAP(model: L2RModel, weights: INDArray): Double {
        val results =  (model.features.dup() mulRowV weights.dup()).sum(1).toDoubleVector()
        var apScore = 0.0
        var totalRight = 0.0
        val sortedResults = results
            .asSequence()
            .mapIndexed { index, score -> index to score }
            .sortedByDescending { it.second }
            .toList()

        sortedResults.forEachIndexed { rank, (docIndex, _) ->
            val rel = model.relevances[docIndex]!!
            totalRight += rel
            if (rel > 0.0) {
                apScore += totalRight / (rank + 1).toDouble()
            }
        }
        return apScore / Math.max(1.0, totalRight)
    }


    fun getMAP(weights: INDArray) = models.pmap { model -> getAP(model, weights) }.average()

//    fun getBalls(): List<Ball> {
//        var curLoc = 0.0
//        val balls = ArrayList<Ball>()
//        val firstBall = covers.first().draw()
//        balls.add(firstBall)
//        curLoc = firstBall.location
//        covers.drop(1).forEach { cover ->
//            val nextBall = cover.draw(curLoc)
//            balls.add(nextBall)
//            curLoc = nextBall.location
//        }
//
//        return balls.toList()
//    }

    fun getBalls() = covers.pmap { it.draw() }

    fun newGeneration(nChildren: Int) = covers.forEachParallelQ { cover -> cover.newGeneration(children = nChildren) }

    var highest = -99999.0

    fun getDiff(model: L2RModel, weights: INDArray): Double {
        val rMap = model.relevances.entries.groupBy { it.value > 0.0 }
        val results = (model.features mulRowV weights).sum(1).toDoubleVector()
        val offset = 0.000000001
        return (0 until 100).map {
            val randBest = rMap[true]!!.map { it.key to (1 / results[it.key]).defaultWhenNotFinite(0.0) + offset }.toMap().weightedPick()
            val randWorst = rMap[false]!!.map { it.key to results[it.key] + offset }.toMap().weightedPick()
            val best = model.features.getRow(randBest)
            val worst = model.features.getRow(randWorst)
            best.sub(worst).sumNumber().toDouble()
        }.average()
    }

    fun getTotalDiff(weights: INDArray): Double = models.pmap { getDiff(it, weights) }.average()


    fun runStep() {
        (0 until 80).forEach {
            val balls=  getBalls()
            val weights = balls.mapIndexed { index, ball ->
                ball.getParam()
            }.normalize()
                .run {
                    if (mapCounter >= nCandidates + 10) {
                        zip(pHist[ThreadLocalRandom.current().nextInt(pHist.size)]).map {
                            (p1, p2) -> (p1 + p2) / 2.0 } }
                    else
                        this
                }
            val map =    getMAP(weights.toNDArray())

            val bestAv = bestMap.average()
            val margin = bestMap.map { (it - bestAv).let { it.pow(2.0) * it.sign }}.average()!!
            val mapMargin = (map - bestAv).let { it.pow(2.0) * it.sign }

            var chanceOfGenerating = 0.0

            if (mapCounter >= 15 + nCandidates) {
                val std = Math.sqrt(bestMap.map { (it - bestAv).let { it.pow(2.0) * it.sign  } }.sum().absoluteValue / 5.0)
//                val std = Math.sqrt(bestMap.map { (it - bestAv).let { it.pow(2.0)  } }.sum().absoluteValue / 5.0)
                if (std > 0.0) {
//                    val averageDeviation = bestMap.map { (it - bestAv).let { it.pow(2.0) * it.sign } }.average()
//                    val averageDeviation = bestMap.map { (it - bestAv).let { it.pow(2.0) * it.sign } }.average()
                    val myDist2 = NormalDistribution(margin, std)
//                    val mapDiff = map - bestAv
//                    chanceOfGenerating = myDist2.probability(mapDiff - std, mapDiff + std)
                    chanceOfGenerating = myDist2.getInvDist(map)
                }

            }

            if (mapMargin > margin)
                chanceOfGenerating = 1.0 - chanceOfGenerating
            else chanceOfGenerating = 0.0

            if (mapCounter < nCandidates || ThreadLocalRandom.current().nextDouble() <= chanceOfGenerating   ) {
//            if (mapMargin > margin || mapCounter < nCandidates || ThreadLocalRandom.current().nextDouble() <= chanceOfGenerating   ) {
//            if (mapMargin > margin || mapCounter < nCandidates    ) {
//            if (ThreadLocalRandom.current().nextDouble() <= ((map * 1.2) / bestMap.average()).defaultWhenNotFinite(1.0)) {
                if (mapCounter >= nCandidates) {
                    var rewardAmount = 4.0
//                    if (mapCounter >= 20) {
//                        rewardAmount += 4.0 * (1.0 - chanceOfGenerating)
//                    }
                    balls.zip(weights).forEach { (ball, param) -> ball.reward(rewardAmount, rewardParam = param) }
                }
//                    paramHistory.zip(weights).forEach { (v1, v2) -> v1.add(v2) }

//                balls.forEachIndexed {index, _ -> covers[index].rewardSpawn() }
//                val lowestIndex = bestMap.withIndex().minBy { (it.value - bestAv).let { it.pow(2.0).times(it.sign) } }!!.index
//                    val lowestIndex = bestMap.withIndex().minBy { (it.value - bestAv).let { 1.0 / it.pow(2.0).times(it.sign) } }!!.index
                val lowestIndex = bestMap.withIndex().minBy { it.value }!!.index
                bestMap[lowestIndex] = map

                if (highest < map) {
                    println("$map : ${getMAP(weights.toNDArray())}")
//                    println(balls.map { it.shift })
                    pHist.add(weights)
                    highest = map
                    println(weights)
                    println()

                }
                mapCounter += 1
            } else {
                balls.forEach { it.penalize(0.1) }

//                if (it == 70) {
//                    balls
//                        .asSequence()
////                        .map { it.beta.alpha to it.beta.beta }
//                        .map { it.location to it.radius }
//                        .map { (alpha, beta) -> "($alpha, $beta)" }
//                        .joinToString(", ")
//                        .apply { println(this) }
//
//                }
            }
        }
    }

    fun cov(v1: Int, v2: Int) {
    }

    fun getCovariances() {

        val means = paramHistory.map { it.average() }
        val sds = paramHistory.map { it.sd() }

        val expectations = paramHistory.map { it.sum() }
        paramHistory.withIndex().flatMap { index ->
            val index1 = index.index
            val v1 = index.value

            paramHistory.drop(index1 + 1).mapIndexed { offset, v2 ->
                val index2 = index1 + offset + 1
                val total = v1.zip(v2).sumByDouble { (it.first - means[index1]) * (it.second - means[index2]) }
                val result = total / (sds[index1] * sds[index2])
                Triple(index1, index2, result)
            }
        }.sortedByDescending { it.third }
            .forEach {
                println("(${it.first}, ${it.second}) : ${it.third} ")
            }

    }

    fun search() {
        var curHighest = highest
        var badCounter = 0

        (0 until 200).forEach {
            runStep()
            println("Highest: $highest")

            if (highest == curHighest) {
                badCounter += 1
                newGeneration(40)
            }
            curHighest = highest
        }
//        getCovariances()



    }
}


fun runStochastic() {
    val models = RanklibReader("ptest.txt")
        .createVectors2()
        .filter { it.relevances.any { it.value > 0.0 } }

    val calculator = StochasticMAP(models)
    calculator.search()
}


fun main(args: Array<String>) {
    runStochastic()

}
