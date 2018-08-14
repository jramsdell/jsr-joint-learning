package learning.graph

import org.jgrapht.alg.shortestpath.DijkstraShortestPath
import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.traverse.ClosestFirstIterator
import utils.misc.pairwise

typealias StringPair = Pair<String, String>


class GraphScorerAlgorithms(val graphScorer: GraphScorer) {
    val g = graphScorer.g
    val edgeHubs = graphScorer.edgeHubs
    var distMaps: Map<Pair<String, String>, HashMap<StringPair, Double>> = emptyMap()

    fun getClosestEdgeHub(source: String): Pair<String, Double> {
        val walker = ClosestFirstIterator(g, source)
        var counter = 0

//        if (source in edgeHubs)
//            return source to 0.0
//
//
//
//        walker.forEach { node ->
//            if (node in hubs)
//                return node to walker.getShortestPathLength(node)
//
//            counter += 1
//        }

        return "" to 0.0
    }


    fun ClosestFirstIterator<String, DefaultWeightedEdge>.traverseEdges(iterations: Int = -1,
            f: (edge: DefaultWeightedEdge, edgeSource: String, edgeTarget: String, dist: Double) -> Unit) {

        var counter = 0

        while (hasNext()) {
            val nextNode = next()
            val nextEdge = getSpanningTreeEdge(nextNode)
            if (nextEdge == null)
                continue

            val edgeSource = graph.getEdgeSource(nextEdge)
            val edgeTarget = graph.getEdgeTarget(nextEdge)
            val dist = getShortestPathLength(nextNode)

            f(nextEdge, edgeSource, edgeTarget, dist)
            counter += 1
            if (iterations != -1 && counter > iterations)
                break
        }

    }

    fun computeShortestEdgeHubs() {
        val hubs  = edgeHubs.keys.toList().pairwise()
        val dist = DijkstraShortestPath(g)

        val results = hubs.map { (s, d) ->
            val (s1, s2) = s
            val (d1, d2) = d
            val dists = listOf(s1 to d1, s1 to d2, s2 to d1, s2 to d2)
                .map { (source, target) -> Math.min(dist.getPathWeight(source, target), dist.getPathWeight(target, source)) }
                .average()
            (s to d) to dists }


        distMaps = edgeHubs.keys.map { it to HashMap<StringPair, Double>() }.toMap()
        results.forEach { (pairs, distance) ->
            val (p1, p2) = pairs
            distMaps[p1]!![p2] = distance
            distMaps[p2]!![p1] = distance
        }

        distMaps.forEach { (k,v) ->
            val wee = v.entries.map { it.key to it.value }.sortedBy { it.second }
//                .map { it.first to it.second.toString().take(5) }
            println("$k : $wee")
        }
    }

    fun printDists() {
        graphScorer.finalNodeDists.forEach { (k,v) ->
            val wee = v.entries.map { it.key to it.value }.sortedBy { it.second }
//                .map { it.first to it.second.toString().take(5) }
            println("$k : $wee")
        }
    }
}
