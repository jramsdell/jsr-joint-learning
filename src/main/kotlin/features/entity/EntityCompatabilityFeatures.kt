package features.entity

import entity.EntityDatabase
import info.debatty.java.stringsimilarity.NormalizedLevenshtein
import lucene.containers.QueryData
import utils.AnalyzerFunctions
import utils.lucene.explainScore
import utils.misc.CONTENT
import utils.misc.identity
import utils.stats.countDuplicates
import utils.stats.normalize
import kotlin.math.absoluteValue

object EntityCompatabilityFeatures {
    fun compatFeatLinks(qd: QueryData): List<Double> = with(qd) {
        return entityContainers.map {  entity ->
            tops.scoreDocs.map { scoreDoc ->
                val doc = indexSearcher.doc(scoreDoc.doc)
                val counts = doc.getValues("spotlight")
                    .groupingBy(::identity)
                    .eachCount()
                counts.getOrDefault(entity.name, 0).toDouble() }
                .sum()
        }
    }

    fun compatDocParagraphToAbstract(qd: QueryData, db: EntityDatabase): List<Double> = with(qd) {
        return entityContainers.map {  entity ->
            tops.scoreDocs.map { scoreDoc ->
                val doc = indexSearcher.doc(scoreDoc.doc)
                val content = doc.get(CONTENT)
                val contentQuery = AnalyzerFunctions.createQuery(content, field="abstract")
                db.searcher.explainScore(contentQuery, entity.docId) }
                .sum()
        }
    }

    fun compatUnigramLikelihood(qd: QueryData, db: EntityDatabase): List<Double> = with(qd) {
        return entityContainers.map { entity ->
            val entityData = db.getEntityByID(entity.docId)
            val result = tops.scoreDocs
                .map { scoreDoc ->
                    val doc = indexSearcher.doc(scoreDoc.doc)
                    val docUnigrams = doc.get("unigram")
                        .split(" ")
                        .countDuplicates()
                        .normalize()

                    val combinedKeys = (entityData.unigram.keys + docUnigrams.keys)
                    val score = combinedKeys
                        .toSet()
                        .map { key ->
                            val docUnigramFreq = docUnigrams.getOrDefault(key, 0.0)
                            val entityUnigramFreq = entityData.unigram.getOrDefault(key, 0.0)
                            1 / (docUnigramFreq - entityUnigramFreq).absoluteValue }
                    score.sum() }
            result.sum()
        }
    }

    fun compatRdf(qd: QueryData, db: EntityDatabase): List<Double> = with(qd) {
        return entityContainers.map { entity ->
            val entityData = db.getEntityByID(entity.docId)
            val result = tops.scoreDocs
                .map { scoreDoc ->
                    val doc = indexSearcher.doc(scoreDoc.doc)
                    val docEntities = doc.getValues("spotlight")
                        .mapNotNull { docEntity -> db.getEntity(docEntity) }
                        .flatMap { docEntity -> docEntity.rdf }
                        .countDuplicates()

                    entityData.rdf
                        .map { rdf -> docEntities.getOrDefault(rdf, 0).toDouble() }
                        .sum()
                }
            result.sum()
        }
    }



}
