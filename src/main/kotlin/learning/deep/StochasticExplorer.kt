package learning.deep

import org.apache.commons.math3.distribution.BetaDistribution
import org.apache.commons.math3.distribution.NormalDistribution
import utils.stats.weightedPick
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.absoluteValue
import kotlin.math.pow

//import smile.stat.distribution.GaussianDistribution

//import org.nd4j.linalg.api.ops.random.impl.GaussianDistribution

//private val betaDists = ConcurrentHashMap<Pair<Double, Double>, BetaDistribution>()


class Ball(val radius: Double = 0.05, val location: Double = 0.5,
           successes: Double = 2.0, failures: Double = 2.0) {
    val dist = NormalDistribution(location, radius)
//    var beta = betaDists.computeIfAbsent(successes to failures) { BetaDistribution(successes, failures) }
    var beta = BetaDistribution(successes, failures)
    var left: Ball? = null
    var right: Ball? = null

    fun spawnBall(): Ball {
        val loc = dist.sample().let { if (it <= 0.025) 0.025 else if (it >= 0.975) 0.975 else it }
//        return Ball(radius = radius * (if (vote() >= 0.5) 0.9 else 1.0), location = loc,
//                return Ball(radius = Math.max(0.5 * radius + 0.5 * (1 - vote()), 0.001), location = loc,
//                        return Ball(radius = Math.max(0.8 * radius + 0.2 * (1 - votes(3).average()), 0.001), location = loc,
                                return Ball(radius = radius *  (beta.alpha  / (beta.alpha + beta.beta)), location = loc,
//                                        return Ball(radius = radius, location = loc,
//                successes = Math.max(beta.alpha - 1.0, 1.0), failures = Math.max(beta.beta - 1.0, 1.0))
//                successes = Math.max(beta.alpha / 2.0, 1.0), failures = Math.max(beta.beta / 2.0, 1.0))
                successes = beta.alpha, failures = beta.beta)
    }

    fun vote() = beta.sample(3).average()
//    fun vote() = beta.sample()
    fun votes(nVotes: Int) = beta.sample(nVotes)

    fun distChance(origin: Double, ball: Ball): Boolean {
//        val dist = (ball.location - this.location).absoluteValue.pow(0.25)
//        return ThreadLocalRandom.current().nextDouble() > dist
        return (ball.location - origin).absoluteValue <= ball.radius * (ball.beta.alpha / (ball.beta.alpha + ball.beta.beta))
    }

    var rewardDecay = 0.1

    fun reward(amount: Double = 1.0, times: Int = 5, origin: Double = this.location) {
//        this.beta = betaDists.computeIfAbsent(this.beta.alpha + amount to this.beta.beta) {
//            BetaDistribution(this.beta.alpha + amount, this.beta.beta) }
        val mult = 0.5
        this.beta =
            BetaDistribution(this.beta.alpha + amount, Math.max(this.beta.beta - amount * mult, 0.1))
        if (times > 0) {
            if (left != null) {
                if (distChance(origin, left!!)) {
                    left!!.reward(amount * rewardDecay, times - 1, origin)
                }
            }

            if (right != null) {
                if (distChance(origin, right!!)) {
                    right!!.reward(amount * rewardDecay, times - 1, origin)
                }
            }
//            left?.reward(amount / (location - left!!.location), false)
//            right?.reward(amount / 2.0, false)
        }
    }

    fun penalize(amount: Double = 1.0, times: Int = 5, origin: Double = this.location) {
//        this.beta = betaDists.computeIfAbsent(this.beta.alpha  to this.beta.beta + amount) {
//            BetaDistribution(this.beta.alpha, this.beta.beta + amount) }
        val mult = 0.5
        this.beta =
            BetaDistribution(Math.max(this.beta.alpha - mult * amount, 0.1), this.beta.beta + amount )
        if (times > 0) {

            if (left != null) {
                if (distChance(origin, left!!)) {
                    left!!.penalize(amount * rewardDecay, times - 1, origin)
                }
            }

            if (right != null) {
                if (distChance(origin, right!!)) {
                    right!!.penalize(amount * rewardDecay, times - 1, origin)
                }
            }

//            left?.penalize(amount / 2.0, false)
//            right?.penalize(amount / 2.0, false)
        }
    }


    fun getParam(): Double {
        val param = dist.sample()
        return  if (param < 0.0) 0.0
                else if (param > 1.0) 1.0
                else param
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
//        if (ThreadLocalRandom.current().nextDouble() <= justRand) {
//            return balls[ThreadLocalRandom.current().nextInt(balls.size)]
//                .apply { curBall= this }
//        }
//        val doRand = ThreadLocalRandom.current().nextDouble() <= justRand
//        return balls.map { it to if (doRand) Math.max(1.0 / it.vote(), 0.0001) else it.vote() }
        return balls.map { it to  it.vote() }
            .toMap()
            .weightedPick()
            .apply { curBall = this }
    }

    fun reward(amount: Double) = curBall.reward(amount)
    fun penalize(amount: Double) = curBall.penalize(amount)

    fun newGeneration(children: Int, respawn: Int = 0) {
//        val generation = (0 until 20).flatMap {
//            balls
//                .filter { it.vote() > 0.7 }
//                .map { it.spawnBall() }
//        }.shuffled().take(children)
//            .sortedBy { it.location }
//        val valid = balls.filter { it.beta.alpha >= 1.0  }
//        balls.clear()
//        balls.addAll(valid)

        val generation = (0 until children).flatMap {
            val ball = draw().spawnBall()
//            listOf(ball) + (0 until 2).mapNotNull { if (ball.vote() > 0.5) ball.spawnBall() else null } }
            listOf(ball)  }
                (0 until respawn).map { draw() }
                    .shuffled()
                    .take(children)
                    .sortedBy { it.location }

        balls.clear()
        balls.addAll(generation)
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
        } else if (ThreadLocalRandom.current().nextDouble() >= (nearTarget / candidates.average()).pow(1.0)) {
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
//        if (ball.location >= 0.2 && ball.location <= 0.35) {
//            ball.reward(1.0)
//            if (ball.location <= 2.3)
//                ball.reward(5.0)
//        }
//        else
//            ball.penalize(5.0)

        ball.getParam()
    }.average().run { println("Average draw: $this") }

}

fun main(args: Array<String>) {
    val cover = Cover()


    (0 until 20).forEach {
        runRewards(cover, 50)
        cover.newGeneration(50)
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