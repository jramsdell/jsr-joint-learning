package utils.parallel

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import kotlinx.coroutines.experimental.runBlocking

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