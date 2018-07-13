package features.section

import experiment.KotlinRanklibFormatter
import experiment.NormType
import features.shared.SharedFeature
import lucene.FieldQueryFormatter
import lucene.containers.*
import lucene.indexers.IndexFields
import org.apache.lucene.index.Term
import utils.AnalyzerFunctions
import utils.stats.countDuplicates
import utils.stats.normalize


object SectionRankingFeatures {

    private fun queryField(qd: QueryData, sf: SharedFeature, field: IndexFields): Unit = with(qd) {
        val tokens = AnalyzerFunctions.createTokenList(queryString, useFiltering = true,
                analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)
        val fq = FieldQueryFormatter()
        val fieldQuery = fq.addWeightedQueryTokens(tokens, field).createBooleanQuery()

        val scores = sectionSearcher.search(fieldQuery, 5000)
            .scoreDocs
            .map { sc -> sc.doc to sc.score.toDouble() }
            .toMap()


        sectionContainers.forEachIndexed { index, container ->
            sf.sectionScores[index] = scores[container.docId] ?: 0.0
        }
    }

    private fun queryDist(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val qDist = AnalyzerFunctions.createTokenList(queryString, AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED, useFiltering = true)
            .countDuplicates()
            .normalize()



        sectionContainers.forEach { container ->

//            val cDist = AnalyzerFunctions.createTokenList(container.doc().unigrams().split()
//                    AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)
//                .countDuplicates()
//                .normalize()

//            val cDist = container.doc().unigrams().split(" ")
//                .countDuplicates()
//                .normalize()

            val cDist2 = container.doc().paragraphs().split(" ")
                .asSequence()
                .mapNotNull { pid ->
                    val q = AnalyzerFunctions.createQuery(pid, IndexFields.FIELD_PID.field)
                    paragraphSearcher.search(q, 1).scoreDocs.firstOrNull() }
                .flatMap { sc ->
                    val text = paragraphSearcher.getIndexDoc(sc.doc).text()
                    AnalyzerFunctions.createTokenList(text, AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED).asSequence()}
                .asIterable()
                .countDuplicates()
                .toList()
                .sortedByDescending { it.second }
                .take(15)
                .toMap()
                .normalize()




            val score = qDist.entries.sumByDouble { (token, freq) ->
                (cDist2[token] ?: 0.000) * freq * sectionSearcher.indexReader.totalTermFreq(Term(IndexFields.FIELD_UNIGRAM.field, token)) / sectionSearcher.indexReader.getSumTotalTermFreq(IndexFields.FIELD_UNIGRAM.field)  }
//            (cDist[token] ?: 0.000) * freq * sectionSearcher.indexReader.totalTermFreq(Term(IndexFields.FIELD_UNIGRAM.field, token)) / sectionSearcher.indexReader.getSumTotalTermFreq(IndexFields.FIELD_UNIGRAM.field)  }
            sf.sectionScores[container.index] = score
        }
    }

    private fun constantScore(qd: QueryData, sf: SharedFeature, constant: Double): Unit = with(qd) {
        sf.sectionScores.fill(constant)
    }

    fun addUnigrams(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = NormType.ZSCORE) =
            fmt.addFeature3(FeatureEnum.SECTION_UNIGRAMS, wt, norm) { qd, sf ->
                queryField(qd, sf, IndexFields.FIELD_UNIGRAM) }

    fun addBigrams(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = NormType.ZSCORE) =
            fmt.addFeature3(FeatureEnum.SECTION_BIGRAMS, wt, norm) { qd, sf ->
                queryField(qd, sf, IndexFields.FIELD_BIGRAM) }

    fun addWindowed(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = NormType.ZSCORE) =
            fmt.addFeature3(FeatureEnum.SECTION_WINDOWED, wt, norm) { qd, sf ->
                queryField(qd, sf, IndexFields.FIELD_WINDOWED_BIGRAM) }

    fun addHeading(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = NormType.ZSCORE) =
            fmt.addFeature3(FeatureEnum.SECTION_HEADING, wt, norm) { qd, sf ->
                queryField(qd, sf, IndexFields.FIELD_SECTION_HEADING) }

    fun addPath(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = NormType.ZSCORE) =
            fmt.addFeature3(FeatureEnum.SECTION_PATH, wt, norm) { qd, sf ->
                queryField(qd, sf, IndexFields.FIELD_SECTION_PATH) }

    fun addConstantScore(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = NormType.ZSCORE) =
            fmt.addFeature3(FeatureEnum.SECTION_CONSTANT_SCORE, wt, norm) { qd, sf ->
                constantScore(qd, sf, 1.0) }

    fun addQueryDist(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = NormType.ZSCORE) =
            fmt.addFeature3(FeatureEnum.SECTION_QUERY_DIST, wt, norm, this::queryDist)
}