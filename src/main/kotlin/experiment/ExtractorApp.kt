@file: JvmName("LaunchSparqlDownloader")
package experiment

import lucene.parsers.ExtractorStream
import lucene.parsers.TrecParagraphAnnotator
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.inf.Subparser
import net.sourceforge.argparse4j.inf.Subparsers
import utils.lucene.getIndexSearcher

class ExtractorApp(resources: HashMap<String, Any>) {
    val corpusFiles: List<String> by resources

    fun extract() {
        val extractor = ExtractorStream(corpusFiles)
        extractor.addParagraphGramExtractor()
        extractor.addAbstractExtractor()
        extractor.addMetadataExtractor()
        extractor.run()
    }


    companion object {
        fun addExperiments(mainSubparser: Subparsers) {
            val mainParser = mainSubparser.addParser("extract")
                .help("Indexes corpus.")
            register("run", mainParser)
        }

        @SuppressWarnings
        fun register(methodType: String, parser: Subparser)  {
            val exec = { namespace: Namespace ->
                val resources = dispatcher.loadResources(namespace)
                val methodName = namespace.get<String>("method")
                val instance = ExtractorApp(resources)
                val method = dispatcher.methodContainer!! as MethodContainer<ExtractorApp>
                method.getMethod("run", methodName)?.invoke(instance)
            }

            parser.help("Downloads abstracts and pages for topics.")
            parser.setDefault("func", exec)
            dispatcher.generateArguments(methodType, parser)
        }


        val dispatcher =
                buildResourceDispatcher {
                    methods<ExtractorApp> {
                        method("run", "extract") { extract() }
                        help = "Creates a new Lucene index."
                    }

                    resource("corpusFiles", true) {
                        help = ""
                        loader = { it.split(" ") }
                    }

                }
    }

}