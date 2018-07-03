package lucene.indexers

import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.TextField
import org.apache.lucene.search.IndexSearcher
import utils.AnalyzerFunctions
import utils.lucene.docs
import utils.lucene.getFieldIterator
import utils.lucene.getIndexWriter
import utils.misc.HashCounter
import utils.parallel.forEachParallelQ
import utils.stats.countDuplicates
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger


class ContextMerger(val contextEntitySearcher: IndexSearcher, val contextSectionSearcher: IndexSearcher) {
    val contextEntityWriter  = getIndexWriter("/speedy/jsc57/complete_entity_context")
    val contextSectionWriter  = getIndexWriter("/speedy/jsc57/complete_section_context")
    val counts = ConcurrentHashMap<String, Int>()

    fun run2() {
        val counter = AtomicInteger()

        getFieldIterator(IndexFields.FIELD_NAME.field, contextEntitySearcher.indexReader)
            .forEachParallelQ { term ->
                counts.merge(term, 1, Int::plus)
            }

//        println("Total keys: ${counts.size}")
//        val repeats = counts.filter { it.value > 1 }
//        println("Repeats: ${repeats.size}")

        contextEntityWriter.commit()
        contextEntityWriter.close()

    }

    fun run4() {
        val counter = AtomicInteger()

        getFieldIterator(IndexFields.FIELD_NAME.field, contextEntitySearcher.indexReader)
            .forEachParallelQ { term ->
                val stored = ConcurrentHashMap<String, HashCounter<String>>()
                val query = AnalyzerFunctions.createQuery(term, IndexFields.FIELD_NAME.field, must = true)
                val results = contextEntitySearcher.search(query, 10000)
                var name = ""
                results.docs(contextEntitySearcher).forEach { (doc, docId) ->
                    val entities = IndexFields.FIELD_ENTITIES.getString(doc) ?: ""
                    name = IndexFields.FIELD_NAME.getString(doc) ?: ""
//                    stored.computeIfAbsent(IndexFields.FIELD_ENTITIES.field, { ArrayList() }).add(entities)
                    stored.computeIfAbsent(IndexFields.FIELD_ENTITIES.field, { HashCounter() }).addAll(entities.split(" "))

                    val entitiesUnigrams = IndexFields.FIELD_ENTITIES_UNIGRAMS.getString(doc) ?: ""
//                    stored.computeIfAbsent(IndexFields.FIELD_ENTITIES_UNIGRAMS.field, { ArrayList() }).add(entitiesUnigrams)
                    stored.computeIfAbsent(IndexFields.FIELD_ENTITIES_UNIGRAMS.field, { HashCounter() }).addAll(entitiesUnigrams.split(" "))

                    val unigrams = IndexFields.FIELD_UNIGRAM.getString(doc) ?: ""
//                    stored.computeIfAbsent(IndexFields.FIELD_UNIGRAM.field, { ArrayList() }).add(unigrams)
                    stored.computeIfAbsent(IndexFields.FIELD_UNIGRAM.field, { HashCounter() }).addAll(unigrams.split(" "))

                    val bigrams = IndexFields.FIELD_BIGRAM.getString(doc) ?: ""
//                    stored.computeIfAbsent(IndexFields.FIELD_BIGRAM.field, { ArrayList() }).add(bigrams)
                    stored.computeIfAbsent(IndexFields.FIELD_BIGRAM.field, { HashCounter() }).addAll(bigrams.split(" "))

                    val windowed = IndexFields.FIELD_WINDOWED_BIGRAM.getString(doc) ?: ""
//                    stored.computeIfAbsent(IndexFields.FIELD_WINDOWED_BIGRAM.field, { ArrayList() }).add(windowed)
                    stored.computeIfAbsent(IndexFields.FIELD_WINDOWED_BIGRAM.field, { HashCounter() }).addAll(windowed.split(" "))

//                    val inUni = IndexFields.FIELD_INLINKS_UNIGRAMS.getString(doc) ?: ""
//                    stored.computeIfAbsent(IndexFields.FIELD_INLINKS_UNIGRAMS.field, { ArrayList() }).add(inUni)

//                    val catUni = IndexFields.FIELD_CATEGORIES_UNIGRAMS.getString(doc) ?: ""
//                    stored.computeIfAbsent(IndexFields.FIELD_CATEGORIES_UNIGRAMS.field, { ArrayList() }).add(catUni)
                }

                val doc = Document()
                IndexFields.FIELD_NAME.setTextField(doc, name)

                stored.entries.forEach { (field, values) ->
                    values.counter
                        .entries
                        .sortedByDescending { it.value }
                        .take(30)
                        .map { it.key }
                        .joinToString(" ")
                        .run { doc.add(TextField(field, this, Field.Store.YES)) }

//                    values.flatMap { it.split(" ") }
//                        .countDuplicates()
//                        .entries
//                        .sortedByDescending { it.value }
//                        .take(15)
//                        .map { it.key }
//                        .joinToString(" ")
//                        .run { doc.add(TextField(field, this, Field.Store.YES)) }
                }

                contextEntityWriter.addDocument(doc)
                counter.getAndIncrement().let { curCount ->
                    if (curCount % 10000 == 0) {
                        println(curCount)
                        contextEntityWriter.commit()
                    }
                }
            }


        contextEntityWriter.commit()
        contextEntityWriter.close()

    }

