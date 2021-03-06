import experiment.*
import lucene.indexers.QuickAndDirtyAnnotator
import net.sourceforge.argparse4j.ArgumentParsers
import net.sourceforge.argparse4j.helper.HelpScreenException
import net.sourceforge.argparse4j.inf.ArgumentParser
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.inf.Subparsers

fun buildParser(): ArgumentParser {
    val mainParser: ArgumentParser = ArgumentParsers.newFor("program").build()
    val subparsers: Subparsers = mainParser.addSubparsers()

    LuceneIndexerApp.addExperiments(subparsers)
    QueryApp.addExperiments(subparsers)
    DebugApp.addExperiments(subparsers)
    RanklibReaderApp.addExperiments(subparsers)
    ExtractorApp.addExperiments(subparsers)
    GroundTruthApp.addExperiments(subparsers)
    AnnotationApp.addExperiments(subparsers)
    SubmissionQueryApp.addExperiments(subparsers)

    return mainParser
}

fun run(args: Array<String>) {
//    runStochastic()
    val parser: ArgumentParser = buildParser()
    val params: Namespace = parser.parseArgs(args)
        params.get<(Namespace) -> Unit>("func")
            .invoke(params)
}

fun main(args: Array<String>) {
//    QuickAndDirtyAnnotator(args[0])
//        .apply { run() }
    try { run(args) }
    catch (e: HelpScreenException) {  } // Print help without annoying exception
//    val indexLoc = args[0]
//    val corpusLoc = args[1]
//    val serverLoc = args[2]
//    val indexer =
//            LuceneIndexer(indexLoc = indexLoc, corpusLoc = corpusLoc, serverLocation = serverLoc)
//    indexer.index()
    System.exit(0)
}

