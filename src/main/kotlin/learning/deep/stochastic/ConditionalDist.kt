package learning.deep.stochastic

import org.apache.commons.math3.distribution.BetaDistribution
import org.apache.commons.math3.distribution.NormalDistribution


class BallConditionalDist(
        radius: Double = 0.05,
        location: Double = 0.5,
        successes: Double = 2.0,
        failures: Double = 2.0,
        level: Int = 0) : Ball(radius, location, successes, failures) {

    var cover: ConditionalCover? = null
    var underlyingBall: Ball? = null

    override fun spawnBall(): BallConditionalDist {
        val loc = dist.sample()
        val ball = BallConditionalDist(radius = radius * (beta.beta / (beta.alpha)), location = loc,
                successes = Math.max(beta.alpha / 2.0, 0.5), failures = Math.max(beta.beta / 1.5, 0.5))

        ball.cover = cover
        return ball
    }

    fun generateBalls(): List<BallConditionalDist> {
        val me = listOf(this)
        return  if (cover == null) me
                else me + cover!!.draw().generateBalls()

    }

    fun generateParams(): List<Double> {
        val me = listOf(this.getParam())
        return  if (cover == null) me
                else me + cover!!.draw().generateParams()
    }

    override fun reward(amount: Double, times: Int, origin: Double, direction: String,
                    rewardParam: Double) {
        val mult = 0.1
        val curBeta = this.betaRef.get()

        val probOfGenerating = getProbOfGenerating(origin)
        val adjustedReward = amount * probOfGenerating

//        if (direction == "origin") {
//            location = rewardParam
//            dist = NormalDistribution(location, radius)
//        }


        underlyingBall?.apply {
            val newBeta = BetaDistribution(curBeta.alpha + adjustedReward, Math.max(curBeta.beta - adjustedReward * mult, 0.1))
            val success = betaRef.compareAndSet(curBeta, newBeta)
        }



        if (times > 0) {
            val toRight = (direction == "origin" || direction == "right")
            val toLeft = (direction == "origin" || direction == "left")
            if (left != null && toLeft) {
                if (distChance(origin, left!!)) {
                    left!!.reward(amount * rewardDecay, times - 1, origin, "left", rewardParam)
                }
            }

            if (right != null && toRight) {
                if (distChance(origin, right!!)) {
                    right!!.reward(amount * rewardDecay, times - 1, origin, "right", rewardParam)
                }
            }
        }
    }

}
