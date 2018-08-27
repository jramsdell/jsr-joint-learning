package learning.graph

import learning.graph.containers.GraphParams
import learning.graph.enums.GraphNormalizationType
import learning.graph.enums.VertexType
import org.jgrapht.graph.*
import org.jgrapht.traverse.ClosestFirstIterator
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import utils.misc.toArrayList
import utils.stats.defaultWhenNotFinite
import utils.stats.normalize


class Grapher(private val kodein: Kodein) {
//    val g: DefaultUndirectedWeightedGraph<String, DefaultWeightedEdge> = DefaultUndirectedWeightedGraph(DefaultWeightedEdge::class.java)
    val g: SimpleDirectedWeightedGraph<String, DefaultWeightedEdge> by kodein.instance()
    val graphParams: GraphParams by kodein.instance()



    fun invertGraphWeights() {
        g.edgeSet().forEach { edge ->
            val base = graphParams.normalizationType.f(g.getEdgeWeight(edge)).defaultWhenNotFinite(0.0)
            g.setEdgeWeight(edge, base)
        }
    }
}

fun main(args: Array<String>) {
//    val grapher = Grapher()
//    grapher.parser.build()
//    grapher.filterLowFreqWeight()
//    grapher.scorer.diffusionWalk()
//    grapher.scorer.diffusionWalkEdges()
//    grapher.scorer.highestDegreeEdges()

//    grapher.invertGraphWeights()
//    grapher.scorer.computeBranchingEdgeDistances()
//        grapher.scorer.algorithms.computeShortestEdgeHubs()

//    grapher.scorer.computeDistanceFromNodeMap()
//    grapher.scorer.algorithms.printDists()
//    grapher.scorer.algorithms.computeShortestEdgeHubs()
//    grapher.scorer.getTotalDists()

//    grapher.scorer.computeBranchingDistances()
//    grapher.scorer.getVoronoi()
}