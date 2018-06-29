package utils.parallel

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import kotlinx.coroutines.experimental.runBlocking
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

// Parallel versions of map/forEach methods.
// See: https://stackoverflow.com/questions/45575516/kotlin-process-collection-in-parallel
fun <A, B>Iterable<A>.pmap(f: suspend (A) -> B): List<B> = runBlocking {
    map { async(CommonPool) { f(it) } }.map { it.await() }
}

fun <A, B>Iterable<A>.pmapRestricted(nThreads: Int = 10, f: suspend (A) -> B): List<B> = runBlocking {
    val pool = newFixedThreadPoolContext(nThreads, "parallel")
    map { async(pool) { f(it) } }.map { it.await() }
}

fun <A>Iterable<A>.forEachParallel(f: suspend (A) -> Unit): Unit = runBlocking {
    map { async(CommonPool) { f(it) } }.forEach { it.await() }
}

fun <A>Iterable<A>.forEachParallelRestricted(nThreads: Int = 10, f: suspend (A) -> Unit): Unit = runBlocking {
    val pool = newFixedThreadPoolContext(nThreads, "parallel")
    map { async(pool) { f(it) } }.forEach { it.await() }
}

fun <A>Iterable<A>.forEachChunkedParallel(chunkSize: Int, f: suspend (A) -> Unit): Unit = runBlocking {
    asSequence()
        .chunked(chunkSize)
        .forEach { chunk ->
            chunk.forEachParallel(f)
        }
}

fun <A>Iterator<A>.asIterable(): Iterable<A> {
    return Iterable { this }
}

fun <A>Iterable<A>.forEachParallelQ(qSize: Int = 1000, nThreads: Int = 30, f: (A) -> Unit) {
    val q = ArrayBlockingQueue<A>(qSize, true)
    val finished = AtomicBoolean(false)
    val taker = {
        while (true) {
            val next = q.poll(100, TimeUnit.MILLISECONDS)
            next?.run(f)
            if (next == null && finished.get())  break
        }
    }
    val threads = (0 until nThreads).map { Thread(taker).apply { start() } }
    forEach { element -> q.put(element)}
    finished.set(true)
    threads.forEach { thread -> thread.join() }
}

fun <A>Sequence<A>.forEachParallelQ(qSize: Int = 1000, nThreads: Int = 30, f: (A) -> Unit) {
    val q = ArrayBlockingQueue<A>(qSize, true)
    val finished = AtomicBoolean(false)
    val taker = {
        while (true) {
            val next = q.poll(100, TimeUnit.MILLISECONDS)
            next?.run(f)
            if (next == null && finished.get())  break
        }
    }
    val threads = (0 until nThreads).map { Thread(taker).apply { start() } }
    forEach { element -> q.put(element)}
    finished.set(true)
    threads.forEach { thread -> thread.join() }
}
