package lucene.containers

import org.mapdb.DBMaker
import org.mapdb.Serializer
import org.mapdb.serializer.SerializerArrayTuple
import java.io.File
import java.util.zip.GZIPOutputStream

class ContextDatabase(dbLoc: String) {
    val db = DBMaker
        .fileDB(dbLoc)
        .fileMmapEnable()
        .closeOnJvmShutdown()
        .concurrencyScale(60)
        .make()

    val contextMap = db.treeMap("context_map")
        .keySerializer(SerializerArrayTuple(Serializer.STRING, Serializer.STRING))
        .valueSerializer(Serializer.INTEGER)
        .createOrOpen()

    fun addContext(entity: String, nearbyToken: String) {
        contextMap.compute(arrayOf(entity, nearbyToken), { key, value -> value?.inc() ?: 1 })
    }

    fun addContexts(contexts: List<Pair<String, String>> ) =
            contexts.forEach { (entity, nearbyToken) -> addContext(entity, nearbyToken) }

    fun getContext(entity: String): Map<String, Double> =
    // Prefix submap lets us return all keys that contain the entity mention
            contextMap.prefixSubMap(arrayOf(entity))
                .let { nearbyTokens ->
                    val total = nearbyTokens.entries.sumBy { it.value }
                    nearbyTokens
                        .entries.map { (arr, count) ->
                        val name: String = arr[1] as String
                        name to count / total.toDouble() } }
                .toMap()

}
