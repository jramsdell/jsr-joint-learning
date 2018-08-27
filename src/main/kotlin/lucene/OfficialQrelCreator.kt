package lucene

import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import entity.EntityStats
import lucene.indexers.IndexFields
import org.apache.lucene.search.IndexSearcher
import utils.AnalyzerFunctions
import utils.lucene.foldOverSection
import utils.lucene.foldOverSectionWithId
import utils.lucene.searchFirstOrNull
import utils.misc.groupOfSetsFlattened
import utils.misc.mapOfSets
import utils.parallel.pmap
import java.io.File
import java.util.*


class OfficialQrelCreator(val cborLoc: String, val entityMapLoc: String) {

    val entityMap = File(entityMapLoc).bufferedReader()
        .readLines()
        .map { line ->
            val (id, rawEntity) = line.replace(" ", "_").split("\t")
            rawEntity to id }
        .toMap()

    fun parse(page: Data.Page): ArrayList<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()

        page.foldOverSectionWithId(useFilter = false) { path, section, paragraphs ->
            val nPath = path.replace(" ", "%20")
            val entitiesInSection = HashMap<String, ArrayList<String>>()
            paragraphs.forEach { p ->
                val pid = p.paraId
                val results = EntityStats.doTagMeQuery(p.textOnly)
                    .distinctBy { it.first }

                results.forEach { (entity, _) ->
                    entitiesInSection.computeIfAbsent(entity) { ArrayList() }
                        .add(pid)
                }
            }

            entitiesInSection.forEach { (entity, support) ->
                val lookup = entityMap[entity] ?: ""
                if (lookup == "") {
                    println("Something went wrong for: $entity ... at path: $nPath")
                } else {
//                val converted = entity.replace("_", " ")
//                    .replace(" ", "%20")
//                    .run { "enwiki:" + this }
                    support.forEach { supportPar ->
                        out.add(nPath to "$nPath Q0 $supportPar/$lookup 1")
                    }
                }
            }
        }

        return out
    }

    fun run() {
        println("File is: $cborLoc")
        val iFile = File(cborLoc)
            .inputStream()
            .buffered()

        val outFile = File("prototype.qrels").bufferedWriter()

        DeserializeData.iterableAnnotations(iFile)
            .pmap { page -> parse(page) }
            .flatten()
            .sortedBy { it.first }
            .forEach { outFile.write(it.second + "\n") }

        outFile.flush()
        outFile.close()

    }

}