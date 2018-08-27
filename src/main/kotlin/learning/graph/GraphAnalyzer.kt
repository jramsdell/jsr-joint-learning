package learning.graph

import learning.graph.containers.GraphData
import learning.graph.containers.GraphParams
import org.jgrapht.Graph
import org.jgrapht.graph.DefaultWeightedEdge
import org.kodein.di.Kodein
import org.kodein.di.generic.instance


class GraphAnalyzer(private val kodein: Kodein) {
    private val graphData: GraphData by kodein.instance()
    private val graphParams: GraphParams by kodein.instance()
    private val g = graphData.g

}