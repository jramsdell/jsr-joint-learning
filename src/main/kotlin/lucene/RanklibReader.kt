package lucene

import learning.LogitThingy
import learning.applyFeatures2
import learning.perturb
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j.ones
import utils.nd4j.combineNDArrays
import utils.nd4j.toNDArray
import utils.stats.normalize
import java.io.File


data class RanklibFeature(val id: String, var value: Double, var lastValue: Double, val base: Double) {
    companion object {
        fun createRanklibFeature(element: String) =
                element.split(":").let { (id, value) ->
                    val v = value.toDouble()
                    RanklibFeature(id, v, v, v) }
    }

    override fun toString(): String {
        return "$id:$value"
    }
}
data class RanklibTrainingExample(val relevant: Int, val features: List<RanklibFeature>, val qid: Int)  {
    companion object {
        fun createTrainingExample(e: List<String>) =
                RanklibTrainingExample(
                        relevant = e[0].toInt(),
                        qid = e[1].split(":")[1].toInt(),
                        features  = e.subList(2, e.size - 1)
                            .map(RanklibFeature.Companion::createRanklibFeature))
    }

    fun applyRescoringFunction(f: (Int, Double, Double) -> Double) {
        features.forEachIndexed { index, ranklibFeature ->
            ranklibFeature.lastValue = ranklibFeature.value
            ranklibFeature.value = f(index, ranklibFeature.value, ranklibFeature.base)  }
    }

    fun printSums() {
        val total = features.sumByDouble { feature -> feature.value }
        val totalLast = features.sumByDouble { feature -> feature.lastValue }
        val baseTotal = features.sumByDouble { feature -> feature.base }
        val valueMultiple = features.fold(1.0) { acc, ranklibFeature -> acc * ranklibFeature.value  }
        return println("$relevant qid:$qid\t$total\t$totalLast\t$baseTotal\t$valueMultiple")
    }

    fun getValueTotal() = features.sumByDouble { feature -> feature.value }
    fun getBaseTotal() = features.sumByDouble { feature -> feature.base }


    override fun toString(): String {
        return "$relevant qid:$qid " + features.joinToString(" ")
    }
}


class RanklibReader(fileLoc: String) {
    val trainingExamples = File(fileLoc).readLines()
        .map { line ->
            line.split("#")
                .first()
                .split(" ")
                .let { elements -> RanklibTrainingExample.createTrainingExample(elements) }
        }

    fun createVectors(): Pair<INDArray, List<INDArray>> {
        val fSize = trainingExamples.first().features.size
        val arrays: Array<ArrayList<Double>> = (0 until fSize).map { ArrayList<Double>() }.toTypedArray()
        val targets = ArrayList<Double>()
        val padding = trainingExamples.size.toDouble()

        trainingExamples.forEach { example ->
//            if (example.relevant == 1) {
                targets += example.relevant.toDouble()
                example.features.forEachIndexed { index, ranklibFeature -> arrays[index].add(ranklibFeature.value) }
//            }
        }
//        val featureMatrices = arrays.map { array -> Matrix.newInstance(array.toDoubleArray()) }
        val featureMatrices = arrays.take(arrays.size - 1).map { it.toList().toNDArray() }
//        val featureMatrices = arrays.map { it.toNDArray() }
        val targetMatrix = targets.normalize().toNDArray()

        return targetMatrix to featureMatrices
    }

    fun getRanklibFile() =
        trainingExamples.map(RanklibTrainingExample::toString)
            .joinToString("\n")

    fun writeRanklibFile(loc: String) =
            File(loc).bufferedWriter()
                .apply { write(getRanklibFile()) }
                .close()

    fun applyRescoringFunction(f: (Int, Double, Double) -> Double) =
            trainingExamples.forEach { example -> example.applyRescoringFunction(f) }


    fun printSums() {
        trainingExamples
            .sortedWith(compareBy({it.qid}, {it.getValueTotal()}))
            .reversed()
            .forEach { example -> example.printSums() }
    }
}

fun main(args: Array<String>) {
    val reader = RanklibReader("data/ranklib_results.txt")
//    reader.printRanklib()

//    val (tMatrix, fMatrix) = reader.createVectors()
//    val perturbations = perturb(tMatrix, 200)
//    val results = applyFeatures2(perturbations,  fMatrix)
//    val uniform = ones(results.columns()) / results.columns().toDouble()
//    val logit = LogitThingy(perturbations, fMatrix.combineNDArrays(), tMatrix)
//    val optimal = logit.training(results.transpose(), uniform.transpose())
//    println(tMatrix)
}



