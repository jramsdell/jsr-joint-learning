package entity

import khttp.get
import khttp.post
import org.json.JSONObject
import utils.io.catchJsonException
import utils.io.doIORequest


data class SurfaceFormData(
        val id: Long,
        val text: String,
        val normalizedText: String,
        val boostedLinkProbability: Double,
        val tfidf: Double,
        val queryScope: Double,
        val clarity: Double,
        val mutualDependency: Double ) {

    companion object {
        fun createSufaceFormData(data: JSONObject): SurfaceFormData = with(data) {
            return SurfaceFormData(
                    id = getLong("id"),
                    text = getString("text"),
                    normalizedText = getString("normalized_text"),
                    boostedLinkProbability = getDouble("boosted_link_probability"),
                    tfidf = getDouble("tf_idf"),
                    queryScope = getDouble("query_scope"),
                    clarity = getDouble("clarity"),
                    mutualDependency = getDouble("mutual_dependency"))
        }

    }
}

data class TagMeData(val id: Long,
                     val title: String,
                     val spot: String,
                     val rho: Double,
                     val abstract: String,
                     val linkProbability: Double,
                     val categories: List<String>,
                     val start: Int,
                     val end: Int) {
    companion object {
        fun createTagMeData(data: JSONObject): TagMeData = with(data) {
            return TagMeData(
                    id = catchJsonException(0L) { getLong("id") },
                    linkProbability = catchJsonException(0.0) { getDouble("link_probability") },
                    rho = catchJsonException(0.0) { getDouble("rho") },
                    start = catchJsonException(0) { getInt("start") },
                    end = catchJsonException(0) { getInt("end") },
                    abstract = catchJsonException("") { getString("abstract") },
                    title = catchJsonException("") { getString("title").replace(" ", "_") },
                    spot = catchJsonException("") { getString("spot") },
                    categories = catchJsonException(emptyList()) {
                        getJSONArray("dbpedia_categories")
                            .map { it.toString().replace(" ", "_") }.toList()
                    }
            )

        }
    }
}


object EntityStats {
//    private val queryEntities = ConcurrentHashMap<String, List<Pair<String, Double>>>()
//    private val documentEntities = ConcurrentHashMap<Int, List<Pair<String, Double>>>()
//    private val entityAbstracts = ConcurrentHashMap<String, EntityData>()
//    private val lock = ReentrantLock()
    private val tok = "7fa2ade3-fce7-4f4a-b994-6f6fefc7e665-843339462"


    fun doTagMeQuery(content: String, minRho: Double = 0.2): List<Pair<String, Double>> {
        val url = "https://tagme.d4science.org/tagme/tag"

        // Try to request links from TagMe a few times. Otherwise, give up and return empty list
        val p = doIORequest {
            post(url, data = mapOf(
                    "gcube-token" to tok,
                    "text" to content))
        } ?: return emptyList()

        // Turn results into a JSon array and retrieve linked entities (and their rho values)
        val results = p.jsonObject.getJSONArray("annotations")
        return  results
            .mapNotNull { result -> (result as JSONObject).run {
                if (getDouble("rho") <= minRho) null
                else getString("title").replace(" ", "_") to getDouble("rho")
            } }
    }

    fun retrieveTagMeData(content: String, minRho: Double = 0.2): List<TagMeData>? {
        val url = "https://tagme.d4science.org/tagme/tag"

        // Try to request links from TagMe a few times. Otherwise, give up and return empty list
        val p = doIORequest {
            post(url, data = mapOf(
                    "gcube-token" to tok,
                    "text" to content,
                    "include_abstract" to "true",
                    "include_categories" to "true"))
        } ?: return null

        // Turn results into a JSon array and retrieve linked entities (and their rho values)
        return p.jsonObject.getJSONArray("annotations")
            .filterIsInstance<JSONObject>()
            .map { TagMeData.createTagMeData(it) }
            .filter { it.rho > 0.2 }
    }

    fun doRelatednessQuery(id1: Long, id2: Long): Double? {
//        val id1 = "534366"
//        val id2 = "20082091293183"
//        val id2 = "20082093"
        val url = "https://wat.d4science.org/wat/relatedness/graph"
//        val url = "https://tagme.d4science.org/tagme/rel"
        val formatted = "$url?gcube-token=$tok&ids=$id1&ids=$id2"
        val p = doIORequest { get(formatted) }

        // If there was an IO error or the tokens are incorrect, return nothing
        if (p == null || p.statusCode == 400) return null
        return p.jsonObject
            .getJSONArray("pairs")
            .getJSONObject(0)
            .getDouble("relatedness")
    }

    fun doRelatednessQuery2(id1: String, id2: String): Double? {
//        val id1 = "534366"
//        val id2 = "20082091293183"
//        val id2 = "20082093"
        val url = "https://tagme.d4science.org/tagme/rel"
        val formatted = "$url?gcube-token=$tok&ids=$id1&ids=$id2"

        val p = doIORequest {
            post(url, data = mapOf(
                    "gcube-token" to tok,
                    "tt" to "$id1 $id2"))
        } ?: return null

        // If there was an IO error or the tokens are incorrect, return nothing
        if (p == null || p.statusCode == 400) return null
        return p.jsonObject.getJSONArray("result").getJSONObject(0).getDouble("rel")
    }

