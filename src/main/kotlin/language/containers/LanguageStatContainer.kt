package language.containers

import language.GramAnalyzer
import language.GramStatType
import language.GramStatType.*
import org.apache.lucene.document.Document
import utils.misc.identity
import utils.stats.defaultWhenNotFinite
import utils.stats.normalize
import java.lang.Math.log

/**
 * Class: LanguageStatContainer
 * Desc: Represents language stats for a lucene or document.
 *       Contains a LanguageStat for each type of -gram.
 * @see LanguageStat
 * @see GramStatType
 */
data class LanguageStatContainer(
        val unigramStat: LanguageStat,
        val bigramStat: LanguageStat,
        val bigramWindowStat: LanguageStat) {


    /**
     * Func: getLikelihood
     * Desc: Gets Dirichlet smoothed log likelihood given a lucene
     * @param queryStat: Represents -gram model of a lucene, and of associated corpus.
     * @param alpha: Used for smoothing
     */
    private fun getLikelihood(queryStat: CorpusStat, alpha: Double): Double {
        val stat = when(queryStat.type) {
            GramStatType.TYPE_UNIGRAM       -> unigramStat
            GramStatType.TYPE_BIGRAM        -> bigramStat
            GramStatType.TYPE_BIGRAM_WINDOW -> bigramWindowStat
        }

        val docLength = stat.docTermCounts.values.sum().toDouble()

        val likelihood =
                queryStat.corpusFrequency
                    .map { (term, freq) ->
                        //                        val pred = docSmooth * (stat.docTermFreqs[term] ?: 0.0) + corpusSmooth * freq
                        val smoothCounts = (stat.docTermCounts[term] ?: 0) + freq * alpha
//                        println("$term : $freq : ${queryStat.type}")
                        log(smoothCounts / (docLength + alpha)).defaultWhenNotFinite(0.0)
                    }
                    .sum()

        return likelihood
    }

    /**
     * Func: getLikelihoodGivenQuery
     * Desc: Calculates likelihood for each type of -gram statistic given a lucene.
     * @see getLikelihood
     */
    fun getLikelihoodGivenQuery(query: CorpusStatContainer, alpha: Double = 1.0): LikelihoodContainer =
            LikelihoodContainer(
                    unigramLikelihood = getLikelihood(query.unigramStat, alpha),
                    bigramLikelihood = getLikelihood(query.bigramStat, alpha),
                    bigramWindowLikelihood = getLikelihood(query.bigramWindowStat, alpha)
            )

    companion object {
        fun createLanguageStatContainer(doc: Document) = doc.run {
            LanguageStatContainer(
                    unigramStat = getLanguageStat(doc.get(TYPE_UNIGRAM.indexField), TYPE_UNIGRAM),
                    bigramStat = getLanguageStat(doc.get(TYPE_BIGRAM.indexField), TYPE_BIGRAM),
                    bigramWindowStat = getLanguageStat(doc.get(TYPE_BIGRAM_WINDOW.indexField), TYPE_BIGRAM_WINDOW)
            )
        }

        private fun getLanguageStat(grams: String, type: GramStatType): LanguageStat {
            val counts = grams
                .split(" ")
                .groupingBy(::identity)
                .eachCount()

            return LanguageStat(counts, counts.normalize(), type)
        }
    }
}