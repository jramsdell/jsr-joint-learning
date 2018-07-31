package lucene

import learning.L2RModel
import learning.LogitThingy
import learning.applyFeatures2
import learning.perturb
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j.ones
import utils.nd4j.combineNDArrays
import utils.nd4j.toNDArray
import utils.stats.defaultWhenNotFinite
import utils.stats.normalize
import java.io.File


data class RanklibFeature(val id: Int, var value: Double, var lastValue: Double, var base: Double) {
    var horizontalScore = value
    var verticalScore = value
    companion object {
        fun createRanklibFeature(element: String) =
                element.split(":").let { (id, value) ->
                    val v = value.toDouble()
                    RanklibFeature(id.toInt(), v, v, v) }
    }

    override fun toString(): String {
        return "$id:$value"
    }
}
data class RanklibTrainingExample(val relevant: Int, val features: ArrayList<RanklibFeature>, val qid: Int,
                                  val query: String, val pid: String)  {
    companion object {
        fun createTrainingExample(e: List<String>, query: String, pid: String) =
                RanklibTrainingExample(
                        relevant = e[0].toInt(),
                        qid = e[1].split(":")[1].toInt(),
                        features  = e.subList(2, e.size - 1)
                            .map(RanklibFeature.Companion::createRanklibFeature).toMutableList() as ArrayList<RanklibFeature>,
                        query = query,
                        pid = pid)
    }

    fun applyRescoringFunction(f: (Int, Double, Double) -> Double) {
        features.forEachIndexed { index, ranklibFeature ->
            ranklibFeature.lastValue = ranklibFeature.value
            ranklibFeature.value = f(index, ranklibFeature.value, ranklibFeature.base)  }
    }

    fun applyVerticalRescoringFunction(f: (Int, Double, Double) -> Double) {
        features.forEachIndexed { index, ranklibFeature ->
            ranklibFeature.verticalScore = f(index, ranklibFeature.verticalScore, ranklibFeature.base)  }
    }

    fun applyHorizontalRescoringFunction(f: (Int, Double, Double) -> Double) {
//        val total = getValueMultiple()
        val total = getHorizontalValueMultiple()
        features.forEachIndexed { index, ranklibFeature ->
//            ranklibFeature.lastValue = ranklibFeature.value
//            ranklibFeature.value = f(index, total, ranklibFeature.base)  }
            ranklibFeature.horizontalScore = f(index, total, ranklibFeature.base)  }
    }

    fun printSums() {
        val total = features.sumByDouble { feature -> feature.value }
        val totalLast = features.sumByDouble { feature -> feature.lastValue }
        val baseTotal = features.sumByDouble { feature -> feature.base }
        val valueMultiple = features.fold(1.0) { acc, ranklibFeature -> acc * ranklibFeature.value  }
        val prevValueMultiple = getValueTotalPrevMultiple()
        return println("$relevant qid:$qid\t$total\t$totalLast\t$baseTotal\t$valueMultiple\t$prevValueMultiple")
    }

    fun addNormal(normDist: Double) {
        features.add(RanklibFeature(features.size + 1, normDist, normDist, normDist))
    }

    fun getValueTotal() = features.sumByDouble { feature -> feature.value }
    fun getValueTotalPrev() = features.sumByDouble { feature -> feature.lastValue }
    fun getValueTotalPrevMultiple() = features.fold(1.0) { acc, ranklibFeature -> acc * ranklibFeature.lastValue  }
    fun getValueMultiple() = features.fold(1.0) { acc, ranklibFeature -> acc * ranklibFeature.value  }
    fun getHorizontalValueMultiple() = features.fold(1.0) { acc, ranklibFeature -> acc * ranklibFeature.horizontalScore  }
    fun getBaseTotal() = features.sumByDouble { feature -> feature.base }
    fun scoringFun() = getValueMultiple()

    fun normalize() {
        val total = getValueTotal()
        features.forEach { feature -> feature.value /= total }
    }

    fun getRunfileLine(index: Int): String {
        val total = scoringFun()
        return "$query Q0 $pid $index $total Query"
    }


    override fun toString(): String {
        return "$relevant qid:$qid " + features.joinToString(" ")
    }
}


class RanklibReader(fileLoc: String) {
    val trainingExamples = File(fileLoc).readLines()
        .map { line ->
            val elements = line.split("#")
            val featureScores = elements[0].split(" ")
            val queryId = elements[1]
            val paragraphId = elements[2]
            RanklibTrainingExample.createTrainingExample(featureScores, queryId, paragraphId)
//            line.split("#")
//                .first()
//                .split(" ")
//                .let { elements -> RanklibTrainingExample.createTrainingExample(elements) }
        }

