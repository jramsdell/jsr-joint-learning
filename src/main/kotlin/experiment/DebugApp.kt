@file: JvmName("LaunchSparqlDownloader")
package experiment

import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import learning.deep.stochastic.runStochastic
import learning.deep.stochastic.runStochasticConditional
import learning.deep.stochastic.runStochasticPoint
import learning.deep.stochastic.runStochasticSpline
import lucene.QuickAndDirtyHierToTree
import lucene.containers.*
import lucene.indexers.IndexFields
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.inf.Subparser
import net.sourceforge.argparse4j.inf.Subparsers
import utils.AnalyzerFunctions
import utils.lucene.*
import java.io.File

class DebugApp(resources: HashMap<String, Any>) {
//    val index: String by resources

    fun debug() {
//        val searcher = getIndexSearcher(index)
//        println(searcher.indexReader.maxDoc())

//        val a = TrecParagraphAnnotator("/home/jsc57/projects/jsr-joint-learning/spotlight_server")
//        a.annotate(a.cborLocations.first())

//        val searcher = getIndexSearcher("/speedy/jsc57/extractions2/page")
//        val searcher = getIndexSearcher("/speedy/jsc57/extractions2/paragraph")

//        (100000 until 100000 + 100).forEach { index ->
//            val doc = searcher.doc(index)
//////            println(doc.get(IndexFields.FIELD_TEXT.field))
////            println(doc.get(IndexFields.FIELD_INLINKS.field).run { AnalyzerFunctions.createTokenList(this.replace("_", " "), AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)
////                .filter { it.length > 2 }
////                }
////            )
//            val text = doc.get(IndexFields.FIELD_NEIGHBOR_BIGRAMS.field)
//            val text2 = doc.get(IndexFields.FIELD_BIGRAM.field)
//            if (text.length > 100 && !text.contains(":") && !text.contains("•")) { println(text + "\n"); println(text2 + "\n") }
//        }

//        val searcher = getIndexSearcher("/speedy/jsc57/extractions2/entity_context")
//        val searcher = getIndexSearcher("/speedy/jsc57/extractions2/section_context")

//        ContextMerger(searcher, searcher).run4()

//        runtest()
//        val searcher = getIndexSearcher("/speedy/jsc57/complete_entity_context/")
//        val searcher = getIndexSearcher("/speedy/jsc57/extractions2/entity_context/")

//        (500000 until 500000 + 100).forEach { index ->
//            val doc = searcher.doc(index)
//            println(doc.get("name"))
//            println(IndexFields.FIELD_UNIGRAM.getString((doc)))
//            println(IndexFields.FIELD_ENTITIES.getString((doc)))
//            println(IndexFields.FIELD_NEIGHBOR_SECTIONS.getString((doc)))
//            println("---------")
//            println()
//        }
//        val q = AnalyzerFunctions.createQuery("health chocolate", "name", analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)



//        RanklibRunner("/home/jsc57/programs/RankLib-2.1-patched.jar", "/home/jsc57/projects/jsr-joint-learning/ranklib_results.txt")
////        RanklibRunner("/home/jsc57/programs/RankLib-2.1-patched.jar", "/home/jsc57/projects/jsr-joint-learning/non_filtered_ranklib_results.txt")
//            .doOptimizer()
//            .runRankLib("wee", useKcv = true)
//        testNewParData()
//        testEntityContext()
//        runTestMap()
//        runStochastic()
//        runStochasticConditional()
//        runStochasticPoint()
//        runStochasticSpline()
        QuickAndDirtyHierToTree("/home/jsc57/data/benchmark/test/benchmarkY1/benchmarkY1-test/test.pages.cbor-hierarchical.entity.qrels")
            .run()

//        sectionContextTest()
    }

    fun sectionContextTest() {
        val searcher = getTypedSearcher<IndexType.CONTEXT_SECTION>("/home/jsc57/speedy/extractions2/section_context/")
        val testPhrase = AnalyzerFunctions.createQuery("Medicine medical condition", IndexFields.FIELD_UNIGRAM.field, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)
        searcher.search(testPhrase, 100).scoreDocs.forEach { sd ->
            val t = searcher.getIndexDoc(sd.doc)
            with(t) {
                println("Name: ${name()}")
                println("Unigrams: ${unigrams()}")
                println("Bigrams: ${bigrams()}")
                println("Neighbors: ${neighborSections()}")
            }

        }

    }

    fun createQueryString(page: Data.Page, sectionPath: List<Data.Section>): String =
            page.pageName + sectionPath.joinToString { section -> " " + section.heading  }

    fun testproblem() {
//        enwiki:Subprime%20mortgage%20crisis/Causes/Governmental%20policies/Policies%20to%20promote%20affordable%20housing
        DeserializeData.iterableAnnotations(File("/home/jsc57/data/benchmark/benchmarkY1/benchmarkY1-train/train.pages.cbor-outlines.cbor").inputStream())
            .withIndex()
            .forEach { indObject ->
                val index = indObject.index
                val page = indObject.value
                val queryStr = createQueryString(page, emptyList())
//                createQueryContainer(queryStr, page.pageId, index, page)
//                val r = page.flatSectionPaths().map { it.map { it.headingId }.joinToString("/") }
                page.flatSectionIntermediatePaths()
//                r.forEach { line -> println(line) }
            }

    }

