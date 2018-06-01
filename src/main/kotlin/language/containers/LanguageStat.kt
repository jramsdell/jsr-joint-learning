package language.containers

import language.GramStatType

/**
 * Class: LanguageStat
 * Desc: Abstraction of a -gram statistic for a document or collection.
 * @param docTermCounts: Number of times each -gram appears.
 * @param docTermFreqs: Relative frequency of each -gram.
 * @param type: The type of -gram (unigram, bigram, or windowed bigram)
 */
data class LanguageStat(val docTermCounts: Map<String, Int>,
                        val docTermFreqs: Map<String, Double>,
                        val type: GramStatType)
