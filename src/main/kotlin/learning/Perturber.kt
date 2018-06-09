package learning

import org.apache.commons.math3.distribution.NormalDistribution
import org.deeplearning4j.nn.conf.NeuralNetConfiguration
import org.deeplearning4j.nn.conf.layers.DenseLayer
import org.deeplearning4j.nn.weights.WeightInit
import utils.misc.sharedRand
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.cpu.nativecpu.NDArray
import org.nd4j.linalg.factory.Nd4j.*
import org.nd4j.linalg.learning.config.Nesterovs
import org.nd4j.linalg.ops.transforms.Transforms.*
import smile.regression.NeuralNetwork
import utils.nd4j.normalizeColumns
import utils.nd4j.normalizeRows
import utils.nd4j.toDuplicatedNDArray
import utils.nd4j.toNDArray
import utils.stats.normalize


fun perturb(arr: INDArray, nSamples: Int = 10): INDArray {

    val mat = randn(nSamples, arr.columns())
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
        pow(perturbations.subRowVector(feature), 2.0).sum(1).toDoubleVector().toList()
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


fun main(args: Array<String>) {
    val orig = listOf(0.1, 0.1, 0.3, 0.3, 0.2).toNDArray()
    val wee = perturb(orig)
    val one = listOf(0.5, 0.1, 0.1, 0.1, 0.2).toNDArray()
    val two = listOf(0.1, 0.5, 0.1, 0.2, 0.1).toNDArray()
    applyFeatures(wee,  listOf(orig, one, two))

}

