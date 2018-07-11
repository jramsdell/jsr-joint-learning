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
import lucene.FieldQueryFormatter
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
import lucene.containers.FeatureEnum.*
import lucene.indexers.IndexFields
import org.apache.lucene.search.similarities.LMDirichletSimilarity
import utils.lucene.docs


object EntityRankingFeatures {
    private fun queryBm25Abstract(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val abstractQuery = AnalyzerFunctions.createQuery(qd.queryString, field = IndexFields.FIELD_TEXT.field, useFiltering = true)
        val scores = entityDb.searcher.search(abstractQuery, 200000)
            .scoreDocs
            .map { sc -> sc.doc to sc.score.toDouble() }
            .toMap()
        entityContainers.mapIndexed {  index, entity ->
            val score = scores[entity.docId] ?: 0.0
            sf.entityScores[index] = score
        }
    }

    private fun queryDirichletAbstract(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        entityDb.searcher.setSimilarity(LMDirichletSimilarity(1.0f))
        val abstractQuery = AnalyzerFunctions.createQuery(qd.queryString, field = IndexFields.FIELD_TEXT.field, useFiltering = true)
        val scores = entityDb.searcher.search(abstractQuery, 200000)
            .scoreDocs
            .map { sc -> sc.doc to sc.score.toDouble() }
            .toMap()
        entityContainers.mapIndexed {  index, entity ->
            val score = scores[entity.docId] ?: 0.0
            sf.entityScores[index] = score
        }
    }



    private fun entityTop25Freq(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val counts = paragraphContainers.flatMap { container ->
            val doc = container.doc()
            doc.get(IndexFields.FIELD_NEIGHBOR_ENTITIES.field).split(" ") }
            .countDuplicates()

        entityContainers.forEachIndexed { index, eContainer ->
            sf.entityScores[index] = (counts[eContainer.name] ?: 0).toDouble()

        }
    }

//    private fun entityScoreTransfer(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
//        val counts = paragraphDocuments.flatMap { doc ->
//            doc.get(IndexFields.FIELD_NEIGHBOR_ENTITIES.field).split(" ") }
//            .countDuplicates()
//
//        entityContainers.forEachIndexed { index, (entity, docId) ->
//            sf.entityScores[index] = (counts[entity] ?: 0).toDouble()
//
//        }
//    }


    private fun queryField(qd: QueryData, sf: SharedFeature, field: IndexFields): Unit = with(qd) {
        // Parse query and retrieve a language model for it
//        val tokens = AnalyzerFunctions.createTokenList(queryString, useFiltering = true,
//                analyzerType = ANALYZER_ENGLISH_STOPPED).joinToString(" ")
        val tokens = AnalyzerFunctions.createTokenList(queryString, useFiltering = true,
                analyzerType = ANALYZER_ENGLISH_STOPPED)
        val fq = FieldQueryFormatter()
        val fieldQuery = fq.addWeightedQueryTokens(tokens, field).createBooleanQuery()

        val scores = entitySearcher.search(fieldQuery, 5000)
            .scoreDocs
            .map { sc -> sc.doc to sc.score.toDouble() }
            .toMap()


        entityContainers.forEachIndexed { index, container ->
            sf.entityScores[index] = scores[container.docId] ?: 0.0
        }
    }

//    private fun querySDMAbstract(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
//        // Parse query and retrieve a language model for it
//        val tokens = AnalyzerFunctions.createTokenList(queryString, useFiltering = true,
//                analyzerType = ANALYZER_ENGLISH_STOPPED)
//        val cleanQuery = tokens.toList().joinToString(" ")
//
//        val gramAnalyzer = GramAnalyzer(entitySearcher)
//        val queryCorpus = gramAnalyzer.getCorpusStatContainer(cleanQuery)
//        val weights = listOf(0.9285990421606605, 0.070308081629, -0.0010928762)
//
//        entityDocuments.forEachIndexed { index, doc ->
//            val docStat = LanguageStatContainer.createLanguageStatContainer(doc)
//            val (uniLike, biLike, windLike) = gramAnalyzer.getQueryLikelihood(docStat, queryCorpus, 4.0)
//            val score = uniLike * weights[0] + biLike * weights[1] + windLike * weights[2]
//            sf.entityScores[index] = score
//        }
//    }

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

//        val documentQuery = AnalyzerFunctions.createWeightedTermsQuery(grams, gramStatType.indexField)
        val documentQuery = AnalyzerFunctions.createQuery(grams.joinToString(" "), gramStatType.indexField)
        val scores = entityDb.searcher.search(documentQuery, 20000)
            .scoreDocs
            .map { sc -> sc.doc to sc.score.toDouble() }
            .toMap()

        entityContainers.mapIndexed {  index, entityContainer ->
//            val score = entitySearcher.explainScore(documentQuery, entityContainer.docId )
            val score = scores[entityContainer.docId] ?: 0.0
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
            fmt.addFeature3(ENTITY_BM25, wt, norm, this::queryBm25Abstract)

    fun addDirichletAbstract(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(ENTITY_DIRICHLET, wt, norm, this::queryBm25Abstract)

//    fun addSDMAbstract(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
//            fmt.addFeature3(ENTITY_SDM, wt, norm, this::querySDMAbstract)

    fun addTop25Freq(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(ENTITY_TOP25_FREQ, wt, norm, this::entityTop25Freq)


    fun addBM25BoostedUnigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(ENTITY_BOOSTED_UNIGRAM, wt, norm) { qd, sf -> entityBoostedGram(qd, sf, TYPE_UNIGRAM) }

    fun addBM25BoostedBigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(ENTITY_BOOSTED_BIGRAM, wt, norm) { qd, sf -> entityBoostedGram(qd, sf, TYPE_BIGRAM) }

    fun addBM25BoostedWindowedBigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(ENTITY_BOOSTED_WINDOW, wt, norm) { qd, sf -> entityBoostedGram(qd, sf, TYPE_BIGRAM_WINDOW) }

    fun addInlinksField(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(ENTITY_INLINKS_FIELD, wt, norm) { qd, sf -> queryField(qd, sf, IndexFields.FIELD_INLINKS_UNIGRAMS) }

    fun addOutlinksField(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(ENTITY_OUTLINKS_FIELD, wt, norm) { qd, sf -> queryField(qd, sf, IndexFields.FIELD_OUTLINKS_UNIGRAMS) }

    fun addDisambigField(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(ENTITY_DISAMBIG_FIELD, wt, norm) { qd, sf -> queryField(qd, sf, IndexFields.FIELD_DISAMBIGUATIONS_UNIGRAMS) }

    fun addRedirectField(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(ENTITY_REDIRECTS_FIELD, wt, norm) { qd, sf -> queryField(qd, sf, IndexFields.FIELD_REDIRECTS_UNIGRAMS) }

    fun addCategoriesField(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(ENTITY_CATEGORIES_FIELD, wt, norm) { qd, sf -> queryField(qd, sf, IndexFields.FIELD_CATEGORIES_UNIGRAMS) }

    fun addSections(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(ENTITY_SECTIONS_FIELD, wt, norm) { qd, sf -> queryField(qd, sf, IndexFields.FIELD_SECTION_UNIGRAM) }

}
