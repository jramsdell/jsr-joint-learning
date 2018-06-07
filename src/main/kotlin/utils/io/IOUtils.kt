@file:JvmName("KotUtils")
package utils.io

//import org.apache.commons.math3.random.JDKRandomGenerator
import org.json.JSONException
import java.io.IOException

//import org.apache.lucene.analysis.en.EnglishAnalyzer
//import org.apache.lucene.analysis.standard.StandardAnalyzer
//import org.apache.lucene.index.DirectoryReader
//import org.apache.lucene.index.IndexWriter
//import org.apache.lucene.index.IndexWriterConfig
//import org.apache.lucene.search.IndexSearcher
//import org.apache.lucene.search.TopDocs
//import org.apache.lucene.search.similarities.BM25Similarity
//import org.apache.lucene.store.FSDirectory

import java.util.concurrent.ThreadLocalRandom


//fun <A>Sequence<A>.forEachParallel(f: suspend (A) -> Unit): Unit = runBlocking {
//    forEach { async(CommonPool) { f(it) }.await() }
//}


inline fun<A> doIORequest(retryAttempts: Int = 5, retryDelay: Long = 5, requestFunction: () -> A): A? {
    (0 until retryAttempts).forEach {
        try {
            return requestFunction()
        } catch (e: IOException) { Thread.sleep(ThreadLocalRandom.current().nextLong(retryDelay)) }
    }
    return null
}


fun <A> catchJsonException(default: A, f: () -> A ): A {
    return try { f() } catch (e: JSONException) { default }
}


