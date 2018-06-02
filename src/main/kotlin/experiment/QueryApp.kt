package experiment

import entity.EntityDatabase
import features.document.DocumentRankingFeatures
import features.document.featSDM
import features.document.featSplitSim
import features.entity.*
//import features.entity.featEntityCategory
import language.GramAnalyzer
import lucene.TagMeSDMIndexer
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.inf.Subparser
import net.sourceforge.argparse4j.inf.Subparsers
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import utils.lucene.getIndexSearcher
import utils.misc.filledArray

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
    val entityQrel: String by resources

    val out: String by resources

    val formatter = KotlinRanklibFormatter(queryPath, indexPath, qrelPath, entityIndex, entityQrel)
    val indexer = getIndexSearcher(indexPath)


    /**
     * Func: querySDMSection
     * Desc: Like the section-paths variant of BM25, the SDM method is run on each of a query's sections,
     *       and the score of a document is expressed as a weighted sum of likelihoods given each section.
     */
    private fun querySDMSection() {
        val hGram = GramAnalyzer(indexer)
//        val weights = listOf(0.18040763371108623, 0.053702972763138165, 0.3145376765137826, 0.45135171701199295)
        val weights = listOf(0.08047025663846726, 0.030239885393043505, 0.15642380129849698, 0.45881012321282,
                0.1370279667285861, 0.1370279667285861
        )

        val bindSDM = { query: String, tops: TopDocs, indexSearcher: IndexSearcher ->
            featSDM(query, tops, indexSearcher, hGram, 4.0)
        }

        formatter.addFeature({ query, tops, indexSearcher ->
            featSplitSim(query, tops, indexSearcher, bindSDM, weights)
        }, normType = NormType.ZSCORE)
    }

    fun querySDM(weights: List<Double>? = null) {
        val hGram = GramAnalyzer(indexer)
        formatter.addBM25(normType = NormType.ZSCORE, weight = weights?.get(0) ?: 1.0)
        formatter.addFeature({ query, tops, indexSearcher -> featSDM(query, tops, indexSearcher, hGram, 4.0) },
                normType = NormType.ZSCORE, weight = weights?.get(1) ?: 1.0)
//        formatter.addFeature2(::featQueryEntityToDocEntity,
//                normType = NormType.ZSCORE, weight = weights?.get(1) ?: 2.0)
    }


    fun doBM25() {
        formatter.addBM25(normType = NormType.ZSCORE)
    }

    fun queryTFIDF(weights: List<Double>? = null) {
        formatter.addBM25(normType = NormType.ZSCORE, weight = weights?.get(0) ?: 1.0)
//        formatter.addFeature2({ queryData -> featAddLuceneSimilarity(queryData, TFIDF)},
//                normType = NormType.ZSCORE, weight = weights?.get(1) ?: 1.0)
//        formatter.addFeature2(::featEntityStringSim,
//                normType = NormType.ZSCORE, weight = weights?.get(1) ?: 1.0)
//        formatter.addFeature2(::featQueryEntityToDocEntity,
//                normType = NormType.ZSCORE, weight = weights?.get(1) ?: 1.0)
    }


    fun queryEntity(weights: List<Double>? = null) {
//        val Weights = listOf(0.2130222927349571, 0.6525891171377952, 0.11243909027833231, -0.021949499848915473)
//        val Weights = listOf(0.24561161088108927 , 0.7452264415257137 , 0.00702869268534017 , 0.002133254907856875 )
        val Weights = null
//        val Weights = listOf(0.5561580572805962 , 0.3878254359818222 , 0.02703809172871249 , 0.0289784150088693 )
//        val Weights = listOf(0.27582754190104186 , 0.3058954371483745 , 0.1357071506153126 , 0.2825698703352709 )
        val norm = NormType.SUM

        formatter.addBM25(normType = norm, weight = Weights?.get(0) ?: 1.0)
        val makeWeight = { index: Int ->  filledArray(6, 0.0).apply { set(index, 1.0) } }
//        (0 until 6).forEach { index ->
//            val w = makeWeight(index)
//            { query, tops, indexSearcher -> featSplitSim(query, tops, indexSearcher) }
//        }

//        DocumentRankingFeatures.addBM25Document(formatter, weights?.get(0) ?: 1.0)

//        DocumentRankingFeatures.addBM25BoostedUnigram(formatter, Weights?.get(1) ?: 1.0, norm = norm)
//        DocumentRankingFeatures.addBM25BoostedBigram(formatter, Weights?.get(2) ?: 1.0, norm = norm)
//        DocumentRankingFeatures.addBM25BoostedWindowedBigram(formatter, Weights?.get(3) ?: 1.0, norm = norm)

//        EntityRankingFeatures.addBM25BoostedUnigram(formatter, Weights?.get(4) ?: 1.0)
//        EntityRankingFeatures.addBM25BoostedBigram(formatter, Weights?.get(5) ?: 1.0)
//        EntityRankingFeatures.addBM25BoostedWindowedBigram(formatter, Weights?.get(6) ?: 1.0)

//        DocumentRankingFeatures.addCombinedBoostedGram(formatter)
//        DocumentRankingFeatures.addSDMDocument(formatter)

//        EntityRankingFeatures.addSDMAbstract(formatter, weights?.get(1) ?: 1.0)
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
                        .apply { rerankQueries() }
                        .writeQueriesToFile(instance.out)
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
                        method("query", "do_bm25") {
                            doBM25()
                        }

                        method("query", "do_db") {
                            doDb()
                        }

                        method("query", "index_entity_sdm") {
                            doEntitySDMDB()
                        }

                        method("query", "debug") {
                            doEntityDebug()
                        }

                        method("query", "sectionSDM") { querySDMSection() }
                        method("query", "sdm") { querySDM(weights = listOf(0.11506359501201444, 0.6050693368783704, 0.2798670681096152
                            )) }
                        method("train", "tfidf") { queryTFIDF() }
                        method("query", "tfidf") { queryTFIDF(weights = listOf(0.683615, 0.3163842)) }
                        method("train", "sdm") { querySDM() }

//                        method("query", "entity") { queryEntity(weights = listOf(0.8366295022089238, -0.16337049779107615)) }
//                        method("query", "entity") { queryEntity() }
                        method("query", "entity") { queryEntity(weights = listOf(0.8570786290155611, 0.07934068299135334, 0.06358068799308561
                            )) }
                        method("train", "entity") { queryEntity() }

                        method("train", "do_bm25") {
                            doBM25()
                        }

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

                    resource("entityQrel") {
                        help = "Location to entity qrel file."
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