@file: JvmName("LaunchSparqlDownloader")
package experiment

import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import learning.deep.stochastic.runStochastic
import learning.deep.stochastic.runStochasticConditional
import learning.deep.stochastic.runStochasticPoint
import learning.deep.stochastic.runStochasticSpline
import lucene.OfficialQrelCreator
import lucene.QuickAndDirtyHierToTree
import lucene.containers.*
import lucene.indexers.IndexFields
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.inf.Subparser
import net.sourceforge.argparse4j.inf.Subparsers
import utils.AnalyzerFunctions
import utils.lucene.*
import java.io.File

class AnnotationApp(resources: HashMap<String, Any>) {
    val cbor: String by resources
    val entityMap: String by resources

    fun run() {
        OfficialQrelCreator(cbor, entityMap)
            .run()
    }



    companion object {
        fun addExperiments(mainSubparser: Subparsers) {
            val mainParser = mainSubparser.addParser("qrel")
                .help("Indexes corpus.")
            register("run", mainParser)
        }

        @SuppressWarnings
        fun register(methodType: String, parser: Subparser)  {
            val exec = { namespace: Namespace ->
                val resources = dispatcher.loadResources(namespace)
                val methodName = namespace.get<String>("method")
                val instance = AnnotationApp(resources)
                val method = dispatcher.methodContainer!! as MethodContainer<AnnotationApp>
                method.getMethod("run", methodName)?.invoke(instance)
            }

            parser.help("Annotates cbor file.")
            parser.setDefault("func", exec)
            dispatcher.generateArguments(methodType, parser)
        }


        val dispatcher =
                buildResourceDispatcher {
                    methods<AnnotationApp> {
                        method("run", "run") { run() }
                        help = "Annotates cbor file."
                    }

                    resource("cbor") {
                        help = "Location of where the cbor is."
                    }

                    resource("entityMap") {
                        help = "Location of where the entity ID map is."
                    }


                }
    }

}