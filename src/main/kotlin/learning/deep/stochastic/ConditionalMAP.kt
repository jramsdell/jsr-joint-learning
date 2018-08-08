package learning.deep.stochastic

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


class ConditionalMAP(val models: List<L2RModel>) {
    val nFeatures = models.first().features.columns()

    val root = ConditionalCover(nFeatures - 1)


    val nCandidates = 10
    val bestMap = (0 until nCandidates).map { -999999.9 }.toArrayList()
    var iterations = 0
    var mapCounter = 0



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

    fun getBalls() = root.draw().generateBalls()
    fun newGeneration(nChildren: Int) = root.newGeneration(nChildren)

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
        (0 until 120).forEach {
            val balls=  getBalls()
            val weights = balls.map { it.getParam() }.normalize()
            val map =    getMAP(weights.toNDArray())

            val bestAv = bestMap.average()
            val margin = bestMap.map { (it - bestAv).let { it.pow(2.0) * it.sign }}.average()!!
            val mapMargin = (map - bestAv).let { it.pow(2.0) * it.sign }

            if (mapMargin > margin || mapCounter < nCandidates    ) {
                if (mapCounter >= nCandidates) {
                    var rewardAmount = 4.0
//                    }
                    balls.zip(weights).forEach { (ball, param) -> ball.reward(rewardAmount, rewardParam = param) }
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
                balls.forEach { it.penalize(0.1) }
            }
        }
    }

    fun search() {
        var curHighest = highest
        var badCounter = 0

        (0 until 300).forEach {
            runStep()
            println("Highest: $highest")

//            if (highest == curHighest) {
//                newGeneration(40)
//            }
            curHighest = highest
        }

    }
}


fun runStochasticConditional() {
    val models = RanklibReader("ony_paragraph.txt")
        .createVectors2()
        .filter { it.relevances.any { it.value > 0.0 } }

    val calculator = ConditionalMAP(models)
    calculator.search()
}


fun main(args: Array<String>) {
    runStochasticConditional()

}
