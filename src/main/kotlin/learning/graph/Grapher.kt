package learning.graph

import edu.stanford.nlp.graph.DijkstraShortestPath
import org.jgrapht.Graph
import org.jgrapht.alg.clique.BronKerboschCliqueFinder
import org.jgrapht.alg.clique.ChordalGraphMaxCliqueFinder
import org.jgrapht.alg.clique.CliqueMinimalSeparatorDecomposition
import org.jgrapht.alg.connectivity.GabowStrongConnectivityInspector
import org.jgrapht.alg.flow.EdmondsKarpMFImpl
import org.jgrapht.alg.scoring.AlphaCentrality
import org.jgrapht.alg.scoring.BetweennessCentrality
import org.jgrapht.alg.scoring.HarmonicCentrality
import org.jgrapht.alg.scoring.PageRank
import org.jgrapht.alg.vertexcover.GreedyVCImpl
import org.jgrapht.graph.*
import org.jgrapht.traverse.BreadthFirstIterator
import org.jgrapht.traverse.ClosestFirstIterator
import org.jgrapht.traverse.RandomWalkIterator
import utils.AnalyzerFunctions
import utils.misc.pairwise
import utils.misc.sampleRandom
import utils.misc.toArrayList
import utils.stats.defaultWhenNotFinite
import utils.stats.normalize
import java.io.File
import java.util.concurrent.ThreadLocalRandom


class Grapher() {
    val g: SimpleWeightedGraph<String, DefaultWeightedEdge> = SimpleWeightedGraph(DefaultWeightedEdge::class.java)

    fun start() {
        g.addVertex("v1")
        g.addVertex("v2")
        val edge = g.addEdge("v1", "v2")
        g.setEdgeWeight(edge, 2.0)
        println(g)
        println(g.getEdgeWeight(edge))

        val edge2 = g.addEdge("v1", "v2") ?: g.getEdge("v1","v2")
        g.setEdgeWeight(edge2, g.getEdgeWeight(edge2) + 1.0)
        println(g.getEdgeWeight(edge2))
        val walk = RandomWalkIterator(g, "v1")
        println(walk.next())
    }


    fun incrementEdge(v1: String, v2: String) {
        if (v1 == v2)
            return
        g.addVertex(v1)
        g.addVertex(v2)
        val edge = g.addEdge(v1, v2)
        if (edge == null) {
            g.getEdge(v1, v2)
                .apply { g.setEdgeWeight(this, g.getEdgeWeight(this) + 1.0) }
        }

    }

    fun parse(file: File) {
        file.readText().split(".")
            .forEach { sentence ->
                val tokens = AnalyzerFunctions.createTokenList(sentence, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)
                tokens.pairwise().forEach { (e1, e2) ->
                    incrementEdge(e1, e2)
                }

            }
    }

    fun build() {
        val loc = "/home/hcgs/Desktop/projects/jsr-joint-learning/resources/paragraphs"
        File(loc).listFiles()
            .drop(5)
            .take(1)
            .forEach { fDir ->
                println(fDir.name)
                fDir.listFiles().forEach { file ->
                    parse(file)
                }
            }

        val edges = g.edgeSet().toList()
        val total = edges.sumByDouble { edge -> g.getEdgeWeight(edge) }
        edges.forEach { edge -> g.setEdgeWeight(edge,g.getEdgeWeight(edge) / total) }
    }

    fun diffusionWalk() {
        val nodes = g.vertexSet().toList()
        val counter = HashMap<String, Double>()

        (0 until 20).forEach {
            val walk = RandomWalkIterator(g, nodes.sampleRandom(), true,
                    1000)

            walk.forEachRemaining { node ->
                if (node !in counter) {
                    counter[node] = 0.0
                }
                counter[node] = counter[node]!! + 1.0
            }
        }

        val results = counter.normalize().entries.sortedByDescending { it.value }
            .take(10)
            .map { it.toPair() }
            .toMap()

        results.forEach { result ->
            println(result)
        }


        val previous = HashMap<String, List<Double>>()
        g.edgeSet().forEach { edge ->
            g.setEdgeWeight(edge, (1.0 / g.getEdgeWeight(edge)).defaultWhenNotFinite(0.0))
        }

        val targets = results.keys.toList()
        getSelfShortest(targets, previous)

        nodes.mapIndexed { index, node ->
            if ((index + 1) % 1000 == 0)
                println(index)
//            val score = getHittingTimes(node, previous, results.keys.toList())
            val score = getScore(node, previous, results.keys.toList())
                .zip(results.values.normalize())
                .map { (score, weight) -> Math.log(score).defaultWhenNotFinite(0.0)  }
                .average()
            node to score
        }.sortedByDescending { it.second }
            .asSequence()
            .filter { it.second > 0.0 }
            .take(30)
            .forEach { println("Node: ${it.first} / ${it.second}") }


    }

