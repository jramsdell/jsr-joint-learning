package language.containers

/**
 * Class: LikelihoodContainer
 * Desc: Contains (log) likelihood values for each type of gram
 * @see LanguageStatContainer
 */
data class LikelihoodContainer(val unigramLikelihood: Double,
                               val bigramLikelihood: Double,
                               val bigramWindowLikelihood: Double)
