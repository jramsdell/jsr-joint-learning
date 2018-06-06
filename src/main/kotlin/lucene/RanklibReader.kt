package lucene

import koma.create
import learning.perturb
import java.io.File
import koma.extensions.*
import koma.matrix.Matrix


data class RanklibFeature(val id: String, val value: Double) {
    companion object {
        fun createRanklibFeature(element: String) =
                element.split(":").let { (id, value) -> RanklibFeature(id, value.toDouble()) }

    }
}
data class RanklibTrainingExample(val relevant: Int, val features: List<RanklibFeature>)  {
    companion object {
        fun createTrainingExample(e: List<String>) =
                RanklibTrainingExample(
                        relevant = e[0].toInt(),
                        features  = e.subList(2, e.size - 1)
                            .map(RanklibFeature.Companion::createRanklibFeature))
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

    fun createVectors(): Pair<Matrix<Double>, List<Matrix<Double>>> {
        val fSize = trainingExamples.first().features.size
        val arrays: Array<ArrayList<Double>> = (0..fSize).map { ArrayList<Double>() }.toTypedArray()
        val targets = ArrayList<Double>()
        val padding = trainingExamples.size.toDouble()

        trainingExamples.forEach { example ->
            targets += example.relevant.toDouble() + padding
            example.features.forEachIndexed { index, ranklibFeature -> arrays[index].add(ranklibFeature.value) }
        }
//        val featureMatrices = arrays.map { array -> Matrix.newInstance(array.toDoubleArray()) }
        val featureMatrices = arrays.map { array -> create(array.toDoubleArray()) }
        val targetMatrix = create(targets.toDoubleArray())
        perturb(targetMatrix, 10)
        return targetMatrix to featureMatrices
    }

}



