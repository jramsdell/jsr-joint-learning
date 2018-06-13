package learning

import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j.*
import org.nd4j.linalg.ops.transforms.Transforms.*
import utils.nd4j.*
import utils.stats.normalize


fun perturb(arr: INDArray, nSamples: Int = 10, intensity: Double = 0.1): INDArray {

    var mat = randn(nSamples, arr.columns(), 3808)
        .mul(0.05)
        .pow(2.0)
        .addRowVector(arr)

//    mat = mat
//        .pow(2.0).mul(mat.sign())
//        .mulRowVector(arr)
//        .addRowVector(arr)
//        .normalizeRows()
    return mat
}

fun applyFeatures(perturbations: INDArray, features: List<INDArray> ): Pair<INDArray, INDArray> {
    val result = features.map { feature ->
        pow(perturbations.subRowVector(feature), 2.0).sum(1).toDoubleVector().toList()
//        pow(perturbations.subRowVector(feature), 2.0).sum(1).sqrt().toDoubleVector().toList()
//        perturbations.subRowVector(feature).abs().sum(1).toDoubleVector().toList().normalize()
//        perturbations.subRowVector(feature).sum(1).toDoubleVector().toList()
//        abs(perturbations.mulRowVector(feature)).sum(1).toDoubleVector().toList()
//        perturbations.divRowVector(feature).log().sum(1).toDoubleVector().toList()
    }
    return result.take(result.size - 1).toNDArray() to result.takeLast(1).first().toNDArray()
}




fun makeStuff(): Pair<INDArray, List<INDArray>> {
    val f1 = listOf(10.0, 5.5, 3.4, 2.0, 10.0, 1.0, 10.0, 40.0, 2.0, 30.0, 30.0).normalize().toNDArray()
    val f2 = listOf(10.0, 1.5, 8.4, 0.2, 5.0, 9.0, 1.0, 2.0, 2.0, 10.0, 1.0).normalize().toNDArray()
    val f3 = listOf(500.0, 40.0, 10.0, 10.0, 10.0, 1.0, 40.0, 4.0, 4.0, 4.0, 20.0).normalize().toNDArray()
    val f4 = listOf(1.0, 5.5, 1.0, 80.0, 1.0, 1.0, 1.0, 2.0, 40.0, 1.0, 1.0).normalize().toNDArray()
//    val f4 = listOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0).normalize().toNDArray()
    val f5 = listOf(0.01, 0.03, 0.06, 0.1, 0.3, 0.15, 0.1, 0.05, 0.02, 0.01, 0.01).normalize().toNDArray()
//    val weights = listOf(100.0, 200.0, 0.0, 25.0, 100.0).normalize()
//    val weights = listOf(200.0, 200.0, 0.0, 25.0, 100.0).normalize()
//    val weights = listOf(20.0, 5.0, 0.0, 0.0, 100.0).normalize()
//    val weights = listOf(1000.0, 0.0, 100.0, 1000.0, 100.0).normalize()
//    val weights = listOf(1000.0, 0.0, 0.0, 100.0, 100.0).normalize()
    val weights = listOf(1000.0, 0.0, 0.0, 1000.0, 100.0).normalize()
    println("WEIGHTS: $weights")

    val target = f1.dup().mul(weights[0])
        .add(f2.dup().mul(weights[1]))
        .add(f3.dup().mul(weights[2]))
        .add(f4.dup().mul(weights[3]))
        .add(f5.dup().mul(weights[4]))

    return target to listOf(f1, f2, f3, f4, f5, target)
}

fun main(args: Array<String>) {
    val myStuff = vectorOf(0.2, 0.2, 0.3, 0.1, 0.1, 0.1)
    val mat = randn(1, 6, 384908192)
        .mul(0.00)
//        .pow(2.0)
        .addRowVector(myStuff)
//        .normalizeRows()
//        .sum(0)
        .pow(2.0)
//        .sqrt()
        .normalizeRows()
        .sum(0)
        .sqrt()
//        .div(1000000)
        .div(1)
    println(mat)


}
