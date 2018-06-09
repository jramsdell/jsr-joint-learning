package utils.nd4j
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j.*


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

fun INDArray.normalizeRows(): INDArray {
    val rowSum = sum(1)
    return divColumnVector(rowSum)
}

fun INDArray.normalizeColumns(): INDArray {
    val rowSum = sum(0)
    return divRowVector(rowSum)
}

fun main(args: Array<String>) {
}