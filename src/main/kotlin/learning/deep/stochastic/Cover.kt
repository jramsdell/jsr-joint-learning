package learning.deep.stochastic

import org.apache.commons.math3.distribution.BetaDistribution
import org.apache.commons.math3.distribution.NormalDistribution
import utils.misc.sharedRand
import utils.stats.weightedPick
import utils.stats.weightedPicks
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.absoluteValue
import kotlin.math.pow

@Suppress("LeakingThis")
abstract class Cover<T: Ball>() {
    open val balls = ArrayList<T>()
//    var curBall = Ball()
    init {
        createBalls()
    }

    abstract fun createBalls()

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

    fun draw(prev: Double? = null): T {
        return balls.map { it to  it.vote(prev) }
            .toMap()
            .weightedPick()
    }

//    fun drawInverse(): Ball {
//        return balls.map { it to  1.0 / it.vote() }
//            .toMap()
//            .weightedPick()
//    }

    fun draws(nTimes: Int): List<T> {
        return balls.map { it to  it.vote() }
            .toMap()
            .weightedPicks(nTimes)
    }



    abstract fun newGeneration(children: Int, respawn: Int = 0)
//    open fun newGeneration(children: Int, respawn: Int = 0) {
//        val generation = (0 until children).flatMap {
//            val ball = draw().spawnBall()
//            listOf(ball)  }
//        balls.clear()
//        balls.addAll((generation).sortedBy { it.location } )
//        linkBalls()
//    }

}
