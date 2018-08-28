package experiment

import entity.EntityDatabase
import features.document.DocumentRankingFeatures
import features.entity.EntityRankingFeatures
import features.shared.SharedFeatures
//import features.entity.featEntityCategory
import lucene.indexers.TagMeSDMIndexer
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.inf.Subparser
import net.sourceforge.argparse4j.inf.Subparsers
import utils.lucene.getIndexSearcher
import experiment.OptimalWeights.*
import features.section.SectionRankingFeatures
import features.subobject.SubObjectFeatures

@Suppress("UNCHECKED_CAST", "UNUSED_CHANGED_VALUE")
/**
 * Class: MasterExperiment
 * Desc: An app used to create ranklib files and run files for all of my methods.
 *       See the train functions below for a description of my methods.
 */
class SubmissionQueryApp(val resources: HashMap<String, Any>) {
    val indexPath: String by resources
    val queryPath: String by resources
    val entityIndex: String by resources
    val contextEntityIndex: String by resources
    val omitArticleLevel: String by resources


    val formatter = KotlinRanklibFormatter(
            paragraphQrelLoc = "",
            paragraphIndexLoc = indexPath,
            entityIndexLoc = entityIndex,
            paragraphQueryLoc = queryPath,
            contextEntityLoc = contextEntityIndex,
            omitArticleLevel = omitArticleLevel == "true" )
        .apply { initialize() }

    fun queryBM25() {
        DocumentRankingFeatures.addBM25Document(formatter, 1.0, norm = NormType.NONE)
    }

    fun queryBoostPage(weights: List<Double>? = null) {
        val norm = NormType.NONE
        DocumentRankingFeatures.addBM25BoostedUnigram(formatter, wt = weights?.get(0) ?: 1.0, norm = norm)
        DocumentRankingFeatures.addBM25BoostedBigram(formatter, wt = weights?.get(1) ?: 1.0, norm = norm)
        DocumentRankingFeatures.addBM25BoostedWindowedBigram(formatter, wt = weights?.get(2) ?: 1.0, norm = norm)
    }
    fun queryGhettoSDM(weights: List<Double>? = null) {
        val norm = NormType.ZSCORE
        var i = 0
        DocumentRankingFeatures.addCombinedBoostedGram(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
    }

    fun queryKitchenSink(weights: List<Double>? = null) {
        val norm = NormType.ZSCORE
        var i = 0

        DocumentRankingFeatures.addCombinedBoostedGram(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
        DocumentRankingFeatures.addNormalBM25Combo(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
        DocumentRankingFeatures.addSDMDocument(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
        DocumentRankingFeatures.addBigramExpanded(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
        EntityRankingFeatures.addTop25Freq(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
        EntityRankingFeatures.addBM25BoostedUnigram(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
        EntityRankingFeatures.addBM25BoostedBigram(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
        EntityRankingFeatures.addBM25BoostedWindowedBigram(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
        EntityRankingFeatures.addCategoriesField(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
        EntityRankingFeatures.addDisambigField(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
        EntityRankingFeatures.addInlinksField(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
        EntityRankingFeatures.addContextUnigram(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
        EntityRankingFeatures.addContextBigram(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
        EntityRankingFeatures.addContextWindowed(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
        EntityRankingFeatures.addOutlinksField(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
        EntityRankingFeatures.addRedirectField(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
        EntityRankingFeatures.addSections(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
    }



    // This part is used to auto-generate required arguments / help for the arg parser.
    companion object {
        fun addExperiments(mainSubparser: Subparsers) {
            val mainParser = mainSubparser.addParser("submission_query")
                .help("Generates run files for TREC CAR submission. " +
                        "Output: paragraph_results.run and entity_results.run")

            val subparsers = mainParser.addSubparsers()
            val trainParser = subparsers.addParser("train")
                .help("Training methods for RankLib")
            register("train", trainParser)

            val queryParser = subparsers.addParser("run")
                .help("Query methods for RankLib")
            register("query", queryParser)

        }

        fun register(methodType: String, parser: Subparser)  {
            val exec = { namespace: Namespace ->
                val resources = dispatcher.loadResources(namespace)
                val methodName = namespace.get<String>("method")
                val instance = SubmissionQueryApp(resources)
                val method = dispatcher.methodContainer!! as MethodContainer<SubmissionQueryApp>
                method.getMethod(methodType, methodName)?.invoke(instance)


                if (methodType == "query") {
                    instance.formatter.finishQuery()
                    instance.formatter
                        .apply {
                            ranklibWriter.writeQueriesToFile("out.txt")
//                            ranklibWriter.writeHtml()
                        }
                } else {
                    instance.formatter.finish()
                    instance.formatter.ranklibWriter.writeToRankLibFile("ranklib_results.txt")
                }
            }

//            parser
//                .help("Collection of features involving the embedding of queries/paragraphs in structures" +
//                        " representing topic models.")
            parser.setDefault("func", exec)
            dispatcher.generateArguments(methodType, parser)
        }


        val dispatcher =

                // This is a DSL I created to specify resources / register methods to be run by the arg parser.
                buildResourceDispatcher {
                    methods<SubmissionQueryApp> {
                        method("query", "ghetto_sdm") { queryGhettoSDM(OptimalWeights.GHETTO_SDM.weights) }
                        method("query", "kitchen_sink") { queryKitchenSink(OptimalWeights.KITCHEN_SINK.weights) }
                        method("query", "kitchen_sink_joint") { queryKitchenSink(OptimalWeights.JOINT_KITCHEN_SINK.weights) }
                        method("train", "kitchen_sink_joint") { }
                    }

                    resource("indexPath") {
                        help = "Location of indexed paragraphCorpus."
                    }

                    resource("entityIndex") {
                        help = "Location of entity index."
                        default = ""
                    }

                    resource("contextEntityIndex") {
                        help = "Location of context entity index."
                        default = ""
                    }


                    resource("queryPath") {
                        help = "Location to query (.cbor) outline file."
                    }

                    resource("omitArticleLevel") {
                        help = ""
                        default = "false"
                    }





                }
    }

}