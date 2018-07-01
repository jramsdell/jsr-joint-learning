package lucene.indexers

import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.MultiFields
import org.apache.lucene.search.IndexSearcher
import utils.AnalyzerFunctions
import utils.lucene.getFieldIterator
import utils.lucene.getIndexWriter
import utils.parallel.asIterable
import utils.parallel.forEachParallelQ
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.experimental.buildIterator


class EntityContextMerger(val contextSearcher: IndexSearcher) {
    val outLoc = getIndexWriter("/speedy/jsc57/complete_entity_context")
    val counts = ConcurrentHashMap<String, Int>()

    fun run() {
        val counter = AtomicInteger()

        getFieldIterator(IndexFields.FIELD_NAME.field, contextSearcher.indexReader)
            .forEachParallelQ { term ->
                counts.merge(term, 1, Int::plus)
            }

        println("Total keys: ${counts.size}")
        val repeats = counts.filter { it.value > 1 }
        println("Repeats: ${repeats.size}")

        outLoc.commit()
        outLoc.close()

    }
}

