package learning.deep.stochastic

open class NormalCover : Cover<Ball>() {
    var curBall = Ball()
//    init {
//        createBalls()
//    }

    override fun createBalls() {
        (0 until 40).map { index ->
            val loc = index * 0.025
            balls.add(Ball(location = loc))
        }
        linkBalls()
    }

    operator fun plus(other: NormalCover): NormalCover {
        val newBalls = (0 until balls.size).map {
            val b1 = draw()
            val b2 = other.draw()
            b1 + b2
        }.sortedBy { it.location }

        val c = NormalCover()
            .apply { balls.addAll(newBalls) }
            .apply { linkBalls() }

        return c
    }

    override fun newGeneration(children: Int, respawn: Int) {

        val generation = (0 until children).flatMap {
            val ball = draw().spawnBall()
            listOf(ball)  }
//        val old = balls.shuffled().take(5)
//        val others = (0 until 5).map { Ball(0.05, Math.random(), 0.5, 0.5) }
        balls.clear()
        balls.addAll((generation).sortedBy { it.location } )
        linkBalls()
    }

}
