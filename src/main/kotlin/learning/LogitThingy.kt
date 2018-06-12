package learning

import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j
import org.nd4j.linalg.factory.Nd4j.*
import org.nd4j.linalg.ops.transforms.Transforms.*
import utils.misc.withTime
import utils.nd4j.*
import utils.stats.normalize
import java.util.*
import kotlin.math.absoluteValue

class StatPrinter {

}


class LogitThingy(val perturbations: INDArray, val originals: INDArray, val originalTarget: INDArray) {
    val maxIterations = 20000
    val reportStep = 100
    var iterations = 0
//    var learningRate = 20.0
    var learningRate = 10000.0
//    val minLearningRate = 0.00000001
//    val minLearningRate = 0.000000001
    val minLearningRate = 0.00000000000001
    var prevKld: Double = 9.0
    var prevKldSum: Double = 0.0
    var bestParams = zeros(1)
    var originalStuff = zeros(1)
    var originalTargetStuff = zeros(1)
    var lowest = 99999.0
    var lowestAt = 0
    var lowestPerturbedTargetDist = 999999999.0
    var lowestPerturbedTarget = zeros(1)
    var lowestPerturbedTargetParams = zeros(1)
    var lowestPerturbedTargetAt = 0
    var lowestSecondDiffSumAt = 0
    var lowestSecondDiffSum = 0.0
    var lowestSecondDiffSumParams = zeros(1)
    var kldSum = 0.0
    var prevKldDiff = 0.0
    val paramHistory = ArrayList<INDArray>()
    var kldLowest = 99999.0 to 0

    fun training(x: INDArray, y: INDArray, perturbedTarget: INDArray): INDArray {
        Nd4j.getRandom().setSeed(1234)
//        var params = Nd4j.rand(x.size(1), 1).normalizeColumns() //random guess
        var params = (0 until x.size(1)).map { 1 / x.size(1).toDouble() }.toNDArray().transpose().normalizeColumns()

        lowestPerturbedTarget = perturbedTarget
        originalStuff = originals
        originalTargetStuff = originalTarget

        var newParams = params.dup()
        var optimalParams = params.dup()

        for (i in 0 until maxIterations) {
            var gradients = gradient(x, y, params)
            iterations += 1
            gradients = gradients.mul(learningRate)
//            newParams = params.sub(gradients).relu()
            newParams = params.sub(gradients).relu().normalizeColumns()
//            newParams = params.sub(gradients).relu()
//            newParams = params.sub(gradients).normalizeColumns()
//            newParams = params.sub(gradients).normalizeColumns()

            if (hasConverged(params, newParams, minLearningRate)) {
                println("Converged in: $i")
                break
            }
//            learningRate *= 0.999
            params = newParams
        }

        optimalParams = newParams
        println(optimalParams)
//        println(optimalParams)
//        println("Best result: $bestResult")
//        val kld2 = y.kld(x.mmul(optimalParams).normalizeRows()).sumNumber()
        val kld2 = y.kld(x.mmul(bestParams.normalizeColumns()).transpose().normalizeRows()).sumNumber().toDouble()
        println("Perturb result: $kld2")
        println("Lowest $lowest at $lowestAt")
        println("Lowest Second Derivative $lowestSecondDiffSum at $lowestSecondDiffSumAt")
        println("Lowest Perturbed  $lowestPerturbedTargetDist at $lowestPerturbedTargetAt")

//        println("Lowest Perturbed Params: ${paramHistory[lowestPerturbedTargetAt].transpose()}")
//        println("Lowest Target Params: ${paramHistory[lowestAt].transpose()}")
//        println("Lowest Kld Params: ${paramHistory[kldLowest.second].transpose()}")

        reportParams("Lowest Second Derivative", lowestSecondDiffSumAt)
        reportParams("Lowest Perturbed", lowestPerturbedTargetAt)
        reportParams("Lowest Target", lowestAt)
        reportParams("Lowest KLD", kldLowest.second)
        reportParams("Last Result", iterations - 1)

        return optimalParams.normalizeColumns()
//        return lowestPerturbedTargetParams.normalizeColumns()
        return bestParams.normalizeColumns()
    }

    fun reportParams(name: String, index: Int) {
        val p = paramHistory[index]
        val mixed = mixFeatures(originals, p)
        val variance = varianceToPerturbed(mixed)
        println("====$name=====")
        println("Params: ${p.transpose().toDoubleVector().toList()}")
        println("Variance: $variance / Distance: ${mixed.euclideanDistance(originalTarget)}")
        println("=========\n")
    }

    fun mixFeatures(feats: INDArray, params: INDArray): INDArray {
        return (feats mulColV params).sum(0)!!
    }

