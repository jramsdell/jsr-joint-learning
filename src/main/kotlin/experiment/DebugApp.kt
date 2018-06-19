@file: JvmName("LaunchSparqlDownloader")
package experiment

import lucene.RanklibRunner
import lucene.parsers.TrecParagraphAnnotator
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.inf.Subparser
import net.sourceforge.argparse4j.inf.Subparsers
import utils.lucene.getIndexSearcher

class DebugApp(resources: HashMap<String, Any>) {
//    val index: String by resources

    fun debug() {
//        val searcher = getIndexSearcher(index)
//        println(searcher.indexReader.maxDoc())

//        val a = TrecParagraphAnnotator("/home/jsc57/projects/jsr-joint-learning/spotlight_server")
//        a.annotate(a.cborLocations.first())

        RanklibRunner("/home/jsc57/programs/RankLib-2.1-patched.jar", "/home/jsc57/projects/jsr-joint-learning/ranklib_results.txt")
            .optimizer()
//            .runRankLib("wee", useKcv = true)
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