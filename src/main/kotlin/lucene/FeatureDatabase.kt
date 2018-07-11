package lucene

import features.shared.SharedFeature
import lucene.containers.ExtractedFeature
import lucene.containers.FeatureEnum
import lucene.containers.QueryContainer
import utils.misc.toArrayList
import java.io.File
import java.util.concurrent.ConcurrentHashMap



private class LineFeature(val query: String, val qid: Int, val id: String, val score: Double) {

    companion object {
        fun createLineFeature(line: String): LineFeature {
            val elements = line.split("\t")
            return LineFeature(
                    query = elements[0],
                    qid = elements[1].toInt(),
                    id = elements[2],
                    score = elements[3].toDouble())
        }
    }
}

data class FeatureSet( val features: Map<String, SharedFeature> )


class FeatureDatabase2() {
    val featuresPath = "features/"
    val featuresDir = File(featuresPath).apply { if(!exists()) mkdirs() }
    private val featureStore = ConcurrentHashMap<String, ConcurrentHashMap<String, SharedFeature>>()

    fun storeFeature(query: String, featureType: FeatureEnum, sf: SharedFeature) {
        featureStore.computeIfAbsent(featureType.text, { ConcurrentHashMap() }).put(query, sf)
    }



    fun writeFeatures(queryContainers: List<QueryContainer>) {
        queryContainers.flatMap { qc -> qc.retrieveFeatures().toList() }
            .groupBy { it.name }
            .values.forEachIndexed(this::writeFeatureResults)
    }

//    private fun createFeatureSet(lines: List<LineFeature>): FeatureSet {
//        val features =
//                lines
//                    .groupBy { line -> line.query }
//                    .mapValues { (_, featuresByQuery) ->
//                        featuresByQuery.map { feature -> feature.id to feature.score }.toMap() }
//                    .toMap()
//
//        return FeatureSet(features)
//    }

    fun writeFeatureResults(qid: Int, featureList: List<ExtractedFeature>) {
        val name = featureList.first().name
        val featuresFile = File(featuresPath + name + ".txt").bufferedWriter()

        val formattedFeatures = ArrayList<String>()

        featureList.forEach { feature ->
            val q = feature.query
            formattedFeatures.addAll(feature.paragraphs.map { (id, score) -> "$q\t$qid\t$id\t$score\tparagraph" })
            formattedFeatures.addAll(feature.entities.map { (id, score) -> "$q\t$qid\t$id\t$score\tentity" })
        }

        featuresFile.write(formattedFeatures.joinToString("\n"))
        featuresFile.close()
    }

    fun writeSharedFeatures() {
        featureStore.forEach { (fName, features) ->
            val featuresFile = File(featuresPath + fName + ".txt").bufferedWriter()

            val result = features.map { (query, feature) ->
                val fPar = feature.paragraphScores.joinToString(" ")
                val fEnt = feature.entityScores.joinToString(" ")
                val fSec = feature.sectionScores.joinToString(" ")
                "$query\t$fPar\t$fEnt\t$fSec" }
                .joinToString("\n")

            featuresFile.write(result)
            featuresFile.close()
        }


    }

    fun getFeature(featureEnum: FeatureEnum): Map<String, SharedFeature> {
        val fPath = featuresPath + featureEnum.text + ".txt"
        val sharedFeatures = getSharedFeatures(filename = fPath)
        return sharedFeatures
    }

//    private fun getFeatures(featureEnums: List<FeatureEnum>) {
//        val features = featureEnums.flatMap(this::getFeature)
//    }

    private fun getSharedFeatures(filename: String) =
            File(filename).bufferedReader()
                .readLines()
                .map { line ->
                    val elements = line.split("\t")
                    val query = elements[0]
                    val features = elements
                        .drop(1)
                        .map { it.split(" ").map(String::toDouble) }

                    val sf = SharedFeature(
                            paragraphScores = features[0].toArrayList(),
                            entityScores = features[1].toArrayList(),
                            sectionScores = features[2].toArrayList())
                    query to sf
                }.toMap()


}