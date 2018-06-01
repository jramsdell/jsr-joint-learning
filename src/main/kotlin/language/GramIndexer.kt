@file:JvmName("KotGram")
package language

import edu.unh.cs.treccar_v2.read_data.DeserializeData
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.TextField
import utils.AnalyzerFunctions
import java.io.BufferedInputStream
import java.lang.Math.min
import java.util.*
import kotlin.coroutines.experimental.buildIterator
import utils.AnalyzerFunctions.AnalyzerType.*


/**
 * Class: GramIndexer
 * Desc: This class is responsible for extracting unigrams, bigrams, and windowed bigrams from the corpus.
 */
class GramIndexer() {
//    val indexWriter = getIndexWriter(filename)

    /**
     * Function: doIndex
     * Desc: Given the content of a paragraph, indexes unigrams, bigrams, and windowed bigrams.
     */
    fun index(doc: Document, parText: String) {
        val tokens = AnalyzerFunctions.createTokenList(parText, ANALYZER_ENGLISH_STOPPED)
//        val doc = Document()
        val unigrams = ArrayList<String>()
        val bigrams = ArrayList<String>()
        val bigramWindows = ArrayList<String>()
        (0 until tokens.size).forEach { i ->
            unigrams.add(tokens[i])
            if (i < tokens.size - 1) {
                bigrams.add(tokens[i] + tokens[i + 1])
            }

            ( i + 1 until min(i + 9, tokens.size)).forEach { j ->
                bigramWindows.add(tokens[i] + tokens[j])
                bigramWindows.add(tokens[j] + tokens[i])
            }
        }
        doc.add(TextField("unigram", unigrams.joinToString(separator = " "), Field.Store.YES))
        doc.add(TextField("bigrams", bigrams.joinToString(separator = " "), Field.Store.YES))
        doc.add(TextField("bigram_windows", bigrams.joinToString(separator = " "), Field.Store.YES))
//        indexWriter.addDocument(doc)

    }

    fun getTokenSubset(text: String, start: Int, stop: Int): List<String> {
        val subset = text.substring(start, stop)
        return AnalyzerFunctions.createTokenList(subset, ANALYZER_ENGLISH_STOPPED)
    }

    /**
     * Function: doIndex
     * Desc: Given the content of a paragraph, indexes unigrams, bigrams, and windowed bigrams.
     */
    fun indexBall(doc: Document, parText: String, ballSize: Int, start: Int, stop: Int) {
        val leftSubset = getTokenSubset(parText, 0, start).takeLast(ballSize)
        val rightSubset = getTokenSubset(parText, stop, parText.length).take(ballSize)
        val center = getTokenSubset(parText, start, stop)
        val tokens = leftSubset + center + rightSubset

        val unigrams = ArrayList<String>()
        val bigrams = ArrayList<String>()
        val bigramWindows = ArrayList<String>()
        (0 until tokens.size).forEach { i ->
            unigrams.add(tokens[i])
            if (i < tokens.size - 1) {
                bigrams.add(tokens[i] + tokens[i + 1])
            }

            ( i + 1 until min(i + 9, tokens.size)).forEach { j ->
                bigramWindows.add(tokens[i] + tokens[j])
                bigramWindows.add(tokens[j] + tokens[i])
            }
        }
        doc.add(TextField("unigram_$ballSize", unigrams.joinToString(separator = " "), Field.Store.YES))
        doc.add(TextField("bigrams_$ballSize", bigrams.joinToString(separator = " "), Field.Store.YES))
        doc.add(TextField("bigram_windows_$ballSize", bigrams.joinToString(separator = " "), Field.Store.YES))
    }

    /**
     * Func: iterWrapper
     * Desc: A really annoying fix to some memory leaks I was seeing. For some reason why I parallelize adding documents,
     *      from the paragraph corpus, the memory was not being freed up in time and everything grinds to a halt.
     *      This function wraps around the iterParagraphs function.
     *      Again, I don't know why this fixes the problem, but it does...
     */
    private fun iterWrapper(f: BufferedInputStream): Iterable<String> {
        val iter = DeserializeData.iterParagraphs(f)
        var counter = 0
        val iterWrapper = buildIterator<String>() {
            while (iter.hasNext()) {
                val nextPar = iter.next()

                // Only using 30% of the available documents in paragraph corpus
                if (counter % 3 == 0) {
                    yield(nextPar.textOnly)
                }
            }
        }
        return Iterable { iterWrapper }
    }


//    /**
//     * Class: indexGrams
//     * Desc: Given a paragraph corpus, creates an index of grams, bigrams, and windowed bigrams.
//     *       Only 30% of the available corpus is used (to save space).
//     */
//    fun indexGrams(filename: String) {
//        val f = File(filename).inputStream().buffered(16 * 1024)
//        val counter = AtomicInteger()
//
//        iterWrapper(f)
//            .forEachParallel { par ->
//
//                // This is just to keep track of how many pages we've parsed
//                counter.incrementAndGet().let {
//                    if (it % 100000 == 0) {
//                        println(it)
//                        indexWriter.commit()
//                    }
//                }
//
//                // Extract all of the anchors/entities and add them to database
//                doIndex(par)
//            }
//
//        indexWriter.close()
//    }
}
