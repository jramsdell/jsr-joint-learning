package learning.deep.stochastic

import org.apache.commons.math3.distribution.BetaDistribution
import org.apache.commons.math3.distribution.NormalDistribution
import java.util.concurrent.ThreadLocalRandom


class GaussianPoint(val nCovers: Int, var covers: List<NormalCover> = emptyList(),
                    val successes: Double =  2.0, val failures: Double = 2.0) {

    init {
        if (covers.isEmpty()) {
            covers = (0 until nCovers).map { NormalCover().apply { createBalls() } }
        }
    }

    var balls = (0 until nCovers).map {
        Ball(location = ThreadLocalRandom.current().nextDouble(),
                radius = 0.1 ) }

//    fun vote() = sample().fold(1.0) { acc, ball -> acc * ball.vote() }
    fun vote() = balls.fold(1.0) { acc, ball -> acc * ball.vote() }

//    fun sample() = covers.map { it.draw() }
    fun sample() = this.balls

//    fun newGeneration(nChildren: Int) = covers.forEach { it.newGeneration(nChildren) }
    fun newGeneration(nChildren: Int) = Unit

    fun reward(amount: Double, point: List<Double>) {
//        point.zip(covers).forEach { (p, cover) -> rewardLayer(cover, p, amount) }
        point.zip(balls).forEach { (p, ball) -> rewardBall(ball, p, amount) }

//        point.zip(shifts).zip(balls).forEach { (p, ball) -> rewardBall(ball, p.first, amount, p.second) }
    }

    fun penalize(amount: Double, point: List<Double>) {
//        point.zip(covers).forEach { (p, cover) -> penalizeLayer(cover, p, amount) }
        point.zip(balls).forEach { (p, ball) -> penalizeBall(ball, p, amount) }
    }

//    fun rewardLayer(cover: NormalCover, point: Double, amount: Double) {
//        cover.balls.forEach { ball -> rewardBall(ball, point, amount) }
//    }

    fun rewardBall(ball: Ball, point: Double, amount: Double) {
        val prob = ball.getProbOfGenerating(point)
        if (ThreadLocalRandom.current().nextDouble() <= prob) {
            ball.location = point
            ball.dist = NormalDistribution(ball.location, ball.radius)
        }

        ball.reward(amount = amount, rewardParam = point, direction = "qweqe",
                origin = point, times = 0)

    }

//    fun penalizeLayer(cover: NormalCover, point: Double, amount: Double) {
//        cover.balls.forEach { ball -> penalizeBall(ball, point, amount) }
//    }

    fun penalizeBall(ball: Ball, point: Double, amount: Double) {
        val prob = ball.getProbOfGenerating(point)
        ball.penalize(amount = amount, direction = "qweqe",
                origin = point, times = 0)

    }


    fun chanceOfGenerating(params: List<Double>)  =
        params.zip(sample()).fold(1.0) { acc, (point, ball) ->
            acc * ball.getProbOfGenerating(point)
        }

//    operator fun plus(other: GaussianPoint): GaussianPoint {
//        val newCovers = covers.zip(other.covers).map { (c1, c2) ->
//            c1 + c2
//        }
//        return GaussianPoint(nCovers, newCovers)
//    }

    operator fun plus(other: GaussianPoint): GaussianPoint {
        val newBalls = balls.zip(other.balls).map { (b1, b2) -> b1 + b2 }
        return GaussianPoint(nCovers, covers)
            .apply { balls = newBalls }
    }


}
