package learning

import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j.*
import org.nd4j.linalg.ops.transforms.Transforms.*
import utils.nd4j.*
import utils.stats.normalize


fun perturb(arr: INDArray, nSamples: Int = 10): INDArray {

    val mat = randn(nSamples, arr.columns(), 382239484)
//        .abs()
//        .normalizeRows()

        .mulRowVector(arr )
        .addRowVector(arr)
//        .mulRowVector(arr)

    return mat
//    println(pow(mat.subRowVector(arrFeat), 2.0).sum(1))

//    val m1 = Matrix.newInstance(arrayOf(row1, row1, row1))
//    val m2 = Matrix.newInstance(arrayOf(row2, row2)).transpose()
//    val m3 = m1.atbmm(m2)
//    println(m1)
//    println(m2)
//    println(m3)
//    yes.mulRowVector(intArrayOf(2,2).asIn)

}

fun applyFeatures(perturbations: INDArray, features: List<INDArray> ): Pair<INDArray, INDArray> {
//    val nFeatures = features.combineNDArrays()

    val result = features.map { feature ->
//        pow(perturbations.subRowVector(feature), 2.0).sum(1).toDoubleVector().toList()
        abs(perturbations.subRowVector(feature)).sum(1).toDoubleVector().toList()
//        sqrt(pow(perturbations.subRowVector(feature), 2.0)).sum(1).toDoubleVector().toList()
//        perturbations.kldRow(feature).toDoubleVector().toList().map { Math.abs(it) }
    }
    return result.take(result.size - 1).toNDArray() to result.takeLast(1).first().toNDArray()
//    return result.toNDArray() to result.toNDArray()
}



fun predict(features: INDArray, originals: INDArray, target: INDArray, perturbs: INDArray): INDArray {


    val (transformed, prediction) = getTransformed(features, originals, 3)
    println("To Uniform: ${onesLike(transformed).normalizeRows().kld(transformed)}")
    println("Mixture Euc: ${transformed.euclideanDistance(target)} / KLD: ${target.kld(transformed)}")
//    println(perturbs.subRowVector(transformed).pow(2.0).sum(1).sum(0))
//    println(sqrt(perturbs.subRowVector(transformed).pow(2.0)).sum(1).sum(0))
//    println(sqrt(perturbs.subRowVector(transformed).pow(2.0)).sum(1).sum(0))
    val varianceResult = sqrt(perturbs.subRowVector(transformed).pow(2.0)).sum(1).varianceRows().sumNumber()
    println("Mixture variance: $varianceResult")
    return prediction
}

fun getTransformed(features: INDArray, originals:INDArray, times: Int = 0): Pair<INDArray, INDArray> {
    val uniform = onesLike(features).normalizeRows()
    val score = uniform.kld(features).pow(1.0)
    val predictionN = score.normalizeColumns().sum(1).normalizeColumns()
    val prediction = onesLike(predictionN).div(predictionN).normalizeColumns()
    val transformedOriginals = originals.mulColumnVector(prediction).sum(0)
    println("Predictions: ${prediction.transpose()}")

    return transformedOriginals to prediction

}

fun makeStuff(): Pair<INDArray, List<INDArray>> {
    val f1 = listOf(10.0, 5.5, 3.4, 2.0, 10.0, 1.0, 10.0, 40.0, 2.0).normalize().toNDArray()
    val f2 = listOf(10.0, 1.5, 8.4, 0.2, 5.0, 9.0, 1.0, 2.0, 2.0).normalize().toNDArray()
    val f3 = f1.dup().mul(0.5).add(f2.dup().mul(0.5))
    val f4 = listOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0).normalize().toNDArray()
    val f5 = listOf(1.0, 5.5, 1.0, 80.0, 1.0, 1.0, 1.0, 2.0, 40.0).normalize().toNDArray()
//    val weights = listOf(0.8, 0.1, 0.1, 0.0)
//    val weights = listOf(0.8, 0.1, 0.1)
//    val weights = listOf(0.1, 0.8, 0.1)
//    val weights = listOf(0.0, 0.0, 0.5, 0.5)
//    val weights = listOf(200.0, 200.0, 0.0, 200.0, 0.0).normalize()
    val weights = listOf(300.0, 300.0, 0.0, 400.0, 0.0).normalize()
    println("WEIGHTS: $weights")

    val target = f1.dup().mul(weights[0])
        .add(f2.dup().mul(weights[1]))
        .add(f3.dup().mul(weights[2]))
        .add(f5.dup().mul(weights[3]))
//        .add(f4.dup().mul(weights[4]))

    return target to listOf(f1, f2, f3, f5, target)
}




fun main(args: Array<String>) {
    val (target, features) = makeStuff()
    val perturbations = perturb(target, 10000)
    val (results, targetPerturbed) = applyFeatures(perturbations,  features)
    predict(results, features.combineNDArrays(), target, perturbations)

    val guess = features.map { feature -> 1.0 / feature.euclideanDistance(target) }.normalize()
    val transformed = features.combineNDArrays().mulColumnVector(guess.toNDArray().transpose()).sum(0)
    println(guess)
    println("Guess Euc: ${transformed.euclideanDistance(target)} / KLD: ${target.kld(transformed)}")
    println(perturbations.subRowVector(transformed).pow(2.0).sqrt().sum(1).sum(0))
    val guessResult = perturbations.subRowVector(transformed).pow(2.0).sqrt().sum(1).varianceRows().sumNumber()
    println("Guess variance: $guessResult")
//    println(perturbations.kldRow(transformed).pow(2.0).sqrt().sum(1).sum(0))
//    println(perturbations.subRowVector(target).pow(2.0).sqrt().sum(1).sum(0))
//    println(perturbations.subRowVector(target).pow(2.0).sqrt().sum(1).sum(0))

    val varianceResult = perturbations.subRowVector(target).pow(2.0).sqrt().sum(1).varianceRows().sumNumber()
    println("Target variance: $varianceResult")


}

