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


class StochasticMAP(val models: List<L2RModel>) {
    val nFeatures = models.first().features.columns()

    val originWeights = listOf(
//            0.12165789853972875 ,0.08944102588504195 ,0.5729872942806236 ,0.0011804417667321465 ,0.055703286685797986 ,-0.059791612511680386 ,1.480083780035103E-4 ,-2.0488257986124757E-4 ,8.92583566527143E-4 ,0.0013857359870333894 ,0.001111359813115724 ,0.0750116970983432 ,-0.0035072148507435765 ,0.016084374490240208 ,8.92583566527143E-4
            0.20626967500977578 ,0.06789250355612644 ,0.5884787395138055 ,-0.009351554319159235 ,0.0046123869508436265 ,0.0018502051736588455 ,0.0015348493766956565 ,0.0017567442300209294 ,0.004862895301289406 ,-0.020439537444889196 ,9.08557585154395E-4 ,0.07020298234050766 ,-0.012113578595494438 ,0.004862895301289406 ,0.004862895301289406



    ).toNDArray()
    val covers = (0 until nFeatures).map { Cover() }
//    val bestMap = (0 until 20).map { -999999.9 }.toArrayList()
    val nCandidates = 10
    val bestMap = (0 until nCandidates).map { -999999.9 }.toArrayList()
    var iterations = 0
//    var mapCounter = AtomicInteger(0)
    var mapCounter = 0
    var prevDiff = 0.0
    var gradientTotal = 0.0
    var bestLearn = 0.0



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



    data class StochasticExperiment(val weights: List<Double>) {
        val lock = ReentrantLock()
        val weightINDArray = weights.toNDArray()
        val results = ArrayList<Double>()
    }

    data class ExperimentStructure(val experiments: List<StochasticExperiment>)


    fun getMAP(weights: INDArray) = models.pmap { model -> getAP(model, weights) }.average()

    fun getBalls(isInverse: Boolean = false) = if (!isInverse) covers.pmap { it.draw() } else covers.pmap { it.drawInverse() }
    fun newGeneration(nChildren: Int) = covers.forEachParallelQ { cover -> cover.newGeneration(children = nChildren) }

//    val bestWeights = ArrayList<List<Double>>()
    var highest = -99999.0

    fun getDiff(model: L2RModel, weights: INDArray): Double {
        val rMap = model.relevances.entries.groupBy { it.value > 0.0 }
        val results = (model.features mulRowV weights).sum(1).toDoubleVector()
//        val averageTrue = rMap[true]?.let { it.map { hi -> results[hi.key] }.sorted().take(1).sum() } ?: 0.0
//        val averageFalse = rMap[false]?.let { it.map { hi -> results[hi.key] }.sortedDescending().take(1).sum() } ?: 0.0
//        return averageTrue - averageFalse

        val offset = 0.000000001
        return (0 until 100).map {
            val randBest = rMap[true]!!.map { it.key to (1 / results[it.key]).defaultWhenNotFinite(0.0) + offset }.toMap().weightedPick()
            val randWorst = rMap[false]!!.map { it.key to results[it.key] + offset }.toMap().weightedPick()
            val best = model.features.getRow(randBest)
            val worst = model.features.getRow(randWorst)
            best.sub(worst).sumNumber().toDouble()
        }.average()


//        val bestScore = model.relFeatures.mulRowVector(weights).sum(1)
//        val worstScore = model.nonRelFeatures.mulRowVector(weights).sum(1)
//        val best = model.relFeatures.transpose().mmul(bestScore).transpose()
//        val worst = model.nonRelFeatures.transpose().mmul(worstScore).transpose()
//        return best.sub(worst).sumNumber().toDouble()
    }

    fun getTotalDiff(weights: INDArray): Double = models.pmap { getDiff(it, weights) }.average()
    var rewarded = false



    fun getBestParams(balls: List<Ball>): Triple<Double, List<Double>, List<Double>> {
        var bestResult = Triple(0.0, emptyList<Double>(), emptyList<Double>())
        (0 until 3).forEach {
            val params = balls.map { it.getParam() }
            val weights = params.normalize()
            val map =   getMAP(weights.toNDArray())
                if (bestResult.first < map) {
                    bestResult = Triple(map, weights, params)
                }

        }
        return bestResult
    }

