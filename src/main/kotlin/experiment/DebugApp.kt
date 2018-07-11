@file: JvmName("LaunchSparqlDownloader")
package experiment

import lucene.FieldQueryFormatter
import lucene.indexers.ContextMerger
import lucene.indexers.IndexFields
import lucene.indexers.getString
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.inf.Subparser
import net.sourceforge.argparse4j.inf.Subparsers
import utils.AnalyzerFunctions
import utils.lucene.docs
import utils.lucene.getIndexSearcher

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
    }

    fun runtest() {
        val searcher = getIndexSearcher("/speedy/jsc57/complete_section_context/")
        val qf = FieldQueryFormatter()
        val toks = AnalyzerFunctions.createTokenList("new hampshire university", AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)
        val q = qf
            .addWeightedQueryTokens(toks, IndexFields.FIELD_NAME)
            .addWeightedQueryTokens(toks, IndexFields.FIELD_ENTITIES_UNIGRAMS)
            .addWeightedQueryTokens(toks, IndexFields.FIELD_UNIGRAM)
            .addWeightedQueryTokens(toks, IndexFields.FIELD_NEIGHBOR_SECTIONS)
            .createBooleanQuery()
        searcher.search(q, 10).docs(searcher).forEach { (doc, docId) ->
            println(doc.get(IndexFields.FIELD_NAME.field))
            println(doc.get(IndexFields.FIELD_UNIGRAM.field))
            println(doc.get(IndexFields.FIELD_ENTITIES.field))
            println(doc.get(IndexFields.FIELD_NEIGHBOR_SECTIONS.field))
            println()
        }

    }


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