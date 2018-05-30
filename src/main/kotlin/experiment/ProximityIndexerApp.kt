@file: JvmName("LaunchSparqlDownloader")
package experiment

import lucene.LuceneIndexer
import lucene.TagMeSDMIndexer
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.inf.Subparser
import net.sourceforge.argparse4j.inf.Subparsers
import utils.identity

/**
 * Class: LaunchSparqlDownloader
 * Desc: This is just an app that retrieves stuff from SPARQL.
 * @see KotlinSparql
 */
class ProximityIndexerApp(resources: HashMap<String, Any>) {
    val index: String by resources

    fun index() {
        TagMeSDMIndexer(index).doTagMeIndexing()
    }


    companion object {
        fun addExperiments(mainSubparser: Subparsers) {
            val mainParser = mainSubparser.addParser("proximity_indexer")
                .help("Indexes corpus.")
            register("run2", mainParser)
        }

        @SuppressWarnings
        fun register(methodType: String, parser: Subparser)  {
            val exec = { namespace: Namespace ->
                val resources = dispatcher.loadResources(namespace)
                val methodName = namespace.get<String>("method")
                val instance = ProximityIndexerApp(resources)
                val method = dispatcher.methodContainer!! as MethodContainer<ProximityIndexerApp>
                method.getMethod("run2", methodName)?.invoke(instance)
            }

            parser.help("Downloads abstracts and pages for topics.")
            parser.setDefault("func", exec)
            dispatcher.generateArguments(methodType, parser)
        }


        val dispatcher =
                buildResourceDispatcher {
                    methods<ProximityIndexerApp> {
                        method("run2", "index2") { index() }
                        help = "Creates a new Lucene index."
                    }

                    resource("index") {
                        help = "Location of where to  create Lucene index directory."
                        default = "index"
                    }


                }
    }

}