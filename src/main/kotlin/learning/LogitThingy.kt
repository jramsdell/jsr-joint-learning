package learning

import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j
import org.nd4j.linalg.factory.Nd4j.*
import org.nd4j.linalg.ops.transforms.Transforms.*
import utils.nd4j.normalizeColumns
import utils.nd4j.normalizeRows
import utils.nd4j.toDuplicatedNDArray
import utils.nd4j.toNDArray
import utils.stats.normalize
import java.util.*


class LogitThingy {
    val maxIterations = 10
    val learningRate = 0.01
    val minLearningRate = 0.0001


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
        var params = Nd4j.rand(x.size(1), 1) //random guess

        var newParams = params.dup()
        var optimalParams = params.dup()

        for (i in 0 until maxIterations) {
            var gradients = gradient(x, y, params)
            gradients = gradients.mul(learningRate)
            newParams = params.sub(gradients)

            if (hasConverged(params, newParams, minLearningRate)) {
                break
            }
            params = newParams
        }

        optimalParams = newParams
        return optimalParams
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
        val pred = predict(x, p)
//        println(p)
//        val diff = pow(pred.dup().sub(y), 2.0)
//        val diff = pred.dup().sub(y.transpose())
        val diff = max(pred.dup().sub(y), 0.0).transpose()
//        val diff = pred.dup().sub(y)
//        println(p)
//        println(pred)
//        println(y)
//        println(x)
        return x.dup()
            .transpose()
            .mmul(diff)
            .mul(1.0 / m)
    }

    /**
     * Binary logistic regression.
     *
     * Computes the probability that one example is a certain type of flower.
     * Can compute a batch of examples at a time, i.e. a matrix with samples
     * as rows and columns as features (this is normally done by DL4J internals).
     *
     * @param x features
     * @param p parameters
     * @return class probability
     */
    private fun predict(x: INDArray, p: INDArray): INDArray {
        val y = x.mmul(p) //linear regression
        return sigmoid(y)
    }

    /**
     * Logistic function.
     *
     * Computes a number between 0 and 1 for each element.
     * Note that ND4J comes with its own sigmoid function.
     *
     * @param y input values
     * @return probabilities
     */
//    private fun sigmoid(y: INDArray): INDArray {
//        var y = y
//        y = y.mul(-1.0)
//        y = exp(y, false)
//        y = y.add(1.0)
//        y = y.rdiv(1.0)
//        return y
//    }

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

fun myp(): Pair<INDArray, INDArray> {
    val rand = Random()
    val myDist = listOf(6.0, 3.0, 1.0, 59.0, 10.0, 20.0, 30.0, 100.0, 9.0, 10.0).normalize()
    val stuff = (0..9).map { myDist.shuffled().toNDArray() }
    val target = myDist.toNDArray()
    val yay = target.dup()
    val others =  stuff
    val perturbs = perturb(target, 10)
    val results = applyFeatures(perturbs, others)
    return target to results
}

fun main(args: Array<String>) {
    val logit = LogitThingy()
//    val (targets, features) = logit.makeStuff()
    val (targets, features) = myp()
    println(features)

//    println(targets)
//    val result = targets.transpose()
//    println(features.transpose())
//    println(features)
//    val optimal = logit.training(features, (0 until 7).map { 1 / 7.0 }.toNDArray())
//    println(optimal.mmul(features))
}


