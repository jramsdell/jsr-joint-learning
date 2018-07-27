package lucene.indexers

import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.ByteString
import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.CborDataItemIterator
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import lucene.containers.LuceneDocumentContainer
import org.apache.lucene.index.IndexWriterConfig
import org.json.JSONObject
import utils.parallel.asIterable
import utils.parallel.forEachChunkedParallel
import utils.parallel.forEachParallel
import utils.parallel.forEachParallelQ
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.StreamSupport
import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.UnicodeString
import edu.unh.cs.treccar_v2.read_data.CborListWithHeaderIterator
import entity.SpotlightEntityLinker
import language.GramAnalyzer
import lucene.indexers.*
import org.apache.lucene.document.Document
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.Term
import org.apache.lucene.search.IndexSearcher
import utils.AnalyzerFunctions
import utils.AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED
import utils.lucene.*
import lucene.indexers.IndexFields.*
import org.apache.lucene.analysis.StopwordAnalyzerBase
import utils.AnalyzerFunctions.AnalyzerType.ANALYZER_STANDARD_STOPPED
import utils.misc.toArrayList
import utils.stats.countDuplicates
import utils.stats.takeMostFrequent

//val paths = listOf(
//        "/home/jsc57/data/shared/paragraph_entity_linking/all_but_benchmark_links.txt",
//        "/home/jsc57/data/shared/paragraph_entity_linking/benchmark_links.txt",
//        "/home/jsc57/data/shared/paragraph_entity_linking/test_200_links.txt"
//)
//
//val totalDone = AtomicInteger()


//fun doIndexings(path: String, indexSearcher: IndexSearcher, indexWriter: IndexWriter) {
//    val f = File(path).bufferedReader()
//    f.forEachLine { line ->
//        val elements = line.split("\t")
//        if (!(elements.size == 1)) {
//            val query = AnalyzerFunctions.createQuery(elements[0], IndexFields.FIELD_PID.field, false)
//            val docId = indexSearcher.search(query, 1).scoreDocs.first().doc
//            val doc = indexSearcher.doc(docId)
//            IndexFields.FIELD_ENTITIES.setTextField(doc, elements[1])
//            indexWriter.updateDocument(Term(IndexFields.FIELD_PID.field), doc)
//        }
//        val done = totalDone.incrementAndGet()
//        if (done % 10000 == 0) {
//            println(done)
//            indexWriter.commit()
//        }
//
//    }
//}


class IndexerStream(corpusLocs: List<String>, val chunkSize: Int = 1000) {
    val linker = SpotlightEntityLinker("/home/jsc57/projects/jsr-joint-learning/spotlight_server")
        .apply { (0 until 100).forEach { queryServer("Test") }   }
    val pageIndexers = ArrayList<Indexer>()
    val paragraphIndexers = ArrayList<Indexer>()
    val entityContextIndexers = ArrayList<Indexer>()
    val paragraphContextIndexers = ArrayList<Indexer>()
    val pageCounter = AtomicInteger()
    val paragraphCounter = AtomicInteger()
//    val speedy = "/speedy/jsc57/"
    val speedy = "/home/jsc57/data/backup"
    val annotationOutput = File(speedy + "/annotations.tsv").bufferedWriter()
    val pageIndex = getIndexWriter("${speedy}extractions/page", mode = IndexWriterConfig.OpenMode.CREATE)
    val sectionIndex = getIndexWriter("${speedy}extractions/section", mode = IndexWriterConfig.OpenMode.CREATE)
    val paragraphIndex = getIndexWriter("${speedy}extractions/paragraph", mode = IndexWriterConfig.OpenMode.CREATE)
    val entityContextIndex = getIndexWriter("${speedy}extractions/entity_context", mode = IndexWriterConfig.OpenMode.CREATE)
    val sectionContextIndex = getIndexWriter("${speedy}extractions/section_context", mode = IndexWriterConfig.OpenMode.CREATE)

