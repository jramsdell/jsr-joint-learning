package features.document

import experiment.KotlinRanklibFormatter
import experiment.NormType
import experiment.NormType.ZSCORE
import features.shared.SharedFeature
import language.GramAnalyzer
import language.GramStatType
import language.GramStatType.*
import language.containers.LanguageStatContainer
import lucene.FieldQueryFormatter
import lucene.indexers.IndexFields
import lucene.containers.QueryData
import utils.AnalyzerFunctions
import utils.AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED

import utils.lucene.explainScore
import utils.stats.takeMostFrequent
import lucene.containers.FeatureEnum.*


object DocumentRankingFeatures {
    private fun queryBM25Document(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
//        val documentQuery = AnalyzerFunctions.createQuery(qd.queryString, field = CONTENT, useFiltering = true)
        paragraphContainers.mapIndexed {  index, paragraphContainer ->
//            val score = paragraphSearcher.explainScore(documentQuery, paragraphContainer.docId )
            sf.paragraphScores[index] = paragraphContainer.score
        }
    }

    private fun queryBM25BoostedGram(qd: QueryData, sf: SharedFeature, gramStatType: GramStatType,
                                weight: Double = 1.0): Unit = with(qd) {
        val terms = AnalyzerFunctions.createTokenList(queryString, analyzerType = ANALYZER_ENGLISH_STOPPED, useFiltering = true)
        val grams = when(gramStatType) {
            TYPE_UNIGRAM -> GramAnalyzer.countUnigrams(terms)
                .takeMostFrequent(15)
                .keys.toList()
//                .normalize()
            TYPE_BIGRAM -> GramAnalyzer.countBigrams(terms)
                .takeMostFrequent(15)
                .keys.toList()
//                .normalize()
            TYPE_BIGRAM_WINDOW -> GramAnalyzer.countWindowedBigrams(terms)
                .takeMostFrequent(15)
                .keys.toList()
//                .normalize()
        }

        val documentQuery = AnalyzerFunctions.createWeightedTermsQuery(grams, gramStatType.indexField)

        paragraphContainers.mapIndexed {  index, paragraphContainer ->
            val score = paragraphSearcher.explainScore(documentQuery, paragraphContainer.docId )
            sf.paragraphScores[index] = score
        }
    }

    private fun combinedBoostedGram(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val terms = AnalyzerFunctions.createTokenList(queryString, analyzerType = ANALYZER_ENGLISH_STOPPED, useFiltering = true)
        val queryFormatter = FieldQueryFormatter()
        val weights = listOf(0.9346718895308014 , 0.049745249968994265 , 0.015582860500204451 )

        queryFormatter.addWeightedQueryTokens(terms, IndexFields.FIELD_UNIGRAM, weights[0])
        queryFormatter.addWeightedQueryTokens(terms, IndexFields.FIELD_BIGRAM, weights[1])
        queryFormatter.addWeightedQueryTokens(terms, IndexFields.FIELD_WINDOWED_BIGRAM, weights[2])
        val documentQuery = queryFormatter.createBooleanQuery()

        paragraphContainers.mapIndexed {  index, paragraphContainer ->
            val score = paragraphSearcher.explainScore(documentQuery, paragraphContainer.docId )
            sf.paragraphScores[index] = score
        }
    }

    private fun sectionBoostedGrams(qd: QueryData, sf: SharedFeature, sectionIndex: Int): Unit = with(qd) {
        val sections = AnalyzerFunctions.splitSections(queryString, analyzerType = ANALYZER_ENGLISH_STOPPED)
        val section = sections.getOrNull(sectionIndex) ?: emptyList()
        val weights = listOf(0.9346718895308014 , 0.049745249968994265 , 0.015582860500204451 )
        val queryFormatter = FieldQueryFormatter()
        queryFormatter.addWeightedQueryTokens(section, IndexFields.FIELD_UNIGRAM, weights[0])
        queryFormatter.addWeightedQueryTokens(section, IndexFields.FIELD_BIGRAM, weights[1])
        queryFormatter.addWeightedQueryTokens(section, IndexFields.FIELD_WINDOWED_BIGRAM, weights[2])
        val documentQuery = queryFormatter.createBooleanQuery()

        paragraphContainers.mapIndexed {  index, paragraphContainer ->
            val score = paragraphSearcher.explainScore(documentQuery, paragraphContainer.docId )
            sf.paragraphScores[index] = score
        }
    }