    fun runStep() {
        (0 until 120).forEach {
            val balls=  getBalls()
            val weights = balls.map { it.getParam() }.normalize()
            val map =    getMAP(weights.toNDArray())

            val bestAv = bestMap.average()
            val margin = bestMap.map { (it - bestAv).let { it.pow(2.0) * it.sign }}.average()!!
            val mapMargin = (map - bestAv).let { it.pow(2.0) * it.sign }

            var chanceOfGenerating = 0.0

//            if (mapCounter >= 15 + nCandidates) {
////                val std = Math.sqrt(bestMap.map { (it - bestAv).let { it.pow(2.0) * it.sign  } }.sum().absoluteValue / 5.0)
//                val std = Math.sqrt(bestMap.map { (it - bestAv).let { it.pow(2.0)  } }.sum().absoluteValue / 5.0)
//                if (std > 0.0) {
////                    val averageDeviation = bestMap.map { (it - bestAv).let { it.pow(2.0) * it.sign } }.average()
////                    val averageDeviation = bestMap.map { (it - bestAv).let { it.pow(2.0) * it.sign } }.average()
//                    val myDist2 = NormalDistribution(bestAv, std)
////                    val mapDiff = map - bestAv
////                    chanceOfGenerating = myDist2.probability(mapDiff - std, mapDiff + std)
//                    chanceOfGenerating = myDist2.getInvDist(map)
//                }
//            }


//            if (mapMargin > margin || mapCounter < nCandidates || ThreadLocalRandom.current().nextDouble() <= chanceOfGenerating   ) {
//                println("Chance of generating: $chanceOfGenerating")
                if (mapMargin > margin || mapCounter < nCandidates    ) {
//            if (ThreadLocalRandom.current().nextDouble() <= ((map * 1.2) / bestMap.average()).defaultWhenNotFinite(1.0)) {




                if (mapCounter >= nCandidates) {
                    var rewardAmount = 4.0
//                    if (mapCounter >= 20) {
//                        rewardAmount += 4.0 * (1.0 - chanceOfGenerating)
//                    }
                    rewarded = true
//                    balls.zip(params).forEach { (ball, param) -> ball.reward(4.0, rewardParam = param) }
                        balls.zip(weights).forEach { (ball, param) -> ball.reward(rewardAmount, rewardParam = param) }
                }
//                balls.forEachIndexed {index, _ -> covers[index].rewardSpawn() }
//                val lowestIndex = bestMap.withIndex().minBy { (it.value - bestAv).let { it.pow(2.0).times(it.sign) } }!!.index
//                    val lowestIndex = bestMap.withIndex().minBy { (it.value - bestAv).let { 1.0 / it.pow(2.0).times(it.sign) } }!!.index
                    val lowestIndex = bestMap.withIndex().minBy { it.value }!!.index
//                    val lowestIndex = bestMap.withIndex().minBy { it.value }!!.index
//                if (map > bestMap[lowestIndex]) {
//                    bestMap[lowestIndex] = map
//                }
                    bestMap[lowestIndex] = map
//                    bestMap[ThreadLocalRandom.current().nextInt(bestMap.size)] = map

//                bestMap[mapCounter % 20] = map
                if (highest < map) {
                    println("$map : ${getMAP(weights.toNDArray())}")
                    highest = map
                    println(weights)
                    println()
                }
                mapCounter += 1
//                bestWeights.add(weights)
            } else {
                balls.forEach { it.penalize(0.1) }
            }
        }
    }

    fun search() {
        var curHighest = highest
        (0 until 300).forEach {
                runStep()
            println("Highest: $highest")
            if (highest == curHighest) {
//            if (!rewarded) {
                 newGeneration(40)
            }
            rewarded = false
            curHighest = highest
        }

    }




}


fun runStochastic() {
//    NativeOpsHolder.getInstance()
//    org.nd4j.nativeblas.NativeOpsHolder.getInstance()
//    org.nd4j.nativeblas.`ios-x86_64`
//    System.setProperty(NATIVE_OPS, "linux-x86_64")
//    println(org.bytedeco.javacpp.presets.openblas.blas_get_vendor())
//            System.setProperty(NATIVE_OPS, "linux-x86_64")
//    NativeOpsHolder.getInstance().deviceNativeOps.setElementThreshold(16384)
//    NativeOpsHolder.getInstance().deviceNativeOps.setTADThreshold(64)
    val models = RanklibReader("ony_paragraph.txt")
        .createVectors2()
        .filter { it.relevances.any { it.value > 0.0 } }

    val calculator = StochasticMAP(models)
    calculator.search()

}


fun main(args: Array<String>) {
    runStochastic()

}