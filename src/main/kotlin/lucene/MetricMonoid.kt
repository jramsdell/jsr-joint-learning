package lucene

import lucene.containers.QueryContainer


class MetricMonoid(val children: ArrayList<MetricMonoid> = ArrayList(),
                   var parent: MetricMonoid? = null,
                   val node: QueryContainer) {

}

class Trie<A>(val curKey: String, var data: A? = null, val children: HashMap<String, Trie<A>> = HashMap(),
              val parent: Trie<A>? = null) {
    fun add(newKey: List<String>, d: A)  {
        if (newKey.size == 1) {
            val k = newKey.first()
            if (curKey != "")
                data = d
            else children[k] = Trie(k, d, parent = this)
        } else  {
            val nextKey = newKey.first()
            val newList = newKey.drop(1)
            if (nextKey in children) {
                children[nextKey]!!.add(newList, d)
            } else {
                children[nextKey] = Trie<A>(nextKey, parent = this).apply { add(newList, d) }
            }
        }

    }

    fun get(path: List<String>): A? {
        if (path.size == 1) {
            return data
        } else {
            val newPath = path.drop(1)
            val nextKey = newPath.first()
            return children[nextKey]?.get(newPath)
        }
    }

    fun traverse(curPath: List<String> = emptyList(), f: (path: List<String>, curNodeKey: String, d: A, children: List<Trie<A>>) -> Unit) {
        val newPath = if (curKey == "") emptyList() else curPath + curKey
        data?.let { d -> f(newPath, curKey, d, children.values.toList()) }
        children.values.forEach { child -> child.traverse(newPath, f) }
    }

    fun traverseBottomUp(curPath: List<String> = emptyList(), f: (path: List<String>, curNodeKey: String, d: A,
                                                                  children: List<Trie<A>>) -> Unit) {
        val newPath = if (curKey == "") emptyList() else curPath + curKey
        children.values.forEach { child -> child.traverseBottomUp(newPath, f) }
        data?.let { d -> f(newPath, curKey, d, children.values.toList()) }
    }

    fun<B> map(curPath: List<String> = emptyList(), curMap: HashMap<List<String>, B> = HashMap(),
               f: (path: List<String>, curNodeKey: String, d: A, children: List<Trie<A>>) -> B) {
        val newPath = if (curKey == "") emptyList() else curPath + curKey
        data?.let { d -> curMap[newPath] = f(newPath, curKey, d, children.values.toList()) }
        children.values.forEach { child -> child.map(newPath, curMap, f) }
    }
}