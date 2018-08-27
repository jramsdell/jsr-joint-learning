package learning.graph

import learning.graph.containers.GraphData
import learning.graph.containers.GraphParams
import learning.graph.containers.ParseParams
import learning.graph.enums.*
import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.graph.SimpleDirectedWeightedGraph
import org.kodein.di.*
import org.kodein.di.generic.bind
import org.kodein.di.generic.singleton


class GraphManager(
        val vertexType: VertexType = VertexType.HYPER,
        val gNum: Int = -1 ) {


    private fun Kodein.MainBuilder.bindGraphParams() {
        bind<GraphParams>() with singleton { GraphParams(
                normalizationType = GraphNormalizationType.G_STANDARD,
                vertexType = VertexType.HYPER,
                directionType = GraphDirectionType.GRAPH_DIRECTED
        ) }
    }

    private fun Kodein.MainBuilder.bindParserParams() {
        bind<ParseParams>() with singleton { ParseParams(
                vertexParseType = VertexParseType.VERTEX_PARSE_UNIGRAM,
                textParseType = TextParseType.TEXT_PARSE_SENTENCE,
                edgeParseType = EdgeParseType.EDGE_PARSE_ADJACENT
        ) }
    }

    private fun Kodein.MainBuilder.bindGraphData() {
        bind<GraphData>() with singleton { GraphData(
                g = SimpleDirectedWeightedGraph(DefaultWeightedEdge::class.java)
        ) }

    }


    fun constructInstance() {
        val kodein = Kodein {
            bindGraphParams()
            bindParserParams()
            bindGraphData()
        }
    }
}