    val corpusStreams = corpusLocs.map { corpusLoc ->
        File(corpusLoc)
            .inputStream()
            .buffered()
    }

//    private inline fun<reified A> getStream(corpusLoc: String) = when (A::class) {
//        Data.Page::class -> fromPageCorpus(corpusLoc)
//        Data.Paragraph::class -> fromParagraphCorpus(corpusLoc)
//        else                  -> throw IllegalArgumentException("Unknown type")
//
//    }

//    @Suppress("UNCHECKED_CAST")
//    private inline fun<reified A> getStream(corpusLoc: String): Iterable<A> = when (A::class) {
//            Data.Page::class      -> fromPageCorpus(corpusLoc)
//            Data.Paragraph::class -> fromParagraphCorpus(corpusLoc)
//            else                  -> throw IllegalArgumentException("Unknown type")
//        } as Iterable<A>

    fun addPageIndexer() {
        val funcs =
                listOf(Indexer::extractEntityTextAndGrams, Indexer::extractMetaData, Indexer::addEntityHeaders)
        pageIndexers.add(Indexer(funcs))
    }

    fun addParagraphIndexer() {
        val funcs =
                listOf(Indexer::extractGram)
        paragraphIndexers.add(Indexer(funcs))

    }

    private fun cleanEntity(entry: String) =
            entry.replace("enwiki:", "")
                .replace("%20", "_")
                .replace(" ", "_")


    fun processSectionContext(page: Data.Page) {
        val seenSections = HashMap<String, HashMap<String, ArrayList<String>>>()
//        val categories = page.pageMetadata.categoryIds.map(::cleanEntity)
//            .joinToString(" ")
//            .run { convertToUnigrams(this) }
//        val inlinks = page.pageMetadata.inlinkIds.map(::cleanEntity)
//            .joinToString(" ")
//            .run { convertToUnigrams(this) }

        page.foldOverSection { path: String, section: Data.Section, paragraphs: List<Data.Paragraph> ->
            val heading = section.heading

            val tokens = AnalyzerFunctions.createTokenList(heading, ANALYZER_ENGLISH_STOPPED)
                .run {
                    if (size == 1) this + " " + this
                    else windowed(2, partialWindows = false).map { bigram ->
                        bigram.sorted().joinToString(" ")
                    }
                }


            tokens.forEach { token ->
                val sectionHash = seenSections.computeIfAbsent(token, { HashMap() })
                sectionHash.computeIfAbsent("inlinks", { ArrayList() }).apply { add(" ") }
                sectionHash.computeIfAbsent("categories", { ArrayList() }).apply { add(" ") }

                paragraphs
                    .asSequence()
                    .filter { p -> p.textOnly.length > 100 && !p.textOnly.contains(":") && !p.textOnly.contains("•") }
                    .forEach { paragraph ->
                    val content = paragraph.textOnly
                    val entities = paragraph.entitiesOnly.map(::cleanEntity)

                    val (unigrams, bigrams, windowed) = getGramsFromContent(content)
                    sectionHash.computeIfAbsent("unigrams", { ArrayList() }).apply { add(unigrams) }
                    sectionHash.computeIfAbsent("bigrams", { ArrayList() }).apply { add(bigrams) }
                    sectionHash.computeIfAbsent("windowed", { ArrayList() }).apply { add(windowed) }
                    sectionHash.computeIfAbsent("entities", { ArrayList() }).apply { addAll(entities) }
                    sectionHash.computeIfAbsent("neighbors", { ArrayList() }).apply { addAll(tokens) }
                }
            }
        }

            seenSections.entries.forEach { (section, sectionHash) ->
                val doc = Document()
                val unigrams = sectionHash.computeIfAbsent("unigrams", { ArrayList() })
                    .run { joinToString(" ") }
                val bigrams = sectionHash.computeIfAbsent("bigrams", { ArrayList() })
                    .run { joinToString(" ") }
                val windowed = sectionHash.computeIfAbsent("windowed", { ArrayList() })
                    .run { joinToString(" ") }
                val entities = sectionHash.computeIfAbsent("entities", { ArrayList() })
                    .run { joinToString(" ") }
                val neighbors = sectionHash.computeIfAbsent("neighbors", { ArrayList() })
                    .run { joinToString(" ") }
//                val sectionInlinks = sectionHash.computeIfAbsent("inlinks", { ArrayList() })
//                    .map { inlinks }
//                    .run { joinToString(" ") }
//                val sectionCategories = sectionHash.computeIfAbsent("categories", { ArrayList() })
//                    .map { categories }
//                    .run { joinToString(" ") }

                IndexFields.FIELD_NAME.setTextField(doc, section)
                IndexFields.FIELD_PID.setTextField(doc, section.replace(" ", "_"))
                IndexFields.FIELD_ENTITIES.setTextField(doc, entities)
                IndexFields.FIELD_ENTITIES_UNIGRAMS.setTextField(doc, convertToUnigrams(entities))
                IndexFields.FIELD_UNIGRAM.setTextField(doc, unigrams)
                IndexFields.FIELD_BIGRAM.setTextField(doc, bigrams)
                IndexFields.FIELD_WINDOWED_BIGRAM.setTextField(doc, windowed)
                IndexFields.FIELD_NEIGHBOR_SECTIONS.setTextField(doc, neighbors)
//                IndexFields.FIELD_INLINKS_UNIGRAMS.setTextField(doc, inlinks)
//                IndexFields.FIELD_CATEGORIES_UNIGRAMS.setTextField(doc, categories)
                sectionContextIndex.addDocument(doc)
            }
    }

