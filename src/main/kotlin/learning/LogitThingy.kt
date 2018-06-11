package learning

import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j
import org.nd4j.linalg.factory.Nd4j.*
import org.nd4j.linalg.ops.transforms.Transforms.*
import utils.misc.withTime
import utils.nd4j.*
import utils.stats.normalize
import java.util.*


class LogitThingy {
//    val maxIterations = 50000
    val maxIterations = 3000
//    val learningRate = 0.01
    var learningRate = 0.9
//    var learningRate = 0.001
//    var learningRate = 0.0000005
//    val minLearningRate = 0.0001
//    val minLearningRate = 0.000001
//    val minLearningRate = 0.000001
    val minLearningRate = 0.00001
    var prevKld: Double = 9.0
    var bestParams = zeros(1)


    /**
     * Training algorithm.
     *
     * Gradient descent optimization.
     * The logistic cost function (or max-entropy) is convex,
     * and thus we are guaranteed to find the global minimum.
     *
     * @param x input features
     * @param y output classes
     * @param maxIterations max number of learning iterations
     * @param learningRate how fast parameters change
     * @param minLearningRate lower bound on learning rate
     * @return optimal parameters
     */
    fun training(x: INDArray, y: INDArray): INDArray {
        Nd4j.getRandom().setSeed(1234)
//        var params = Nd4j.rand(x.size(1), 1).normalizeColumns() //random guess
        var params = (0 until x.size(1)).map { 1 / x.size(1).toDouble() }.toNDArray().transpose().normalizeColumns()

        val weights = listOf(300.0, 300.0, 0.0, 400.0).normalize().toNDArray()
//        params = bestBest.transpose()
        val bestResult = y.kld(x.mmul(weights.transpose()).transpose().normalizeRows()).sumNumber()

        var newParams = params.dup()
        var optimalParams = params.dup()

        for (i in 0 until maxIterations) {
            var gradients = gradient(x, y, params)
            gradients = gradients.mul(learningRate)
//            newParams = params.sub(gradients).relu()
            newParams = params.sub(gradients).relu().normalizeColumns()
//            newParams = params.sub(gradients).normalizeColumns()
//            newParams = params.sub(gradients).normalizeColumns()

            if (hasConverged(params, newParams, minLearningRate)) {
                println("Converged in: $i")
                break
            }
//            learningRate *= 0.99
            params = newParams
        }

        optimalParams = newParams
//        println(optimalParams)
//        println("Best result: $bestResult")
//        val kld2 = y.kld(x.mmul(optimalParams).normalizeRows()).sumNumber()
        val kld2 = y.kld(x.mmul(bestParams.normalizeColumns()).transpose().normalizeRows()).sumNumber()
        println("Perturb result: $kld2")
        println("Best params: $bestParams")
//        return optimalParams.normalizeColumns()
        return bestParams
    }


    /**
     * Gradient function.
     *
     * Compute the gradient of the cost function and
     * how much error each parameter puts into the result.
     *
     * @param x features
     * @param y labels
     * @param p parameters
     * @return paramters gradients
     */
    private fun gradient(x: INDArray, y: INDArray, p: INDArray): INDArray {
        val m = x.size(0) //number of examples
//        val pred = predict(x, p.normalizeColumns())
        val pred = x.mmul(p)
//        val diff = pred.sub(y) * 10

        val diff = y.kld(pred.transpose().normalizeRows()) * -1

//        val kld2 = y.kld(pred).sum(1).sum(0)
//        val kld2 = y.transpose().kld(pred.transpose().normalizeRows())
        val kld2 = y.transpose().kld(pred.transpose().normalizeRows()).sumNumber()
//        if (prevKld - kld == 0.0)
        if (kld2.toDouble() < prevKld) {
            prevKld = kld2.toDouble()
            bestParams = p
        }
//        prevKld = kld2.toDouble()
//        if (kld2 - prevKld == 0) return zerosLike(p)
//        println(p)
//        println(x.transpose().mmul(diff))
//        val diff = y.kld(pred) * -100
//        val diff = y.div(pred).log().mul(y)
//        println("Diff: $diff")
//        println(p)
//        println(pred)
//        println(y)
//        println(x.transpose().mmul(diff))
        return x
            .transpose()
            .mmul(diff) * p.sign()
//            .mul(1/m.toDouble())
    }



    private fun hasConverged(oldParams: INDArray, newParams: INDArray, threshold: Double): Boolean {
        val diffSum = abs(oldParams.sub(newParams)).sumNumber().toDouble()
        return diffSum / oldParams.size(0) < threshold
    }


    fun makeStuff(): Pair<INDArray, INDArray> {
        val targets = (0 until 10).map { 1 / 10.0 }.toDuplicatedNDArray(10)
        val features = randn(10, 10)
        return targets to features
    }

}


fun main(args: Array<String>) {
    val logit = LogitThingy()
//    val (targets, features) = logit.makeStuff()
    val (targets, features) = makeStuff()
    val perturbations = perturb(targets, 200000)
    val (results, perturbedTarget) = applyFeatures(perturbations,  features)

//    val optimal = logit.training(results, listOf(0.0, 0.0, 1.0, 0.0, 0.0).toNDArray())
//    val optimal = logit.training(stuff mulRowV vectorOf(1.0, 0.0, 1.0), stuff.sum(1))

//    val weights = predict(results, features.combineNDArrays(), targets, perturbations)

    val uniform = ones(results.columns()) / results.columns().toDouble()
    val optimal = logit.training(results.transpose(), uniform.transpose())
//    val (time, optimal) = withTime { logit.training(results.transpose(), perturbedTarget.transpose()) }
//    println("TIME: $time")
//    val optimal = logit.training(features.combineNDArrays().transpose(), targets.transpose())
//    println(optimal)
//    val evals = (onesLike(optimal).div(optimal.normalizeColumns())).normalizeColumns()
    val transformed = (features.take(features.size - 1).combineNDArrays() mulColV optimal).sum(0)

    println(transformed distEuc targets)
//    val varianceResult = sqrt(perturbations.subRowVector(transformed).pow(2.0)).sum(1).varianceRows().sumNumber()
    val varianceResult = abs(perturbations.subRowVector(transformed)).sum(1).varianceRows().sumNumber()
    println("Mixture variance: $varianceResult")
//    val optimal = logit.training(features, (0 until 7).map { 1 / 7.0 }.toNDArray())
//    println(optimal.mmul(features))

//    val varianceResult2 = perturbations.subRowVector(targets).pow(2.0).sqrt().sum(1).varianceRows().sumNumber()
    val varianceResult2 = perturbations.subRowVector(targets).abs().sum(1).varianceRows().sumNumber()
    println("Target variance: $varianceResult2")
//    predict(results.transpose(), features.combineNDArrays(), targets, perturbations)
}


