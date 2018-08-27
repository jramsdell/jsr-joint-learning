package learning.graph

import learning.graph.enums.EdgeParseType
import learning.graph.enums.TextParseType
import learning.graph.enums.VertexParseType
import org.jgrapht.Graph
import org.jgrapht.graph.DefaultWeightedEdge
import utils.AnalyzerFunctions
import utils.misc.pairwise
import java.io.File


class GraphParser(val g: Graph<String, DefaultWeightedEdge>,
                  val textParseType: TextParseType = TextParseType.TEXT_PARSE_SENTENCE,
                  val vertexParseType: VertexParseType = VertexParseType.VERTEX_PARSE_UNIGRAM,
                  val edgeParseType: EdgeParseType = EdgeParseType.EDGE_PARSE_ADJACENT,
                  val gNum: Int = -1
) {

    fun incrementEdge(v1: String, v2: String) {
        // No loops
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

    fun getTokens(text: String): List<String> {
        val tokens = AnalyzerFunctions.createTokenList(text,
                analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)
        return when (vertexParseType) {
            VertexParseType.VERTEX_PARSE_UNIGRAM -> tokens
            VertexParseType.VERTEX_PARSE_BIGRAM -> tokens.windowed(2).map { it[0] + "_" + it[1] }
        }
    }

    fun assignEdges(tokens: List<String>) {
        when (edgeParseType) {
            EdgeParseType.EDGE_PARSE_ADJACENT ->
                tokens.windowed(2).forEach { t -> incrementEdge(t[0], t[1]) }
            EdgeParseType.EDGE_PARSE_PAIRWISE ->
                tokens.pairwise().forEach { (e1, e2) -> incrementEdge(e1, e2) }
        }
    }

    fun parseText(file: File) {
        val text = file.readText()
        when (textParseType) {
            TextParseType.TEXT_PARSE_DOCUMENT -> getTokens(text).run { assignEdges(this) }
            TextParseType.TEXT_PARSE_SENTENCE -> text.split(".").forEach { sentence ->
                getTokens(sentence).run { assignEdges(this) } }
        }
    }


    fun build() {
        val loc = "/home/hcgs/Desktop/projects/jsr-joint-learning/resources/paragraphs"
        val fileList = File(loc).listFiles().toList()
            .run { if (gNum != -1)  drop(gNum).take(1) else this}

        fileList
            .forEach { fDir ->
                println(fDir.name)
                fDir.listFiles().forEach { file ->
                    parseText(file)
                }
            }
    }
}