    fun processEntityContext(page: Data.Page) {
        val seenEntities = HashMap<String, HashMap<String, ArrayList<String>>>()
//        val categories = page.pageMetadata.categoryIds.map(::cleanEntity)
//            .joinToString(" ")
//            .run { convertToUnigrams(this) }
//        val inlinks = page.pageMetadata.inlinkIds.map(::cleanEntity)
//            .joinToString(" ")
//            .run { convertToUnigrams(this) }

        page.foldOverSection { path: String, section: Data.Section, paragraphs: List<Data.Paragraph> ->
            paragraphs
                .asSequence()
                .filter { p -> p.textOnly.length > 100 && !p.textOnly.contains(":") && !p.textOnly.contains("•") }
                .forEach { paragraph ->
                val entities = paragraph.entitiesOnly.map(::cleanEntity)
                val content = paragraph.textOnly
                val (unigrams, bigrams, windowed) = getGramsFromContent(content)
                entities.forEach { entity ->
                    val entityHash = seenEntities.computeIfAbsent(entity, { HashMap() })
                    entityHash.computeIfAbsent("unigrams", { ArrayList() }).apply { add(unigrams) }
                    entityHash.computeIfAbsent("bigrams", { ArrayList() }).apply { add(bigrams) }
                    entityHash.computeIfAbsent("windowed", { ArrayList() }).apply { add(windowed) }
                    entityHash.computeIfAbsent("sections", { ArrayList() }).apply { addAll(AnalyzerFunctions.createTokenList(section.heading, ANALYZER_ENGLISH_STOPPED)) }
                    entityHash.computeIfAbsent("neighbors", { ArrayList() }).apply { addAll(entities) }
                    entityHash.computeIfAbsent("inlinks", { ArrayList() }).apply { add(" ") }
                    entityHash.computeIfAbsent("categories", { ArrayList() }).apply { add(" ") }
                }
            }
        }

        seenEntities.entries.forEach { (entity, entityHash) ->
            val entityUnigrams = entityHash.computeIfAbsent("unigrams", { ArrayList() })
                .run { joinToString(" ") }
            val entityBigrams = entityHash.computeIfAbsent("bigrams", { ArrayList() })
                .run { joinToString(" ") }
            val entityWindowed = entityHash.computeIfAbsent("windowed", { ArrayList() })
                .run { joinToString(" ") }
            val entityNeighbors = entityHash.computeIfAbsent("neighbors", { ArrayList() })
                .run { joinToString(" ") }
//            val entityInlinks = entityHash.computeIfAbsent("inlinks", { ArrayList() })
//                .map { inlinks }
//                .run { joinToString(" ") }
//            val entityCategories = entityHash.computeIfAbsent("categories", { ArrayList() })
//                .map { categories }
//                .run { joinToString(" ") }

            val doc = Document()
            IndexFields.FIELD_NAME.setTextField(doc, entity)
            IndexFields.FIELD_ENTITIES.setTextField(doc, entityNeighbors)
            IndexFields.FIELD_ENTITIES_UNIGRAMS.setTextField(doc, convertToUnigrams(entityNeighbors))
            IndexFields.FIELD_UNIGRAM.setTextField(doc, entityUnigrams)
            IndexFields.FIELD_BIGRAM.setTextField(doc, entityBigrams)
            IndexFields.FIELD_WINDOWED_BIGRAM.setTextField(doc, entityWindowed)
//            IndexFields.FIELD_CATEGORIES_UNIGRAMS.setTextField(doc, categories)
//            IndexFields.FIELD_INLINKS_UNIGRAMS.setTextField(doc, inlinks)

            entityContextIndex.addDocument(doc)
        }

    }

