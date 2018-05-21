import net.sourceforge.argparse4j.ArgumentParsers
import net.sourceforge.argparse4j.inf.ArgumentParser
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.inf.Subparsers

fun buildParser(): ArgumentParser {
    val mainParser: ArgumentParser = ArgumentParsers.newFor("program").build()
    val subparsers: Subparsers = mainParser.addSubparsers()
    return mainParser
}

fun main(args: Array<String>) {
    val parser: ArgumentParser = buildParser()
    val params: Namespace = parser.parseArgs(args)
    params.get<(Namespace) -> Unit>("func")
        .invoke(params)

}

