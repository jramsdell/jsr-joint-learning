package learning.graph.containers

import learning.graph.enums.*
import org.jgrapht.Graph
import org.jgrapht.graph.DefaultWeightedEdge
import java.util.concurrent.ConcurrentHashMap


data class ParseParams(val vertexParseType: VertexParseType,
                     val textParseType: TextParseType,
                     val edgeParseType: EdgeParseType)

data class GraphParams(val normalizationType: GraphNormalizationType,
                       val vertexType: VertexType,
                       val directionType: GraphDirectionType)


data class GraphData(val g: Graph<String, DefaultWeightedEdge>)


