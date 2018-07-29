@file: JvmName("LaunchSparqlDownloader")
package experiment

import lucene.indexers.IndexFields
import lucene.indexers.IndexerStream
import lucene.indexers.QuickAndDirtyParagraphIndexer
import lucene.parsers.ExtractorStream
import lucene.parsers.TrecParagraphAnnotator
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.inf.Subparser
import net.sourceforge.argparse4j.inf.Subparsers
import org.apache.lucene.index.IndexWriterConfig
import utils.lucene.getIndexSearcher
import utils.lucene.getIndexWriter
import utils.parallel.forEachParallelQ

class ExtractorApp(resources: HashMap<String, Any>) {
    val corpusFiles: List<String> by resources

    fun extract() {
        val doExtract = true
        if (doExtract) {

//            val extractor = IndexerStream(corpusFiles)
//            extractor.addPageIndexer()
//            extractor.addParagraphIndexer()
//            extractor.run()

            val extractor = QuickAndDirtyParagraphIndexer()
            extractor.run()
            return
        }

//        val pageIndex = getIndexSearcher("extractions2/page")
        val speedy = "/speedy/jsc57/"

       val paragraphIndex = getIndexSearcher("${speedy}extractions2/paragraph")
        val paragraphWriter = getIndexWriter("${speedy}extractions2/paragraph", IndexWriterConfig.OpenMode.APPEND)
//        val sectionContextIndex = getIndexSearcher("extractions2/section_context")
//        val page = sectionContextIndex.doc(1)
//        println(page.get(IndexFields.FIELD_UNIGRAM.field))
//        println()
//        println(page.get(IndexFields.FIELD_NAME.field))
//        println()
//        println(page.get(IndexFields.FIELD_ENTITIES.field))
        val doc = paragraphIndex.doc(4)
        println(doc.get(IndexFields.FIELD_NAME.field))
        println(doc.get(IndexFields.FIELD_TEXT.field))
        println(doc.get(IndexFields.FIELD_ENTITIES.field))

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