    fun getSelfShortest(targets: List<String>, previous: HashMap<String, List<Double>>) {
        val dist = org.jgrapht.alg.shortestpath.DijkstraShortestPath(g)
        targets.map { source ->
            val result = targets.map { target -> dist.getPathWeight(source, target).defaultWhenNotFinite(1.0)}
            previous[source] = result
        }
    }

    fun getHittingTime(source: String, target: String): Double {
        if (source == target)
            return 1.0

        val walk = RandomWalkIterator(g, source, true)
        var counter = 0
        walk.forEach { node ->
            counter += 1
            if (node == target) {
                return counter.toDouble()
            }
        }
        return 0.0
    }

    fun getScore(source: String, previous: HashMap<String, List<Double>>, targets: List<String>): List<Double> {
//        val walk = ClosestFirstIterator(g, source)
        val walk = ClosestFirstIterator(g, source)
        val targetMap = targets.mapIndexed { index, s -> s to index  }.toMap()
        val hittingTimes = (0 until targets.size).map { 0.0 }.toArrayList()
        val seen = HashSet<String>()
        var remaining = targets.size

        var steps = 0
        walk.forEach { node ->
            steps += 1

            if (node in previous) {
                val dist = walk.getShortestPathLength(node)
                val prevWeights = previous[node]!!
                    .map { it + dist }
                previous[source] = prevWeights
                return prevWeights
            }
            if (node in targetMap && node !in seen) {
                hittingTimes[targetMap[node]!!]  = walk.getShortestPathLength(node)
                remaining -= 1
                if (remaining == 0) {
                    previous[source] = hittingTimes.toList()
                    return hittingTimes
                }
            }
            if (steps > 1000) {
//                if (closest != "") {
//                    val dist = walk.getShortestPathLength(closest)
//                    val res = previous[closest]!!.map { it + dist }
//                    previous[source] = res
//                    return res
//                }
//                previous[source] = hittingTimes.toList()
                return hittingTimes

            }
        }
        return hittingTimes
    }

    fun getHittingTimes(source: String, previous: HashMap<String, List<Double>>, targets: List<String>): List<Double> {
        val targetMap = targets.mapIndexed { index, s -> s to index  }.toMap()
        val seen = HashSet<String>()
        var remaining = targets.size
        val hittingTimes = (0 until remaining).map { 0.0 }.toArrayList()


        (0 until 10).forEach {
            val walk = RandomWalkIterator(g, source, true, 5)
            var counter = 0.0
            var prev = source

            walk.forEach { node ->
                //            counter += 1

                val weight = g.getEdgeWeight(g.getEdge(prev, node))
                counter += weight

//                if (node in previous) {
//                    val newWeights = previous[node]!!.map { it + counter.toDouble() }
//                    previous[source] = newWeights
//                    return newWeights
//                }


//                if (node in targetMap && node !in seen) {
//                    seen += node
//                    hittingTimes[targetMap[node]!!] = counter.toDouble()
//                    remaining -= 1
//                    if (remaining == 0) {
//                        previous[source] = hittingTimes.toList()
//                        return hittingTimes.toList()
//                    }
//                }

                if (node in targetMap) {
                    hittingTimes[targetMap[node]!!] += weight
                }

                if (counter > 100000) {
                    return hittingTimes
                }

                prev = node
            }
        }
        return hittingTimes.map { it / 10.0 }
    }


    fun analyze() {
//        val flow = EdmondsKarpMFImpl(g)
//        println(flow.calculateMaximumFlow("war", "drug"))
//        println("\n\nAnalyze:")
//        val greedy = PageRank(g)
//        greedy.scores
//            .normalize()
//            .entries
//            .sortedByDescending { it.value }
//            .take(8)
//            .forEach { println(it) }


//        val start = g.vertexSet().toList().sampleRandom()
//        val walker = RandomWalkIterator(g, start, true)
//        var prev = start
//        (0 until 10).forEach {
//            val cur = walker.next()
//            val edge = g.getEdge(prev, cur)
//            val weight = g.getEdgeWeight(edge)
//            println("$prev -> $cur : $weight")
//            prev = cur
//        }
    }
}

fun main(args: Array<String>) {
    val grapher = Grapher()
    grapher.build()
    grapher.diffusionWalk()
//    grapher.analyze()
}