@file: JvmName("LaunchSparqlDownloader")
package experiment

import lucene.FieldQueryFormatter
import lucene.GroundTruthGenerator
import lucene.indexers.ContextMerger
import lucene.indexers.IndexFields
import lucene.indexers.getString
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.inf.Subparser
import net.sourceforge.argparse4j.inf.Subparsers
import utils.AnalyzerFunctions
import utils.lucene.docs
import utils.lucene.getIndexSearcher

class GroundTruthApp(resources: HashMap<String, Any>) {
//    val index: String by resources

    fun debug() {

//        val cborLoc = "/home/jsc57/data/benchmark/benchmarkY1/benchmarkY1-train/train.pages.cbor-outlines.cbor"
        val cborLoc = "/home/jsc57/data/benchmark/benchmarkY1/benchmarkY1-train/train.pages.cbor"
        val clickstreamLoc = "/home/jsc57/data/clickstream/clickstream-enwiki-2017-11.tsv"
        GroundTruthGenerator(clickStreamLoc = clickstreamLoc, cborOutlineLoc = cborLoc)
            .generateQrels()
//            .run(clickstreamLoc)
    }


    companion object {
        fun addExperiments(mainSubparser: Subparsers) {
            val mainParser = mainSubparser.addParser("ground_truth")
                .help("Indexes corpus.")
            register("run", mainParser)
        }

        @SuppressWarnings
        fun register(methodType: String, parser: Subparser)  {
            val exec = { namespace: Namespace ->
                val resources = dispatcher.loadResources(namespace)
                val methodName = namespace.get<String>("method")
                val instance = GroundTruthApp(resources)
                val method = dispatcher.methodContainer!! as MethodContainer<GroundTruthApp>
                method.getMethod("run", methodName)?.invoke(instance)
            }

            parser.help("Downloads abstracts and pages for topics.")
            parser.setDefault("func", exec)
            dispatcher.generateArguments(methodType, parser)
        }


        val dispatcher =
                buildResourceDispatcher {
                    methods<GroundTruthApp> {
                        method("run", "run") { debug() }
                        help = "Creates a new Lucene index."
                    }


                }
    }

}