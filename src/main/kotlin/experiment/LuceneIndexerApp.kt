@file: JvmName("LaunchSparqlDownloader")
package experiment

import lucene.LuceneIndexer
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.inf.Subparser
import net.sourceforge.argparse4j.inf.Subparsers
import utils.identity

/**
 * Class: LaunchSparqlDownloader
 * Desc: This is just an app that retrieves stuff from SPARQL.
 * @see KotlinSparql
 */
class LuceneIndexerApp(resources: HashMap<String, Any>) {
    val indexLoc: String by resources
    val corpus: String by resources
    val serverLoc: String by resources

    fun index() {
        LuceneIndexer(indexLoc, corpus, serverLoc)
    }


    companion object {
        fun addExperiments(mainSubparser: Subparsers) {
            val mainParser = mainSubparser.addParser("lucene_indexer")
                .help("Indexes corpus.")
            register("run", mainParser)


        }

        @SuppressWarnings
        fun register(methodType: String, parser: Subparser)  {
            val exec = { namespace: Namespace ->
                val resources = dispatcher.loadResources(namespace)
                val methodName = namespace.get<String>("method")
                val instance = LuceneIndexerApp(resources)
                val method = dispatcher.methodContainer!! as MethodContainer<LuceneIndexerApp>
                method.getMethod("", methodName)?.invoke(instance)
            }

            parser.help("Downloads abstracts and pages for topics.")
            parser.setDefault("func", exec)
            dispatcher.generateArguments(methodType, parser)
        }


        val dispatcher =
                buildResourceDispatcher {
                    methods<LuceneIndexerApp> {
                        method("run", "index") { index() }
                        help = "Creates a new Lucene index using a paragraph corpus."
                    }

                    resource("indexLoc") {
                        help = "Location of where to  create Lucene index directory."
                        default = "index"
                    }

                    resource("corpus") {
                        help = "Paragraph corpus (.cbor) file."
                    }

                    resource("serverLoc") {
                        help = "Location for spotlight directory. Downloads spotlight server to location if it doesn't exist."
                        default = "spotlight"
                    }
                }
    }

}