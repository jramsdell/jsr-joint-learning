package features.entity

import entity.EntityDatabase
import experiment.FeatureType
import experiment.KotlinRanklibFormatter
import experiment.NormType
import experiment.NormType.*
import features.shared.SharedFeature
import info.debatty.java.stringsimilarity.NormalizedLevenshtein
import language.GramAnalyzer
import language.GramStatType
import language.GramStatType.*
import language.containers.LanguageStatContainer
import lucene.containers.QueryData
import utils.AnalyzerFunctions
import utils.AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED
import utils.lucene.explainScore
import utils.misc.identity
import utils.stats.countDuplicates
import utils.stats.normalize
import utils.stats.takeMostFrequent
import java.lang.Double.sum
import kotlin.math.absoluteValue


object EntityRankingFeatures {
    private fun queryBm25Abstract(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val abstractQuery = AnalyzerFunctions.createQuery(qd.queryString, field = "abstract", useFiltering = true)
        entityContainers.mapIndexed {  index, entity ->
            val score = entityDb.searcher.explainScore(abstractQuery, entity.docId)
            sf.entityScores[index] = score
        }
    }

    private fun queryRdf(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val queryRdf = queryEntities
            .flatMap { (entityToken, rho) ->
                val entity = qd.entityDb.getEntityDocument(entityToken)
                entity?.getValues("rdf")?.map { it to rho } ?: emptyList() }
            .groupingBy { it.first }
            .fold(0.0) { acc, (_, normalizedRdfScore) -> acc + normalizedRdfScore }
        entityDocuments.forEachIndexed { index, doc ->
            val rdfs = doc.getValues("rdf")?.toList() ?: emptyList()
            val score = rdfs.sumByDouble { rdf -> queryRdf.getOrDefault(rdf, 0.0) }
            sf.entityScores[index] = score
        }
    }


    private fun entityTop25Freq(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
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

    private fun entityMatchQueryEntity(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val dist = NormalizedLevenshtein()
        entityContainers
            .forEachIndexed { index, entity  ->
                val entityScore = queryEntities.map { (qEntity, rho) ->
                    if (dist.similarity(qEntity, entity.name) >= 0.9) 1.0 * rho else 0.0  }
                    .sum()
                sf.entityScores[index] = entityScore
            }
    }

    private fun querySDMAbstract(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        // Parse query and retrieve a language model for it
        val tokens = AnalyzerFunctions.createTokenList(queryString, useFiltering = true,
                analyzerType = ANALYZER_ENGLISH_STOPPED)
        val cleanQuery = tokens.toList().joinToString(" ")

        val gramAnalyzer = GramAnalyzer(entitySearcher)
        val queryCorpus = gramAnalyzer.getCorpusStatContainer(cleanQuery)
        val weights = listOf(0.9285990421606605, 0.070308081629, -0.0010928762)

        entityDocuments.forEachIndexed { index, doc ->
            val docStat = LanguageStatContainer.createLanguageStatContainer(doc)
            val (uniLike, biLike, windLike) = gramAnalyzer.getQueryLikelihood(docStat, queryCorpus, 4.0)
            val score = uniLike * weights[0] + biLike * weights[1] + windLike * weights[2]
            sf.entityScores[index] = score
        }
    }

    private fun entityBoostedGram(qd: QueryData, sf: SharedFeature, gramStatType: GramStatType,
                                  weight: Double = 1.0): Unit = with(qd) {
        val terms = AnalyzerFunctions.createTokenList(queryString, analyzerType = ANALYZER_ENGLISH_STOPPED, useFiltering = true)
        val grams = when(gramStatType) {
            GramStatType.TYPE_UNIGRAM       -> GramAnalyzer.countUnigrams(terms)
                .takeMostFrequent(15)
                .keys.toList()
//                .normalize()
            GramStatType.TYPE_BIGRAM        -> GramAnalyzer.countBigrams(terms)
                .takeMostFrequent(15)
                .keys.toList()
//                .normalize()
            GramStatType.TYPE_BIGRAM_WINDOW -> GramAnalyzer.countWindowedBigrams(terms)
                .takeMostFrequent(15)
                .keys.toList()
//                .normalize()
        }

        val documentQuery = AnalyzerFunctions.createWeightedTermsQuery(grams, gramStatType.indexField)

        entityContainers.mapIndexed {  index, entityContainer ->
            val score = entitySearcher.explainScore(documentQuery, entityContainer.docId )
            sf.entityScores[index] = score
        }
    }

//    fun entityUnigram(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
//        val queryUnigrams = AnalyzerFunctions
//            .createTokenList(queryString, useFiltering = true, analyzerType = ANALYZER_ENGLISH_STOPPED)
//            .countDuplicates()
//            .normalize()
//
//        entityContainers
//            .forEachIndexed { index, entity ->
//                val entityData = entityDb.getEntityByID(entity.docId)
//                val combinedKeys = (entityData.unigram.keys + queryUnigrams.keys)
//                val score = combinedKeys
//                    .toSet()
//                    .map { key ->
//                        val queryUnigramFreq = queryUnigrams.getOrDefault(key, 0.0)
//                        val entityUnigramFreq = entityData.unigram.getOrDefault(key, 0.0)
//                        1 / (queryUnigramFreq - entityUnigramFreq).absoluteValue }
//                    .sum()
//                sf.entityScores[index] = score
//            }
//    }

    fun addBM25Abstract(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3("entity_bm25_abstract", FeatureType.ENTITY, wt, norm, this::queryBm25Abstract)

    fun addSDMAbstract(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3("entity_sdm_abstract", FeatureType.ENTITY, wt, norm, this::querySDMAbstract)

    fun addTop25Freq(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3("entity_top25_freq", FeatureType.ENTITY, wt, norm, this::entityTop25Freq)

    fun addQuerySimilarity(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3("entity_query_similarity", FeatureType.ENTITY, wt, norm, this::entityMatchQueryEntity)

    fun addQueryRdf(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3("entity_rdf", FeatureType.ENTITY, wt, norm, this::queryRdf)

    fun addBM25BoostedUnigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3("entity_boosted_unigram", FeatureType.ENTITY, wt, norm) { qd, sf -> entityBoostedGram(qd, sf, TYPE_UNIGRAM) }

    fun addBM25BoostedBigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3("entity_boosted_bigram", FeatureType.ENTITY, wt, norm) { qd, sf -> entityBoostedGram(qd, sf, TYPE_BIGRAM) }

    fun addBM25BoostedWindowedBigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3("entity_boosted_window", FeatureType.ENTITY, wt, norm) { qd, sf -> entityBoostedGram(qd, sf, TYPE_BIGRAM_WINDOW) }

}
