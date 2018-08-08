package learning.deep.stochastic



class ConditionalCover(val level: Int = 0) : Cover<BallConditionalDist>() {
    override fun newGeneration(children: Int, respawn: Int) {
//        val generation = (0 until children).flatMap {
//            val ball = draw().spawnBall()
//            listOf(ball)  }

        val generation = balls.map { it.spawnBall() }
        balls.clear()
        balls.addAll((generation).sortedBy { it.location } )
        linkBalls()

        val seen = HashSet<ConditionalCover>()
        balls.forEach { ball ->
            ball.cover?.let { cover ->
                if (seen.add(cover)) {
                    cover.newGeneration(children)
                }
            }
        }
    }


    override fun createBalls() {

        (0 until 80).map { index ->
            val loc = index * 0.0125
            balls.add(BallConditionalDist(location = loc, level = level, radius = 0.01))
        }
        linkBalls()

        if (level > 0) {
            val newCover = ConditionalCover(level - 1)
            balls.forEach { ball -> ball.cover = newCover }
        }
    }


}

fun main(args: Array<String>) {
    val c1 = ConditionalCover()
}