    fun processPageIndexers(page: Data.Page) {
        val doc = LuceneDocumentContainer(page = page)
        pageIndexers.forEach { pageIndexer -> pageIndexer.doExtract(listOf(doc)) }
        pageIndex.addDocument(doc.doc)
    }

    private data class ParagraphData(val pid: String, val content: String, val unigrams: String,
                                     val bigrams: String, val windowed: String, val entities: String)

    private fun buildParagraphData(par: Data.Paragraph): ParagraphData {
        val (unigrams, bigrams, windowed) = getGramsFromContent(par.textOnly)
        return ParagraphData(
                pid = par.paraId,
                content = par.textOnly,
                entities = par.entitiesOnly.map(::cleanEntity).joinToString(" "),
                unigrams = unigrams,
                bigrams = bigrams,
                windowed = windowed
        )
    }

    private fun convertToUnigrams(text: String) =
            text.split(" ")
                .asSequence()
                .map { it.replace("_", " ") }
                .map {  AnalyzerFunctions.createTokenList(it, ANALYZER_ENGLISH_STOPPED) }
                .map { it.joinToString("_") }
                .joinToString(" ")
//            AnalyzerFunctions.createTokenList(text.replace("_", " "), ANALYZER_ENGLISH_STOPPED)
//                .filter { it.length > 2 }
//                .joinToString(" ")

    fun processParagraphs(page: Data.Page) {
        val outlinkSet = page.outlinks()
        val inlinkSet = page.filteredInlinks().toSet()
        page.foldOverSection { path, section, paragraphs ->
            val pData = paragraphs.map(this::buildParagraphData)
            val allEntities = pData.map { p -> p.entities }.joinToString(" ")
            val allUnigrams = pData.map { p -> p.unigrams }.joinToString(" ")
            val allBigrams = pData.map { p -> p.bigrams }.joinToString(" ")
//            val allWindowed = pData.map { p -> p.windowed }.joinToString(" ")

            val entitySets = pData.map { p -> p.entities.split(" ").toSet() }
            val unigramSets = pData.map { p -> p.unigrams.split(" ").toSet() }
            val bigramSets = pData.map { p -> p.bigrams.split(" ").toSet() }
//            val windowedSets = pData.map { p -> p.windowed.split(" ").toSet() }

            pData
                .asSequence()
                .filter { p -> p.content.length > 100 && !p.content.contains(":") && !p.content.contains("•") }
                .forEach { p ->
                    val doc = Document()
                    FIELD_PID.setTextField(doc, p.pid)
                    FIELD_TEXT.setTextField(doc, p.content)
                    FIELD_ENTITIES.setTextField(doc, p.entities)
                    FIELD_ENTITIES_UNIGRAMS.setTextField(doc, convertToUnigrams(p.entities))
                    FIELD_UNIGRAM.setTextField(doc, p.unigrams)
                    FIELD_BIGRAM.setTextField(doc, p.bigrams)
                    FIELD_WINDOWED_BIGRAM.setTextField(doc, p.windowed)
                    FIELD_NEIGHBOR_ENTITIES.setTextField(doc, allEntities)
                    FIELD_NEIGHBOR_ENTITIES_UNIGRAMS.setTextField(doc, convertToUnigrams(allEntities))
                    FIELD_NEIGHBOR_UNIGRAMS.setTextField(doc, allUnigrams)
                    FIELD_NEIGHBOR_BIGRAMS.setTextField(doc, allBigrams)
//                    FIELD_NEIGHBOR_WINDOWED.setTextField(doc, allWindowed)

                    val fContent = AnalyzerFunctions.createTokenList(p.content, ANALYZER_STANDARD_STOPPED)
                        .joinToString(" ")
                    val annotations = linker.queryServer(fContent).toArrayList()
                    p.entities.split(" ")
                        .forEach { entity ->
                            if (entity !in annotations)
                                annotations.add(entity)
                        }


                    val anOutlink = annotations.filter { it in outlinkSet }
                        .joinToString(" ")

                    val anInlink = annotations.filter { it in inlinkSet }
                        .joinToString(" ")

                    FIELD_ENTITIES_EXTENDED.setTextField(doc, anOutlink)
                    FIELD_ENTITIES_INLINKS.setTextField(doc, anInlink)

                    annotationOutput.write("${p.pid}\t$anOutlink\n")




                    val jointEntities = p.entities.split(" ").toSet().let { set ->
                        entitySets.flatMap { other ->
                            if (other == set) emptyList()
                            else other.intersect(set).toList() }
                    }.joinToString(" ")

                    val jointUnigrams = p.unigrams.split(" ").toSet().let { set ->
                        unigramSets.flatMap { other ->
                            if (other == set) emptyList()
                            else other.intersect(set).toList() }
                    }.joinToString(" ")

                    val jointBigrams = p.bigrams.split(" ").toSet().let { set ->
                        bigramSets.flatMap { other ->
                            if (other == set) emptyList()
                            else other.intersect(set).toList() }
                    }.joinToString(" ")

//                    val jointWindowed = p.windowed.split(" ").toSet().let { set ->
//                        windowedSets.flatMap { other ->
//                            if (other == set) emptyList()
//                            else other.intersect(set).toList() }
//                    }.joinToString(" ")

                    FIELD_JOINT_UNIGRAMS.setTextField(doc, jointUnigrams)
                    FIELD_JOINT_BIGRAMS.setTextField(doc, jointBigrams)
//                    FIELD_JOINT_WINDOWED.setTextField(doc, jointWindowed)
                    FIELD_JOINT_ENTITIES.setTextField(doc, jointEntities)
                    FIELD_JOINT_ENTITIES_UNIGRAMS.setTextField(doc, convertToUnigrams(jointEntities))

                    paragraphIndex.addDocument(doc)
                }
        }
    }



