package features.document

import experiment.FeatureType
import experiment.KotlinRanklibFormatter
import experiment.NormType
import experiment.NormType.ZSCORE
import features.shared.SharedFeature
import language.GramAnalyzer
import language.GramStatType
import language.GramStatType.*
import language.containers.LanguageStatContainer
import lucene.FieldQueryFormatter
import lucene.containers.FieldNames
import lucene.containers.QueryData
import org.apache.lucene.index.Term
import org.apache.lucene.search.BoostQuery
import org.apache.lucene.search.TermQuery
import utils.AnalyzerFunctions
import utils.AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED

import utils.lucene.explainScore
import utils.lucene.splitAndCount
import utils.misc.CONTENT
import utils.stats.countDuplicates
import utils.stats.normalize
import utils.stats.takeMostFrequent


object DocumentRankingFeatures {
    private fun queryBM25Document(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val documentQuery = AnalyzerFunctions.createQuery(qd.queryString, field = CONTENT, useFiltering = true)
        paragraphContainers.mapIndexed {  index, paragraphContainer ->
            val score = paragraphSearcher.explainScore(documentQuery, paragraphContainer.docId )
            sf.paragraphScores[index] = score
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
        val weights = listOf(0.8570786290155611, 0.07934068299135334, 0.0635806879930856)

        queryFormatter.addWeightedQueryTokens(terms, FieldNames.FIELD_UNIGRAMS, weights[0])
        queryFormatter.addWeightedQueryTokens(terms, FieldNames.FIELD_BIGRAMS, weights[1])
        queryFormatter.addWeightedQueryTokens(terms, FieldNames.FIELD_WINDOWED_BIGRAMS, weights[2])
//        queryFormatter.addNormalQuery(queryString, FieldNames.FIELD_TEXT, weights[3])
        val documentQuery = queryFormatter.createBooleanQuery()

        paragraphContainers.mapIndexed {  index, paragraphContainer ->
            val score = paragraphSearcher.explainScore(documentQuery, paragraphContainer.docId )
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
        fmt.addFeature3(this::queryBM25Document, FeatureType.PARAGRAPH, wt, norm)

    fun addSDMDocument(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(this::querySDMDocument, FeatureType.PARAGRAPH, wt, norm)

    fun addBM25BoostedUnigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3({ qd, sf -> queryBM25BoostedGram(qd, sf, TYPE_UNIGRAM) },
                FeatureType.PARAGRAPH, wt, norm)

    fun addBM25BoostedBigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3({ qd, sf -> queryBM25BoostedGram(qd, sf, TYPE_BIGRAM) },
                    FeatureType.PARAGRAPH, wt, norm)

    fun addBM25BoostedWindowedBigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3({ qd, sf -> queryBM25BoostedGram(qd, sf, TYPE_BIGRAM_WINDOW) },
                    FeatureType.PARAGRAPH, wt, norm)
    fun addCombinedBoostedGram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(this::combinedBoostedGram, FeatureType.PARAGRAPH, wt, norm)
}