    fun run() {
        val counter = AtomicInteger()

        getFieldIterator(IndexFields.FIELD_PID.field, contextSectionSearcher.indexReader)
            .forEachParallelQ { term ->
                val stored = ConcurrentHashMap<String, HashCounter<String>>()
                val query = AnalyzerFunctions.createQuery(term, IndexFields.FIELD_PID.field, must = true)
                val results = contextSectionSearcher.search(query, 4000)
                var name = ""
                results.docs(contextEntitySearcher).forEach { (doc, docId) ->
                    val entities = IndexFields.FIELD_ENTITIES.getString(doc) ?: ""
                    name = IndexFields.FIELD_NAME.getString(doc) ?: ""
//                    stored.computeIfAbsent(IndexFields.FIELD_ENTITIES.field, { ArrayList() }).add(entities)
                    stored.computeIfAbsent(IndexFields.FIELD_ENTITIES.field, { HashCounter() }).addAll(entities.split(" "))

                    val entitiesUnigrams = IndexFields.FIELD_ENTITIES_UNIGRAMS.getString(doc) ?: ""
//                    stored.computeIfAbsent(IndexFields.FIELD_ENTITIES_UNIGRAMS.field, { ArrayList() }).add(entitiesUnigrams)
                    stored.computeIfAbsent(IndexFields.FIELD_ENTITIES_UNIGRAMS.field, { HashCounter() }).addAll(entitiesUnigrams.split(" "))

                    val unigrams = IndexFields.FIELD_UNIGRAM.getString(doc) ?: ""
//                    stored.computeIfAbsent(IndexFields.FIELD_UNIGRAM.field, { ArrayList() }).add(unigrams)
                    stored.computeIfAbsent(IndexFields.FIELD_UNIGRAM.field, { HashCounter() }).addAll(unigrams.split(" "))

                    val bigrams = IndexFields.FIELD_BIGRAM.getString(doc) ?: ""
//                    stored.computeIfAbsent(IndexFields.FIELD_BIGRAM.field, { ArrayList() }).add(bigrams)
                    stored.computeIfAbsent(IndexFields.FIELD_BIGRAM.field, { HashCounter() }).addAll(bigrams.split(" "))

                    val windowed = IndexFields.FIELD_WINDOWED_BIGRAM.getString(doc) ?: ""
//                    stored.computeIfAbsent(IndexFields.FIELD_WINDOWED_BIGRAM.field, { ArrayList() }).add(windowed)
                    stored.computeIfAbsent(IndexFields.FIELD_WINDOWED_BIGRAM.field, { HashCounter() }).addAll(windowed.split(" "))

                    val neighbors = IndexFields.FIELD_NEIGHBOR_SECTIONS.getString(doc) ?: ""
//                    stored.computeIfAbsent(IndexFields.FIELD_WINDOWED_BIGRAM.field, { ArrayList() }).add(windowed)
                    stored.computeIfAbsent(IndexFields.FIELD_NEIGHBOR_SECTIONS.field, { HashCounter() }).addAll(neighbors.split(" "))

//                    val inUni = IndexFields.FIELD_INLINKS_UNIGRAMS.getString(doc) ?: ""
//                    stored.computeIfAbsent(IndexFields.FIELD_INLINKS_UNIGRAMS.field, { ArrayList() }).add(inUni)

//                    val catUni = IndexFields.FIELD_CATEGORIES_UNIGRAMS.getString(doc) ?: ""
//                    stored.computeIfAbsent(IndexFields.FIELD_CATEGORIES_UNIGRAMS.field, { ArrayList() }).add(catUni)
                }

                val doc = Document()
                IndexFields.FIELD_NAME.setTextField(doc, name)

                stored.entries.forEach { (field, values) ->
                    values.counter
                        .entries
                        .sortedByDescending { it.value }
                        .take(50)
                        .map { it.key }
                        .joinToString(" ")
                        .run { doc.add(TextField(field, this, Field.Store.YES)) }

//                    values.flatMap { it.split(" ") }
//                        .countDuplicates()
//                        .entries
//                        .sortedByDescending { it.value }
//                        .take(15)
//                        .map { it.key }
//                        .joinToString(" ")
//                        .run { doc.add(TextField(field, this, Field.Store.YES)) }
                }

                contextSectionWriter.addDocument(doc)
                counter.getAndIncrement().let { curCount ->
                    if (curCount % 10000 == 0) {
                        println(curCount)
                        contextSectionWriter.commit()
                    }
                }
            }


        contextSectionWriter.commit()
        contextSectionWriter.close()

    }

