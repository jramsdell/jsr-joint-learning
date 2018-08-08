package learning.deep.stochastic

import org.apache.commons.math3.distribution.BetaDistribution


class Voter(successes: Double, failures: Double) {
    val beta = BetaDistribution(successes, failures)
    fun vote(): Double = beta.sample(3).average()

}