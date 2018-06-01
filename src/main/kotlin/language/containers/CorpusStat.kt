package language.containers

import language.GramStatType

/**
 * Class: CorpusStat
 * Desc: Represents a -gram model for a collection
 */
data class CorpusStat(val corpusFrequency: Map<String, Double>, val corpusDoc: LanguageStat) {
    val type: GramStatType
        get() = corpusDoc.type
}
