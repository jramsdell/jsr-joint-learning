package utils.misc

import kotlin.system.measureTimeMillis

fun<A> withTime(f: () -> A): Pair<Long, A> {
    var result: A? = null
    val time = measureTimeMillis { result = f() }
    return time to result!!
}

// I don't know why the hell they don't have an identity function..
fun <A> identity(it: A): A = it

const val PID = "paragraphid"
const val CONTENT = "text"
fun <A, B, C>Iterable<A>.accumMap(keyFun: (A) -> C, f: (B?, A) -> B): List<Pair<C, B>> {
    var init: B? = null
    return map { element ->
        val key = keyFun(element)
        val result = f(init, element)
        init = result
        key to result
    }
}

// Map Extensions
fun <K,V>MutableMap<K,V>.removeAll(f: (key:K,value:V) -> Boolean) {
    this.entries
        .filter{(key,value) -> f(key,value)}
        .forEach { (key,_) ->
            remove(key)
        }
}