package lucene.containers


@Suppress("UNCHECKED_CAST")
class TypedMapCollection<C: MutableCollection<Any>>(val initializer: () -> C) {
    val map = HashMap<Class<*>, C>()

    inline fun<reified A> put(item: List<A>) {
        val c = A::class.java
        if (c !in map) map[c] = initializer()
        map[c]!!.add(item)
//        map[c] = arrayListOf(item)
    }

    inline fun<reified A> get(): MutableCollection<A>? {
        val c = A::class.java
        return map[c] as MutableCollection<A>
    }
}
