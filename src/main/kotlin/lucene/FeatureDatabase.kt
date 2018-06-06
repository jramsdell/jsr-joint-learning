package lucene

import lucene.containers.ExtractedFeature
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

class FeatureDatabase2() {
    val paragraphsPath = "features/paragraphs/"
    val entitiesPath = "features/entities/"
    val paragraphsDir = File(paragraphsPath).apply { if(!exists()) mkdirs() }
    val entitiesDir = File(entitiesPath).apply { if(!exists()) mkdirs() }

    fun writeFeatures(queryContainers: List<QueryContainer>) {
        queryContainers.flatMap { qc -> qc.retrieveFeatures().toList() }
            .groupBy { it.name }
            .values.forEach(this::writeFeatureResults)
    }

    fun writeFeatureResults(featureList: List<ExtractedFeature>) {
        val name = featureList.first().name
        val entitiesFile = File(entitiesPath + name + ".txt").bufferedWriter()
        val paragraphsFile = File(paragraphsPath + name + ".txt").bufferedWriter()
        featureList.forEach { feature ->
            val q = feature.query
            paragraphsFile.write( feature.paragraphs.map { (id, score) -> "$q\t$id\t$score" }.joinToString { "\n" } )
            entitiesFile.write( feature.entities.map { (id, score) -> "$q\t$id\t$score" }.joinToString { "\n" } )
        }
        entitiesFile.close()
        paragraphsFile.close()
    }

    fun getFeature(name: String) {

    }


}