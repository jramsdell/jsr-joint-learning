package utils.nd4j
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j.*
import org.nd4j.linalg.ops.transforms.Transforms


fun Iterable<Double>.toNDArray(vararg shape: Int): INDArray {
    val arr = toList().toDoubleArray()
    return if (shape.isNotEmpty())  create(arr, shape) else create(arr)
}

fun Iterable<Double>.toDuplicatedNDArray(nDuplicates: Int): INDArray {
    val arr = toNDArray()
    val mat = zeros(nDuplicates, arr.columns())
    return mat.addRowVector(arr)
}

fun Iterable<Iterable<Double>>.toNDArray(): INDArray {
    return create(map { it.toList().toDoubleArray() }.toTypedArray())
}

fun Array<DoubleArray>.toNDArray(): INDArray {
    return create(this)
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


fun vectorOf(vararg values: Double) = create(values)

// Adding Transformation excentions

fun INDArray.abs(): INDArray = Transforms.abs(this)
fun INDArray.pow(power: Number): INDArray = Transforms.pow(this, power)

fun INDArray.sigmoid(): INDArray = Transforms.sigmoid(this)
fun INDArray.relu(): INDArray = Transforms.relu(this)
fun INDArray.leakyRelu(): INDArray = Transforms.leakyRelu(this)
fun INDArray.elu(): INDArray = Transforms.elu(this)
fun INDArray.softPlus(): INDArray = Transforms.softPlus(this)
fun INDArray.softMax(): INDArray = Transforms.softmax(this)
fun INDArray.sign(): INDArray = Transforms.sign(this)

fun INDArray.acos(): INDArray = Transforms.acos(this)
fun INDArray.exp(): INDArray = Transforms.exp(this)
fun INDArray.expm1(): INDArray = Transforms.expm1(this, true)
fun INDArray.sqrt(): INDArray = Transforms.sqrt(this)
fun INDArray.log(base: Double? = null): INDArray =
        if (base != null) Transforms.log(this, base)
        else Transforms.log(this)

fun INDArray.log1p(): INDArray = Transforms.log1p(this, true)

fun INDArray.normalizeZeroMeanAndUnitVariance(): INDArray = Transforms.normalizeZeroMeanAndUnitVariance(this)

fun INDArray.cosineSim(other: INDArray): Double = Transforms.cosineSim(this, other)
fun INDArray.cosineDistance(other: INDArray): Double = Transforms.cosineDistance(this, other)
fun INDArray.euclideanDistance(other: INDArray): Double = Transforms.euclideanDistance(this, other)
fun INDArray.manhattanDistance(other: INDArray): Double = Transforms.manhattanDistance(this, other)

//fun INDArray.kld(other: INDArray): INDArray = this.dup().div(other).mul(this).sum(1)
fun INDArray.kld(other: INDArray): INDArray = this.div(other).log().mul(this).sum(1)
fun INDArray.kldSymmetric(other: INDArray): INDArray = this.div(other).log().mul(this - other).sum(1)
//fun INDArray.kldSymmetric(other: INDArray): INDArray = (this.kld(other) + other.kld(this))/2.0
fun INDArray.kldRow(other: INDArray): INDArray = this.divRowVector(other).log().mul(this).sum(1)
fun INDArray.invertDistribution(): INDArray = onesLike(this).div(this)
fun INDArray.invertDistribution2(): INDArray {
    val o = onesLike(this).div(this)
    val total = o.sumNumber().toDouble()
    return o / total

}
fun INDArray.varianceRows(): INDArray {
    val total = this.sum(0).div(this.rows().toDouble())
    return this.subRowVector(total).pow(2.0).sum(0).sqrt()
}

//fun INDArray.variance(): INDArray {
//    val total = this.sumNumber()
//    return this.subRowVector(total).pow(2.0).sqrt()
//}

fun INDArray.allCosineSimilarities(other: INDArray): INDArray = Transforms.allCosineSimilarities(this, other)
fun INDArray.allEuclideanDistances(other: INDArray): INDArray = Transforms.allEuclideanDistances(this, other)
fun INDArray.allManhattanDistances(other: INDArray): INDArray = Transforms.allManhattanDistances(this, other)


// Override operators

operator fun INDArray.plus(other: INDArray): INDArray = this.add(other)
operator fun INDArray.plus(other: Number): INDArray = this.add(other)
operator fun INDArray.plusAssign(other: INDArray) { this.addi(other) }
operator fun INDArray.plusAssign(other: Number) { this.addi(other) }
operator fun INDArray.minus(other: INDArray): INDArray = this.sub(other)
operator fun INDArray.minus(other: Number): INDArray = this.sub(other)
operator fun INDArray.minusAssign(other: INDArray) { this.subi(other) }
operator fun INDArray.minusAssign(other: Number) { this.subi(other) }
operator fun INDArray.div(other: INDArray): INDArray = this.div(other)
operator fun INDArray.div(other: Number): INDArray = this.div(other)
operator fun INDArray.divAssign(other: INDArray) { this.divi(other) }
operator fun INDArray.divAssign(other: Number) { this.divi(other) }
operator fun INDArray.times(other: INDArray): INDArray = this.mul(other)
operator fun INDArray.times(other: Number): INDArray = this.mul(other)
operator fun INDArray.timesAssign(other: INDArray) { this.muli(other) }
operator fun INDArray.timesAssign(other: Number) { this.muli(other) }


// Infixes

infix fun INDArray.addColV(other: INDArray) = this.addColumnVector(other)
infix fun INDArray.addRowV(other: INDArray) = this.addRowVector(other)
infix fun INDArray.mulRowV(other: INDArray) = this.mulRowVector(other)
infix fun INDArray.mulColV(other: INDArray) = this.mulColumnVector(other)
infix fun INDArray.divRowV(other: INDArray) = this.divRowVector(other)
infix fun INDArray.divColV(other: INDArray) = this.divColumnVector(other)
infix fun INDArray.subRowV(other: INDArray) = this.subRowVector(other)
infix fun INDArray.subColV(other: INDArray) = this.subColumnVector(other)
infix fun INDArray.distEuc(other: INDArray) = this.euclideanDistance(other)
infix fun INDArray.distMan(other: INDArray) = this.manhattanDistance(other)
infix fun INDArray.distCos(other: INDArray) = this.cosineDistance(other)
infix fun INDArray.simCos(other: INDArray) = this.cosineSim(other)
infix fun INDArray.distKld(other: INDArray) = this.kld(other)
infix fun INDArray.mmul(other: INDArray) = this.mmul(other)
infix fun INDArray.mmulT(other: INDArray) = this.mmul(other.transpose())



fun main(args: Array<String>) {
    val base = (0 .. 5).map { 1.0 }.toNDArray()
    val base2 = base.dup()
    base.addi(base)
}