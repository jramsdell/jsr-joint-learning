@file:JvmName("KotGramAnalyzer")
package language

import language.containers.CorpusStat
import language.containers.CorpusStatContainer
import language.containers.LanguageStat
import language.containers.LanguageStatContainer
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.index.Term
import org.apache.lucene.search.IndexSearcher
import utils.AnalyzerFunctions
import utils.lucene.getIndexSearcher
import utils.AnalyzerFunctions.AnalyzerType.*
import utils.misc.identity


/**
 * Enum: GramStatType
 * Desc: Used to indicate what type of gram that this language statistic represents.
 */
enum class GramStatType  { TYPE_UNIGRAM, TYPE_BIGRAM, TYPE_BIGRAM_WINDOW }


/**
 * Class: GramAnalyzer
 * Desc: Given a corpus indexed with -grams, calculates language model statistics.
 */
class GramAnalyzer(val indexSearcher: IndexSearcher) {
    constructor(indexLoc: String) : this(getIndexSearcher(indexLoc))
    val analyzer = EnglishAnalyzer()

    // Returns total frequency of a -gram in corpus
    private fun getCorpusGram(gram: String, gramType: String): Long =
            indexSearcher.indexReader.totalTermFreq(Term(gramType, gram))

    /**
     * Func: getLanguageStatContainer
     * Desc: Given text (such as a paragraph body), calculates -gram statistics and returns
     *       stats wrapped in a LanguageStatContainer.
     */
    fun getLanguageStatContainer(text: String): LanguageStatContainer =
        LanguageStatContainer(
                unigramStat = getStats(text, GramStatType.TYPE_UNIGRAM),
                bigramStat = getStats(text, GramStatType.TYPE_BIGRAM),
                bigramWindowStat = getStats(text, GramStatType.TYPE_BIGRAM_WINDOW)
        )


    /**
     * Func: getCorpusStatContainer
     * Desc: Given a lucene, returns -gram model statistics for lucene, and also returns
     *      the collection statistics for each -gram.
     */
    fun getCorpusStatContainer(text: String): CorpusStatContainer =
            CorpusStatContainer(
                    unigramStat = getCorpusStat(text, GramStatType.TYPE_UNIGRAM),
                    bigramStat = getCorpusStat(text, GramStatType.TYPE_BIGRAM),
                    bigramWindowStat = getCorpusStat(text, GramStatType.TYPE_BIGRAM_WINDOW)
            )


    /**
     * Func: getCorpusStat
     * Desc: Returns collection statistic for a given -gram type.
     */
    private fun getCorpusStat(text: String, type: GramStatType): CorpusStat {
        val languageStats = getStats(text, type)

        val field = when(type) {
            GramStatType.TYPE_UNIGRAM -> "unigram"
            GramStatType.TYPE_BIGRAM -> "bigrams"
            GramStatType.TYPE_BIGRAM_WINDOW -> "bigram_windows"
        }

        val totalCorpusFreq = indexSearcher.indexReader
            .getSumTotalTermFreq(field)
            .toDouble()

        val corpusFreqs = languageStats.docTermCounts.keys
            .map { key -> Pair(key, getCorpusGram(key, field) / totalCorpusFreq) }
            .toMap()

        return CorpusStat(corpusFrequency = corpusFreqs, corpusDoc = languageStats)
    }


    /**
     * Func: createLanguageStats
     * Desc: Creates a language model for given -gram type (does not include collection statistics).
     */
    private fun createLanguageStats(counts: Map<String, Int>, type: GramStatType): LanguageStat {
        val totalCount = counts.values
            .sum()
            .toDouble()

        val freqs = counts
            .mapValues { (gram, count) -> count / totalCount }
            .toMap()

        return LanguageStat(counts, freqs, type)
    }


    /**
     * Func: countBigrams
     * Desc: Function used to count number of (stemmed) bigrams in text.
     */
    private fun countBigrams(text: String): Map<String, Int> {
        val terms = AnalyzerFunctions.createTokenList(text, analyzerType = ANALYZER_ENGLISH).toList()
        val docBigramCounts = terms.windowed(2, 1)
            .map { window -> window.joinToString(separator = "") }
            .groupingBy(::identity)
            .eachCount()

        return docBigramCounts
    }


    /**
     * Func: countWindowedBigrams
     * Desc: Function used to count number of (stemmed) windowed bigrams in text.
     */
    private fun countWindowedBigrams(text: String): Map<String, Int> {
        val terms = AnalyzerFunctions.createTokenList(text, analyzerType = ANALYZER_ENGLISH)
        val docBigramWindowCounts = terms
            .windowed(8, 1, true)
            .flatMap { window ->
                            val firstTerm = window[0]
                            window
                                .slice(1  until window.size)
                                .flatMap { secondTerm -> listOf(firstTerm + secondTerm, secondTerm + firstTerm) } }
            .groupingBy(::identity)
            .eachCount()
            .toMap()
        return docBigramWindowCounts
    }


    /**
     * Func: countUnigrams
     * Desc: Functions used to count number of (stemmed) unigrams in text.
     */
    private fun countUnigrams(text: String): Map<String, Int> {
        val terms = AnalyzerFunctions.createTokenList(text, analyzerType = ANALYZER_ENGLISH)

        val docTermCounts = terms
            .groupingBy(::identity)
            .eachCount()
            .toMap()

        return docTermCounts
    }

    /**
     * Func: getStats
     * Desc: Counts -grams of a given type and returns a language model using these stats.
     */
    private fun getStats(text: String, statType: GramStatType): LanguageStat {
        val counts = when (statType) {
            GramStatType.TYPE_UNIGRAM -> countUnigrams(text)
            GramStatType.TYPE_BIGRAM -> countBigrams(text)
            GramStatType.TYPE_BIGRAM_WINDOW -> countWindowedBigrams(text)
        }

        return createLanguageStats(counts, statType)
    }

    fun runTest() {
    }

    /**
     * Func: getQueryLikelihood
     * Desc: Wrapper function around LanguageStatsContainer to run lucene likelihood and return just that
     *       scores.
     */
    fun getQueryLikelihood(langStat: LanguageStatContainer, corpStat: CorpusStatContainer, alpha: Double)
            : Triple<Double, Double, Double> {
        val queryLikelihoodContainer = langStat.getLikelihoodGivenQuery(corpStat, alpha)
        val uniLike = queryLikelihoodContainer.unigramLikelihood
        val biLike = queryLikelihoodContainer.bigramLikelihood
        val windLike = queryLikelihoodContainer.bigramWindowLikelihood
        return Triple(uniLike, biLike, windLike)
    }
}

