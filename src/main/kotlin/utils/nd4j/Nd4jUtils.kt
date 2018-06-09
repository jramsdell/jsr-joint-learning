package utils.nd4j
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j.*
import org.nd4j.linalg.ops.transforms.Transforms


fun Iterable<Double>.toNDArray(shape: IntArray? = null): INDArray {
    val arr = toList().toDoubleArray()
    return shape?.run { create(shape, arr) } ?: create(arr)
}

fun Iterable<Double>.toDuplicatedNDArray(nDuplicates: Int): INDArray {
    val arr = toNDArray()
    val mat = zeros(nDuplicates, arr.columns())
    return mat.addRowVector(arr)
}

fun Iterable<Iterable<Double>>.toNDArray(): INDArray {
    return create(map { it.toList().toDoubleArray() }.toTypedArray())
}

fun Iterable<INDArray>.combineNDArrays(): INDArray {
    val converted = map { arr -> arr.toDoubleVector() }.toTypedArray()
    return create(converted)
}

fun INDArray.normalizeRows(): INDArray {
    val rowSum = sum(1)
    return divColumnVector(rowSum)
}

fun INDArray.normalizeColumns(): INDArray {
    val rowSum = sum(0)
    return divRowVector(rowSum)
}


// Adding Transformation excentions

fun INDArray.abs(): INDArray = Transforms.abs(this)
fun INDArray.pow(power: Number): INDArray = Transforms.pow(this, power)
fun INDArray.sigmoid(): INDArray = Transforms.sigmoid(this)
fun INDArray.acos(): INDArray = Transforms.acos(this)
fun INDArray.exp(): INDArray = Transforms.exp(this)
fun INDArray.sqrt(): INDArray = Transforms.sqrt(this)
fun INDArray.log(base: Double?): INDArray =
        if (base != null) Transforms.log(this, base)
        else Transforms.log(this)

fun INDArray.softmax(): INDArray = Transforms.softmax(this)
fun INDArray.sign(): INDArray = Transforms.sign(this)
fun INDArray.normalizeZeroMeanAndUnitVariance(): INDArray = Transforms.normalizeZeroMeanAndUnitVariance(this)

fun INDArray.cosineSim(other: INDArray): Double = Transforms.cosineSim(this, other)
fun INDArray.euclideanDistance(other: INDArray): Double = Transforms.euclideanDistance(this, other)

fun INDArray.allCosineSimilarities(other: INDArray): INDArray = Transforms.allCosineSimilarities(this, other)
fun INDArray.allEuclideanDistances(other: INDArray): INDArray = Transforms.allEuclideanDistances(this, other)
fun INDArray.allManhattanDistances(other: INDArray): INDArray = Transforms.allManhattanDistances(this, other)



fun main(args: Array<String>) {
}