package lucene

import lucene.containers.ExtractedFeature
import lucene.containers.FeatureEnum
import lucene.containers.QueryContainer
import org.mapdb.BTreeMap
import org.mapdb.DBMaker
import org.mapdb.Serializer
import org.mapdb.serializer.SerializerArrayTuple
import java.io.File
import java.util.concurrent.ConcurrentHashMap


class FeatureDatabase {
    val db = DBMaker
        .fileDB("features.db")
        .fileMmapEnable()
        .closeOnJvmShutdown()
        .concurrencyScale(60)
        .make()


    val memoizedFeatureMaps = ConcurrentHashMap<String, BTreeMap<Array<Any>, Double>>()

    fun getFeatureMap(name: String) =
                db.treeMap(name)
                    .keySerializer(SerializerArrayTuple(Serializer.STRING, Serializer.STRING))
                    .valueSerializer(Serializer.DOUBLE)
                    .createOrOpen()

//    fun writeFeatures(qc: QueryContainer) {
//        qc.retrieveFeatures().forEach { (name, paragraphScores, entityScores) ->
//            val paragraphMap = getFeatureMap("${name}_paragraph")
//            val entityMap = getFeatureMap("${name}_entity")
//
//            paragraphScores.forEach { (id, score) -> paragraphMap[arrayOf(qc.query,  id)] = score }
//            entityScores.forEach { (id, score) -> entityMap[arrayOf(qc.query, id)] = score }
//        }
//    }

    fun aggregateFeature(featureMap: BTreeMap<Array<Any>, Double>, query: String) =
        featureMap.prefixSubMap(arrayOf(query)).entries.map { entry ->
            val id = entry.key[1] as String
            id to entry.value
        }

//    fun retrieveFeature(name: String, query: String): ExtractedFeature {
//        val paragraphMap = getFeatureMap("${name}_paragraph")
//        val entityMap = getFeatureMap("${name}_entity")
//        val paragraphScores = aggregateFeature(paragraphMap, query)
//        val entityScores = aggregateFeature(paragraphMap, query)
//        return ExtractedFeature(name, paragraphs = paragraphScores, entities = entityScores)
//    }
}


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

class FeatureDatabase2() {
    val featuresPath = "features/"
    val featuresDir = File(featuresPath).apply { if(!exists()) mkdirs() }

    fun writeFeatures(queryContainers: List<QueryContainer>) {
        queryContainers.flatMap { qc -> qc.retrieveFeatures().toList() }
            .groupBy { it.name }
            .values.forEachIndexed(this::writeFeatureResults)
    }

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

    private fun getFeature(featureEnum: FeatureEnum) =
            getLineFeatures(featuresPath + featureEnum.text)

    private fun getFeatures(featureEnums: List<FeatureEnum>) {
        val features = featureEnums.flatMap(this::getFeature)
    }

    private fun getLineFeatures(filename: String) =
        File(filename).bufferedReader()
            .readLines()
            .map(LineFeature.Companion::createLineFeature)


}