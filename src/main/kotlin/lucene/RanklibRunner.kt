@file:JvmName("KotFeatureSelector")
package lucene

import org.apache.commons.math3.fraction.Fraction
import utils.parallel.pmap
import java.io.File


/**
 * Class: KotlinFeatureSelector
 * Desc: Given location to RankLib Jar, will run ranklib are perform subset selection and training for parameters.
 *       Both approaches are parallelized.
 */
class RanklibRunner(val rankLibLoc: String, var featuresLoc: String) {

    // Initialize Directories required for FeatureSelector if they don't already exist
    init {
        createFeatureSubsetsDirectory()
        createLogDirectory()
        createModelDirectory()
    }

    private fun createModelDirectory() = File("models/").apply { if (!exists()) mkdir() }
    private fun createLogDirectory() = File("ranklib_logs/").apply { if (!exists()) mkdir()}
    private fun createFeatureSubsetsDirectory() = File("ranklib_subsets/").apply { if (!exists()) mkdir() }

    /**
     * Function: retrieveWeights
     * Desc: Given location of model, retrieves weights associated with model.
     */
    private fun retrieveWeights(fileLoc: String) =
        File(fileLoc)
            .readLines()
            .last()
            .split(" ")
            .map { result ->
                result.split(":")
//                    .let { (id, weight) -> id.toInt() - 1 to weight.toDouble() }
                    .let { (id, weight) -> weight.toDouble() }
            }


    /**
     * Function writeFeatures
     * Desc: Writes a subset of features to a file (for use in selection)
     */
    private fun writeFeatures(featList: List<Int>, logLoc: String) =
        File("ranklib_subsets/${logLoc}.txt")
            .writeText(featList.joinToString("\n"))


    /**
     * Function: countFeatures
     * Desc: Returns number of features in ranklib feature file.
     */
    private fun countFeatures(): Int =
            File(featuresLoc)
                .bufferedReader()
                .readLine()
                .split(" ")
                .size - 2


    /**
     * Function: printLogResults
     * Desc: Reports results of running RankLib (Map/Fold results)
     */
    private fun printLogResults() {
        File("log_rank.log")
            .readLines()
            .filter { it.startsWith("Fold ") || it.startsWith("MAP on") }
            .onEach(::println)
    }


    /**
     * Function: extractLogResults
     * Desc: Given location of log, extracts MAP results (for training and test) and averages them.
     * @return Averaged results of each model
     */
    private fun extractLogResults(logLoc: String) =
        File("ranklib_logs/${logLoc}.log")
            .readLines()
            .filter { line -> line.startsWith("MAP on") }
            .map { line -> line.split(":")[1].toDouble() }
            .chunked(2)
            .mapIndexed { index, chunk -> index + 1 to chunk.average() }


    /**
     * Function: getBestModel
     * Desc: Given cross validation results, prints weights of the best model.
     */
    private fun getBestModel(logLoc: String): List<Double> =
            extractLogResults(logLoc)
                .maxBy { it.second }
                .let { result ->
//                    retrieveWeights("models/f${result!!.first}.default")
                    retrieveWeights("models/f5.default")
                }


    /**
     * Function: getAveragePerformance
     * Desc: Returns averaged MAP value across folds
     */
    private fun getAveragePerformance(logLoc: String) =
            extractLogResults(logLoc)
                .run { sumByDouble { it.second } / size }


    /**
     * Function: alphaSelection
     * Desc: Tries to select the best parameter based on MAP improvement when combining each feature
     *       independentantly with feature 1 (typically BM25 feature).
     */
    private fun alphaSelection() {
        val nFeatures = countFeatures()
        val features = (2 .. nFeatures)
        val results = features.map { feature ->
            val featLog = "feature_$feature"
            writeFeatures(listOf(1, feature), featLog)
            runRankLib(featLog, useFeatures = true, useKcv = false)
            val result = getAveragePerformance(featLog)
            feature to result
        }

        // Report results
        results.sortedBy { result -> result.first }
            .forEach { (feature, score) -> println("$feature: $score") }
    }


    /**
     * Function subsetSelection
     * Desc: Forward subset selection. Tries adding features while MAP can be improved.
     *       Prints the best model after features have been found.
     */
    private fun subsetSelection() {
        val nFeatures = countFeatures()
        var features = (2 .. nFeatures).toList()
        val curBestFeatures = arrayListOf(1)

        var baseline = tryAddingFeature(arrayListOf(), 1, "initial")
        println("Baseline: $baseline")

        for (i in 0 until nFeatures - 1) {
            var bestBaseline = 0.0
            var bestFeature = 0

            val results = features.pmap { feature ->
                val featLog = "feature_$feature"
                feature to tryAddingFeature(curBestFeatures, feature, featLog)
            }

            results.forEach { (feature, result) ->
                val improvement = result - baseline
                if (improvement > 0 && result > bestBaseline ) {
                    bestBaseline = result
                    bestFeature = feature
                }
            }


            if (bestFeature != 0 && (bestBaseline - baseline) > 0.001) {
                println("Adding feature $bestFeature to $curBestFeatures")
                println("Previous / New Baseline: $baseline / $bestBaseline")
                baseline = bestBaseline
                curBestFeatures += bestFeature
                features = features.filter { it != bestFeature }.toList()
            } else {
                // There was no feature that improved MAP, or the improvement was too small
                println("Can do no further improvements")
                break
            }
        }

        // Report results
        println("Training final model")
        writeFeatures(curBestFeatures, "final")
        runRankLib("final", useFeatures = true)
        println("Best Model:")
        getBestModel("final")
    }


