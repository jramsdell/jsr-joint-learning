package language.containers

/**
 * Class: CorpusStatContainer
 * Desc: Convenience container for each type of -gram model for collection.
 */
data class CorpusStatContainer(
        val unigramStat: CorpusStat,
        val bigramStat: CorpusStat,
        val bigramWindowStat: CorpusStat
)
