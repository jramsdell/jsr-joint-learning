package learning

import lucene.RanklibReader
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.api.ops.impl.shape.ZerosLike
import org.nd4j.linalg.cpu.nativecpu.NDArray
import utils.misc.sharedRand
import utils.misc.toArrayList
import org.nd4j.linalg.factory.Nd4j.*
import utils.nd4j.*
import utils.parallel.forEachParallelQ

import utils.parallel.pmap
import utils.stats.normalize
import utils.stats.weightedPick
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.absoluteValue
import kotlin.math.pow


data class L2RModel(val features: INDArray, val relevances: Map<Int, Double>,
                    val relFeatures: INDArray, val nonRelFeatures: INDArray)

class MAPCalculator(val models: List<L2RModel>) {
    val nFeatures = models.first().features.columns()

    val originWeights = listOf(
//            0.12165789853972875 ,0.08944102588504195 ,0.5729872942806236 ,0.0011804417667321465 ,0.055703286685797986 ,-0.059791612511680386 ,1.480083780035103E-4 ,-2.0488257986124757E-4 ,8.92583566527143E-4 ,0.0013857359870333894 ,0.001111359813115724 ,0.0750116970983432 ,-0.0035072148507435765 ,0.016084374490240208 ,8.92583566527143E-4
            0.20626967500977578 ,0.06789250355612644 ,0.5884787395138055 ,-0.009351554319159235 ,0.0046123869508436265 ,0.0018502051736588455 ,0.0015348493766956565 ,0.0017567442300209294 ,0.004862895301289406 ,-0.020439537444889196 ,9.08557585154395E-4 ,0.07020298234050766 ,-0.012113578595494438 ,0.004862895301289406 ,0.004862895301289406



    ).toNDArray()

//    val originWeights = (0 until nFeatures).map { 1.0 }.toNDArray()


    fun getAP(model: L2RModel, weights: INDArray): Double {
        val results = (model.features mulRowV weights).sum(1).toDoubleVector()
        val sortedResults = results
            .asSequence()
            .mapIndexed { index, score -> index to score }
            .sortedByDescending { it.second }
            .toList()

        var apScore = 0.0
        var totalRight = 0.0

        sortedResults.forEachIndexed  { rank, (docIndex, _) ->
            val rel = model.relevances[docIndex]!!
            totalRight += rel
            if (rel > 0.0) {
                apScore += totalRight / (rank + 1).toDouble()
            }

        }

        return apScore / Math.max(1.0, totalRight)
    }

    fun getDiff(model: L2RModel, weights: INDArray): Double {
        val rMap = model.relevances.entries.groupBy { it.value > 0.0 }
        val results = (model.features mulRowV weights).sum(1).toDoubleVector()
        val averageTrue = rMap[true]?.let { it.map { hi -> results[hi.key] }.sorted().take(1).sum() } ?: 0.0
        val averageFalse = rMap[false]?.let { it.map { hi -> results[hi.key] }.sortedDescending().take(1).sum() } ?: 0.0
        return averageTrue - averageFalse
    }

    fun getDiffGradient(model: L2RModel, weights: INDArray): INDArray {
        val rMap = model.relevances.entries.groupBy { it.value > 0.0 }
        val results = (model.features mulRowV weights).sum(1).toDoubleVector()

//        val bestScore = model.relFeatures.mulRowVector(weights).sum(1)
//        val worstScore = model.nonRelFeatures.mulRowVector(weights).sum(1)
//        val best = model.relFeatures.transpose().mmul(bestScore).transpose()
//        val worst = model.nonRelFeatures.transpose().mmul(worstScore).transpose()


//        val randBest = rMap[true]!!.map { it.key to 1 / results[it.key] }.toMap().weightedPick()
//        val randWorst = rMap[false]!!.map { it.key to results[it.key] }.toMap().weightedPick()
//        val best = model.features.getRow(randBest)
//        val worst = model.features.getRow(randWorst)

        val resultContainer = zerosLike(weights)

        (0 until 5).forEach {
            val randBest = rMap[true]!!.map { it.key to 1 / results[it.key] }.toMap().weightedPick()
            val randWorst = rMap[false]!!.map { it.key to results[it.key] }.toMap().weightedPick()
            val best = model.features.getRow(randBest)
            val worst = model.features.getRow(randWorst)
            resultContainer.addi(best.sub(worst))
        }

        return resultContainer.div(5)

//        return randBest.sub(randWorst).sigmoid().mul(weights)!!
//        return best.sub(worst).sigmoid().normalizeRows().mul(weights)!!
//        return best.sub(worst)
//        return best.sub(worst)
    }

    fun getTotalDiffGradient(weights: INDArray): INDArray {

        val base = zerosLike(weights)
        val lock = ReentrantLock()
        models.forEachParallelQ {
            val result = getDiffGradient(it, weights)
            lock.withLock { base.addi(result) }
        }
        return base
    }

    fun getTotalDiff(weights: INDArray): Double = models.map { getDiff(it, weights) }.sum()

    fun getMAP(weights: INDArray) = models.map { model -> getAP(model, weights) }.average()

    var iterations = 0
    var prevDiff = 0.0
    var gradientTotal = 0.0
    var bestLearn = 0.0

    fun run(cur: INDArray, learningRate: Double): Pair<INDArray, Double> {
        var params = cur
        val baseline = getMAP(params)
        var curLearning = if (bestLearn > 0.0) bestLearn else learningRate
        var highest = Triple(baseline, params, 0)

        var gradHistory = ArrayList<Double>()

        (0 until 5).forEach {
            val gradients = gradient(params, curLearning)

            params = params.plus(gradients)
//            params.addi(gradients)
            iterations += 1
            val gradSum = gradients.sumNumber().toDouble()
            gradientTotal += gradSum
            gradHistory.add(gradSum)

        }
        val curGrad = gradHistory.takeLast(5).sum()  / 5

        println("$curGrad at $iterations")

            prevDiff = curGrad

        val normal = params
        println(normal.toDoubleVector().toList())
        println()
        return normal to curLearning
    }

    private fun gradient(p: INDArray, learningRate: Double): INDArray {
        val result = getTotalDiffGradient(p)
        return result * learningRate
    }
}


fun runTestMap() {
    val models = RanklibReader("ony_paragraph.txt")
//    val models = RanklibReader("ranklib_results.txt")
        .createVectors2()
        .filter { it.relevances.any { it.value > 0.0 } }

    val calculator = MAPCalculator(models)
    println(calculator.getTotalDiff(calculator.originWeights))
    println(calculator.getMAP(calculator.originWeights))
    println()
    var learningRate = 0.01
    var params = (0 until calculator.nFeatures).map { sharedRand.nextDouble() }.normalize().toNDArray()
    (0 until 250).forEach {
        val (params2, newRate) = calculator.run(params, learningRate )
        learningRate = newRate
        params = params2
        val totalDiff = calculator.getTotalDiff(params)
        println(totalDiff)
        println(calculator.getMAP(params))
    }
    println(params)
    println(calculator.getTotalDiff(params))

}

fun main(args: Array<String>) {
    runTestMap()

}