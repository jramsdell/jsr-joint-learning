package learning

import lucene.RanklibReader
import org.nd4j.linalg.api.ndarray.INDArray
import utils.misc.toArrayList
import utils.nd4j.mulRowV
import utils.nd4j.toNDArray


data class L2RModel(val features: INDArray, val relevances: Map<Int, Double>)

class MAPCalculator(val models: List<L2RModel>) {
    val nFeatures = models.first().features.columns()

//    val originWeights = listOf(
//            0.19968149528823698 ,0.05903326103422949 ,0.178261511590582 ,-0.04336229243252232 ,0.03708468830093088 ,-0.10529136821895785 ,0.05372094550495852 ,-0.02410382146621995 ,0.021820244558321922 ,-0.04788504115346008 ,-0.022841738566369278 ,0.11494571282470924 ,-0.015968908672345027 ,0.05417872582983464 ,0.021820244558321922
//
//    ).toNDArray()

    val originWeights = (0 until nFeatures).map { 1.0 }.toNDArray()


    fun getAP(model: L2RModel, weights: INDArray): Double {
        val results = (model.features mulRowV weights).sum(1).toDoubleVector()
        val sortedResults = results
            .asSequence()
            .mapIndexed { index, score -> index to score }
            .sortedByDescending { it.second }
            .toList()

        var apScore = 0.0
        var totalRight = 0.0

        sortedResults.forEachIndexed  { rank, (docIndex, _) ->
            val rel = model.relevances[docIndex]!!
            totalRight += rel
            if (rel > 0.0) {
                apScore += totalRight / (rank + 1).toDouble()
            }

        }

        return apScore / Math.max(1.0, totalRight)
    }

    fun getMAP(weights: INDArray) = models.map { model -> getAP(model, weights) }.average()



}

fun main(args: Array<String>) {
    val models = RanklibReader("ony_paragraph.txt")
        .createVectors2()

    val calculator = MAPCalculator(models)
    println(calculator.getMAP(calculator.originWeights))

}