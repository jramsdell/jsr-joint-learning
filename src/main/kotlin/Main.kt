import experiment.DebugApp
import experiment.LuceneIndexerApp
import experiment.ProximityIndexerApp
import experiment.QueryApp
import lucene.LuceneIndexer
import net.sourceforge.argparse4j.ArgumentParsers
import net.sourceforge.argparse4j.helper.HelpScreenException
import net.sourceforge.argparse4j.inf.ArgumentParser
import net.sourceforge.argparse4j.inf.ArgumentParserException
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.inf.Subparsers
import net.sourceforge.argparse4j.internal.UnrecognizedCommandException

fun buildParser(): ArgumentParser {
    val mainParser: ArgumentParser = ArgumentParsers.newFor("program").build()
    val subparsers: Subparsers = mainParser.addSubparsers()

    ProximityIndexerApp.addExperiments(subparsers)
    LuceneIndexerApp.addExperiments(subparsers)
    QueryApp.addExperiments(subparsers)
    DebugApp.addExperiments(subparsers)

    return mainParser
}

fun run(args: Array<String>) {
    val parser: ArgumentParser = buildParser()
    val params: Namespace = parser.parseArgs(args)
        params.get<(Namespace) -> Unit>("func")
            .invoke(params)
}

fun main(args: Array<String>) {
    try { run(args) }
    catch (e: HelpScreenException) {  } // Print help without annoying exception
//    val indexLoc = args[0]
//    val corpusLoc = args[1]
//    val serverLoc = args[2]
//    val indexer =
//            LuceneIndexer(indexLoc = indexLoc, corpusLoc = corpusLoc, serverLocation = serverLoc)
//    indexer.index()
}

