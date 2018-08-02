package learning.deep.stochastic

import org.apache.commons.math3.distribution.BetaDistribution
import org.apache.commons.math3.distribution.NormalDistribution
import utils.misc.sharedRand
import utils.stats.weightedPick
import utils.stats.weightedPicks
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.absoluteValue
import kotlin.math.pow

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

    fun drawInverse(): Ball {
        return balls.map { it to  1.0 / it.vote() }
            .toMap()
            .weightedPick()
            .apply { curBall = this }
    }

    fun draws(nTimes: Int): List<Ball> {
        return balls.map { it to  it.vote() }
            .toMap()
            .weightedPicks(nTimes)
    }

    fun rewardSpawn() {
        val child = curBall.spawnBall()
        curBall.reward(4.0)
        balls.add(child)
    }

    fun reward(amount: Double, param: Double) {
        val pMin = param - 0.01
        val pMax = param + 0.01
        balls.forEach { ball ->
            val probToGenerate = ball.dist.probability(pMin, pMax)
            ball.reward(amount * probToGenerate, times = 0)
        }
    }

    fun penalize(amount: Double, param: Double) {
        val pMin = param - 0.01
        val pMax = param + 0.01
        balls.forEach { ball ->
            val probToGenerate = ball.dist.probability(pMin, pMax)
            ball.penalize(amount * probToGenerate, times = 0)
        }
    }

    fun penalize(amount: Double) = curBall.penalize(amount)

    fun newGeneration(children: Int, respawn: Int = 0) {

        val generation = (0 until children).flatMap {
            val ball = draw().spawnBall()
            listOf(ball)  }
//        val generation = draws(children).map { it.spawnBall() }



        balls.clear()
        balls.addAll((generation).sortedBy { it.location } )
        linkBalls()
    }

    fun sampleLocs(nTimes: Int) =
            (0 until nTimes).map { draw().location }
}
