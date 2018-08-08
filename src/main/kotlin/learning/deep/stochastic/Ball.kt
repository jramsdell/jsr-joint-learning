package learning.deep.stochastic

import org.apache.commons.math3.distribution.BetaDistribution
import org.apache.commons.math3.distribution.NormalDistribution
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.absoluteValue
import kotlin.math.pow

open class Ball(var radius: Double = 0.05, var location: Double = 0.5,
                successes: Double = 2.0, failures: Double = 2.0, var shift: Double = 0.0, val gen: Int = 0) {
    var dist = NormalDistribution(location, radius)
    var shiftDist = NormalDistribution(shift, radius)
    //    var beta = betaDists.computeIfAbsent(successes to failures) { BetaDistribution(successes, failures) }
    val betaRef = AtomicReference(BetaDistribution(successes, failures))
    var left: Ball? = null
    var right: Ball? = null
    var lastParam = 0.0
    var lastShift = 0.0



    val beta: BetaDistribution
        get() = betaRef.get()


    open fun spawnBall(): Ball {
        val loc = dist.sample()
        val newShift = shiftDist.sample()
//        return Ball(radius = radius * (beta.beta / (beta.alpha)), location = loc,
//                return Ball(radius = Math.min(radius * (beta.beta / (beta.alpha)), loc.absoluteValue.div(20)), location = loc,
                        return Ball(radius = loc.absoluteValue.div(10 + gen.toDouble() / 5.0), location = loc,
//                                return Ball(radius = loc.absoluteValue.div(10 ), location = loc,
                successes = Math.max(beta.alpha / 2.0, 0.5), failures = Math.max(beta.beta / 1.5, 0.5),
                shift = newShift, gen = gen + 1)
    }

    operator fun plus(other: Ball): Ball {
        val vLoc = (location + other.location) / 2
        val vRadius = (radius + other.radius) / 2
        val vSuccesses = (beta.alpha + other.beta.alpha) / 2
        val vFailures = (beta.beta + other.beta.beta) / 2
        return Ball(radius = vRadius, location = vLoc, successes = vSuccesses, failures = vFailures)
    }



    fun vote(prev: Double? = null) = beta.sample()
    //    fun vote() = Math.log(beta.alpha.pow(2.0) / beta.beta)
    fun votes(nVotes: Int) = beta.sample(nVotes)

    fun getProbOfGenerating(point: Double): Double {
//        return dist.probability(point - radius / 1.0, point + radius / 1.0)
        return dist.getInvDist(point)
    }

    fun distChance(origin: Double, ball: Ball): Boolean {
//        return (ball.location - origin).absoluteValue <= ball.radius * (ball.beta.alpha / (ball.beta.alpha + ball.beta.beta))
//        return ball.dist.probability(origin - 0.01, origin + 0.01) >= 0.1
        return ball.getProbOfGenerating(origin)  >= 0.1
    }

    fun doShift(prev: Double): Double {
        val s = shiftDist.getInvDist(prev) + 0.00001
        return s
    }

    var rewardDecay = 1.0

    open fun reward(amount: Double = 1.0, times: Int = 40, origin: Double = this.location, direction: String = "origin",
               rewardParam: Double = this.lastParam) {
        val mult = 0.1
        val curBeta = this.betaRef.get()

        val probOfGenerating = getProbOfGenerating(origin)
        val adjustedReward = amount * probOfGenerating

        if (direction == "origin") {
            location = rewardParam
            dist = NormalDistribution(location, radius)

//            shift = lastShift
//            shiftDist = NormalDistribution(shift, 0.01)
        }

        val newBeta = BetaDistribution(curBeta.alpha + adjustedReward, Math.max(curBeta.beta - adjustedReward * mult, 0.1))
        val success = this.betaRef.compareAndSet(curBeta, newBeta)
        if(!success) {
            println("Something went wrong!")
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

    fun penalize(amount: Double = 1.0, times: Int = 0, origin: Double = this.location, direction: String = "origin") {
        val mult = 0.5


//        val adjustedAmount = if (direction == "origin") amount else amount * getProbOfGenerating(origin)
        val adjustedAmount = getProbOfGenerating(origin) * amount

        val curBeta = this.betaRef.get()
        val newBeta = BetaDistribution(Math.max(this.beta.alpha - mult * adjustedAmount, 0.1), this.beta.beta + adjustedAmount )
        this.betaRef.compareAndSet(curBeta, newBeta)

        if (times > 0) {
            val toRight = (direction == "origin" || direction == "right")
            val toLeft = (direction == "origin" || direction == "left")

            if (left != null && toLeft) {
                if (distChance(origin, left!!)) {
                    left!!.penalize(amount * rewardDecay * 1.0, times - 1, origin, "left")
                }
            }

            if (right != null && toRight) {
                if (distChance(origin, right!!)) {
                    right!!.penalize(amount * rewardDecay * 1.0, times - 1, origin, "right")
                }
            }

        }
    }


    fun getParam(): Double {
        lastParam = dist.sample()
        return lastParam
//        return  if (lastParam < 0.0) 0.0
////                else if (lastParam > 1.0) 1.0
//        else lastParam
    }
}