    // GRADIENT
    private fun gradient(x: INDArray, y: INDArray, p: INDArray): INDArray {
        val m = x.size(0) //number of examples
//        val pred = x.mmul(p).abs()
        val pred = x.mmul(p)
        paramHistory.add(p)

//        val diff = pred.sub(y)
//        val diff = y.sub(pred)
//        val diff = y.kld(pred.transpose().abs().normalizeRows())
        val diff = y.kld(pred.transpose().normalizeRows()) * -1
//        val diff3 = pred.normalizeColumns().kld(y.transpose())
//        val diff2 = y.kld(pred.transpose().normalizeRows()) * -1
//        val diff = (diff2 + diff3) / 2.0
//        println(diff)
//        val diff = lowestPerturbedTarget.sub(pred) * -100
//        println(lowestPerturbedTarget)
//        val diff = lowestPerturbedTarget.transpose().kld(pred.transpose().normalizeRows()) * -1


        val kld2 = y.transpose().kld(pred.transpose().normalizeRows()).sumNumber().toDouble().absoluteValue
//        val kld2 = pred.transpose().normalizeRows().kld(y.transpose()).sumNumber().toDouble()
        if (kld2 < kldLowest.first) { kldLowest = kld2 to iterations }

        val combined = originalStuff.transpose().mmul(p).euclideanDistance(originalTargetStuff)
        if (combined < lowest) {
            lowest = combined
            lowestAt = iterations
        }

        val combined2 = pred.euclideanDistance(lowestPerturbedTarget)
//        val combined2 = lowestPerturbedTarget.div(x.mmul(p)).log().sumNumber().toDouble() * -0.00001
//        val combined2 = x.mmul(p).transpose().kldSymmetric(lowestPerturbedTarget.transpose()).sumNumber().toDouble()
        if (combined2 < lowestPerturbedTargetDist) {
            lowestPerturbedTargetDist = combined2
            lowestPerturbedTargetAt = iterations
            lowestPerturbedTargetParams = p
        }


        val kldDiff = (prevKld - kld2.toDouble())
//        kldSum = kldDiff
        val klDiff2 = kldDiff - prevKldDiff
//        val kldSumDiff = kldSum - prevKldSum
//        prevKldSum = kldDiff
        kldSum += klDiff2
        prevKldDiff = klDiff2
        if (kldSum > lowestSecondDiffSum) {
            lowestSecondDiffSum = kldSum
            lowestSecondDiffSumAt = iterations
        }

//        println("${kld2.toDouble()} : ${kldDiff} : $klDiff2 : $kldSum : $combined  : $iterations")
        if (iterations % reportStep == 0) {
            println("${kld2.toDouble()} : $lowestPerturbedTargetDist : $combined  : $iterations")
        }
        prevKld = kld2.toDouble()
        bestParams = p
        if (kld2.toDouble() < prevKld) {
            prevKld = kld2.toDouble()
            bestParams = p
        }
        return x
            .transpose()
            .mmul(diff)
//            .mmul(diff) * p.sign()
//            .mul(1/m.toDouble())
    }



    private fun hasConverged(oldParams: INDArray, newParams: INDArray, threshold: Double): Boolean {
        val diffSum = abs(oldParams.sub(newParams)).sumNumber().toDouble()
        return diffSum / oldParams.size(0) < threshold
    }


    fun varianceToPerturbed(vec: INDArray): Double {
        return perturbations.normalizeRows().subRowVector(vec.normalizeRows()).abs().sum(1).varianceRows().sumNumber().toDouble()
//        return perturbations.subRowVector(vec).abs().sum(1).varianceRows().sumNumber().toDouble()
//        return perturbations.subRowVector(vec.normalizeRows()).abs().sum(1).sumNumber().toDouble()
//        return perturbations.subRowVector(vec.normalizeRows()).abs().sum(1).varianceRows().sumNumber().toDouble()
//        return perturbations.subRowVector(vec.normalizeRows()).pow(2.0).sqrt().sum(1).varianceRows().sumNumber().toDouble()
//        return perturbations.subRowVector(vec.normalizeRows()).pow(2.0).sum(1).varianceRows().sumNumber().toDouble()
//        return perturbations.div(vec.normalizeRows()).log().sum(1).varianceRows().sumNumber().toDouble()
    }

}


fun main(args: Array<String>) {
//    val (targets, features) = logit.makeStuff()
    val (targets, features) = makeStuff()
    val perturbations = perturb(targets, 1000)
    val (results, perturbedTarget) = applyFeatures(perturbations,  features)
    val originals = (features.take(features.size - 1).combineNDArrays())
    val logit = LogitThingy(perturbations, originals, targets)

//    val optimal = logit.training(results, listOf(0.0, 0.0, 1.0, 0.0, 0.0).toNDArray())
//    val optimal = logit.training(stuff mulRowV vectorOf(1.0, 0.0, 1.0), stuff.sum(1))

//    val weights = predict(results, features.combineNDArrays(), targets, perturbations)

    val uniform = ones(results.columns()) / results.columns().toDouble()
    val optimal = logit.training(results.transpose(), uniform.transpose(), perturbedTarget)
//    val optimal = logit.training(results.transpose(), perturbedTarget.transpose())
//    val (time, optimal) = withTime { logit.training(results.transpose(), perturbedTarget.transpose()) }
//    println("TIME: $time")
//    val optimal = logit.training(features.combineNDArrays().transpose(), targets.transpose())
//    println(optimal)
//    val evals = (onesLike(optimal).div(optimal.normalizeColumns())).normalizeColumns()
    val transformed = (features.take(features.size - 1).combineNDArrays() mulColV optimal).sum(0)

    println(transformed distEuc targets)
//    val varianceResult = sqrt(perturbations.subRowVector(transformed).pow(2.0)).sum(1).varianceRows().sumNumber()
//    val varianceResult = abs(perturbations.subRowVector(transformed)).sum(1).varianceRows().sumNumber()
//    println("Mixture variance: $varianceResult")
//    val optimal = logit.training(features, (0 until 7).map { 1 / 7.0 }.toNDArray())
//    println(optimal.mmul(features))

//    val varianceResult2 = perturbations.subRowVector(targets).pow(2.0).sqrt().sum(1).varianceRows().sumNumber()
//    val varianceResult2 = perturbations.subRowVector(targets).abs().sum(1).varianceRows().sumNumber()
    val varianceResult2 = logit.varianceToPerturbed(targets)
    println("Target variance: $varianceResult2")
//    predict(results.transpose(), features.combineNDArrays(), targets, perturbations)
    val bestResult = uniform.kld(perturbedTarget.normalizeRows()).sumNumber()
    println("Distance to perturbed: $bestResult")
}


