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

/**
 * Class: MasterExperiment
 * Desc: An app used to create ranklib files and run files for all of my methods.
 *       See the train functions below for a description of my methods.
 */
class QueryApp(val resources: HashMap<String, Any>) {
    val indexPath: String by resources
    val qrelPath: String by resources
    val queryPath: String by resources
    val entityIndex: String by resources
    val sectionIndex: String by resources
    val contextEntityIndex: String by resources
    val entityQrel: String by resources
    val sectionQrel: String by resources

    val out: String by resources

    val formatter = KotlinRanklibFormatter(queryPath, indexPath, qrelPath, entityIndex, entityQrel, sectionIndexLoc = sectionIndex,
            sectionQrelLoc = sectionQrel, contextEntityLoc = contextEntityIndex )
        .apply { initialize() }
    val indexer = getIndexSearcher(indexPath)


    /**
     * Func: querySDMSection
     * Desc: Like the section-paths variant of BM25, the SDM method is run on each of a query's sections,
     *       and the score of a document is expressed as a weighted sum of likelihoods given each section.
     */
//    private fun querySDMSection() {
//        val hGram = GramAnalyzer(indexer)
////        val weights = listOf(0.18040763371108623, 0.053702972763138165, 0.3145376765137826, 0.45135171701199295)
//        val weights = listOf(0.08047025663846726, 0.030239885393043505, 0.15642380129849698, 0.45881012321282,
//                0.1370279667285861, 0.1370279667285861
//        )
//
//        val bindSDM = { query: String, tops: TopDocs, indexSearcher: IndexSearcher ->
//            featSDM(query, tops, indexSearcher, hGram, 4.0)
//        }
//
//        formatter.addFeature({ query, tops, indexSearcher ->
//            featSplitSim(query, tops, indexSearcher, bindSDM, weights)
//        }, normType = NormType.ZSCORE)
//    }

//    fun querySDM(weights: List<Double>? = null) {
//        val hGram = GramAnalyzer(indexer)
//        formatter.addBM25(normType = NormType.ZSCORE, weight = weights?.get(0) ?: 1.0)
//        formatter.addFeature({ query, tops, indexSearcher -> featSDM(query, tops, indexSearcher, hGram, 4.0) },
//                normType = NormType.ZSCORE, weight = weights?.get(1) ?: 1.0)
////        formatter.addFeature2(::featQueryEntityToDocEntity,
////                normType = NormType.ZSCORE, weight = weights?.get(1) ?: 2.0)
//    }


//    fun doBM25() {
//        formatter.addBM25(normType = NormType.ZSCORE)
//    }

//    fun queryTFIDF(weights: List<Double>? = null) {
//        formatter.addBM25(normType = NormType.ZSCORE, weight = weights?.get(0) ?: 1.0)
////        formatter.addFeature2({ queryData -> featAddLuceneSimilarity(queryData, TFIDF)},
////                normType = NormType.ZSCORE, weight = weights?.get(1) ?: 1.0)
////        formatter.addFeature2(::featEntityStringSim,
////                normType = NormType.ZSCORE, weight = weights?.get(1) ?: 1.0)
////        formatter.addFeature2(::featQueryEntityToDocEntity,
////                normType = NormType.ZSCORE, weight = weights?.get(1) ?: 1.0)
//    }

    fun queryBM25() {
        DocumentRankingFeatures.addBM25Document(formatter, 1.0, norm = NormType.NONE)
    }

    fun queryBoostPage(weights: List<Double>? = null) {
        val norm = NormType.NONE
        DocumentRankingFeatures.addBM25BoostedUnigram(formatter, wt = weights?.get(0) ?: 1.0, norm = norm)
        DocumentRankingFeatures.addBM25BoostedBigram(formatter, wt = weights?.get(1) ?: 1.0, norm = norm)
        DocumentRankingFeatures.addBM25BoostedWindowedBigram(formatter, wt = weights?.get(2) ?: 1.0, norm = norm)
    }


