package lucene.parsers

import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import entity.SpotlightEntityLinker
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap
import utils.lucene.foldOverSection
import utils.lucene.outlinks
import utils.misc.toArrayList
import utils.parallel.forEachParallelQ
import utils.parallel.pmap
import java.io.File


class TrecCarQrelGenerator() {
    val outlineCbor = "/home/jsc57/data/benchmark/benchmarkY1/benchmarkY1-train/train.pages.cbor"
        .run { File(this).inputStream().buffered() }

    val linker = SpotlightEntityLinker("/home/jsc57/projects/jsr-joint-learning/spotlight_server")
        .apply { (0 until 100).forEach { queryServer("Test") }   }

    private fun cleanEntity(entry: String) =
            entry.replace("enwiki:", "")
                .replace("%20", "_")
                .replace(" ", "_")

    fun run() {
        val qrelOut = File("hierarchical_qrels.qrels").bufferedWriter()
        DeserializeData.iterableAnnotations(outlineCbor)
            .forEach { page: Data.Page ->
                val qtitle = cleanEntity(page.pageId).replace("enwiki:", "")
                qrelOut.write("${page.pageId} 0 $qtitle 1\n")

                val outlinks = page.outlinks()

                page.foldOverSection { path, section, paragraphs ->
                    val entities = paragraphs.pmap { paragraph ->
                        val text = paragraph.textOnly

                        val annotatedEntities = linker.queryServer(text)
                            .filter { it in outlinks }

                        val existingEntities = paragraph.entitiesOnly.map(this::cleanEntity)
                        annotatedEntities + existingEntities
                    }.flatten()

                    val sectionEntities = entities.toSet()

                    val qrelPath = "enwiki:${path.replace(" ", "%20")}"
                    sectionEntities.forEach { entity ->
                        qrelOut.write("$qrelPath 0 ${entity.replace("_", "%20")} 1\n")
                    }
                }
            }

        qrelOut.close()
    }

}
