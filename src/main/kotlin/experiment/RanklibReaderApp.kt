package experiment

import lucene.RanklibReader
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.inf.Subparser
import net.sourceforge.argparse4j.inf.Subparsers

/**
 * Class: LaunchSparqlDownloader
 * Desc: This is just an app that retrieves stuff from SPARQL.
 * @see KotlinSparql
 */
class RanklibReaderApp(resources: HashMap<String, Any>) {
//    val index: String by resources

    fun analyze() {
        val reader = RanklibReader("ranklib_results.txt")
        reader.createVectors()
    }


    companion object {
        fun addExperiments(mainSubparser: Subparsers) {
            val mainParser = mainSubparser.addParser("learning")
                .help("Indexes corpus.")
            register("run", mainParser)
        }

        @SuppressWarnings
        fun register(methodType: String, parser: Subparser)  {
            val exec = { namespace: Namespace ->
                val resources = dispatcher.loadResources(namespace)
                val methodName = namespace.get<String>("method")
                val instance = RanklibReaderApp(resources)
                val method = dispatcher.methodContainer!! as MethodContainer<RanklibReaderApp>
                method.getMethod("run", methodName)?.invoke(instance)
            }

            parser.help("Downloads abstracts and pages for topics.")
            parser.setDefault("func", exec)
            dispatcher.generateArguments(methodType, parser)
        }


        val dispatcher =
                buildResourceDispatcher {
                    methods<RanklibReaderApp> {
                        method("run", "analyze") { analyze() }
                        help = "Creates a new Lucene index."
                    }
                }
    }

}