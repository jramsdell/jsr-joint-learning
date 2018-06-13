package learning

import learning.containers.LogitDiff
import learning.containers.LogitDiffNormal
import learning.containers.LogitPred
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j.*
import org.nd4j.linalg.ops.transforms.Transforms.*
import utils.nd4j.*
import java.util.*
import kotlin.math.absoluteValue

class StatPrinter {

}


class LogitThingy(val perturbations: INDArray,
                  val originalFeatures: INDArray,
                  val originalTarget: INDArray,
                  val perturbedTarget: INDArray) {
    val maxIterations = 100
    val reportStep = 10
    var iterations = 0
    var learningRate = listOf(0.5, 1.0, 2.0, 5.0, 10.0, 50.0)[1]
    val normalizeBySize = true

    val predFun = LogitPred.PRED_DOT_SIGMOID
    val diffFun = LogitDiff.DIFF_SUB
//    val diffNormal = LogitDiffNormal.DMOD_EXP
    val diffNormal = LogitDiffNormal.DMOD_EXP_NORMAL


    val minLearningRate = 0.000001
    var prevKld: Double = 9999999.0
    var bestParams = zeros(1)
    var lowest = 99999.0
    var lowestAt = 0
    var lowestSecondDiffSumAt = 0
    var lowestSecondDiffSum = 0.0
    val paramHistory = ArrayList<INDArray>()
    var kldLowest = 99999.0 to 0

    fun training(x: INDArray, y: INDArray): INDArray {
//        var params = (0 until x.size(1)).map { 1 / x.size(1).toDouble() }.toNDArray().transpose().normalizeColumns()
//        var params = (0 until x.size(1)).map { 1.0 }.toNDArray().transpose().normalizeColumns()
        var params = (0 until x.size(1)).map { x.size(1).toDouble() }.toNDArray().transpose().normalizeColumns()

        var newParams = params.dup()
        var optimalParams = params.dup()

        for (i in 0 until maxIterations) {
//            val gradients = gradient(x, y, params.relu().normalizeColumns())
            var gradients = gradient(x, y, params)
//            var gradients = gradient(x, y, params.normalizeColumns())
//            var gradients = gradient(x, y, params.exp().normalizeColumns())
//            var gradients = gradient(x, y, params.normalizeColumns())
            iterations += 1
            params.addi(gradients.mul(learningRate))
//            params.subi(gradients.mul(learningRate))
            learningRate *= 1.04


//            if (hasConverged(params, newParams, minLearningRate)) {
//                println("Converged in: $i")
//                break
//            }
//            params = newParams
        }

        reportResults()
        optimalParams = newParams
        return optimalParams.normalizeColumns()
    }


    // GRADIENT
    private fun gradient(x: INDArray, y: INDArray, p: INDArray): INDArray {
        // Calculate dot-product
        val pred = predFun.f(this, x, p.relu())
        paramHistory.add(p.relu().normalizeColumns())

        // Get difference
//        val diff = diffFun.f(this, y, pred.normalizeRows())
        val diff = diffFun.f(this, y, pred)
            .let { result ->  diffNormal.f(this, result) }


        // Logging and Stuff
//        val kld2 = y.transpose().kld(pred.normalizeRows()).sumNumber().toDouble().absoluteValue
        val kld2 = y.transpose().kld(x.mmul(p.relu())).sumNumber().toDouble()
//        val kld2 = y.transpose().kld(x.mmul(p)).sumNumber().toDouble().absoluteValue
        if (kld2.absoluteValue < kldLowest.first) { kldLowest = kld2.absoluteValue to iterations }

//        val combined = originalFeatures.transpose().mmul(p.relu().normalizeColumns()).euclideanDistance(originalTarget)
        val combined = originalFeatures.transpose().mmul(p.relu().normalizeColumns()).euclideanDistance(originalTarget)
        if (combined < lowest) {
            lowest = combined
            lowestAt = iterations
        }


        if (iterations % reportStep == 0) {
            println("${kld2.toDouble()}  : $combined  : $iterations")
        }
//        prevKld = kld2.toDouble()
//        bestParams = p
//        if (kld2.toDouble().absoluteValue < prevKld) {
//            prevKld = kld2.toDouble().absoluteValue
//            bestParams = p
//        }

        // Return gradient
        val m = x.size(0) //number of examples
        return x
            .transpose()
//            .mmul(diff) * p.relu().mul(kld2 * -1)
            .mmul(diff).mul(kld2 * -1) * p.relu()
//            .mmul(diff)
            .run { if (normalizeBySize) mul(1/m.toDouble()) else this }
    }



    private fun hasConverged(oldParams: INDArray, newParams: INDArray, threshold: Double): Boolean {
        val diffSum = abs(oldParams.sub(newParams)).sumNumber().toDouble()
        return diffSum / oldParams.size(0) < threshold
    }


    fun varianceToPerturbed(vec: INDArray): Double {
        return perturbations.normalizeRows().subRowVector(vec.normalizeRows()).abs().sum(1).varianceRows().sumNumber().toDouble()
//        return perturbations.normalizeRows().subRowVector(vec.normalizeRows()).pow(2).sum(1).varianceRows().sumNumber().toDouble()
    }

    private fun reportResults() {
        println("Lowest $lowest at $lowestAt")
        println("Lowest Second Derivative $lowestSecondDiffSum at $lowestSecondDiffSumAt")
        reportParams("Lowest Second Derivative", lowestSecondDiffSumAt)
        reportParams("Lowest Target", lowestAt)
        reportParams("Lowest KLD", kldLowest.second)
        reportParams("Last Result", iterations - 1)
    }

    fun reportParams(name: String, index: Int) {
        val p = paramHistory[index].relu().normalizeColumns()
        val mixed = mixFeatures(originalFeatures, p)
        val variance = varianceToPerturbed(mixed)
        println("====$name=====")
        println("Params: ${p.transpose().toDoubleVector().toList()}")
        println("Variance: $variance / Distance: ${mixed.euclideanDistance(originalTarget)}\n")
    }

    fun mixFeatures(feats: INDArray, params: INDArray): INDArray {
        return (feats mulColV params).sum(0)!!
    }

}


fun main(args: Array<String>) {
    val (targets, features) = makeStuff()
    val perturbations = perturb(targets, 100)
    val (results, perturbedTarget) = applyFeatures(perturbations,  features)
    val originals = (features.take(features.size - 1).combineNDArrays())
    val logit = LogitThingy(perturbations, originals, targets, perturbedTarget)


    val uniform = ones(results.columns()) / results.columns().toDouble()
    val optimal = logit.training(results.transpose(), uniform.transpose())
//    val optimal = logit.training(results.transpose(), perturbedTarget.transpose())

//    val transformed = (features.take(features.size - 1).combineNDArrays() mulColV optimal).sum(0)
//    println(transformed distEuc targets)

    val varianceResult2 = logit.varianceToPerturbed(targets)
    println("Target variance: $varianceResult2")
//    predict(results.transpose(), features.combineNDArrays(), targets, perturbations)

//    val bestResult = uniform.kld(perturbedTarget.normalizeRows()).sumNumber()
//    println("Distance to perturbed: $bestResult")
}


