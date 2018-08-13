package learning.graph

import org.jgrapht.Graph
import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.graph.SimpleWeightedGraph
import utils.AnalyzerFunctions
import utils.misc.pairwise
import java.io.File


class GraphParser(val g: Graph<String, DefaultWeightedEdge>) {

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

    fun parseUnigram(file: File) {
        file.readText()
            .split(".")
            .forEach { sentence ->
                val tokens = AnalyzerFunctions.createTokenList(sentence, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)
//                tokens.pairwise().forEach { (e1, e2) -> incrementEdge(e1, e2) }
                tokens.windowed(2).forEach { t -> incrementEdge(t[0], t[1]) }
            }
    }

    fun parseBigram(file: File) {
        file.readText()
            .let { sentence ->
                val tokens = AnalyzerFunctions.createTokenList(sentence, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)
                    .windowed(2, partialWindows = false)
                    .map { it.joinToString("_") }

                tokens.pairwise().forEach { (e1, e2) -> incrementEdge(e1, e2) }
//                tokens.windowed(2).forEach { t -> incrementEdge(t[0], t[1]) }
            }
    }

    fun build() {
        val loc = "/home/hcgs/Desktop/projects/jsr-joint-learning/resources/paragraphs"
        File(loc).listFiles()
            .drop(2)
            .take(1)
            .forEach { fDir ->
                println(fDir.name)
                fDir.listFiles().forEach { file ->
                    parseUnigram(file)
                }
            }

//        g.edgeSet().toList().map { it to g.getEdgeWeight(it) }
//            .let { edgeMap ->
//                edgeMap.sortedByDescending { it.second }
//                .take(edgeMap.size / 2)
//                    .let { badEdges -> g.removeAllEdges(badEdges.map { it.first }) }
//            }

//        val edges = g.edgeSet().toList()
//        val total = edges.sumByDouble { edge -> g.getEdgeWeight(edge) }
//        edges.forEach { edge -> g.setEdgeWeight(edge,g.getEdgeWeight(edge) / total) }
    }

}