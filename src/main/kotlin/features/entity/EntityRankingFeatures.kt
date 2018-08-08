package features.entity

import entity.EntityDatabase
import experiment.FeatureType
import experiment.KotlinRanklibFormatter
import experiment.NormType
import experiment.NormType.*
import features.shared.SharedFeature
import info.debatty.java.stringsimilarity.NormalizedLevenshtein
import jdk.nashorn.internal.parser.Token
import khttp.patch
import language.GramAnalyzer
import language.GramStatType
import language.GramStatType.*
import language.containers.LanguageStatContainer
import lucene.FieldQueryFormatter
import lucene.containers.*
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
import lucene.indexers.boostedTermQuery
import lucene.indexers.termQuery
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.BoostQuery
import org.apache.lucene.search.DisjunctionMaxQuery
import org.apache.lucene.search.similarities.LMDirichletSimilarity
import org.apache.lucene.util.QueryBuilder
import utils.lucene.docs


object EntityRankingFeatures {
    private fun queryBm25Abstract(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val abstractQuery = AnalyzerFunctions.createQuery(qd.queryString, field = IndexFields.FIELD_TEXT.field, useFiltering = true)
        val scores = entitySearcher.search(abstractQuery, 200000)
            .scoreDocs
            .map { sc -> sc.doc to sc.score.toDouble() }
            .toMap()
        entityContainers.mapIndexed {  index, entity ->
            val score = scores[entity.docId] ?: 0.0
            sf.entityScores[index] = score
        }
    }

    private fun queryDirichletAbstract(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        entitySearcher.setSimilarity(LMDirichletSimilarity(1.0f))
        val abstractQuery = AnalyzerFunctions.createQuery(qd.queryString, field = IndexFields.FIELD_TEXT.field, useFiltering = true)
        val scores = entitySearcher.search(abstractQuery, 200000)
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
//            doc.get(IndexFields.FIELD_ENTITIES.field).split(" ") }
//        doc.get(IndexFields.FIELD_NEIGHBOR_ENTITIES.field).split(" ") }
//        doc.load(IndexFields.FIELD_ENTITIES_EXTENDED).split(" ") }
        doc.load(IndexFields.FIELD_ENTITIES).split(" ") }
            .countDuplicates()

