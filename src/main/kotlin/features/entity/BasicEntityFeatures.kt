package features.entity

import experiment.retrieveTagMeEntities2
import info.debatty.java.stringsimilarity.NormalizedLevenshtein
import lucene.containers.QueryData
import entity.EntityStats


fun featEntityStringSim(qd: QueryData): List<Double> = with(qd) {
    val sim = NormalizedLevenshtein()
    return tops.scoreDocs
        .map { scoreDoc ->
            val doc = indexSearcher.doc(scoreDoc.doc)
            val entities = doc.getValues("spotlight")
                .flatMap { it.split("_") }
                .map { it.toLowerCase() }
            val tokenSims = entities.flatMap { entity -> queryTokens.map { query -> sim.similarity(entity, query) } }
            tokenSims.filter { score -> score >= 0.9 }.sum()
        }
}

fun featEntitySurface(qd: QueryData): List<Double> = with(qd) {
    val queryEntities = retrieveTagMeEntities2(queryString)
        .map { (entity, rho) -> Triple(entity, rho, EntityStats.doWikipediaIDQuery(entity)) }

    return tops.scoreDocs
        .map { scoreDoc ->
            val doc = indexSearcher.doc(scoreDoc.doc)
            val entities = doc.getValues("spotlight")
                .map { entity -> entity to EntityStats.doWikipediaIDQuery(entity) }
            val tokenSims = entities.flatMap { (entity, entityID) ->
                queryEntities.map { (queryEntity, rho, queryEntityID) ->
                    if (queryEntityID == entityID) 1 * rho else 0.0
                }
            }
            tokenSims.filter { score -> score >= 0.9 }.sum()
        }
}

fun featQueryEntityToDocEntity(qd: QueryData): List<Double> = with(qd) {
    val queryEntities = retrieveTagMeEntities2(queryString)
    val sim = NormalizedLevenshtein()
    return tops.scoreDocs
        .map { scoreDoc ->
            val doc = indexSearcher.doc(scoreDoc.doc)
            val entities = doc.getValues("spotlight")
            val tokenSims = entities.flatMap { entity ->
                queryEntities.map { query -> sim.similarity(entity, query.first) }
            }
            tokenSims.filter { score -> score >= 0.9 }.sum()
        }
}

//fun featEntityCategory(qd: QueryData): List<Double> = with(qd) {
//    val queryEntities = EntityStats.getQueryEntities(queryString)
//        .map { EntityStats.getEntityRdf(it.first).toSet() to it.second }
//
//    return tops.scoreDocs
//        .map { scoreDoc ->
//            val doc = indexSearcher.doc(scoreDoc.doc)
//            val entities = doc.getValues("spotlight")
//                .map { EntityStats.getEntityRdf(it).toSet() }
//            entities.flatMap { entity ->
//                queryEntities.map { query ->
//                    entity.intersect(query.first).count().toDouble() * query.second
//                } }.sum()
//        }
//}
//
//fun featRdf(qd: QueryData, entityDatabase: EntityDatabase): List<Double> = with(qd) {
//    val queryRdf = EntityStats.getQueryEntities(queryString)
//        .map { entityDatabase.getEntity(it.first).rdf }
//        .reduce { acc, next -> acc + next }
//
//
//    return tops.scoreDocs
//        .map { scoreDoc ->
//            val doc = indexSearcher.doc(scoreDoc.doc)
//            val entities = doc.getValues("spotlight")
//                .map { entityDatabase.getEntity(it).rdf }
//                .reduce { acc, next -> acc + next }
//            entities.intersect(queryRdf).count().toDouble()
//        }
//
//}
//
//fun featEntityAbstractSDM(qd: QueryData, entityDatabase: EntityDatabase,
//                          gramAnalyzer: GramAnalyzer, gramType: GramStatType?): List<Double> = with(qd) {
//    val queryEntities = EntityStats.getQueryEntities(queryString)
//        .map { entityDatabase.getEntity(it.first).abstract!! }
//        .joinToString("\n")
//
//    val queryModel = gramAnalyzer.getCorpusStatContainer(queryEntities)
//    val weights = listOf(0.9285990421606605, 0.070308081629, -0.0010928762)
//
//    return tops.scoreDocs
//        .map { scoreDoc ->
//            val doc = indexSearcher.doc(scoreDoc.doc)
//            val entities = doc.getValues("spotlight")
//                .map { entityDatabase.getEntity(it).abstract!! }
//                .joinToString("\n")
//
//            val docStat = gramAnalyzer.getLanguageStatContainer(entities)
//            val (uniLike, biLike, windLike) = gramAnalyzer.getQueryLikelihood(docStat, queryModel, 4.0)
//            when (gramType) {
//                GramStatType.TYPE_UNIGRAM -> uniLike
//                GramStatType.TYPE_BIGRAM -> biLike
//                GramStatType.TYPE_BIGRAM_WINDOW -> windLike
//                else -> uniLike * weights[0] + biLike * weights[1] + windLike * weights[2]
//            }
//
//
//
//
//
//        }
//
//}


//fun featEntityAbstract(qd: QueryData): List<Double> = with(qd) {
//    val queryEntities = EntityStats.getQueryEntities(queryString)
//        .map { EntityStats.getEntityRdf(it.first).toSet() }
//
//    return tops.scoreDocs
//        .map { scoreDoc ->
//            val doc = indexSearcher.doc(scoreDoc.doc)
//            val entities = doc.getValues("spotlight")
//                .map { EntityStats.getEntityRdf(it).toSet() }
//            entities.flatMap { entity ->
//                queryEntities.map { query ->
//                    entity.intersect(query).count().toDouble()
//                } }.sum()
//        }
//}

