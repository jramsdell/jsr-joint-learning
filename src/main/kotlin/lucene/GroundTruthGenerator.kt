package lucene

import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import utils.lucene.paragraphs
import utils.parallel.pmap
import utils.stats.normalize
import java.io.File

class GroundTruthGenerator(clickStreamLoc: String, val cborOutlineLoc: String) {
    val pageSet =  getPageSet(cborOutlineLoc)

    val entityScores = run(clickStreamLoc)

    private fun cleanEntity(entry: String) =
            entry.replace("enwiki:", "")
                .replace("%20", "_")
                .replace(" ", "_")

    private fun createQueryString(page: Data.Page, sectionPath: List<Data.Section>): String =
            page.pageName + sectionPath.joinToString { section -> " " + section.heading  }

    private fun getPageSet(cborOutlineLoc: String): Set<String> {
        val input = File(cborOutlineLoc).inputStream().buffered()
        return DeserializeData.iterableAnnotations(input)
            .map { page: Data.Page ->
                val queryStr = createQueryString(page, emptyList())
                queryStr.replace(" ", "_")
            }.toSet()
    }

    fun run(clickStreamLoc: String): Map<String, Map<String, Int>> {
        val reader = File(clickStreamLoc).bufferedReader()
        val results = HashMap<String, ArrayList<Pair<String, Int>>>()
        reader.forEachLine { line ->
            val elements = line.split("\t")
            if (elements[0] in pageSet) {
                results.computeIfAbsent(elements[0], { ArrayList() })
                    .add(elements[1] to elements[3].toInt())
            }
        }

        return results.mapValues { it.value.toMap() }

//        results.entries.forEach { (k,v) ->
//            println("$k")
//            v.toMap().entries.sortedByDescending { it.value }.forEach { println("\t$it") }
//            println()
//        }
    }

    fun generateQrels() {
        val input = File(cborOutlineLoc).inputStream().buffered()
        val entityQrels = HashMap<String, List<Pair<String, Int>>>()
        val paragraphQrels = HashMap<String, List<Pair<String, Int>>>()
        DeserializeData.iterableAnnotations(input)
            .forEach { page ->
                val queryStr = createQueryString(page, emptyList()).replace(" ", "_")
                val id = page.pageId
                val pageScores = entityScores[queryStr] ?: emptyMap()

                val relParagraphs = page.paragraphs()
                    .asSequence()
                    .map { paragraph -> paragraph.paraId to paragraph.entitiesOnly.map(this::cleanEntity) }
                    .map { (parId, parEntities) ->
                        parId to parEntities.sumBy { entity -> pageScores[entity] ?: 0 } }
                    .filter { it.second > 0 }.toList()
                    .sortedByDescending { it.second }

                paragraphQrels[id] = relParagraphs.distinctBy { it.first }
                entityQrels[id] = pageScores.toList().sortedByDescending { it.second }.distinctBy { it.first }

//                println(queryStr)
//                println("---------")
//                relParagraphs.filter { it.second > 0 }
//                    .sortedByDescending { it.second }
//                    .forEach { (paragraph, score) -> println("$paragraph\n\tScore: $score\n---\n") }
            }


        val entityWriter = File("entity_qrels.qrels").bufferedWriter()
        val paragraphWriter = File("paragraph_qrels.qrels").bufferedWriter()

        entityQrels
            .filter { it.value.isNotEmpty() }
            .forEach { (id, qrels) ->
            qrels.map { (entity, score) -> "$id 0 enwiki:${entity.replace("_","%20")} $score" }
                .joinToString("\n")
                .apply { entityWriter.write(this.trim() + "\n") }
        }

        paragraphQrels
            .filter { it.value.isNotEmpty() }
            .forEach { (id, qrels) ->
            qrels.map { (pid, score) -> "$id 0 $pid $score" }
                .joinToString("\n")
                .apply { paragraphWriter.write(this.trim() + "\n") }
        }

        entityWriter.close()
        paragraphWriter.close()

    }

}