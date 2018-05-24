package entity

import com.jsoniter.JsonIterator
import khttp.post
import org.json.JSONObject
import org.jsoup.Jsoup
import org.mapdb.DBMaker
import org.mapdb.Serializer
import org.mapdb.serializer.SerializerArray
import org.mapdb.serializer.SerializerArrayTuple
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

data class EntityData(
        val abstract: String?,
        val rdf: HashMap<String, HashSet<String>>
)


object EntityStats {
//    private val queryEntities = ConcurrentHashMap<String, List<Pair<String, Double>>>()
//    private val documentEntities = ConcurrentHashMap<Int, List<Pair<String, Double>>>()
//    private val entityAbstracts = ConcurrentHashMap<String, EntityData>()
//    private val lock = ReentrantLock()

    private val db = DBMaker
        .fileDB("entity_database.db")
        .fileMmapEnable()
        .closeOnJvmShutdown()
        .make()

    val entityAbstracts = db.hashMap("entityAbstracts")
        .keySerializer(Serializer.STRING)
        .valueSerializer(Serializer.STRING)
        .createOrOpen()

    val paragraphEntities = db.hashMap("paragraphEntities")
        .keySerializer(Serializer.STRING)
        .valueSerializer(Serializer.STRING)
        .createOrOpen()

    val queryEntities = db.hashMap("queryEntities")
        .keySerializer(Serializer.STRING)
        .valueSerializer(Serializer.STRING)
        .createOrOpen()

    val entitySet = db.hashSet("entitySet")
        .serializer(Serializer.STRING)
        .createOrOpen()

    val entityTypes = db.hashMap("entityTypes")
        .keySerializer(Serializer.STRING)
        .valueSerializer(SerializerArray<String>(Serializer.STRING))
        .createOrOpen()

    fun doTagMeQuery(content: String, minRho: Double = 0.2): List<Pair<String, Double>> {
        val tok = "7fa2ade3-fce7-4f4a-b994-6f6fefc7e665-843339462"
        val url = "https://tagme.d4science.org/tagme/tag"
        val p = post(url, data = mapOf(
                "gcube-token" to tok,
                "text" to content
        ))
        val results = p.jsonObject.getJSONArray("annotations")
        return  results
            .mapNotNull { result -> (result as JSONObject).run {
                if (getDouble("rho") <= minRho) null
                else getString("title").replace(" ", "_") to getDouble("rho")
            } }
    }

    private fun  doEntityQuery(entity: String): EntityData {
        val doc = Jsoup.connect("http://dbpedia.org/page/$entity")
            .post()
        val abstract =  doc.select("span[property=dbo:abstract]")
            .select("span[xml:lang=en")
            .text()

        val rdfMappings = HashMap<String, HashSet<String>>()
        doc.select("a[class=uri]")
            .map { element -> element.text().split(":", limit = 2) }
            .filter { it.size == 2 }
            .forEach { rdfMappings.computeIfAbsent(it[0], { HashSet() }).add(it[1]) }

        return EntityData(abstract = abstract, rdf = rdfMappings)
    }


    private fun wrapEntity(entity: String): EntityData {
        return try {
            doEntityQuery(entity)
        } catch (e: Exception) {
            EntityData(abstract = "", rdf = HashMap())
        }
    }

    fun getEntityAbstract(entity: String) =
        entityAbstracts.computeIfAbsent(entity, KotlinSparql::extractSingleAbstract)

    fun getEntityRdf(entity: String) =
            entityTypes.computeIfAbsent(entity, { KotlinSparql.extractTypes(entity).toTypedArray() })
                .toList()

//    fun getParagraphEntities(pid: String, content: String) =
//            paragraphEntities.computeIfAbsent(pid) { doTagMeQuery(content) }
//                .filterIsInstance<Pair<String, Double>>()

    fun getQueryEntities(query: String) =
            queryEntities.computeIfAbsent(query) {
                doTagMeQuery(query).flatMap { listOf(it.first,  it.second.toString())  }
                    .joinToString("%%") }
                .split("%%")
                .chunked(2)
                .map { it[0] to it[1].toDouble() }

    fun getParagraphEntities(pid: String, content: String) =
            paragraphEntities.computeIfAbsent(pid) {
                doTagMeQuery(content).flatMap { listOf(it.first,  it.second.toString())  }
                    .joinToString("%%") }
                .split("%%")
                .chunked(2)
                .map { it[0] to it[1].toDouble() }

//    fun getEntityAbstract(entity: String) =
//            entityAbstracts.computeIfAbsent(entity, this::wrapEntity).abstract
//
//    fun getEntityRdf(entity: String) =
//            entityAbstracts.computeIfAbsent(entity, this::wrapEntity).rdf
//
//    fun getQueryEntities(content: String, minRho: Double = 0.2): List<Pair<String, Double>> =
//            queryEntities.computeIfAbsent(content) { doTagMeQuery(content, minRho) }
//
//    fun getDocumentEntities(content: String, docId: Int, minRho: Double = 0.2): List<Pair<String, Double>> =
//            documentEntities.computeIfAbsent(docId) { doTagMeQuery(content, minRho) }
}

fun main(args: Array<String>) {
//    println(EntityStats.doTagMeQuery("Computer science is a thing in which you do"))
    println(EntityStats.getEntityRdf("God"))
}
