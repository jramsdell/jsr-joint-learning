package lucene.parsers

import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import entity.EntityStats
import entity.SpotlightEntityLinker
import kotlinx.coroutines.experimental.launch
import utils.lucene.outlinks
import utils.lucene.paragraphs
import utils.parallel.asIterable
import utils.parallel.forEachChunkedParallel
import utils.parallel.forEachParallel
import utils.parallel.forEachParallelRestricted
import java.io.BufferedInputStream
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import java.util.stream.StreamSupport
import kotlin.concurrent.withLock
import kotlin.coroutines.experimental.buildIterator

class TrecParagraphAnnotator(serverLocation: String) {
    val linker = SpotlightEntityLinker(serverLocation)
    val cborLocations = listOf(
            "/home/jsc57/data/unprocessedAllButBenchmark.cbor/unprocessedAllButBenchmark.cbor",
            "/home/jsc57/data/test200/test200-train/train.pages.cbor" )

    val output = File("paragraph_mappings.txt").bufferedWriter()
    val counter = AtomicInteger()

    private fun iterWrapper(f: BufferedInputStream): Iterable<Pair<Set<String>, List<Data.Paragraph>>> {
        val iter = DeserializeData.iterAnnotations(f)
        val iterWrapper = buildIterator() {
            while (iter.hasNext()) {
                val nextPage = iter.next()

                yield(nextPage.outlinks() to nextPage.paragraphs())
            }
        }
        return Iterable { iterWrapper }
    }

    fun process(stuff: List<Pair<Set<String>, List<Data.Paragraph>>>) {
        stuff.forEachParallel { (links, paragraphs) ->
            val builder = StringBuilder()
            paragraphs.forEach { paragraph ->
                val id = paragraph.paraId
                val entities = linker.queryServer(paragraph.textOnly)
                    .filter(links::contains)
                    .joinToString(" ")
                builder.append("$id\t$entities\n")
            }

            println(counter.get())
            output.append(builder.toString())
            counter.addAndGet(paragraphs.size).let { value ->
                if (value % 10000 == 0) {
                    output.flush()
                }
            }
        }
    }

    fun processs2(page: Data.Page) {
        val links = page.outlinks()
        val paragraphs = page.paragraphs()
        val builder = StringBuilder()
        paragraphs.forEach { paragraph ->
            val id = paragraph.paraId
            val entities = linker.queryServer(paragraph.textOnly)
                .filter(links::contains)
                .joinToString(" ")
            builder.append("$id\t$entities\n")
        }

        println(counter.get())
        output.append(builder.toString())
        counter.addAndGet(paragraphs.size).let { value ->
            if (value % 10000 == 0) {
                output.flush()
            }
        }
    }


    fun annotate(corpusLoc: String) {
        var fStream = File(corpusLoc).inputStream().buffered()
        val lock = ReentrantLock()
//        val iter = iterWrapper(fStream)
        val iter = DeserializeData.iterAnnotations(fStream)
//        StreamSupport.stream(DeserializeData.iterableAnnotations(fStream).spliterator(), true)
//        DeserializeData.iterableAnnotations(fStream)
//            .forEach { page ->
//        var buildQueue = StringBuilder()


//        StreamSupport.stream(iter.spliterator(), true)
        (0 until 50).forEach {
            linker.queryServer("Hello there")
        }


        iter.asIterable()
            .forEachChunkedParallel(5000) { page -> processs2(page) }



        output.close()
    }
}

