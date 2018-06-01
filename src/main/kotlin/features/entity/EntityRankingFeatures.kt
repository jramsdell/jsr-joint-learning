package features.entity

import entity.EntityDatabase
import features.shared.SharedFeature
import info.debatty.java.stringsimilarity.NormalizedLevenshtein
import lucene.containers.QueryData
import utils.AnalyzerFunctions
import utils.AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED
import utils.lucene.explainScore
import utils.misc.identity
import utils.stats.countDuplicates
import utils.stats.normalize
import java.lang.Double.sum
import kotlin.math.absoluteValue


object EntityRankingFeatures {
    fun queryBm25Abstract(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val abstractQuery = AnalyzerFunctions.createQuery(qd.queryString, field = "abstract", useFiltering = true)
        entityContainers.mapIndexed {  index, entity ->
            val score = entityDb.searcher.explainScore(abstractQuery, entity.docId)
            sf.entityScores[index] = score
        }
    }

    fun entityTop25Freq(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val abstractQuery = AnalyzerFunctions.createQuery(qd.queryString, field = "abstract", useFiltering = true)
        entityContainers.mapIndexed {  index, entity ->
            val score = entityDb.searcher.explainScore(abstractQuery, entity.docId)
            sf.entityScores[index] = score
        }
    }

    fun entityFeatTop25Freq(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        paragraphToEntity
            .flatMap { (_, entityDistribution) ->
                entityDistribution.map { (entityId, entityProb) ->  entityId to entityProb } }
            .groupingBy( { it.first })
            .fold(0.0) { acc, freq -> acc + freq.second}
            .entries
            .sortedByDescending(Map.Entry<Int, Double>::value)
            .take(25)
            .forEach { (entity, score) -> sf.entityScores[entity] = score }
    }

    fun entityMatchQueryEntity(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val dist = NormalizedLevenshtein()
        entityContainers
            .forEachIndexed { index, entity  ->
                val entityScore = queryEntities.map { (qEntity, rho) ->
                    if (dist.similarity(qEntity, entity.name) >= 0.9) 1.0 * rho else 0.0  }
                    .sum()
                sf.entityScores[index] = entityScore
            }
    }

    fun entityUnigram(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val queryUnigrams = AnalyzerFunctions
            .createTokenList(queryString, useFiltering = true, analyzerType = ANALYZER_ENGLISH_STOPPED)
            .countDuplicates()
            .normalize()

        entityContainers
            .forEachIndexed { index, entity ->
                val entityData = entityDb.getEntityByID(entity.docId)
                val combinedKeys = (entityData.unigram.keys + queryUnigrams.keys)
                val score = combinedKeys
                    .toSet()
                    .map { key ->
                        val queryUnigramFreq = queryUnigrams.getOrDefault(key, 0.0)
                        val entityUnigramFreq = entityData.unigram.getOrDefault(key, 0.0)
                        1 / (queryUnigramFreq - entityUnigramFreq).absoluteValue }
                score.sum()
            }
    }

}
