package learning.containers


open class EnhancedMap<K, V>(val defaultFun: () -> V,
                             val incrementFun: ((K, EnhancedMap<K,V>) -> Unit)? = null,
                             val appendFun: ((K, EnhancedMap<K,V>) -> Unit)? = null
                             ) : HashMap<K, V>() {

    override fun get(key: K): V {
        if (!containsKey(key))
            put(key, defaultFun())
        return super.get(key)!!
    }

    fun increment(key: K) {
        incrementFun?.let { f -> f(key, this) }
    }

    fun append(key: K) {
        appendFun?.let { f -> f(key, this) }
    }


    companion object {
        fun<K> createNumberMap(): EnhancedMap<K, Double> {
            val defFun = { 0.0 }
            val inc = { key: K, eMap: EnhancedMap<K, Double> ->
                eMap.put(key, eMap.get(key) + 1.0)
                Unit
            }

            return EnhancedMap<K, Double>(defaultFun = defFun, incrementFun = inc)
        }
    }
}

//class EnhancedNumMap<K, V: Number>(defaultFun: () -> V) : EnhancedMap<K, V>(defaultFun) {
//
//    fun increment(key: K) {
//        get(key).
//    }
//}
