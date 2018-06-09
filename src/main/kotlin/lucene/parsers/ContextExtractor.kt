package lucene.parsers

import org.mapdb.DBMaker
import org.mapdb.Serializer
import org.mapdb.serializer.SerializerArrayTuple

class ContextExtractor<T>(outLoc: String,
                   val extractFun: Extractor<T>.(T) -> String, val debug: Boolean = false) {

    val db = DBMaker
        .fileDB("extractions/context.db")
        .fileMmapEnable()
        .closeOnJvmShutdown()
        .concurrencyScale(60)
        .make()

    val contextMap =
            db.treeMap("context_map")
                .keySerializer(SerializerArrayTuple(Serializer.STRING, Serializer.STRING))
                .valueSerializer(Serializer.DOUBLE)
                .createOrOpen()

    fun getFeatureMap(name: String) =
            db.treeMap(name)
                .keySerializer(SerializerArrayTuple(Serializer.STRING, Serializer.STRING))
                .valueSerializer(Serializer.DOUBLE)
                .createOrOpen()

}
