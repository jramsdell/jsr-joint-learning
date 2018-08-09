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
import lucene.containers.FeatureEnum.*
import lucene.containers.paragraphs
import lucene.containers.text
import lucene.containers.unigrams
import lucene.indexers.boostedTermQuery
import lucene.indexers.getList
import lucene.indexers.termQuery
import org.apache.lucene.index.Term
import org.apache.lucene.queries.function.BoostedQuery
import org.apache.lucene.queryparser.xml.builders.BooleanQueryBuilder
import org.apache.lucene.search.*
import utils.stats.*
import java.lang.Double.sum
import java.lang.Math.log


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

        val ff = qd.sectionPaths.flatten()
        val paths = qd.sectionPaths + listOf(ff)
        paths.forEach { sp ->

            val sQuery = sp.joinToString(" ")
//            val sQuery = queryString
            val terms = AnalyzerFunctions.createTokenList(sQuery, analyzerType = ANALYZER_ENGLISH_STOPPED, useFiltering = true)
            val grams = when (gramStatType) {
                TYPE_UNIGRAM       -> GramAnalyzer.countUnigrams(terms)
                    .takeMostFrequent(15)
                    .keys.toList()
//                .normalize()
                TYPE_BIGRAM        -> GramAnalyzer.countBigrams(terms)
                    .takeMostFrequent(15)
                    .keys.toList()
//                .normalize()
                TYPE_BIGRAM_WINDOW -> GramAnalyzer.countWindowedBigrams(terms)
                    .takeMostFrequent(15)
                    .keys.toList()
//                .normalize()
            }

            val documentQuery = AnalyzerFunctions.createWeightedTermsQuery(grams, gramStatType.indexField)
            val scores = paragraphSearcher.search(documentQuery, 1000)
                .scoreDocs
                .map { it.doc to it.score.toDouble() }
                .toMap()

            paragraphContainers.mapIndexed { index, paragraphContainer ->
                //                val score = paragraphSearcher.explainScore(documentQuery, paragraphContainer.docId)
                val score = scores[paragraphContainer.docId] ?: 0.0
                sf.paragraphScores[index] = Math.max(score, sf.paragraphScores[index])

            }
        }
    }

    //0.3512, 0.1854

    private fun combinedBoostedGram(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val terms = AnalyzerFunctions.createTokenList(queryString, analyzerType = ANALYZER_ENGLISH_STOPPED, useFiltering = true)
        val queryFormatter = FieldQueryFormatter()
        val weights = listOf(0.9346718895308014, 0.049745249968994265, 0.015582860500204451)

//        val replaceNumbers = """(\d+|enwiki:|%)""".toRegex()
//        val finalQuery =
//                 queryString.replace(replaceNumbers, "").replace("/", " ")
//        val replaceNumbers2 = """(\d+|enwiki:)""".toRegex()
//        val finalQuery2 =
//                queryString.replace(replaceNumbers2, "").replace("/", " ")
//
//        println("$finalQuery : $finalQuery2")
//        println("$queryString: $terms")


        queryFormatter.addWeightedQueryTokens(terms, IndexFields.FIELD_UNIGRAM, weights[0])
        queryFormatter.addWeightedQueryTokens(terms, IndexFields.FIELD_BIGRAM, weights[1])
        queryFormatter.addWeightedQueryTokens(terms, IndexFields.FIELD_WINDOWED_BIGRAM, weights[2])
        val documentQuery = queryFormatter.createBooleanQuery()

        val scores = paragraphSearcher.searchToScoreMap(documentQuery, 2000)

        paragraphContainers.mapIndexed { index, paragraphContainer ->
//            val score = paragraphSearcher.explainScore(documentQuery, paragraphContainer.docId)
//            sf.paragraphScores[index] = score
            sf.paragraphScores[index] = scores[paragraphContainer.docId] ?: 0.0
        }
    }
    private fun combinedBoostedGramDisjunct(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val booleanQueries = sectionPaths.map { sp ->
            val terms = AnalyzerFunctions.createTokenList(sp.joinToString(" "), analyzerType = ANALYZER_ENGLISH_STOPPED, useFiltering = true)
            val queryFormatter = FieldQueryFormatter()
            val weights = listOf(0.9346718895308014, 0.049745249968994265, 0.015582860500204451)

            queryFormatter.addWeightedQueryTokens(terms, IndexFields.FIELD_UNIGRAM, weights[0])
                .addWeightedQueryTokens(terms, IndexFields.FIELD_BIGRAM, weights[1])
                .addWeightedQueryTokens(terms, IndexFields.FIELD_WINDOWED_BIGRAM, weights[2])
                .createBooleanQuery()
        }

        val disjunctQuery = DisjunctionMaxQuery(booleanQueries, 0.0f)
        val scores = paragraphSearcher.searchToScoreMap(disjunctQuery, 2000)

        paragraphContainers.mapIndexed { index, paragraphContainer ->
            //            val score = paragraphSearcher.explainScore(disjunctQuery, paragraphContainer.docId)
            val score = scores[paragraphContainer.docId] ?: 0.0
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

    private fun queryDist(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val qDist = AnalyzerFunctions.createTokenList(queryString, ANALYZER_ENGLISH_STOPPED, useFiltering = true)
//            .flatMap { it.windowed(2) }
            .countDuplicates()
//            .normalize()

        paragraphContainers.forEach { container ->
            val cDist = AnalyzerFunctions.createTokenList(container.doc().text(),
                    ANALYZER_ENGLISH_STOPPED)
//                .flatMap { it.windowed(2) }
                .countDuplicates()
                .mapValues { it.value.toDouble() }
//                .toList()
//                .sortedByDescending { it.second }
//                .take(15)
//                .toMap()
//                .normalize()

            val cTot = cDist.values.sum()

            val score = qDist.entries.sumByDouble { (token, freq) ->
                log((cDist[token] ?: 0.5) * freq) }
//                log((cDist[token] ?: 0.01) * freq * paragraphSearcher.indexReader.totalTermFreq(Term(IndexFields.FIELD_UNIGRAM.field, token)) / paragraphSearcher.indexReader.getSumTotalTermFreq(IndexFields.FIELD_UNIGRAM.field))  }
//                (cDist[token] ?: 0.000) * freq * paragraphSearcher.indexReader.totalTermFreq(Term(IndexFields.FIELD_UNIGRAM.field, token)) / paragraphSearcher.indexReader.getSumTotalTermFreq(IndexFields.FIELD_UNIGRAM.field)  }
            sf.paragraphScores[container.index] = score
        }
    }

    private fun queryField(qd: QueryData, sf: SharedFeature, field: IndexFields): Unit = with(qd) {
        // Parse query and retrieve a language model for it

        val tokens = AnalyzerFunctions.createTokenList(queryString, useFiltering = true,
                analyzerType = ANALYZER_ENGLISH_STOPPED)
        val fq = FieldQueryFormatter()
        val fieldQuery = fq.addWeightedQueryTokens(tokens, field).createBooleanQuery()


        val scores = paragraphSearcher.searchToScoreMap(fieldQuery, 4000)
        paragraphContainers.forEachIndexed { index, container ->
            val score = scores[container.docId] ?: 0.0
            sf.paragraphScores[index] = score
        }
    }

    private fun queryFieldRequired(qd: QueryData, sf: SharedFeature, field: IndexFields): Unit = with(qd) {
        val elements = queryString.split("/")
        val topLevel = AnalyzerFunctions.createTokenList(elements.last(), useFiltering = true, analyzerType = ANALYZER_ENGLISH_STOPPED)
        val builder = BooleanQuery.Builder()

        topLevel.forEach { token ->
            builder.add(TermQuery(Term(field.field, token)), BooleanClause.Occur.MUST)
        }

        val fieldQuery = builder.build()
        val scores = paragraphSearcher.searchToScoreMap(fieldQuery, 4000)
        paragraphContainers.forEachIndexed { index, container ->
            val score = scores[container.docId] ?: 0.0
            sf.paragraphScores[index] = score
        }
    }

    private fun doIdentity(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        paragraphContainers.forEachIndexed { index, pContainer ->
            sf.paragraphScores[index] = pContainer.isRelevant.toDouble()
        }
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
        val scores = paragraphSearcher.searchToScoreMap(comboQuery, 4000)
        paragraphContainers.forEachIndexed { index, container ->
            val score = scores[container.docId] ?: 0.0
            sf.paragraphScores[index] += score
        }
    }

    private fun queryFieldLog(qd: QueryData, sf: SharedFeature, field: IndexFields): Unit = with(qd) {
        // Parse query and retrieve a language model for it
        val elements = queryString.split("/")
        sf.paragraphScores.fill(1.0)
        elements.reversed().forEachIndexed { i, e ->
            val topLevel = AnalyzerFunctions.createTokenList(e, useFiltering = true, analyzerType = ANALYZER_ENGLISH_STOPPED)
            val builder = BooleanQuery.Builder()
            topLevel.forEach { token ->
                builder.add(TermQuery(Term(field.field, token)), BooleanClause.Occur.MUST)
            }
            val fieldQuery = builder.build()
            val scores = paragraphSearcher.searchToScoreMap(fieldQuery, 4000)
            paragraphContainers.forEachIndexed { index, container ->
                val score = scores[container.docId] ?: 0.0
                sf.paragraphScores[index] += Math.pow(0.5, i.toDouble()) * score
            }
        }


    }

    private fun queryFieldExpanded(qd: QueryData, sf: SharedFeature, field: IndexFields): Unit = with(qd) {
        // Parse query and retrieve a language model for it

        val tokens = AnalyzerFunctions.createTokenList(queryString, useFiltering = true,
                analyzerType = ANALYZER_ENGLISH_STOPPED)

        val unigramDist = paragraphContainers.flatMap { pContainer ->
            pContainer.doc().unigrams().split(" ")
                .filter { it != "" }
                .windowed(2, partialWindows = false)
                .map { it[0] to it[1] } }
            .groupBy { it.first }
            .mapValues { it.value.map { it.second }.countDuplicates().normalize().takeMostFrequent(2).toList() }


        val queries = tokens
            .mapNotNull { token -> unigramDist[token]?.to(token) }
            .flatMap { expandedTerms ->
                expandedTerms.first.map { term ->
                    BooleanQuery.Builder()
                        .add(IndexFields.FIELD_UNIGRAM.termQuery(expandedTerms.second), BooleanClause.Occur.MUST)
                        .add( IndexFields.FIELD_UNIGRAM.boostedTermQuery(term.first, term.second), BooleanClause.Occur.MUST )
                        .build()
                }
            }


        val fieldQuery = queries.fold(BooleanQuery.Builder()) { builder, q ->
            builder.add(q, BooleanClause.Occur.SHOULD) }
            .build()


        val scores = paragraphSearcher.searchToScoreMap(fieldQuery, 4000)
        paragraphContainers.forEachIndexed { index, container ->
            val score = scores[container.docId] ?: 0.0
            sf.paragraphScores[index] = score
        }
    }

    private fun queryFieldExpandedSections(qd: QueryData, sf: SharedFeature, field: IndexFields): Unit = with(qd) {
        // Parse query and retrieve a language model for it

        val paths = sectionPaths.map { sp -> sp.joinToString(" ") }

        val unigramDist = paragraphContainers.flatMap { pContainer ->
            pContainer.doc().unigrams().split(" ")
                .filter { it != "" }
                .windowed(2, partialWindows = false)
                .map { it[0] to it[1] }
        }
            .groupBy { it.first }
            .mapValues { it.value.map { it.second }.countDuplicates().normalize().takeMostFrequent(5).toList() }

        val pathQueries = paths.map { sp ->
            val tokens = AnalyzerFunctions.createTokenList(sp, useFiltering = true,
                    analyzerType = ANALYZER_ENGLISH_STOPPED)

            val queries = tokens
                .mapNotNull { token -> unigramDist[token]?.to(token) }
                .flatMap { expandedTerms ->
                    expandedTerms.first.map { term ->
                        BooleanQuery.Builder()
                            .add(IndexFields.FIELD_UNIGRAM.termQuery(expandedTerms.second), BooleanClause.Occur.MUST)
                            .add( IndexFields.FIELD_UNIGRAM.boostedTermQuery(term.first, term.second), BooleanClause.Occur.MUST )
                            .build()
                    }
                }

            val fieldQuery = queries.fold(BooleanQuery.Builder()) { builder, q ->
                builder.add(q, BooleanClause.Occur.SHOULD) }
                .build()
            fieldQuery }


        val disjointQuery = DisjunctionMaxQuery(pathQueries, 0.0f)







        val scores = paragraphSearcher.searchToScoreMap(disjointQuery, 4000)
        paragraphContainers.forEachIndexed { index, container ->
            val score = scores[container.docId] ?: 0.0
            sf.paragraphScores[index] = score
        }

    }

    private fun queryFieldSections(qd: QueryData, sf: SharedFeature, field: IndexFields, sectionLevel: Int? = null): Unit = with(qd) {
        // Parse query and retrieve a language model for it

        qd.sectionPaths
            .filter { sectionLevel == null || it.size == sectionLevel }
            .forEach { sp ->
                val sQuery = sp.joinToString(" ")
                val tokens = AnalyzerFunctions.createTokenList(sQuery, useFiltering = true,
                        analyzerType = ANALYZER_ENGLISH_STOPPED)
                val fq = FieldQueryFormatter()
                val fieldQuery = fq.addWeightedQueryTokens(tokens, field).createBooleanQuery()
                val scores = paragraphSearcher.searchToScoreMap(fieldQuery, 4000)


                paragraphContainers.forEachIndexed { index, container ->
                    //                val score = paragraphSearcher.explainScore(fieldQuery, container.docId)
                    val score = scores[container.docId] ?: 0.0
//            sf.paragraphScores[index] = scores[container.docId] ?: 0.0
                    sf.paragraphScores[index] = Math.max(score, sf.paragraphScores[index])
                }
            }
    }




    private fun querySDMDocument(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        // Parse query and retrieve a language model for it
        val tokens = AnalyzerFunctions.createTokenList(queryString, useFiltering = true,
                analyzerType = ANALYZER_ENGLISH_STOPPED)
        val cleanQuery = tokens.toList().joinToString(" ")
        val gramAnalyzer = GramAnalyzer(paragraphSearcher)
        val queryCorpus = gramAnalyzer.getCorpusStatContainer(cleanQuery)
        val weights = listOf(0.9285990421606605, 0.070308081629, -0.0010928762)

        paragraphContainers.forEachIndexed { index, container ->
            val doc = container.doc()
            val docStat = LanguageStatContainer.createLanguageStatContainer(doc)
            val (uniLike, biLike, windLike) = gramAnalyzer.getQueryLikelihood(docStat, queryCorpus, 2.0)
            val score = uniLike * weights[0] + biLike * weights[1] + windLike * weights[2]
            sf.paragraphScores[index] = score
        }
    }


    private fun constantScore(qd: QueryData, sf: SharedFeature, constant: Double): Unit = with(qd) {
        sf.paragraphScores.fill(constant)
    }

    private fun scoreBySectionLinks(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val sectionLinks = sectionContainers
            .flatMap { sContainer -> sContainer.doc().paragraphs().split(" ") }
            .countDuplicates()

        paragraphContainers.forEachIndexed { index, pContainer ->
            sf.paragraphScores[index] = sectionLinks[pContainer.name]?.toDouble() ?: 0.0
        }
    }

    fun addConstantScore(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_CONSTANT_SCORE, wt, norm, { qd, sf -> constantScore(qd, sf, 1.0) })

    fun addSectionFreq(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_SECTION_FREQ, wt, norm, this::scoreBySectionLinks)

    fun addBM25Document(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_BM25, wt, norm, this::queryBM25Document)


//    fun addSDMDocument(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
//            fmt.addFeature3(DOC_SDM, wt, norm, this::querySDMDocument)


    fun addBM25BoostedUnigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_BOOSTED_UNIGRAM, wt, norm) { qd, sf -> queryBM25BoostedGram(qd, sf, TYPE_UNIGRAM) }

    fun addBM25BoostedBigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_BOOSTED_BIGRAM, wt, norm) { qd, sf -> queryBM25BoostedGram(qd, sf, TYPE_BIGRAM) }

    fun addBM25BoostedWindowedBigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_BOOSTED_WINDOW, wt, norm)  { qd, sf -> queryBM25BoostedGram(qd, sf, TYPE_BIGRAM_WINDOW) }

    fun addWindowGram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_BOOSTED_WINDOW, wt, norm)  { qd, sf -> queryField(qd, sf, IndexFields.FIELD_LETTER_3) }

    fun addIdentity(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_BOOSTED_WINDOW, wt, norm, this::doIdentity)

    fun addUnigram2(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_BOOSTED_WINDOW, wt, norm)  { qd, sf -> queryFieldSections(qd, sf, IndexFields.FIELD_UNIGRAM, 2) }

    fun addUnigram3(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_BOOSTED_WINDOW, wt, norm)  { qd, sf -> queryFieldSections(qd, sf, IndexFields.FIELD_UNIGRAM, 3) }

    fun addUnigram4(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_BOOSTED_WINDOW, wt, norm)  { qd, sf -> queryFieldSections(qd, sf, IndexFields.FIELD_UNIGRAM, 4) }

    fun addWindowGram2(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_BOOSTED_WINDOW, wt, norm)  { qd, sf -> queryField(qd, sf, IndexFields.FIELD_LETTER_4) }

    fun addBigramExpanded(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_BOOSTED_WINDOW, wt, norm)  { qd, sf -> queryFieldExpanded(qd, sf, IndexFields.FIELD_BIGRAM) }

