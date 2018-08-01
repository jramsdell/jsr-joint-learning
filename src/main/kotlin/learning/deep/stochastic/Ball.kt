package learning.deep.stochastic

import org.apache.commons.math3.distribution.BetaDistribution
import org.apache.commons.math3.distribution.NormalDistribution
import utils.misc.sharedRand
import utils.stats.weightedPick
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.absoluteValue
import kotlin.math.pow

class Ball(val radius: Double = 0.05, var location: Double = 0.5,
           successes: Double = 2.0, failures: Double = 2.0) {
    var dist = NormalDistribution(location, radius)
    //    var beta = betaDists.computeIfAbsent(successes to failures) { BetaDistribution(successes, failures) }
    val betaRef = AtomicReference(BetaDistribution(successes, failures))
    var left: Ball? = null
    var right: Ball? = null
    var lastParam = 0.0

    val beta: BetaDistribution
        get() = betaRef.get()

    fun spawnBall(): Ball {
        val loc = dist.sample().let { if (it <= 0.0) 0.0 else if (it >= 1.0) 1.0 else it }
        return Ball(radius = radius * (beta.beta / (beta.alpha)), location = loc,
                successes = Math.max(beta.alpha / 2.0, 0.5), failures = Math.max(beta.beta / 1, 0.5))
    }

    fun vote() = beta.sample(1).average()
    fun votes(nVotes: Int) = beta.sample(nVotes)

    fun distChance(origin: Double, ball: Ball): Boolean {
        return (ball.location - origin).absoluteValue <= ball.radius * (ball.beta.alpha / (ball.beta.alpha + ball.beta.beta))
    }

    var rewardDecay = 0.8

    fun reward(amount: Double = 1.0, times: Int = 10, origin: Double = this.location, direction: String = "origin") {
//        this.beta = betaDists.computeIfAbsent(this.beta.alpha + amount to this.beta.beta) {
//            BetaDistribution(this.beta.alpha + amount, this.beta.beta) }
        val mult = 0.5

        val curBeta = this.betaRef.get()
        if (direction == "origin") {
            location = lastParam
            dist = NormalDistribution(location, radius)

        } else {
            location = (lastParam + this.location) / 2.0
            dist = NormalDistribution(location, radius)
        }


        val newBeta = BetaDistribution(curBeta.alpha + amount, Math.max(curBeta.beta - amount * mult, 0.1))

        val success = this.betaRef.compareAndSet(curBeta, newBeta)
        if(!success) {
            println("Something went wrong!")
        }


        if (times > 0) {
            val toRight = (direction == "origin" || direction == "right")
            val toLeft = (direction == "origin" || direction == "left")
            if (left != null && toLeft) {
                if (distChance(origin, left!!)) {
                    left!!.reward(amount * rewardDecay, times - 1, origin, "left")
                }
            }

            if (right != null && toRight) {
                if (distChance(origin, right!!)) {
                    right!!.reward(amount * rewardDecay, times - 1, origin, "right")
                }
            }
        }
    }

    fun penalize(amount: Double = 1.0, times: Int = 5, origin: Double = this.location, direction: String = "origin") {
        val mult = 0.5


        val curBeta = this.betaRef.get()
        val newBeta = BetaDistribution(Math.max(this.beta.alpha - mult * amount, 0.1), this.beta.beta + amount )
        this.betaRef.compareAndSet(curBeta, newBeta)

//        this.beta =
//            BetaDistribution(Math.max(this.beta.alpha - mult * amount, 0.1), this.beta.beta + amount )
        if (times > 0) {
            val toRight = (direction == "origin" || direction == "right")
            val toLeft = (direction == "origin" || direction == "left")

            if (left != null && toLeft) {
                if (distChance(origin, left!!)) {
                    left!!.penalize(amount * rewardDecay * 0.0, times - 1, origin, "left")
                }
            }

            if (right != null && toRight) {
                if (distChance(origin, right!!)) {
                    right!!.penalize(amount * rewardDecay * 0.0, times - 1, origin, "right")
                }
            }

        }
    }


    fun getParam(): Double {
        lastParam = dist.sample()
//        return lastParam
        return  if (lastParam < 0.0) 0.0
////                else if (lastParam > 1.0) 1.0
        else lastParam
    }
}