    /**
     * Function: tryAddingFeature
     * Desc: Try adding a feature to current list of features and report RankLib results.
     */
    private fun tryAddingFeature(curBestFeature: ArrayList<Int>, featureToAdd: Int, logLoc: String): Double {
        val flist = curBestFeature.toList() + listOf(featureToAdd)
        writeFeatures(flist, logLoc)
        runRankLib(logLoc, useFeatures = true, useKcv = false)
        return getAveragePerformance(logLoc)
    }


    /**
     * Function: runRankLib
     * Desc: Runs RankLib (results are saved to separate logs so that it can be parallelized)
     * @param logLoc: Location of where to store log (and feature subset which uses same prefix)
     * @param useFeatures: Use only a subset of the available features.
     * @param useKcv: Use cross validation.
     */
     fun runRankLib(logLoc: String, useFeatures: Boolean = false, useKcv: Boolean = true): List<Double> {
        val commands = arrayListOf(
                "java", "-jar", rankLibLoc,
                "-train", featuresLoc,
                "-ranker", "4",
                "-metric2t", "map"
//                "-i", "20",
//                "-r", "10",
//                "-tvs", "0.3"
                )

        if (useFeatures) {
            commands.addAll(listOf("-feature", "ranklib_subsets/${logLoc}.txt"))
        }

        if (useKcv) {
            commands.addAll(listOf("-kcv", "5"))
            commands.addAll(listOf("-kcvmd", "models/"))
        } else {
            commands.addAll(listOf("-save", "model.txt"))
        }

        val log = File("ranklib_logs/${logLoc}.log")


        // Store results of RankLib in a log file for later analysis.
        val processBuilder = ProcessBuilder(commands)
        processBuilder.redirectOutput(log)
            .redirectErrorStream(false)
        val process = processBuilder.start()
        process.waitFor()
        println(getAveragePerformance(logLoc))
        return getBestModel(logLoc)
    }

    /**
     * Function runMethod
     * Desc: Runs the chosen method (alpha selection or subset selection)
     */
    fun runMethod(method: String) {
        when (method) {
            "alpha_selection" -> alphaSelection()
            "subset_selection" -> subsetSelection()
            else -> println("Unknown method!")
        }
    }

    fun optimizer() {
        var result = runRankLib("wee", useKcv = true)
        val reader = RanklibReader(featuresLoc)
        val fractionList = ArrayList<List<Fraction>>()
        var curResult = result
        val nIterations = 4

        (0 until nIterations).forEach { iteration ->
            fractionList.add(result.map { r -> Fraction(r) })
            val rescore = { index: Int, curScore: Double, baseScore: Double ->
//                val toExp = Math.exp(Math.pow(curResult[index], (iteration.toDouble() + 1.0)) * baseScore)
                val toExp = Math.exp(curResult[index] * baseScore)
                if (iteration == 0) toExp else toExp * curScore
            }
            reader.applyRescoringFunction(rescore)
            reader.writeRanklibFile("test.txt")
            featuresLoc = "test.txt"

            if (iteration != nIterations - 1) {
                result = runRankLib("wee", useKcv = true)
                curResult = curResult.zip(result).map { (f1, f2) -> f1 * f2 }
            }

        }

//        val new = fractionList
//            .reduce { acc, list -> acc.zip(list).map { (f1, f2) ->
//                //                    Fraction(f1.numerator * f2.denominator + f2.numerator, f1.denominator * f2.denominator)
//                Fraction(f1.numerator * f2.denominator + f2.numerator, f1.denominator * f2.denominator)
//            }  }

        reader.applyRescoringFunction { index, curScore, baseScore ->
            var tensor = 1.0 to 0.0
            fractionList.forEach { fractionCollection ->
                val fraction = fractionCollection[index]
                tensor = tensor.first / fraction.toDouble() to tensor.second / fraction.toDouble() + baseScore
            }


//            val res = Math.exp(new[index].toDouble() * baseScore)
//            println(tensor)
            println(tensor)
            val res = Math.exp(tensor.second / (tensor.first - 1.0))
//            val res = tensor.second / (tensor.first - 1.0)
            res
        }
        reader.printSums()

    }

}

fun main(args: Array<String>) {
//    val runner = RanklibRunner("RankLib-2.1-patched.jar", "ranklib_features.txt")
//    runner.runMethod("alpha_selection")

    val frac = 0.125
}