    fun processSections(page: Data.Page) {
        page.foldOverSection { path, section, paragraphs ->
            val doc = Document()
            IndexFields.FIELD_SECTION_ID.setTextField(doc, path.replace(" ", "_"))
            val filteredHeading = AnalyzerFunctions.createTokenList(section.heading, ANALYZER_ENGLISH_STOPPED)
                .joinToString(" ")
            val filteredPath = AnalyzerFunctions.createTokenList(path, ANALYZER_ENGLISH_STOPPED)
                .joinToString(" ")
            IndexFields.FIELD_SECTION_HEADING.setTextField(doc, filteredHeading)
            IndexFields.FIELD_SECTION_PATH.setTextField(doc, filteredPath)
            val text = paragraphs.joinToString("\n") { it.textOnly + "\n" }
            val (unigrams, bigrams, windowed) = getGramsFromContent(text, 60)
            val childrenIds = paragraphs.joinToString(" "){ it.paraId }
            IndexFields.FIELD_UNIGRAM.setTextField(doc, unigrams)
            IndexFields.FIELD_BIGRAM.setTextField(doc, bigrams)
            IndexFields.FIELD_WINDOWED_BIGRAM.setTextField(doc, windowed)
            IndexFields.FIELD_CHILDREN_IDS.setTextField(doc, childrenIds)
            if (paragraphs.isNotEmpty())
                sectionIndex.addDocument(doc)
        }

    }




    fun run() {
        corpusStreams.forEach { corpusStream ->
            DeserializeData.iterableAnnotations(corpusStream)
                .forEachParallelQ(1000, 120) { page: Data.Page ->
//                    processSectionContext(page)
                    processEntityContext(page)
                    processPageIndexers(page)
                    processSections(page)
                    processParagraphs(page)
                    val result = pageCounter.incrementAndGet()
                    println(result)
                    if (result % 10000 == 0) {
                        println(result)
                        paragraphIndex.commit()
                        pageIndex.commit()
                        sectionIndex.commit()
                        sectionContextIndex.commit()
                        entityContextIndex.commit()
                    }
                }
        }
        paragraphIndex.commit()
        pageIndex.commit()
        sectionContextIndex.commit()
        entityContextIndex.commit()
        annotationOutput.close()
        sectionIndex.commit()
        paragraphIndex.close()
        pageIndex.close()
        sectionContextIndex.close()
        entityContextIndex.close()
        sectionIndex.close()
    }

}



