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
import utils.stats.countDuplicates
import utils.stats.defaultWhenNotFinite
import utils.stats.normalize
import java.io.File
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.pow


class Grapher() {
//    val g: DefaultUndirectedWeightedGraph<String, DefaultWeightedEdge> = DefaultUndirectedWeightedGraph(DefaultWeightedEdge::class.java)
    val g: SimpleDirectedWeightedGraph<String, DefaultWeightedEdge> = SimpleDirectedWeightedGraph(DefaultWeightedEdge::class.java)
    val parser: GraphParser = GraphParser(g)
    val scorer: GraphScorer = GraphScorer(g)


    fun invertGraphWeights() {
        g.edgeSet().forEach { edge ->
//            g.setEdgeWeight(edge, (1.0 / g.getEdgeWeight(edge)).defaultWhenNotFinite(0.0))
//            g.setEdgeWeight(edge, Math.log((1.0 / g.getEdgeWeight(edge))).defaultWhenNotFinite(0.0))
//            val base = 1.0 / g.getEdgeWeight(edge).run { Math.exp(this + 1.0) }.defaultWhenNotFinite(0.0)
//            val base = 1.0 / Math.exp(g.getEdgeWeight(edge))
            val base = 1.0 / g.getEdgeWeight(edge).pow(20.0)
//            val base = 1.0 / (g.getEdgeWeight(edge)).pow(20.0)
//            val base = (1.0 / g.getEdgeWeight(edge)).defaultWhenNotFinite(0.0)
//            val base = 1.0 / (Math.log(1.0 + g.getEdgeWeight(edge).defaultWhenNotFinite(0.0)))
            g.setEdgeWeight(edge, base)
        }
//        val total = g.edgeSet().sumByDouble { g.getEdgeWeight(it) }
//        g.edgeSet().forEach { g.setEdgeWeight(it, g.getEdgeWeight(it) / total) }
    }


    fun getDistances(hubs: Map<String, Double>)  {
        val nodes = g.vertexSet().toList()
        val previous = HashMap<String, List<Double>>()
        g.edgeSet().forEach { edge ->
            g.setEdgeWeight(edge, (1.0 / g.getEdgeWeight(edge)).defaultWhenNotFinite(0.0))
        }

        val targets = listOf("sexual_access")
        println(targets)
        getSelfShortest(targets, previous)

        val finalResults = nodes.mapIndexed { index, node ->
            if ((index + 1) % 1000 == 0)
                println(index)
//            val score = getScore(node, previous, results.keys.toList())
            val score = getScore(node, previous, targets)
                .zip(hubs.values.normalize())
//                .map { (score, weight) -> Math.log(score).defaultWhenNotFinite(0.0)  }
//                .map { (score, weight) -> score.defaultWhenNotFinite(0.0).pow(2.0)  }
                .map { (score, weight) -> Math.log(score).defaultWhenNotFinite(0.0)  }
                .average()!!
            node to score
        }
        finalResults.sortedBy { it.second }
            .asSequence()
            .filter { it.second > 0.0 }
            .take(15)
            .forEach { println("Node: ${it.first} / ${it.second}") }

        println("\nBad\n")

        finalResults.sortedByDescending { it.second }
            .asSequence()
            .filter { it.second > 0.0 }
            .take(15)
            .forEach { println("Node: ${it.first} / ${it.second}") }

    }

    fun getSelfShortest(targets: List<String>, previous: MutableMap<String, List<Double>>) {
        val dist = org.jgrapht.alg.shortestpath.DijkstraShortestPath(g)
        targets.map { source ->
            val result = targets.map { target -> dist.getPathWeight(source, target).defaultWhenNotFinite(1.0)}
            previous[source] = result
        }
    }

    fun getScore(source: String, previous: HashMap<String, List<Double>>, targets: List<String>): List<Double> {
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
                return hittingTimes
            }
        }
        return hittingTimes
    }

    fun filterLowFreqWeight() {
        val edges = g.edgeSet().filter { g.getEdgeWeight(it) == 1.0 }
        g.removeAllEdges(edges)
        val nodes = g.vertexSet().filter { g.degreeOf(it) == 0 }
        g.removeAllVertices(nodes)
    }


}

fun main(args: Array<String>) {
    val grapher = Grapher()
    grapher.parser.build()
//    grapher.filterLowFreqWeight()
//    grapher.scorer.diffusionWalk()
//    grapher.scorer.diffusionWalkEdges()
    grapher.scorer.highestDegreeEdges()

    grapher.invertGraphWeights()
    grapher.scorer.computeBranchingEdgeDistances()
    grapher.scorer.getTotalDists()

//    grapher.scorer.computeBranchingDistances()
//    grapher.scorer.getVoronoi()
}