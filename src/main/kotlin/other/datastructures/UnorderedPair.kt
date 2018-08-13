package other.datastructures



class UnorderedPairContainer<A>(val a: A, val b: A) {
    val set = setOf(a, b)
    var normalScore = 0.0
    val revereScore = 0.0

    override fun hashCode(): Int {
        return set.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return set == other
    }

    fun first() = a
    fun second() = b


}