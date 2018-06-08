package lucene.parsers

import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import org.json.JSONObject
import utils.parallel.asIterable
import java.io.File
import java.util.concurrent.atomic.AtomicInteger


class ExtractorStream(corpusLocs: List<String>, val chunkSize: Int = 5000) {
    val extractors = ArrayList<Extractor<Data.Page>>()
    val counter = AtomicInteger()

    val corpusStreams = corpusLocs.map { corpusLoc ->
        File(corpusLoc)
            .inputStream()
            .buffered()
    }

//    private inline fun<reified A> getStream(corpusLoc: String) = when (A::class) {
//        Data.Page::class -> fromPageCorpus(corpusLoc)
//        Data.Paragraph::class -> fromParagraphCorpus(corpusLoc)
//        else                  -> throw IllegalArgumentException("Unknown type")
//
//    }

//    @Suppress("UNCHECKED_CAST")
//    private inline fun<reified A> getStream(corpusLoc: String): Iterable<A> = when (A::class) {
//            Data.Page::class      -> fromPageCorpus(corpusLoc)
//            Data.Paragraph::class -> fromParagraphCorpus(corpusLoc)
//            else                  -> throw IllegalArgumentException("Unknown type")
//        } as Iterable<A>

    fun addAbstractExtractor() = extractors +
        Extractor<Data.Page>("entity_abstracts.tsv", Extractor<Data.Page>::extractAbstractAndGrams)

    fun addMetadataExtractor() = extractors +
            Extractor<Data.Page>("entity_metadata.tsv", Extractor<Data.Page>::extractMetadata)

    fun addParagraphGramExtractor() = extractors +
            Extractor<Data.Page>("paragraph_grams.tsv", Extractor<Data.Page>::extractGram)

    fun run() = corpusStreams.forEach { corpusStream ->
        DeserializeData.iterAnnotations(corpusStream)
            .asSequence()
            .chunked(chunkSize)
            .forEach { chunk ->
                println(counter.incrementAndGet())
                extractors.forEach { extractor -> extractor.doExtract(chunk) } }
            .apply { corpusStream.close() }
    }

}



fun main(args: Array<String>) {
}