    fun testEntityContext() {
        val contextLoc = "/speedy/jsc57/extractions2/entity_context"
        val contextSearcher = getTypedSearcher<IndexType.CONTEXT_ENTITY>(contextLoc)
        val entityLoc = "/speedy/jsc57/extractions2/page"
        val entitySearcher = getTypedSearcher<IndexType.ENTITY>(entityLoc)
//        (0 until 10).forEach { index ->
//            val doc = contextSearcher.getIndexDoc(index)
//            with (doc) {
//                println(name())
//                println(entities())
//                println(bigrams())
//                println()
//
//            }
//
//            println("Compared to:")
//            val eDoc = entitySearcher.getDocumentByField(doc.name())
//
//            eDoc?.apply {
//                println(bigrams())
//                println()
//            }
//
//        }
        val q = AnalyzerFunctions.createQuery("Chocolate", IndexFields.FIELD_NAME.field)
        val results = contextSearcher.search(q, 10).scoreDocs.forEach {
            val doc = contextSearcher.getIndexDoc(it.doc)
            println(doc.name())
            println(doc.unigrams())
            println(doc.bigrams())
        }
    }

    fun testNewParData() {
        val searcher = getTypedSearcher<IndexType.PARAGRAPH>("/speedy/jsc57/extractions/paragraph/")
        val doc = searcher.getIndexDoc(190)
        println(doc.spotlightEntities())
        println()
        println(doc.spotlightInlinks())
        println()
        println(doc.entities())
        println(doc.text())
    }

    fun findPage() {
        val pageLoc = "/speedy/jsc57/data/unprocessedAllButBenchmark.cbor"
        val corpusStream = File(pageLoc).inputStream().buffered()
        DeserializeData.iterableAnnotations(corpusStream)
            .asSequence()
            .drop(1)
            .take(1)
            .forEach { page ->
                println(page.outlinks())
                println(page.filteredInlinks())
                println("\n\n")

                val outlinkSet = page.outlinks()
                page.foldOverSection { s, section, paragraphs ->
                    paragraphs.forEach { p -> p.entitiesOnly.filter { it !in outlinkSet }.forEach {
                        println(it)
                        val firstTwo = it.take(2)
                        outlinkSet.filter { it.startsWith(firstTwo) }
                            .forEach { println("\t$it") }
                    }
                    }
                }
            }

    }

//    fun runtest() {
//        val searcher = getIndexSearcher("/speedy/jsc57/complete_section_context/")
//        val qf = FieldQueryFormatter()
//        val toks = AnalyzerFunctions.createTokenList("new hampshire university", AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)
//        val q = qf
//            .addWeightedQueryTokens(toks, IndexFields.FIELD_NAME)
//            .addWeightedQueryTokens(toks, IndexFields.FIELD_ENTITIES_UNIGRAMS)
//            .addWeightedQueryTokens(toks, IndexFields.FIELD_UNIGRAM)
//            .addWeightedQueryTokens(toks, IndexFields.FIELD_NEIGHBOR_SECTIONS)
//            .createBooleanQuery()
//        searcher.search(q, 10).docs(searcher).forEach { (doc, docId) ->
//            println(doc.get(IndexFields.FIELD_NAME.field))
//            println(doc.get(IndexFields.FIELD_UNIGRAM.field))
//            println(doc.get(IndexFields.FIELD_ENTITIES.field))
//            println(doc.get(IndexFields.FIELD_NEIGHBOR_SECTIONS.field))
//            println()
//        }
//
//    }


    companion object {
        fun addExperiments(mainSubparser: Subparsers) {
            val mainParser = mainSubparser.addParser("debug")
                .help("Indexes corpus.")
            register("run", mainParser)
        }

        @SuppressWarnings
        fun register(methodType: String, parser: Subparser)  {
            val exec = { namespace: Namespace ->
                val resources = dispatcher.loadResources(namespace)
                val methodName = namespace.get<String>("method")
                val instance = DebugApp(resources)
                val method = dispatcher.methodContainer!! as MethodContainer<DebugApp>
                method.getMethod("run", methodName)?.invoke(instance)
            }

            parser.help("Downloads abstracts and pages for topics.")
            parser.setDefault("func", exec)
            dispatcher.generateArguments(methodType, parser)
        }


        val dispatcher =
                buildResourceDispatcher {
                    methods<DebugApp> {
                        method("run", "debug") { debug() }
                        help = "Creates a new Lucene index."
                    }

//                    resource("index") {
//                        help = "Location of where to  create Lucene index directory."
//                        default = "index"
//                    }


                }
    }

}