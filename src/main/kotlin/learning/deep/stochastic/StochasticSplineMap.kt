package learning.deep.stochastic

import kotlinx.coroutines.experimental.channels.actor
import learning.L2RModel
import lucene.RanklibReader
import org.apache.commons.math3.distribution.NormalDistribution
import org.nd4j.linalg.api.ndarray.INDArray
import utils.misc.printTime
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


class StochasticSplineMAP(val models: List<L2RModel>) {
    val nFeatures = models.first().features.columns()

    val actors = (0 until 5).map {
        (0 until nFeatures).map { NormalCover().apply {
            val nBalls = (0 until 40).map { Ball(location = ThreadLocalRandom.current().nextDouble(), radius = 0.01) }
                .sortedBy { it.location }
            balls.addAll(nBalls)
            linkBalls()
        } }
    }

    val covers = (0 until 5).map { NormalCover().apply { createBalls() } }

    val nCandidates = 5
    val bestMap = (0 until nCandidates).map { -999999.9 }.toArrayList()
    var iterations = 0
    var mapCounter = 0
    val paramHistory = (0 until nFeatures).map { ArrayList<Double>() }.toArrayList()


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

    fun newGeneration(nChildren: Int)  {
        covers.forEachParallelQ { cover -> cover.newGeneration(children = nChildren) }
        actors.forEachParallelQ { covers -> covers.forEach { cover -> cover.newGeneration(children = nChildren)  } }

    }

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
//            val weights = balls.map { it.getParam() }.normalize()
            val actorWeights = balls.mapIndexed { index, ball -> ball.getParam() }.normalize()
            val paramBalls = actors.map { it.map { it.draw() } }
            val paramWeights = paramBalls.map { it.map { it.getParam() } }

            val weights = actorWeights.zip(paramWeights).map { (aWeight, pList)  ->
                pList.map { it * aWeight } }
                .reduce { acc, list -> acc.zip(list).map { (l1, l2) -> l1 + l2 }  }
                .map { it / actors.size }

            val map =    getMAP(weights.toNDArray())

            val bestAv = bestMap.average()
            val margin = bestMap.map { (it - bestAv).let { it.pow(2.0) * it.sign }}.average()!!
            val mapMargin = (map - bestAv).let { it.pow(2.0) * it.sign }

            if (mapMargin > margin || mapCounter < nCandidates    ) {
                if (mapCounter >= nCandidates) {

                    var rewardAmount = 4.0

                    balls.zip(actorWeights).forEach { (ball, param) -> ball.reward(rewardAmount, rewardParam = param) }
                    paramBalls.zip(paramWeights).forEach { (pBalls, pWeights) ->
                        pBalls.zip(pWeights).forEach { (ball, param) -> ball.reward(rewardAmount, rewardParam = param, origin = param) }
                    }
//                    balls.zip(weights).forEach { (ball, param) -> ball.reward(rewardAmount, rewardParam = param) }

                }

                val lowestIndex = bestMap.withIndex().minBy { it.value }!!.index
                bestMap[lowestIndex] = map

                if (highest < map) {
                    println("$map : ${getMAP(weights.toNDArray())}")
                    highest = map
                    println(weights)
                    println()

                }
                mapCounter += 1
            } else {
//                balls.forEach { it.penalize(0.1) }

                balls.forEach { it.penalize(0.1) }
                paramBalls.zip(paramWeights).forEach { (pBalls, pWeights) ->
                    pBalls.zip(pWeights).forEach { (ball, param) -> ball.penalize(0.1, origin = param) }
                }


            }
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


fun runStochasticSpline() {
    val models = RanklibReader("ptest.txt")
        .createVectors2()
        .filter { it.relevances.any { it.value > 0.0 } }

    val calculator = StochasticSplineMAP(models)
    calculator.search()
}


