package learning.deep

import org.apache.commons.math3.distribution.BetaDistribution
import org.apache.commons.math3.distribution.NormalDistribution
import utils.misc.sharedRand
import utils.stats.weightedPick
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.absoluteValue
import kotlin.math.pow

//import smile.stat.distribution.GaussianDistribution

//import org.nd4j.linalg.api.ops.random.impl.GaussianDistribution

//private val betaDists = ConcurrentHashMap<Pair<Double, Double>, BetaDistribution>()


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
//        val loc = dist.sample().let { if (it <= 0.0) 0.0 else it }
//        val loc = dist.sample().let {it}
//        return Ball(radius = radius * (if (vote() >= 0.5) 0.9 else 1.0), location = loc,
//                return Ball(radius = Math.max(0.5 * radius + 0.5 * (1 - vote()), 0.001), location = loc,
//                        return Ball(radius = Math.max(0.8 * radius + 0.2 * (1 - votes(3).average()), 0.001), location = loc,
//                                return Ball(radius = radius *  (beta.alpha  / (beta.beta)), location = loc,
                                        return Ball(radius = radius *  (beta.beta  / (beta.alpha)), location = loc,
//                                                return Ball(radius = radius * 0.99, location = loc,
//                                        return Ball(radius = radius * 0.99, location = loc,
//                                        return Ball(radius = radius, location = loc,
//                successes = Math.max(beta.alpha - 1.0, 1.0), failures = Math.max(beta.beta - 1.0, 1.0))
                successes = Math.max(beta.alpha / 2.0, 0.5), failures = Math.max(beta.beta / 1, 0.5))
//                successes = Math.max(beta.alpha / 2.0, 1.0), failures = Math.max(beta.beta / 1, 1.0))
//                successes = beta.alpha, failures = beta.beta)
    }

//    fun vote() = beta.sample(3).average()
    fun vote() = beta.sample(1).average()
//    fun vote() = beta.sample()
    fun votes(nVotes: Int) = beta.sample(nVotes)

    fun distChance(origin: Double, ball: Ball): Boolean {
//        val dist = (ball.location - this.location).absoluteValue.pow(0.25)
//        return ThreadLocalRandom.current().nextDouble() > dist
        return (ball.location - origin).absoluteValue <= ball.radius * (ball.beta.alpha / (ball.beta.alpha + ball.beta.beta))
//        return (ball.location - origin).absoluteValue <= ball.radius
//        return (ball.location - origin).absoluteValue <= 0.05
    }

    var rewardDecay = 0.8

    fun reward(amount: Double = 1.0, times: Int = 5, origin: Double = this.location, direction: String = "origin") {
//        this.beta = betaDists.computeIfAbsent(this.beta.alpha + amount to this.beta.beta) {
//            BetaDistribution(this.beta.alpha + amount, this.beta.beta) }
        val mult = 0.5

        val curBeta = this.betaRef.get()
        location = lastParam
        dist = NormalDistribution(location, radius)


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

    fun penalize(amount: Double = 1.0, times: Int = 5, origin: Double = this.location) {
        val mult = 0.5


        val curBeta = this.betaRef.get()
        val newBeta = BetaDistribution(Math.max(this.beta.alpha - mult * amount, 0.1), this.beta.beta + amount )
        this.betaRef.compareAndSet(curBeta, newBeta)

//        this.beta =
//            BetaDistribution(Math.max(this.beta.alpha - mult * amount, 0.1), this.beta.beta + amount )
        if (times > 0) {

            if (left != null) {
                if (distChance(origin, left!!)) {
                    left!!.penalize(amount * rewardDecay * 0.0, times - 1, origin)
                }
            }

            if (right != null) {
                if (distChance(origin, right!!)) {
                    right!!.penalize(amount * rewardDecay * 0.0, times - 1, origin)
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

class Cover() {
    val balls = ArrayList<Ball>()
    var curBall = Ball()
    init {
        (0 until 40).map { index ->
            val loc = index * 0.025
            balls.add(Ball(location = loc))
        }
        linkBalls()
    }

    fun linkBalls() {
        balls.forEachIndexed { index, ball ->
            try {
                balls[index + 1].left = ball
            } catch (e: IndexOutOfBoundsException) {}

            try {
                balls[index - 1].right = ball
            } catch (e: IndexOutOfBoundsException) {}
        }
    }

    fun draw(): Ball {
        return balls.map { it to  it.vote() }
            .toMap()
            .weightedPick()
            .apply { curBall = this }
    }

    //    fun reward(amount: Double) = curBall.reward(amount)
    fun rewardSpawn() {
        val child = curBall.spawnBall()
        curBall.reward(4.0)
        balls.add(child)
    }
    fun penalize(amount: Double) = curBall.penalize(amount)

    fun newGeneration(children: Int, respawn: Int = 0) {

        val generation = (0 until children).flatMap {
            val ball = draw().spawnBall()
//            val left = Ball(radius = ball.radius, location = 0.01 + ball.location,
//                    successes = 2.0, failures = 2.0)
//                    val right = Ball(radius = ball.radius, location = Math.max(0.0, ball.location - 0.01),
//                    successes = 2.0, failures = 2.0)
            listOf(ball)  }


//            .sortedBy { it.location }

        balls.clear()
        balls.addAll((generation).sortedBy { it.location } )
        linkBalls()
    }

    fun sampleLocs(nTimes: Int) =
        (0 until nTimes).map { draw().location }
}


var candidates = ArrayList<Double>().apply { addAll((0 until 10).map { 999.0 }) }
var counter = 0
fun runRewards(cover: Cover, nRewards: Int, rewardMult: Double = 1.0) {
    (0 until nRewards).map {
        val ball = cover.draw()
        val nearTarget = (ball.location - 0.5).absoluteValue
        if (candidates.isEmpty()) {
            candidates.add(nearTarget)
            ball.reward(1.0)
//        } else if (ThreadLocalRandom.current().nextDouble() >= (nearTarget / candidates.average()).pow(1.0)) {
        } else if (sharedRand.nextDouble() >= (nearTarget / candidates.average()).pow(1.0)) {
            if (candidates.size >= 10) {
                candidates[counter % 10] = nearTarget
            } else {
                candidates.add(nearTarget)
            }
            counter += 1
            ball.reward(1.0)
        } else {
            ball.penalize(1.0)
        }

        ball.getParam()
    }.average().run { println("Average draw: $this") }

}

fun main(args: Array<String>) {
    val cover = Cover()


    (0 until 10).forEach {
        runRewards(cover, 20)
        cover.newGeneration(20)
//        if (it > 10)
//            cover.newGeneration(40)
        val avLoc = cover.balls.map { it.location }.average()
        println("Drawn loc: ${cover.sampleLocs(2)}")
        println("Average loc: $avLoc\n")
    }



    cover.balls.map { it to it.location }
        .sortedByDescending { it.second }
        .run { println(this.map { "${it.second} : ${it.first.beta.alpha} / ${it.first.beta.beta}" }) }
    println("Final: ${candidates.toList()} \n\t: ${candidates.average()}")


}