//    fun addBigramExpanded2(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
//            fmt.addFeature3(DOC_BOOSTED_WINDOW, wt, norm)  { qd, sf -> queryFieldExpanded(qd, sf, IndexFields.FIELD_BIGRAM) }

    fun addCombinedBoostedGram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_BOOSTED_COMBINED, wt, norm, this::combinedBoostedGram)

    fun addCombinedBoostedGramDisjunction(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_BOOSTED_COMBINED, wt, norm, this::combinedBoostedGramDisjunct)

    fun addJointUnigramField(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_JOINT_UNIGRAM_FIELD, wt, norm) { qd, sf -> queryField(qd, sf, IndexFields.FIELD_JOINT_UNIGRAMS) }

    fun addJointBigramField(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_JOINT_BIGRAM_FIELD, wt, norm) { qd, sf -> queryField(qd, sf, IndexFields.FIELD_JOINT_BIGRAMS) }

    fun addNormalBM25(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_JOINT_BIGRAM_FIELD, wt, norm) { qd, sf -> queryField(qd, sf, IndexFields.FIELD_TEXT_STEMMED) }

    fun addNormalBM25Combo(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_JOINT_BIGRAM_FIELD, wt, norm) { qd, sf -> queryFieldCombo(qd, sf, IndexFields.FIELD_TEXT_STEMMED) }

    fun addNormalBM25First(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_JOINT_BIGRAM_FIELD, wt, norm) { qd, sf -> queryFieldRequired(qd, sf, IndexFields.FIELD_TEXT_STEMMED) }

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

    fun addSDMDocument(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_SDM, wt, norm) { qd, sf -> querySDMDocument(qd, sf) }

    fun addQueryDist(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(DOC_QUERY_DIST, wt, norm, this::queryDist)



//    fun addDistScore(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
//            fmt.addFeature3(DOC_JOINT_ENTITIES_FIELD, wt, norm, this::distributeByScore)

//    fun addSectionBoostedGrams(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE, index: Int) =
//            fmt.addFeature3( FeatureType.PARAGRAPH, wt, norm) { qd, sf -> sectionBoostedGrams(qd, sf, index)}
}