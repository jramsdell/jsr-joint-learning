package learning

import learning.containers.LogitDiff
import learning.containers.LogitDiffNormal
import learning.containers.LogitPred
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j.*
import org.nd4j.linalg.ops.transforms.Transforms.*
import utils.nd4j.*
import utils.stats.defaultWhenNotFinite
import java.util.*
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sign

class StatPrinter {

}


class LogitThingy(val perturbations: INDArray,
                  val originalFeatures: INDArray,
                  val originalTarget: INDArray,
                  val perturbedTarget: INDArray) {
    val maxIterations = 5
    val reportStep = 1
    var iterations = 0
    var learningRate = listOf(0.5, 1.0, 2.0, 5.0, 10.0, 50.0)[1]
    val normalizeBySize = true

    val predFun = LogitPred.PRED_DOT
    val diffFun = LogitDiff.DIFF_SUB
    val diffNormal = LogitDiffNormal.DMOD_NONE
//    val diffNormal = LogitDiffNormal.DMOD_EXP_NORMAL
//    val diffNormal = LogitDiffNormal.DMOD_NONE


    val minLearningRate = 0.000001
    var prevKld: Double = 0.0
    var prevDiffVar = 0.0
    var bestParams = zeros(1)
    var lowest = 99999.0
    var lowestAt = 0
    var lowestSecondDiffSumAt = 0
    var lowestSecondDiffSum = 0.0
    val paramHistory = ArrayList<INDArray>()
    val predHistory = ArrayList<INDArray>()
    var kldLowest = 99999.0 to 0
    var curKld = 0.0
    var diffSum = 0.0
    var highest = 0.0
    var prevTotal = 0.0

    fun getBestChange(pT: Double, cT: Double, pP: INDArray, cP: INDArray): INDArray {
        val dT = cT - pT
        val dP = (cP - pP).sumNumber().toDouble()
        val dSolve = ((cT / (dT / dP).absoluteValue) * 1  )
//        val dSolve = ((cT / (dT)) * 1  )
        println("\tdT: $dT ($pT, $cT, $dSolve),  dP: $dP")
        println(cP * dSolve * -1 * Math.signum(pT))
//        return (cP * dSolve * -1 * Math.signum(pT))
        return (cP * dSolve)
//        return wee.sigmoid() * wee.sign()

    }

    fun training(x: INDArray, y: INDArray): INDArray {
//        var params = (0 until x.size(1)).map {  }.toNDArray().transpose().normalizeColumns()
//        var params = rand(x.size(1), 1)
//        var params = (0 until x.size(1)).map { 1.0 }.toNDArray().transpose()
        var params = (0 until x.size(1)).map { x.size(1).toDouble() }.toNDArray().transpose().normalizeColumns()

        var newParams = params.dup()
        var optimalParams = params.dup()
        var prevGradients = params

        for (i in 0 until maxIterations) {
//            val gradients = gradient(x, y, params.relu().normalizeColumns())
            curKld = prevKld
//            var gradients = gradient(x, y, params.softMax())
//            println(gradients.normalizeColumns())
//            var gradients = gradient(x, y, params.normalizeColumns())
            val curDiff = prevTotal
            var gradients = gradient(x, y, params)
//            prevTotal = gradients.sumNumber().toDouble()
            println(prevTotal)



            if (iterations > 0) {
                val diffRatio = 1 / (curDiff - prevTotal)
                val gradientRatio =  prevGradients - gradients
//                if (Math.abs(curDiff - prevTotal) <= 0.000000000000000001) {
//                    println("Converged at $iterations")
//                    break
//                }
                gradients = getBestChange(prevTotal, curDiff, prevGradients, gradients)
            }

//            var gradients = gradient(x, y, params.exp().normalizeColumns())
//            var gradients = gradient(x, y, params.normalizeColumns())
//            params.addi(gradients.mul(learningRate))
            iterations += 1
            params = (params - gradients.mul(learningRate))
            prevGradients = params
//            params.subi(gradients.mul(learningRate))
//            println(gradients)
            learningRate *= 1.0



//            if (hasConverged(params, newParams, minLearningRate)) {
//            if (kldLowest.first < 0.00000000000000000000001 || curKld > prevKld) {
//                println(prevKld)
//                println("Converged in: $i")
//                break
//            }
//            params = newParams
        }

        reportResults()
        optimalParams = newParams
        return optimalParams
    }


    // GRADIENT
    private fun gradient(x: INDArray, y: INDArray, p: INDArray): INDArray {
        // Calculate dot-product
//        val pred = predFun.f(this, x, p.relu())
//        val pred = predFun.f(this, x, p.normalizeColumns())
//        val pred = predFun.f(this, x, p.normalizeColumns())
//        val pred = predFun.f(this, x, p.normalizeColumns())
//        val inverseP = p.relu().invertDistribution().normalizeColumns()
//        val inverseP = p.invertDistribution().normalizeColumns()
        val inverseP = p.normalizeColumns()
//        val inverseP = p
//        val pred = predFun.f(this, x, p.normalizeColumns())
//        val pred = predFun.f(this, x, p.normalizeColumns())
        val pred = predFun.f(this, x, p)
        val pred5 = predFun.f(this, x, inverseP)
//        paramHistory.add(p.relu().normalizeColumns())
        paramHistory.add(inverseP)


        // Get difference
//        val diff = diffFun.f(this, y, pred.normalizeRows())
//        val diff = diffFun.f(this, y, pred)
//            .let { result ->  diffNormal.f(this, result) }

        val diff = pred.transpose()
//        val diffTotal = diff.sumNumber().toDouble()
//        println(diffLog)
//
//        if (iterations > 60 && iterations < 80) {
//            println("$iterations: $diffTotal : $diffLog : $diffRatio")
//        }



        prevTotal = diff.sumNumber().toDouble()
//        val pred2 = (pred ).abs().sumNumber()
//        val predMean = diff.sumNumber().toDouble() / diff.rows().toDouble()
//        val variance = (diff - predMean).pow(2.0).sumNumber().toDouble()
//        val varianceMean = variance.sumNumber().toDouble() / variance.rows().toDouble()
//        val secondVariance = (variance - varianceMean).pow(2.0).sumNumber().toDouble()
//        println("\n$variance\n")


        // Logging and Stuff
//        val kld2 = y.transpose().kld(pred5.normalizeRows()).sumNumber().toDouble().absoluteValue
//        println(x.mmul(p.normalizeColumns()).sumNumber().toDouble())
//        val kld2 = y.transpose().kld(x.mmul(p.normalizeColumns())).sumNumber().toDouble().defaultWhenNotFinite(0.0)
//        val kld2 = y.transpose().kld(x.mmul(p.normalizeColumns()).transpose()).sumNumber().toDouble().defaultWhenNotFinite(0.0)
//        val kld2 = y.transpose().kld(pred).sumNumber().toDouble().defaultWhenNotFinite(0.0)
//        val kld2 = x.mmul(p.relu()).kld(y).sumNumber().toDouble().defaultWhenNotFinite(0.0)
//        val kld2 = pred.transpose().kld(y).sumNumber().toDouble()
//        val kld2 = y.kldSymmetric(x.mmul(p.relu())).sumNumber().toDouble().defaultWhenNotFinite(0.0)
//        val kld2 = y.transpose().kld(x.mmul(p)).sumNumber().toDouble().absoluteValue
        val kld2 = 0.0

//        val kld2 = 0.0
        if (kld2.absoluteValue < kldLowest.first && iterations != 0) { kldLowest = kld2.absoluteValue to iterations }
//        if (kld2 == 0.0) return zerosLike(p)

        val combined = originalFeatures.transpose().mmul(inverseP).euclideanDistance(originalTarget)
//        val combined = originalFeatures.transpose().mmul(p).euclideanDistance(originalTarget).absoluteValue
//        val combined = originalFeatures.transpose().mmul(p).euclideanDistance(originalTarget).absoluteValue
        if (combined < lowest) {
            lowest = combined
            lowestAt = iterations
        }

        val kldDiff = if (iterations == 0) 0.0 else prevKld - kld2
        diffSum += kldDiff

        if (iterations % reportStep == 0) {
            println("${kld2.toDouble()}  : $combined  : $diffSum : $iterations")
        }
        prevKld = kld2



        // Return gradient
        val m = x.size(0) //number of examples
//        val result = x.transpose().mmul(diff)
//        val result = x.transpose().mmul(diff).mul((p.relu().sign()))
        val result = x.transpose().mmul(diff)
//            .run { if (iterations != 0) mul(diffRatio) else this }
            .run { if (normalizeBySize) mul(1/m.toDouble()) else this }

//        val pred2 = predFun.f(this, x, result)
////        val pred2 = pred5
//        val diff2 = diffFun.f(this, pred2, pred)
//            .let { result2 ->  diffNormal.f(this, result2) }
//        val result2 = x.transpose().mmul(diff2)
//            .run { if (normalizeBySize) mul(1/m.toDouble()) else this }

//        val pred3 = predFun.f(this, x, result2)
//        val diff3 = diffFun.f(this, pred3, pred2)
//            .let { result3 ->  diffNormal.f(this, result3) }
//        val result3 = x.transpose().mmul(diff3).mul(result2 )
//            .run { if (normalizeBySize) mul(1/m.toDouble()) else this }


        return result
    }



    private fun hasConverged(oldParams: INDArray, newParams: INDArray, threshold: Double): Boolean {
        val diffSum = abs(oldParams.sub(newParams)).sumNumber().toDouble()
        return diffSum / oldParams.size(0) < threshold
    }


    fun varianceToPerturbed(vec: INDArray): Double {
        return perturbations.normalizeRows().subRowVector(vec.normalizeRows()).pow(2.0).sum(1).varianceRows().sumNumber().toDouble()
//        return perturbations.subRowVector(vec.normalizeRows()).pow(2.0).sum(1).varianceRows().sumNumber().toDouble()
//        return perturbations.normalizeRows().subRowVector(vec.normalizeRows()).pow(2.0).sumNumber().toDouble()
//        return perturbations.normalizeRows().subRowVector(vec.normalizeRows()).pow(2).sum(1).varianceRows().sumNumber().toDouble()
    }

    private fun reportResults() {
//        predHistory.forEach { println(it) }
        println("Lowest $lowest at $lowestAt")
        println("Lowest Second Derivative $lowestSecondDiffSum at $lowestSecondDiffSumAt")
        reportParams("Lowest Second Derivative", lowestSecondDiffSumAt)
        reportParams("Lowest Target", lowestAt)
        reportParams("Lowest KLD", kldLowest.second)
        reportParams("Last Result", iterations - 1)
        reportParams("Minimum Variance", getMinimumVariance())
    }

    fun getMinimumVariance() =
            paramHistory.mapIndexed { index, p -> varianceToPerturbed(mixFeatures(originalFeatures, p)) to index  }
                .minBy { (variance, index) -> variance  }!!.second

    fun reportParams(name: String, index: Int) {
        val p = paramHistory[index].relu().normalizeColumns()
        val mixed = mixFeatures(originalFeatures, p)
        val variance = varianceToPerturbed(mixed)
        println("====$name ($index)=====")
        println("Params: ${p.transpose().toDoubleVector().toList()}")
        println("Variance: $variance / Distance: ${mixed.euclideanDistance(originalTarget)}\n")
    }

    fun mixFeatures(feats: INDArray, params: INDArray): INDArray {
        return (feats mulColV params).sum(0)!!
    }

}


fun main(args: Array<String>) {
    val (targets, features) = makeStuff()
    val perturbations = perturb(targets, 50)
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


