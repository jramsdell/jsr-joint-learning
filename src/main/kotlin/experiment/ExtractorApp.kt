@file: JvmName("LaunchSparqlDownloader")
package experiment

import lucene.indexers.IndexFields
import lucene.indexers.IndexerStream
import lucene.parsers.ExtractorStream
import lucene.parsers.TrecParagraphAnnotator
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.inf.Subparser
import net.sourceforge.argparse4j.inf.Subparsers
import utils.lucene.getIndexSearcher

class ExtractorApp(resources: HashMap<String, Any>) {
    val corpusFiles: List<String> by resources

    fun extract() {
//        val extractor = IndexerStream(corpusFiles)
//        extractor.addPageIndexer()
//        extractor.addParagraphIndexer()
//        extractor.run()

        val pageIndex = getIndexSearcher("extractions/page")
        val paragraphIndex = getIndexSearcher("extractions/paragraph")
        val page = pageIndex.doc(0)
        println(page.get(IndexFields.FIELD_TEXT.field))
        println()
        println(page.get(IndexFields.FIELD_INLINKS.field))
        println()
        println(page.get(IndexFields.FIELD_OUTLINKS.field))
        println()
        println(page.get(IndexFields.FIELD_REDIRECTS.field))
        println()
        println(page.get(IndexFields.FIELD_DISAMBIGUATIONS.field))
        println()
        println(page.get(IndexFields.FIELD_UNIGRAM.field))
        println()
        println(page.get(IndexFields.FIELD_CATEGORIES.field))

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