    private fun queryField(qd: QueryData, sf: SharedFeature, field: IndexFields): Unit = with(qd) {
        // Parse query and retrieve a language model for it
        val tokens = AnalyzerFunctions.createTokenList(queryString, useFiltering = true,
                analyzerType = ANALYZER_ENGLISH_STOPPED)
        val fq = FieldQueryFormatter()
        val fieldQuery = fq.addWeightedQueryTokens(tokens, field).createBooleanQuery()
//        val fieldQuery = AnalyzerFunctions.createQuery(queryString, field.field, useFiltering = true, analyzerType = ANALYZER_ENGLISH_STOPPED)

//        val scores = paragraphSearcher.search(fieldQuery, 2000)
//            .scoreDocs
//            .map { sc -> sc.doc to sc.score.toDouble() }
//            .toMap()


        paragraphContainers.forEachIndexed { index, container ->
            val score = paragraphSearcher.explainScore(fieldQuery, container.docId)
//            sf.paragraphScores[index] = scores[container.docId] ?: 0.0
            sf.paragraphScores[index] = score
        }
    }

//    private fun queryCombinedBoostedGram(qd: QueryData, sf: SharedFeature,
//                                     weight: Double = 1.0): Unit = with(qd) {
//        val terms = AnalyzerFunctions.createTokenList(queryString, analyzerType = ANALYZER_ENGLISH_STOPPED, useFiltering = true)
//        val unigrams = GramAnalyzer.countUnigrams(terms).takeMostFrequent(15).normalize()
//        val bigrams = GramAnalyzer.countBigrams(terms).takeMostFrequent(15).normalize()
//        val windows = GramAnalyzer.countWindowedBigrams(terms).takeMostFrequent(15).normalize()
//
//
//
////        val documentQuery = AnalyzerFunctions.createWeightedTermsQuery(grams, gramStatType.indexField)
//
//        paragraphContainers.mapIndexed {  index, paragraphContainer ->
//            val score = paragraphSearcher.explainScore(documentQuery, paragraphContainer.docId )
//            sf.paragraphScores[index] = score
//        }
//    }


    private fun querySDMDocument(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        // Parse query and retrieve a language model for it
        val tokens = AnalyzerFunctions.createTokenList(queryString, useFiltering = true,
                analyzerType = ANALYZER_ENGLISH_STOPPED)
        val cleanQuery = tokens.toList().joinToString(" ")
        val gramAnalyzer = GramAnalyzer(paragraphSearcher)
        val queryCorpus = gramAnalyzer.getCorpusStatContainer(cleanQuery)
        val weights = listOf(0.9285990421606605, 0.070308081629, -0.0010928762)

        paragraphDocuments.forEachIndexed { index, doc ->
            val docStat = LanguageStatContainer.createLanguageStatContainer(doc)
            val (uniLike, biLike, windLike) = gramAnalyzer.getQueryLikelihood(docStat, queryCorpus, 2.0)
            val score = uniLike * weights[0] + biLike * weights[1] + windLike * weights[2]
            sf.paragraphScores[index] = score
        }
    }

    fun addBM25Document(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
        fmt.addFeature3(DOC_BM25, wt, norm, this::queryBM25Document)

    fun addSDMDocument(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_SDM, wt, norm, this::querySDMDocument)

    fun addBM25BoostedUnigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_BOOSTED_UNIGRAM, wt, norm) { qd, sf -> queryBM25BoostedGram(qd, sf, TYPE_UNIGRAM) }

    fun addBM25BoostedBigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_BOOSTED_BIGRAM, wt, norm) { qd, sf -> queryBM25BoostedGram(qd, sf, TYPE_BIGRAM) }

    fun addBM25BoostedWindowedBigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_BOOSTED_WINDOW, wt, norm)  { qd, sf -> queryBM25BoostedGram(qd, sf, TYPE_BIGRAM_WINDOW) }

    fun addCombinedBoostedGram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_BOOSTED_COMBINED, wt, norm, this::combinedBoostedGram)

    fun addJointUnigramField(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_JOINT_UNIGRAM_FIELD, wt, norm) { qd, sf -> queryField(qd, sf, IndexFields.FIELD_JOINT_UNIGRAMS) }

    fun addJointBigramField(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_JOINT_BIGRAM_FIELD, wt, norm) { qd, sf -> queryField(qd, sf, IndexFields.FIELD_JOINT_BIGRAMS) }

    fun addJointWindowedField(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_JOINT_WINDOWED_FIELD, wt, norm) { qd, sf -> queryField(qd, sf, IndexFields.FIELD_JOINT_WINDOWED) }

    fun addUnionUnigramField(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_UNION_UNIGRAM_FIELD, wt, norm) { qd, sf -> queryField(qd, sf, IndexFields.FIELD_NEIGHBOR_UNIGRAMS) }

    fun addUnionBigramField(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_UNION_BIGRAM_FIELD, wt, norm) { qd, sf -> queryField(qd, sf, IndexFields.FIELD_NEIGHBOR_BIGRAMS) }

    fun addUnionWindowedField(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_UNION_WINDOWED_FIELD, wt, norm) { qd, sf -> queryField(qd, sf, IndexFields.FIELD_NEIGHBOR_WINDOWED) }

    fun addJointEntityField(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_JOINT_ENTITIES_FIELD, wt, norm) { qd, sf -> queryField(qd, sf, IndexFields.FIELD_NEIGHBOR_ENTITIES_UNIGRAMS) }

//    fun addSectionBoostedGrams(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE, index: Int) =
//            fmt.addFeature3( FeatureType.PARAGRAPH, wt, norm) { qd, sf -> sectionBoostedGrams(qd, sf, index)}
}