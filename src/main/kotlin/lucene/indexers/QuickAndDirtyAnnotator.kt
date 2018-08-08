package lucene.indexers

import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.ByteString
import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.CborDataItemIterator
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import lucene.containers.LuceneDocumentContainer
import org.apache.lucene.index.IndexWriterConfig
import org.json.JSONObject
import utils.parallel.asIterable
import utils.parallel.forEachChunkedParallel
import utils.parallel.forEachParallel
import utils.parallel.forEachParallelQ
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.StreamSupport
import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.UnicodeString
import edu.unh.cs.treccar_v2.read_data.CborListWithHeaderIterator
import entity.SpotlightEntityLinker
import language.GramAnalyzer
import lucene.indexers.*
import org.apache.lucene.document.Document
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.Term
import org.apache.lucene.search.IndexSearcher
import utils.AnalyzerFunctions
import utils.AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED
import utils.lucene.*
import lucene.indexers.IndexFields.*
import org.apache.lucene.analysis.StopwordAnalyzerBase
import utils.AnalyzerFunctions.AnalyzerType.ANALYZER_STANDARD_STOPPED
import utils.misc.toArrayList
import utils.stats.countDuplicates
import utils.stats.takeMostFrequent
import java.lang.Integer.min


class QuickAndDirtyAnnotator(corpusLoc: String) {
    val corpus = File(corpusLoc).inputStream().buffered()
    val paragraphCounter = AtomicInteger()
    val linker = SpotlightEntityLinker("/home/jsc57/projects/jsr-joint-learning/spotlight_server")
        .apply { (0 until 100).forEach { queryServer("Test") }   }

    val output = File("paragraph_annotations.tsv").writer().buffered()



    private fun cleanEntity(entry: String) =
            entry.replace("enwiki:", "")
                .replace("%20", "_")
                .replace(" ", "_")




    fun processParagraphs(page: Data.Page) {
        val outlinkSet = page.outlinks()
        val buffer = ArrayList<String>()
        page.foldOverSection(false) { path, section, paragraphs ->
//            val windowedSets = pData.map { p -> p.windowed.split(" ").toSet() }
            paragraphs.forEach { paragraph ->
                val text = paragraph.textOnly
                    .run { AnalyzerFunctions.createTokenList(this, analyzerType = ANALYZER_STANDARD_STOPPED) }
                    .joinToString(" ")
                val annotations = linker.queryServer(text).toHashSet()

                paragraph.entitiesOnly
                    .map { cleanEntity(it) }
                    .forEach { entity ->
                        if (entity !in annotations)
                            annotations.add(entity) }

                val anOutlink = annotations.filter { it in outlinkSet }
                    .joinToString(" ")
                buffer.add("${paragraph.paraId}\t$anOutlink\n")

            }
        }

        output.write(buffer.joinToString(""))

        val times = paragraphCounter.incrementAndGet()
        if (times % 10 == 0) {
            println("Pages Annotated: $times")
        }
    }
    fun run() {
        DeserializeData.iterableAnnotations(corpus)
            .asSequence()
            .forEachParallelQ(1000, 500) { processParagraphs(it) }

        output.close()
    }

}



