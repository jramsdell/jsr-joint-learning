package learning

import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j.*
import org.nd4j.linalg.ops.transforms.Transforms.*
import utils.nd4j.*
import utils.stats.normalize


fun perturb(arr: INDArray, nSamples: Int = 10): INDArray {

    val mat = randn(nSamples, arr.columns(), 10)
        .mul(0.005)
        .addRowVector(arr)
        .normalizeRows()

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

fun applyFeatures(perturbations: INDArray, features: List<INDArray> ): INDArray {
    return features.map { feature ->
//        pow(perturbations.subRowVector(feature), 2.0).sum(1).toDoubleVector().toList()
        abs(perturbations.subRowVector(feature)).sum(1).toDoubleVector().toList()
    }.toNDArray()
}

//fun applyFeatures2(perturbations: INDArray, features: INDArray ) {
//    pow(perturbations.sub(features))
//    features.forEach { feature ->
//        println(pow(perturbations.subRowVector(feature), 2.0).sum(1))
//    }
//}

fun buildThingy() {
//    NeuralNetConfiguration.Builder()
//        .weightInit(WeightInit.XAVIER)
//        .updater(Nesterovs(0.01, 0.9))
//        .list()
//        .layer(0, DenseLayer.Builder().nIn(10).nOut(10)
//            .activation(Activation.)
}

fun predict(features: INDArray, originals: INDArray, target: INDArray) {
//    val array = features.combineNDArrays()

    val antitone = abs(onesLike(features).div(features)).normalizeRows()
    val diff = abs(features.dup().sub(antitone))
    val score = onesLike(features).div(diff)
//    println(score.normalizeRows().normalizeColumns().sum(1).normalizeColumns())
    val prediction = score.normalizeColumns().sum(1).normalizeColumns()
//    val transformed = originals.mulColumnVector(prediction).sum(0)
    val transformed = originals.mulColumnVector(prediction).sum(0)
    println(prediction)
    println(transformed.euclideanDistance(target))
//    println(prediction)
//    println(features)


//    features.forEach { feature ->
//        val total = feature.sumNumber()
//        feature.div(total)
//    }
//
//    val antitone = features.map { feature ->
//        val inverse = ones(feature.columns()).div(feature)
//        val total = inverse.sumNumber()
//        inverse.div(total)
//    }
//
//    features.zip(antitone).forEach { (f1, f2) ->
//        println("F: $f1\nR: $f2")
//    }
//
//    val diffs = features.zip(antitone).map { (f1, f2) ->
//        val diff = abs(f1.dup().sub(f2))
//        val result = ones(f1.columns()).div(diff)
//        println(result)
//    }


}

fun makeStuff(): Pair<INDArray, List<INDArray>> {
    val f1 = listOf(10.0, 5.5, 3.4, 2.0, 10.0, 1.0, 10.0).normalize().toNDArray()
    val f2 = listOf(10.0, 1.5, 8.4, 0.2, 5.0, 9.0, 1.0).normalize().toNDArray()
    val f3 = f1.dup().mul(0.5).add(f2.dup().mul(0.5))
//    val f3 = listOf(9.0, 2.5, 6.4, 0.2, 10.0, 1.0, 15.0).normalize().toNDArray()
//    val weights = listOf(0.2, 0.3, 0.5)
    val weights = listOf(0.5, 0.5, 0.0)

    val target = f1.dup().mul(weights[0])
        .add(f2.dup().mul(weights[1]))
        .add(f3.dup().mul(weights[2]))

    return target to listOf(f1, f2, f3)
}




fun main(args: Array<String>) {
    val (target, features) = makeStuff()
    val perturbations = perturb(target, 100)
    val results = applyFeatures(perturbations,  features)
    predict(results, features.combineNDArrays(), target)

}

