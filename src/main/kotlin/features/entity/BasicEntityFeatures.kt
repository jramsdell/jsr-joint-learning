package features.entity

import entity.EntityStats
import experiment.QueryData
import experiment.retrieveTagMeEntities2
import info.debatty.java.stringsimilarity.NormalizedLevenshtein
import info.debatty.java.stringsimilarity.interfaces.StringSimilarity
import language.GramAnalyzer
import language.GramStatType
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import org.apache.lucene.search.similarities.*
import org.apache.lucene.util.BytesRef
import utils.AnalyzerFunctions
import utils.CONTENT


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

fun featEntityCategory(qd: QueryData): List<Double> = with(qd) {
    val queryEntities = EntityStats.getQueryEntities(queryString)
        .map { EntityStats.getEntityRdf(it.first).toSet() }

    return tops.scoreDocs
        .map { scoreDoc ->
            val doc = indexSearcher.doc(scoreDoc.doc)
            val entities = doc.getValues("spotlight")
                .map { EntityStats.getEntityRdf(it).toSet() }
            entities.flatMap { entity ->
                queryEntities.map { query ->
                    entity.intersect(query).count().toDouble()
                } }.sum()
        }
}