    fun doSurfaceFormQuery(entity: String): SurfaceFormData? {
        val url = "https://wat.d4science.org/wat/sf/sf"
        val formatted = "$url?gcube-token=$tok&text=$entity"
        val p = doIORequest { get(formatted) }
        if (p == null || p.jsonObject.getLong("id") == -1L) return null
        return SurfaceFormData.createSufaceFormData(p.jsonObject)
    }

    fun doWikipediaIDQuery(entity: String): Long {
        val url = "https://wat.d4science.org/wat/title"
        val formatted = "$url?gcube-token=$tok&title=$entity"
        val p = doIORequest { get(formatted) } ?: return -1
        return p.jsonObject.getLong("wiki_id")
    }

    fun doEntitySaliencyQuery(content: String, title: String = "") {
        val url = "https://swat.d4science.org/tag\n"

        // Try to request links from TagMe a few times. Otherwise, give up and return empty list
        val p = doIORequest {
            post(url, data = mapOf(
                    "gcube-token" to tok,
                    "content" to content,
                    "title" to title))
        }

    }

//    fun doWatEntityLinking(content: String): Long {
//        val url = "https://wat.d4science.org/wat/tag/tag"
//        val formatted = "$url?gcube-token=$tok&text=${content.replace(" ", "+")}"
//        val p = doIORequest { get(formatted) } ?: return -1
//        return 1L
//    }


//    private fun  doEntityQuery(entity: String): EntityData {
//        val doc = Jsoup.connect("http://dbpedia.org/page/$entity")
//            .post()
//        val abstract =  doc.select("span[property=dbo:abstract]")
//            .select("span[xml:lang=en")
//            .text()
//
//        val rdfMappings = HashMap<String, HashSet<String>>()
//        doc.select("a[class=uri]")
//            .map { element -> element.text().split(":", limit = 2) }
//            .filter { it.size == 2 }
//            .forEach { rdfMappings.computeIfAbsent(it[0], { HashSet() }).add(it[1]) }
//
//        return EntityData(abstract = abstract, rdf = rdfMappings)
//    }



//    fun getEntityAbstract(entity: String) =
//        entityAbstracts.computeIfAbsent(entity, KotlinSparql::extractSingleAbstract)
//
//    fun getEntityRdf(entity: String) =
//            entityTypes.computeIfAbsent(entity, { KotlinSparql.extractTypes(entity).toTypedArray() })
//                .toList()
//
//
//    fun getQueryEntities(query: String) =
//            queryEntities.computeIfAbsent(query) {
//                doTagMeQuery(query).flatMap { listOf(it.first,  it.second.toString())  }
//                    .joinToString("%%") }
//                .split("%%")
//                .chunked(2)
//                .map { it[0] to it[1].toDouble() }
//
//    fun getParagraphEntities(pid: String, content: String) =
//            paragraphEntities.computeIfAbsent(pid) {
//                doTagMeQuery(content).flatMap { listOf(it.first,  it.second.toString())  }
//                    .joinToString("%%") }
//                .split("%%")
//                .chunked(2)
//                .map { it[0] to it[1].toDouble() }

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
//    println(EntityStats.getEntityRdf("God"))
//    EntityStats.doRelatednessQuery("hi", "hi")
//    println(EntityStats.doSurfaceFormQuery("Barack"))
//    println(EntityStats.doSurfaceFormQuery("Barack"))
    val test = """
        Computer science is the study of the theory, experimentation, and engineering that form the basis for the design and use of computers. It is the scientific and practical approach to computation and its applications and the systematic study of the feasibility, structure, expression, and mechanization of the methodical procedures (or algorithms) that underlie the acquisition, representation, processing, storage, communication of, and access to, information. An alternate, more succinct definition of computer science is the study of automating algorithmic processes that scale. A computer scientist specializes in the theory of computation and the design of computational systems. See glossary of computer science.
        """
//    println(EntityStats.doWatEntityLinking(test))
//    val myfun = { EntityStats.doSurfaceFormQuery("Barack") }
//    println(EntityStats.doRelatednessQuery("hi", "hi"))
//    EntityStats.doEntitySaliencyQuery(test, title = "Computer science")
    val id1 = EntityStats.doWikipediaIDQuery("Computer_science")
    val id2 = EntityStats.doWikipediaIDQuery("Soda")
    val id3 = EntityStats.doWikipediaIDQuery("Cola")
    println(id1)
    println(id2)
//    println(EntityStats.doRelatednessQuery2(id1, id2))
//    println(EntityStats.doRelatednessQuery2(id1, id3))
//    println(EntityStats.doRelatednessQuery2("Computer_science", "Bill_Gates"))
//    println(EntityStats.doTagMeQuery2("Computer science is a thing that smart people do. A computer scientist is someone who"))
}


