package utils.misc

import org.apache.commons.math3.random.JDKRandomGenerator
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

fun<A> withTime(f: () -> A): Pair<Long, A> {
    var result: A? = null
    val time = measureTimeMillis { result = f() }
    return time to result!!
}

fun<A> printTime(label: String = "Time", f: () -> A): A {
    var result: A? = null
    val time = measureTimeMillis { result = f() }
    println("$label : $time")
    return result!!
}

fun Any.println() = println(this)

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

//fun<A> filledArray(nSize: Int, fillValue: A ) = ArrayList<A>(nSize).apply { fill(fillValue) }
fun<A> filledArray(nSize: Int, fillValue: A ): ArrayList<A> {
    val array = ArrayList<A>()
    (0 until nSize).forEach { array += fillValue }
    return array
}

fun<A, B> Iterable<Pair<A, B>>.groupOfLists(): Map<A, List<B>> {
    val newMap = HashMap<A, ArrayList<B>>()
    forEach { (k,v) ->
        newMap.computeIfAbsent(k, { ArrayList() }).add(v)
    }
    return newMap
}

fun<A, B> Iterable<Pair<A, Iterable<B>>>.groupOfListsFlattened(): Map<A, List<B>> {
    val newMap = HashMap<A, ArrayList<B>>()
    forEach { (k,v) ->
        newMap.computeIfAbsent(k, { ArrayList() }).addAll(v)
    }
    return newMap
}

fun<A, B> Iterable<Pair<A, B>>.groupOfSets(): Map<A, Set<B>> {
    val newMap = HashMap<A, HashSet<B>>()
    forEach { (k,v) ->
        newMap.computeIfAbsent(k, { HashSet() }).add(v)
    }
    return newMap
}

fun<A, B> Iterable<Pair<A, Iterable<B>>>.groupOfSetsFlattened(): Map<A, Set<B>> {
    val newMap = HashMap<A, HashSet<B>>()
    forEach { (k,v) ->
        newMap.computeIfAbsent(k, { HashSet() }).addAll(v)
    }
    return newMap
}



fun<A, B, C> Iterable<A>.mapOfLists(f: (A) -> Pair<B, C>): Map<B, List<C>> {
    val newMap = HashMap<B, ArrayList<C>>()
    forEach { element ->
        val (k,v) = f(element)
        newMap.computeIfAbsent(k, { ArrayList() }).add(v)
    }
    return newMap
}

fun <A, B> Iterable<Pair<A, B>>.toHashMap(): HashMap<A, B> {
    val hashMap = HashMap<A, B>()
    forEach { (k,v) -> hashMap[k] = v }
    return hashMap
}




fun<A, B, C> Iterable<A>.mapOfSets(f: (A) -> Pair<B, C>): Map<B, Set<C>> {
    val newMap = HashMap<B, HashSet<C>>()
    forEach { element ->
        val (k,v) = f(element)
        newMap.computeIfAbsent(k, { HashSet() }).add(v)
    }
    return newMap
}

fun<A, B> Iterable<A>.forEachWith(other: B, f: (A, B) -> Unit) {
    forEach { item -> f(item, other) }
}

fun<A> List<A>.toArrayList() = ArrayList(this)


//fun<A, B, C> Iterable<A>.mapOfMaps(f: (A) -> Pair<B, C>): Map<A, Map<B, C>> {
//    val newMap = HashMap<A, HashMap<B, C>>()
//    forEach { element ->
//        val (k,v) = f(element)
//        newMap.computeIfAbsent(element, { HashMap() })[k] = v
//    }
//    return newMap
//}


class HashCounter<A> {
    val counter = ConcurrentHashMap<A, Int>()
    fun add(item: A) {
        counter.merge(item, 1, Int::plus)
    }

    fun addAll(items: List<A>) {
        items.forEach { item ->
            counter.merge(item, 1, Int::plus)
        }
    }
}


val sharedRand = JDKRandomGenerator(12941)