    fun run3() {
        val counter = AtomicInteger()

        getFieldIterator(IndexFields.FIELD_PID.field, contextSectionSearcher.indexReader)
            .forEachParallelQ { term ->
                val stored = HashMap<String, ArrayList<String>>()
                val query = AnalyzerFunctions.createQuery(term, IndexFields.FIELD_PID.field)
                val results = contextSectionSearcher.search(query, 1000000)
                results.docs(contextSectionSearcher).forEach { (doc, docId) ->
                    val entities = IndexFields.FIELD_ENTITIES.getString(doc) ?: ""
                    stored.computeIfAbsent(IndexFields.FIELD_ENTITIES.field, { ArrayList() }).add(entities)

                    val entitiesUnigrams = IndexFields.FIELD_ENTITIES_UNIGRAMS.getString(doc) ?: ""
                    stored.computeIfAbsent(IndexFields.FIELD_ENTITIES_UNIGRAMS.field, { ArrayList() }).add(entitiesUnigrams)

                    val unigrams = IndexFields.FIELD_UNIGRAM.getString(doc) ?: ""
                    stored.computeIfAbsent(IndexFields.FIELD_UNIGRAM.field, { ArrayList() }).add(unigrams)

                    val bigrams = IndexFields.FIELD_BIGRAM.getString(doc) ?: ""
                    stored.computeIfAbsent(IndexFields.FIELD_BIGRAM.field, { ArrayList() }).add(bigrams)

                    val windowed = IndexFields.FIELD_WINDOWED_BIGRAM.getString(doc) ?: ""
                    stored.computeIfAbsent(IndexFields.FIELD_WINDOWED_BIGRAM.field, { ArrayList() }).add(windowed)

                    val inUni = IndexFields.FIELD_INLINKS_UNIGRAMS.getString(doc) ?: ""
                    stored.computeIfAbsent(IndexFields.FIELD_INLINKS_UNIGRAMS.field, { ArrayList() }).add(inUni)

                    val catUni = IndexFields.FIELD_CATEGORIES_UNIGRAMS.getString(doc) ?: ""
                    stored.computeIfAbsent(IndexFields.FIELD_CATEGORIES_UNIGRAMS.field, { ArrayList() }).add(catUni)
                }

                val doc = Document()
                IndexFields.FIELD_NAME.setTextField(doc, term)

                stored.entries.forEach { (field, values) ->
                    values.flatMap { it.split(" ") }
                        .countDuplicates()
                        .entries
                        .sortedByDescending { it.value }
                        .take(15)
                        .map { it.key }
                        .joinToString(" ")
                        .run { doc.add(TextField(field, this, Field.Store.YES)) }
                }

                contextEntityWriter.addDocument(doc)
                counter.getAndIncrement().let { curCount ->
                    if (curCount % 10000 == 0) {
                        println(curCount)
                        contextEntityWriter.commit()
                    }
                }
            }


        contextEntityWriter.commit()
        contextEntityWriter.close()

    }
}