        entityContainers.forEachIndexed { index, eContainer ->
            sf.entityScores[index] = (counts[eContainer.name] ?: 0).toDouble()

        }
    }

    private fun topParagraphUnigrams(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
//        val freqQuery = paragraphContainers.flatMap { container ->
//            val doc = container.doc()
//            doc.load(IndexFields.FIELD_BIGRAM).split(" ") }
//            .countDuplicates()
//            .takeMostFrequent(20)
//            .toList()
//            .fold(BooleanQuery.Builder()) { builder, (term, freq) ->
//                builder.add(
//                        IndexFields.FIELD_BIGRAM.boostedTermQuery(term, freq.toDouble()),
//                        BooleanClause.Occur.SHOULD) }
//            .build()

        val freqQuery = paragraphContainers.map { pContainer ->
            pContainer.doc().bigrams()
                .split(" ")
                .countDuplicates()
                .map { IndexFields.FIELD_BIGRAM.boostedTermQuery(it.key, it.value.toDouble()) }
                .fold(BooleanQuery.Builder()) { builder, q -> builder.add(q, BooleanClause.Occur.SHOULD) }
                .build()
        }.fold(BooleanQuery.Builder()) { builder, term -> builder.add(term, BooleanClause.Occur.SHOULD) }
            .build()

//        val doc = container.doc()
//        doc.load(IndexFields.FIELD_BIGRAM).split(" ") }
//        .countDuplicates()
//        .takeMostFrequent(20)
//        .toList()
//        .fold(BooleanQuery.Builder()) { builder, (term, freq) ->
//            builder.add(
//                    IndexFields.FIELD_BIGRAM.boostedTermQuery(term, freq.toDouble()),
//                    BooleanClause.Occur.SHOULD) }
//        .build()


        val scores = contextEntitySearcher.search(freqQuery, 5000)
            .scoreDocs
            .map { it.doc to it.score.toDouble() }
            .toMap()
//        val scores = pseudo.entitySearcher.search(freqQuery, 5000)
//            .scoreDocs
//            .map { pseudo.entitySearcher.getIndexDoc(it.doc).load("did").toInt() to it.score.toDouble() }
//            .toMap()

        entityContainers.forEachIndexed { index, eContainer ->
            sf.entityScores[index] = scores[eContainer.docId] ?: 0.0
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


    private fun queryField(qd: QueryData, sf: SharedFeature, field: IndexFields, firstOnly: Boolean = true): Unit = with(qd) {
        queryFieldCombo(qd, sf, field)
//        val tokens = AnalyzerFunctions.createTokenList(queryString, useFiltering = true,
//                analyzerType = ANALYZER_ENGLISH_STOPPED)
//        val fq = FieldQueryFormatter()
//        val fieldQuery = fq.addWeightedQueryTokens(tokens, field).createBooleanQuery()
//        val scores = entitySearcher.searchToScoreMap(fieldQuery, 4000)
//
//        entityContainers.forEachIndexed { index, container ->
//            sf.entityScores[index] = scores[container.docId] ?: 0.0
//        }
    }


    private fun queryFieldCombo(qd: QueryData, sf: SharedFeature, field: IndexFields): Unit = with(qd) {
        val elements = queryString.split("/")
        val topLevel = AnalyzerFunctions.createTokenList(elements.last(), useFiltering = true, analyzerType = ANALYZER_ENGLISH_STOPPED)
        val builder = BooleanQuery.Builder()

        topLevel.forEach { token ->
            builder.add(field.boostedTermQuery(token, 1.0), BooleanClause.Occur.MUST)
        }
        val fieldQuery1 = builder.build()

//        val scores1 = paragraphSearcher.searchToScoreMap(fieldQuery1, 4000)
//        paragraphContainers.forEachIndexed { index, container ->
//            val score = scores1[container.docId] ?: 0.0
//            sf.paragraphScores[index] += score
//        }

        val builder2 = BooleanQuery.Builder()
        val tokens = AnalyzerFunctions.createTokenList(queryString, useFiltering = true, analyzerType = ANALYZER_ENGLISH_STOPPED)
        tokens.forEach { token ->
            builder2.add(field.boostedTermQuery(token, 1.0), BooleanClause.Occur.SHOULD)
        }


        val fieldQuery2 = builder2.build()
//        val scores2 = paragraphSearcher.searchToScoreMap(fieldQuery2, 4000)
//        paragraphContainers.forEachIndexed { index, container ->
//            val score = scores2[container.docId] ?: 0.0
//            sf.paragraphScores[index] += score
//        }

        val comboQuery = BooleanQuery.Builder()
            .add(BoostQuery(fieldQuery1, .25555711777636486f), BooleanClause.Occur.SHOULD)
            .add(BoostQuery(fieldQuery2, 0.7444428822236351f), BooleanClause.Occur.SHOULD)
//            .add(fieldQuery2, BooleanClause.Occur.MUST)
            .build()
        val scores = entitySearcher.searchToScoreMap(comboQuery, 4000)
        entityContainers.forEachIndexed { index, container ->
            val score = scores[container.docId] ?: 0.0
            sf.entityScores[index] += score
        }
    }


    private fun queryFieldDisjunction(qd: QueryData, sf: SharedFeature, field: IndexFields): Unit = with(qd) {
        val queries = qd.sectionPaths.map { sp ->
            val tokens = AnalyzerFunctions.createTokenList(sp.joinToString(" "), useFiltering = true,
                    analyzerType = ANALYZER_ENGLISH_STOPPED)
            val fq = FieldQueryFormatter()
            fq.addWeightedQueryTokens(tokens, field).createBooleanQuery()
        }
        val disjunctQuery = DisjunctionMaxQuery(queries, 0.0f)
        val scores = entitySearcher.searchToScoreMap(disjunctQuery, 4000)

        entityContainers.forEachIndexed { index, container ->
            val score = scores[container.docId] ?: 0.0
            sf.entityScores[index] = score
        }
    }

    private fun queryContext(qd: QueryData, sf: SharedFeature, field: IndexFields): Unit = with(qd) {
        val tokens = AnalyzerFunctions.createTokenList(queryString, useFiltering = true,
                analyzerType = ANALYZER_ENGLISH_STOPPED)
        val topParagraph = AnalyzerFunctions.createQuery(queryString, IndexFields.FIELD_UNIGRAM.field, true, ANALYZER_ENGLISH_STOPPED)
            .run { paragraphSearcher.search(this, 1).scoreDocs.firstOrNull()?.let { paragraphSearcher.getIndexDoc(it.doc) } }

        val combined = if (topParagraph == null || field != IndexFields.FIELD_BIGRAM || tokens.size > 1) tokens else tokens + topParagraph.unigrams().split(" ").countDuplicates().toList().sortedByDescending { it.second }.take(2).map { it.first }
        val fq = FieldQueryFormatter()
        val fieldQuery = fq
            .addWeightedQueryTokens(combined, field)
            .createBooleanQuery()

        val scores = contextEntitySearcher.search(fieldQuery, 4000)
            .scoreDocs
            .map { sc -> contextEntitySearcher.getIndexDoc(sc.doc).name().toLowerCase() to sc.score.toDouble() }
            .groupBy { it.first }
//            .mapValues { it.value.maxBy { it.second }!!.second }
            .mapValues { it.value.sumByDouble { it.second.toDouble() } }
            .toMap()


        entityContainers.forEachIndexed { index, container ->
            sf.entityScores[index] = scores[container.name.toLowerCase()] ?: 0.0
        }
    }

    private fun queryContextDisjunct(qd: QueryData, sf: SharedFeature, field: IndexFields): Unit = with(qd) {
        val queries = sectionPaths.map { sp ->
            val tokens = AnalyzerFunctions.createTokenList(sp.joinToString(" "), useFiltering = true,
                    analyzerType = ANALYZER_ENGLISH_STOPPED)
            val topParagraph = AnalyzerFunctions.createQuery(queryString, IndexFields.FIELD_UNIGRAM.field, true, ANALYZER_ENGLISH_STOPPED)
                .run { paragraphSearcher.search(this, 1).scoreDocs.firstOrNull()?.let { paragraphSearcher.getIndexDoc(it.doc) } }

            val combined = if (topParagraph == null || field != IndexFields.FIELD_BIGRAM || tokens.size > 1) tokens else tokens + topParagraph.unigrams().split(" ").countDuplicates().toList().sortedByDescending { it.second }.take(2).map { it.first }
            FieldQueryFormatter()
                .addWeightedQueryTokens(combined, field)
                .createBooleanQuery()
        }

        val disjunctQuery = DisjunctionMaxQuery(queries, 0.0f)
        val scores = contextEntitySearcher.search(disjunctQuery, 4000)
            .scoreDocs
            .map { sc -> contextEntitySearcher.getIndexDoc(sc.doc).name().toLowerCase() to sc.score.toDouble() }
            .groupBy { it.first }
            .mapValues { it.value.sumByDouble { it.second.toDouble() } }
            .toMap()


        entityContainers.forEachIndexed { index, container ->
            sf.entityScores[index] = scores[container.name.toLowerCase()] ?: 0.0
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
        val scores = entitySearcher.search(documentQuery, 20000)
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

    fun addTopParagraphUnigrams(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(ENTITY_TOP25_FREQ, wt, norm, this::topParagraphUnigrams)

    fun addBM25BoostedUnigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(ENTITY_BOOSTED_UNIGRAM, wt, norm) { qd, sf -> queryField(qd, sf, IndexFields.FIELD_UNIGRAM) }

//    fun addBM25BoostedUnigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
//            fmt.addFeature3(ENTITY_BOOSTED_UNIGRAM, wt, norm) { qd, sf -> entityBoostedGram(qd, sf, TYPE_UNIGRAM) }

    fun addBM25BoostedBigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(ENTITY_BOOSTED_BIGRAM, wt, norm) { qd, sf -> queryField(qd, sf, IndexFields.FIELD_BIGRAM) }

    fun addBM25BoostedWindowedBigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(ENTITY_BOOSTED_WINDOW, wt, norm) { qd, sf -> queryField(qd, sf, IndexFields.FIELD_WINDOWED_BIGRAM) }

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

    fun addContextUnigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(ENTITY_CONTEXT_UNIGRAMS, wt, norm) { qd, sf -> queryContext(qd, sf, IndexFields.FIELD_UNIGRAM) }

    fun addContextBigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(ENTITY_CONTEXT_BIGRAMS, wt, norm) { qd, sf -> queryContext(qd, sf, IndexFields.FIELD_BIGRAM) }

    fun addContextWindowed(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(ENTITY_CONTEXT_WINDOWED, wt, norm) { qd, sf -> queryContext(qd, sf, IndexFields.FIELD_WINDOWED_BIGRAM) }

}