    fun createVectors(): Pair<INDArray, List<INDArray>> {
        val fSize = trainingExamples.first().features.size
        val arrays: Array<ArrayList<Double>> = (0 until fSize).map { ArrayList<Double>() }.toTypedArray()
        val targets = ArrayList<Double>()
        val padding = trainingExamples.size.toDouble()

        trainingExamples.forEach { example ->
                targets += example.relevant.toDouble()
                example.features.forEachIndexed { index, ranklibFeature -> arrays[index].add(ranklibFeature.value) }
        }
        val featureMatrices = arrays.take(arrays.size - 1).map { it.toList().toNDArray() }
        val targetMatrix = targets.toNDArray()

        return targetMatrix to featureMatrices
    }

    fun createVectors2(): List<L2RModel> {
        val results = trainingExamples.groupBy { it.qid }
            .filter { examples -> examples.value.any { it.relevant > 0 } }
            .map { examples ->
                val relevances = examples.value.map { it.relevant.toDouble() }
                val features = examples.value.map { it.features.map { it.value }.toNDArray() }.combineNDArrays()
                val relFeatures = examples.value
                    .filter { example -> example.relevant > 0 }
                    .map { it.features.map { it.value }.toNDArray() }.combineNDArrays()
                val irelFeatures = examples.value
                    .filter { example -> example.relevant == 0 }
                    .map { it.features.map { it.value }.toNDArray() }.combineNDArrays()
                L2RModel(
                        features = features,
                        relevances = relevances.mapIndexed { index, d -> index to d }.toMap(),
                        relFeatures = relFeatures,
                        nonRelFeatures = irelFeatures
                )

//                relevances to features
            }

//        val models = results.map { L2RModel(it.second, it.first.mapIndexed{ index, score -> index to score }.toMap()) }
        return results
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

    fun applyVerticalRescoringFunction(f: (Int, Double, Double) -> Double) =
            trainingExamples.forEach { example -> example.applyVerticalRescoringFunction(f) }

    fun applyHorizontalRescoringFunction(f: (Int, Double, Double) -> Double) =
            trainingExamples.forEach { example -> example.applyHorizontalRescoringFunction(f) }

    fun applyRowFunctor(f: (RanklibTrainingExample) -> Unit) =
            trainingExamples.forEach(f)

    fun applyElementFunctor(f: (RanklibFeature) -> Unit) =
            trainingExamples.forEach { example -> example.features.forEach(f) }

    fun applyColumnFunctor(f: (List<RanklibFeature>) -> Unit) {
        (0 until trainingExamples.first().features.size).forEach { fIndex ->
            groupByQid()
                .forEach { (qid, examples) ->
                    val column = examples.map { example -> example.features[fIndex] }
                    f(column)
                }
        }
    }

    fun addNormal() {
            groupByQid()
            .forEach { (qid, feats) ->
                val normDist = 1 / feats.size.toDouble()
                feats.forEach { feat -> feat.addNormal(normDist) }

            }
    }

    fun addInverses() =
            groupByQid()
                .forEach { (qid, feats) ->
                    val normDist = 1 / feats.size.toDouble()
                    val nFeats = feats[0].features.size
                    (0 until nFeats).forEach { fId ->
                        val inverseTotal = feats.sumByDouble { 1.0 / it.features[fId].value }
                        feats.forEach { feat ->
                            val newVal = ((1.0 / feat.features[fId].value) / inverseTotal).defaultWhenNotFinite(0.0)
                            feat.features.add(RanklibFeature(nFeats + 1 + fId, newVal, newVal, newVal))

                             }
                    }

                }

    private fun groupByQid() =
            trainingExamples
                .groupBy { it.qid }
                .toList()

    fun printSums() {
        trainingExamples
            .sortedWith(compareBy({it.qid}, {it.scoringFun()}))
            .reversed()
            .forEach { example -> example.printSums() }
    }

    fun normalize() {
        trainingExamples
            .groupBy { it.qid }
            .toList()
            .forEach { (qid, feats) ->
                val nFeats = feats[0].features.size
                (0 until nFeats).forEach { fId ->
                    val total = feats.sumByDouble { it.features[fId].value }
                    feats.forEach { feat -> feat.features[fId].value /= total }
                }
            }
    }

    fun setBase() {
        trainingExamples.forEach { example -> example.features.forEach { feature -> feature.base = feature.value  } }
    }

    fun normalizeRow() {
        trainingExamples
            .forEach { e -> e.normalize() }
    }

    fun printRunfile() {
        trainingExamples
//            .sortedWith(compareBy({it.qid}, {it.getValueMultiple()}))
            .sortedWith(compareBy({it.qid}, {it.scoringFun()}))
            .reversed()
            .groupBy { it.qid }
            .flatMap { grouping ->
                grouping.value.mapIndexed { index, example ->
                    example.getRunfileLine(index + 1)
                } }
            .joinToString("\n")
            .let { result ->  File("query_results.run").bufferedWriter().apply{ write(result) }.close() }
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