    fun queryEntity(weights: List<Double>? = null) {
        val norm = NormType.ZSCORE
        var i = 0



        // Super awesome features

//        DocumentRankingFeatures.addCombinedBoostedGram(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        DocumentRankingFeatures.addSDMDocument(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)

//        DocumentRankingFeatures.addQueryDist(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)

//        DocumentRankingFeatures.addSectionFreq(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)

//        DocumentRankingFeatures.addUnionUnigramField(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        DocumentRankingFeatures.addUnionBigramField(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
        DocumentRankingFeatures.addJointUnigramField(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        DocumentRankingFeatures.addJointBigramField(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
//
//        EntityRankingFeatures.addTop25Freq(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)

        EntityRankingFeatures.addBM25BoostedUnigram(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        EntityRankingFeatures.addCategoriesField(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        EntityRankingFeatures.addDisambigField(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        EntityRankingFeatures.addInlinksField(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        EntityRankingFeatures.addOutlinksField(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        EntityRankingFeatures.addRedirectField(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        EntityRankingFeatures.addSections(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
//
        SectionRankingFeatures.addUnigrams(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        SectionRankingFeatures.addBigrams(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        SectionRankingFeatures.addWindowed(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        SectionRankingFeatures.addHeading(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        SectionRankingFeatures.addPath(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)

//        SectionRankingFeatures.addConstantScore(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)

//        SectionRankingFeatures.addQueryDist(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)


    }

    fun queryFunctor(weights: List<Double>? = null) {
        val norm = NormType.ZSCORE
        var i = 0
//        SubObjectFeatures.addLinkFreq(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        SubObjectFeatures.addPUnigramToECategory(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        SubObjectFeatures.addPUnigramToEInlinks(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)

//        SubObjectFeatures.addPUnigramToEUnigram(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)

//        SubObjectFeatures.addPUnigramToERedirects(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        SubObjectFeatures.addPUnigramToEDisambig(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)

//        SubObjectFeatures.addPUnigramToESection(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)

//        SubObjectFeatures.addPUnigramToEOutlinks(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
////
//        SubObjectFeatures.addPJointUnigramToEUnigram(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        SubObjectFeatures.addPBigramToEBigram(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        SubObjectFeatures.addPJointBigramToEBigram(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        SubObjectFeatures.addPEntityToInlinks(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        SubObjectFeatures.addPEntityToOutlinks(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)

//        SubObjectFeatures.addPWindowedToEWindowed(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
//        SubObjectFeatures.addPJointWindowedToEWindowed(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)

        SubObjectFeatures.addPUnigramToContextUnigram(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
        SubObjectFeatures.addPUnigramToContextBigram(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
        SubObjectFeatures.addPUnigramToContextWindowed(formatter, wt = weights?.get(i++) ?: 1.0, norm = norm)
    }

    fun queryScorer(weights: List<Double>? = null) {
        val norm = NormType.NONE
//        DocumentRankingFeatures.addDistScore(formatter, norm = norm)
    }


    fun doDb() {
        val ed = EntityDatabase("index")
        ed.createEntityDatabase(indexer)
    }

    fun doEntitySDMDB() {
        val ed = TagMeSDMIndexer("index")
        ed.doTagMeIndexing()
    }

    fun doEntityDebug() {
//        val ed = TagMeSDMIndexer("index")
//        ed.debugDocuments()
//        val ed = EntityDatabase("entity_index_2/")
//        ed.getEntity("Chocolate")
    }



    // This part is used to auto-generate required arguments / help for the arg parser.
    companion object {
        fun addExperiments(mainSubparser: Subparsers) {
            val mainParser = mainSubparser.addParser("query")
                .help("Collection of features involving the embedding of queries/paragraphs in structures" +
                        " representing topic models.")

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
                val instance = QueryApp(resources)
                val method = dispatcher.methodContainer!! as MethodContainer<QueryApp>
                method.getMethod(methodType, methodName)?.invoke(instance)


                if (methodType == "query") {
                    instance.formatter
                        .apply {
                            writeQueriesToFile(instance.out)
                            writeHtml()
                        }
                } else {
                    instance.formatter.writeToRankLibFile("ranklib_results.txt")
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
                    methods<QueryApp> {
//                        method("train", "hier_ascent") { trainAscentMethods() }

                        method("query", "do_db") { doDb() }

                        method("query", "index_entity_sdm") { doEntitySDMDB() }

                        method("query", "debug") { doEntityDebug() }
                        method("query", "bm25") { queryBM25() }
                        method("query", "boostPage") { queryBoostPage() }



                        method("query", "entity") { queryEntity(OptimalWeights.ONLY_ENTITY_WITH_NO_SHARED.weights) }
                        method("train", "entity") { queryEntity() }

                        method("train", "boostPage") { queryBoostPage() }

                        method("query", "functor") { queryFunctor(OptimalWeights.PARAGRAPH_FUNCTOR_WEIGHTS.weights) }
                        method("train", "functor") { queryFunctor() }
                        method("train", "scorer") { queryScorer() }


                    }

                    resource("indexPath") {
                        help = "Location of the Lucene index database (Default: /trec_data/team_1/myindex"
                    }

                    resource("qrelPath") {
                        help = "Location to qrel file. Not required for querying/creating runfiles. (default: '')"
                        default = ""
                    }

                    resource("entityIndex") {
                        help = "Location to entity Lucene index."
                        default = ""
                    }

                    resource("sectionIndex") {
                        help = "Location to section Lucene index."
                        default = ""
                    }

                    resource("contextEntityIndex") {
                        help = "Location to section entity context index."
                        default = ""
                    }

                    resource("entityQrel") {
                        help = "Location to entity qrel file."
                        default = ""
                    }

                    resource("sectionQrel") {
                        help = "Location to section qrel file."
                        default = ""
                    }

                    resource("queryPath") {
                        help = "Location to query (.cbor) file."
                    }

                    resource("out") {
                        help = "Name of query file to create"
                        default = "query_results.run"
                    }


                    resource("paragraphs") {
                        help = "Location to paragraphs training directory (Default: /trec_data/team_1/paragraphs)"
                    }


                }
    }

}