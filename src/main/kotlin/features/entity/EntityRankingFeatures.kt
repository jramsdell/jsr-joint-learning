package features.entity

import entity.EntityDatabase
import info.debatty.java.stringsimilarity.NormalizedLevenshtein
import lucene.containers.QueryData
import utils.AnalyzerFunctions
import utils.misc.identity
import utils.stats.normalize

object EntityRankingFeatures {
    fun entityFeatTop25Freq(qd: QueryData): List<Double> = with(qd) {
        val freqs = documentEntities
            .take(25)
            .flatten()
            .groupingBy(::identity)
            .eachCount()
            .normalize()

        return candidateEntities
            .map { (entity, _)  ->
                freqs.getOrDefault(entity, 0.0)
            }
    }

    fun entityFeatMatchingQueryEntity(qd: QueryData): List<Double> = with(qd) {
        val dist = NormalizedLevenshtein()
        return candidateEntities
            .map { (entity, _)  ->
                queryEntities.map { (qEntity, rho) ->
                    if (dist.similarity(qEntity, entity) >= 0.9) 1.0 * rho else 0.0  }
                    .sum()
            }
    }

    fun bm25Score(qd: QueryData, entityDatabase: EntityDatabase): List<Double> = with(qd) {
        val abstractQuery = AnalyzerFunctions.createQuery(queryString, field = "abstract", useFiltering = true)

        return candidateEntities
            .map { (entity, _)  ->
                entityDatabase.getEntityDocId(entity)
                    ?.let { id -> indexSearcher.explain(abstractQuery, id).value.toDouble() } ?: 0.0
            }
    }

}
