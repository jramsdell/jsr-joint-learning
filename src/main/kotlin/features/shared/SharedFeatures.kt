package features.shared

import entity.EntityDatabase
import experiment.FeatureType
import experiment.KotlinRanklibFormatter
import experiment.NormType
import experiment.NormType.ZSCORE
import language.GramAnalyzer
import language.GramStatType
import language.containers.LanguageStatContainer
import lucene.FieldQueryFormatter
import lucene.containers.QueryData
import utils.AnalyzerFunctions
import utils.lucene.explainScore
import utils.lucene.splitAndCount
import utils.misc.CONTENT
import utils.stats.countDuplicates
import utils.stats.normalize
import kotlin.math.absoluteValue
import lucene.containers.FeatureEnum
import lucene.containers.FeatureEnum.*
import lucene.indexers.IndexFields
import org.apache.lucene.document.Document
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.similarities.LMDirichletSimilarity
import utils.misc.PID
import utils.stats.takeMostFrequent
import kotlin.math.max


data class SharedFeature(val paragraphScores: ArrayList<Double>, val entityScores: ArrayList<Double>)
private fun<A, B> scoreBoth(sf: SharedFeature, entityList: List<A>, paragraphList: List<B>,
                            scoreFunction: (A, B) -> Double) {
    entityList.mapIndexed { entityIndex, a ->
        paragraphList.mapIndexed { paragraphIndex, b ->
            val score = scoreFunction(a, b)
            sf.entityScores[entityIndex] += score
            sf.paragraphScores[paragraphIndex] += score
//            sf.entityScores[entityIndex] = max(score, sf.entityScores[entityIndex] ?: 0.0)
//            sf.paragraphScores[paragraphIndex] = max(score, sf.paragraphScores[paragraphIndex] ?: 0.0)
        }
    }
}

private fun<A, B> scoreBothSeparate(sf: SharedFeature, entityList: List<A>, paragraphList: List<B>,
                            scoreFunction: (A, B) -> Pair<Double, Double>) {
    entityList.mapIndexed { entityIndex, a ->
        paragraphList.mapIndexed { paragraphIndex, b ->
            val (scorePara, scoreEntity) = scoreFunction(a, b)
            sf.entityScores[entityIndex] += scoreEntity
            sf.paragraphScores[paragraphIndex] += scorePara
        }
    }
}

object SharedFeatures {

    // Similarity based on how much paragraph's rdf overlaps with entity's
    private fun sharedRdf(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val entityFeatures = entityContainers.map { entity ->
            val doc = entityDb.getDocumentById(entity.docId)
            doc.getValues("rdf")?.toList() ?: emptyList()
        }
        val documentFeatures =
                paragraphDocuments.map { doc ->
                    //                tops.scoreDocs.map { scoreDoc ->
//                    val doc = paragraphSearcher.doc(scoreDoc.doc)
//                    doc.getValues("spotlight")
                    doc.get(IndexFields.FIELD_ENTITIES.field).split(" ")
                        .mapNotNull { docEntity -> entityDb.getEntityDocument(docEntity) }
                        .flatMap { docEntity -> docEntity.getValues("rdf")?.toList() ?: emptyList() }
                        .countDuplicates()
                }

        scoreBoth(sf, entityFeatures, documentFeatures,
                { rdfs, rdfCount ->
                    rdfs.map { rdf ->
                        rdfCount.getOrDefault(rdf, 0).toDouble() }
                        .sum()
                })
    }


    // Similarity based on proportion of entity links from paragraph to entity
    private fun sharedFeatLinks(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val documentFeatures =
                paragraphDocuments.map { doc ->
                    doc.get(IndexFields.FIELD_NEIGHBOR_ENTITIES.field).split(" ")
//                    doc.getValues("spotlight")
                        .toList()
                        .countDuplicates()
                        .normalize()
                }
        val entityFeatures = entityContainers.map { entityContainer -> entityContainer.name }
        scoreBoth(sf, entityFeatures, documentFeatures,
                { entityName, docLinks ->
                    docLinks.getOrDefault(entityName, 0.0)
                })
    }

    // Similarity based on proportion of entity links from paragraph to entity
    private fun sharedMeta(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val documentFeatures =
                paragraphContainers.map { container ->
                    val unigrams = container.doc.get(IndexFields.FIELD_NEIGHBOR_UNIGRAMS.field)
                        .run { AnalyzerFunctions.createTokenList(this) }

                    val q = FieldQueryFormatter()
                        .addWeightedQueryTokens(unigrams, IndexFields.FIELD_NAME)
                        .addWeightedQueryTokens(unigrams, IndexFields.FIELD_CATEGORIES_UNIGRAMS)
                        .addWeightedQueryTokens(unigrams, IndexFields.FIELD_REDIRECTS_UNIGRAMS)
                        .addWeightedQueryTokens(unigrams, IndexFields.FIELD_INLINKS_UNIGRAMS)
                        .createBooleanQuery()


                    val result = entityDb.searcher.search(q, 1000)
                        .scoreDocs
                        .map { sc -> sc.doc to sc.score.toDouble() }
                        .toMap()
                    result
                }
        val entityFeatures = entityContainers.map { entityContainer -> entityContainer.docId }
        scoreBoth(sf, entityFeatures, documentFeatures,
                { entityId, scores ->
                    scores[entityId] ?: 0.0
                })
    }

    // Similarity based on proportion of entity links from paragraph to entity
    private fun sharedFeatLinksSymmetric(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val documentFeatures =
                paragraphDocuments.map { doc ->
                    doc.get(IndexFields.FIELD_ENTITIES.field).split(" ")
//                    doc.getValues("spotlight")
                        .toList()
                        .countDuplicates()
                        .normalize()
                }

        val entityFeatures = entityContainers.map { entityContainer -> entityContainer.name }
        scoreBoth(sf, entityFeatures, documentFeatures,
                { entityName, docLinks ->
                    docLinks.getOrDefault(entityName, 0.0)
                })
    }


    // Similarity based on unigram (from paragraph text to entity abstract)
    private fun sharedUnigramLikelihood(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val documentFeatures =
                paragraphDocuments.map { doc ->
                    doc.splitAndCount(GramStatType.TYPE_UNIGRAM.indexField)
                        .normalize() }

        val entityFeatures = entityContainers.map { entityContainer ->
            val entity = entityDb.getDocumentById(entityContainer.docId)
            entity.splitAndCount(GramStatType.TYPE_UNIGRAM.indexField)
                .normalize() }

        scoreBoth(sf, entityFeatures, documentFeatures,
                { entityUnigrams, docUnigrams ->
                    val combinedKeys = (docUnigrams.keys + entityUnigrams.keys).toSet()
                    combinedKeys
                        .map { key ->
                            val docFreq = docUnigrams.getOrDefault(key, 0.0)
                            val entityFreq = entityUnigrams.getOrDefault(key, 0.0)
                            (docFreq - entityFreq).absoluteValue }
                        .sum()
                })
    }

    private fun getDocumentGramQuery(paragraphDoc: Document, gramStatType: GramStatType): BooleanQuery {
        val terms = paragraphDoc.get(gramStatType.indexField).split(" ")
            .countDuplicates()
            .takeMostFrequent(15)
            .keys.toList()
            .joinToString(" ")
        return AnalyzerFunctions.createQuery(terms, gramStatType.indexField)
    }


    private fun sharedBoostedGram(qd: QueryData, sf: SharedFeature, gramStatType: GramStatType,
                                  weight: Double = 1.0): Unit = with(qd) {
         val documentFeatures =
                 paragraphDocuments.map{ doc ->
                     val docQuery = getDocumentGramQuery(doc, gramStatType)
                     val searchResult = entityDb.searcher.search(docQuery, 1000)
                         .scoreDocs
                     searchResult
                         .map { sc -> sc.doc to sc.score.toDouble()  }
                         .toMap()
                 }


        val entityFeatures = entityContainers.map { it.docId }

        scoreBoth(sf, entityFeatures, documentFeatures,
                { entityId, docQuery ->
//                    entityDb.searcher.explainScore(docQuery, entityId)
                    docQuery[entityId] ?: 0.0
                })
    }

    private fun sharedBoostedGramSymmetric(qd: QueryData, sf: SharedFeature, gramStatType: GramStatType,
                                  weight: Double = 1.0): Unit = with(qd) {
        val documentToEntity =
                paragraphContainers.map{ container ->
                    val doc = container.doc
                    val docQuery = getDocumentGramQuery(doc, gramStatType)
                    val result = entityDb.searcher.search(docQuery, 1000)
                        .scoreDocs
                        .map { sc -> sc.doc to sc.score.toDouble() }
                        .toMap()
                    container.docId to result
                }

        val entityToDocument =
                entityContainers.map{ container ->
                    val doc = container.doc
                    val docQuery = getDocumentGramQuery(doc, gramStatType)
                    val result = paragraphSearcher.search(docQuery, 1000)
                        .scoreDocs
                        .map { sc -> sc.doc to sc.score.toDouble() }
                        .toMap()
                    container.docId to result
                }.toMap()


        val entityFeatures = entityContainers.map { it.docId }

        scoreBoth(sf, entityFeatures, documentToEntity,
                { entityId, (docId, docQuery) ->
                    //                    entityDb.searcher.explainScore(docQuery, entityId)
                    val docScore = docQuery[entityId] ?: 0.0
                    val entityScore = entityToDocument[entityId]?.get(docId) ?: 0.0
                    docScore + entityScore
                })
    }

    private fun sharedBoostedGramSeparate(qd: QueryData, sf: SharedFeature, gramStatType: GramStatType,
                                           weight: Double = 1.0): Unit = with(qd) {
        val documentToEntity =
                paragraphContainers.map{ container ->
                    val doc = container.doc
                    val docQuery = getDocumentGramQuery(doc, gramStatType)
                    val result = entityDb.searcher.search(docQuery, 1000)
                        .scoreDocs
                        .map { sc -> sc.doc to sc.score.toDouble() }
                        .toMap()
                    container.docId to result
                }

        val entityToDocument =
                entityContainers.map{ container ->
                    val doc = container.doc
                    val docQuery = getDocumentGramQuery(doc, gramStatType)
                    val result = paragraphSearcher.search(docQuery, 1000)
                        .scoreDocs
                        .map { sc -> sc.doc to sc.score.toDouble() }
                        .toMap()
                    container.docId to result
                }.toMap()


        val entityFeatures = entityContainers.map { it.docId }

        scoreBothSeparate(sf, entityFeatures, documentToEntity,
                { entityId, (docId, docQuery) ->
                    //                    entityDb.searcher.explainScore(docQuery, entityId)
                    val docScore = docQuery[entityId] ?: 0.0
                    val entityScore = entityToDocument[entityId]?.get(docId) ?: 0.0
                    docScore to entityScore
                })
    }



    // Similarity based on BM25 (from paragraph text to entity abstract)
    private fun sharedBM25(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val documentFeatures =
                paragraphDocuments.map { doc ->
                    val text = doc.get(CONTENT)
                    val query = AnalyzerFunctions.createQuery(text, field = IndexFields.FIELD_TEXT.field)
                    entityDb.searcher.search(query, 1000)
                        .scoreDocs
                        .map { sc -> sc.doc to sc.score.toDouble() }
                        .toMap()
                }
        val entityFeatures = entityContainers.map { entityContainer -> entityContainer.docId }
        scoreBoth(sf, entityFeatures, documentFeatures,
                { entityId, docQuery ->
                    docQuery[entityId] ?: 0.0
                })
    }

    private fun sharedDirichlet(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        entityDb.searcher.setSimilarity(LMDirichletSimilarity(1.0f))
        val documentFeatures =
                paragraphDocuments.map { doc ->
                    val text = doc.get(CONTENT)
                    val query = AnalyzerFunctions.createQuery(text, field = IndexFields.FIELD_TEXT.field)
                    entityDb.searcher.search(query, 1000)
                        .scoreDocs
                        .map { sc -> sc.doc to sc.score.toDouble() }
                        .toMap()
                }
        val entityFeatures = entityContainers.map { entityContainer -> entityContainer.docId }
        scoreBoth(sf, entityFeatures, documentFeatures,
                { entityId, docQuery ->
                    docQuery[entityId] ?: 0.0
                })
    }

    private fun sharedDirichletSymmetric(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        entityDb.searcher.setSimilarity(LMDirichletSimilarity(1.0f))
        paragraphSearcher.setSimilarity(LMDirichletSimilarity(1.0f))
        val docToEntityFeature =
                paragraphContainers.map { container ->
                    val doc = container.doc
                    val text = doc.get(CONTENT)
                    val query = AnalyzerFunctions.createQuery(text, field = IndexFields.FIELD_TEXT.field)
                    val result = entityDb.searcher.search(query, 1000)
                        .scoreDocs
                        .map { sc -> sc.doc to sc.score.toDouble() }
                        .toMap()
                    container.docId to result
                }

        val entityToDocFeature =
                entityContainers.map { container ->
                    val doc = container.doc
                    val text = doc.get(IndexFields.FIELD_TEXT.field)
                    val query = AnalyzerFunctions.createQuery(text, field = IndexFields.FIELD_TEXT.field)
                    val result = paragraphSearcher.search(query, 1000)
                        .scoreDocs
                        .map { sc -> sc.doc to sc.score.toDouble() }
                        .toMap()
                    container.docId to result
                }.toMap()
        val entityFeatures = entityContainers.map { entityContainer -> entityContainer.docId }

        scoreBoth(sf, entityFeatures, docToEntityFeature,
                { entityId, (docId, docQuery) ->
                    val docToEntityScore = docQuery[entityId] ?: 0.0
                    val entityToDocScore = entityToDocFeature[entityId]?.get(docId) ?: 0.0
                    docToEntityScore + entityToDocScore
                })
    }

    private fun sharedDirichletSeparate(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        entityDb.searcher.setSimilarity(LMDirichletSimilarity(1.0f))
        paragraphSearcher.setSimilarity(LMDirichletSimilarity(1.0f))
        val docToEntityFeature =
                paragraphContainers.map { container ->
                    val doc = container.doc
                    val text = doc.get(CONTENT)
                    val query = AnalyzerFunctions.createQuery(text, field = IndexFields.FIELD_TEXT.field)
                    val result = entityDb.searcher.search(query, 1000)
                        .scoreDocs
                        .map { sc -> sc.doc to sc.score.toDouble() }
                        .toMap()
                    container.docId to result
                }

        val entityToDocFeature =
                entityContainers.map { container ->
                    val doc = container.doc
                    val text = doc.get(IndexFields.FIELD_TEXT.field)
                    val query = AnalyzerFunctions.createQuery(text, field = IndexFields.FIELD_TEXT.field)
                    val result = paragraphSearcher.search(query, 1000)
                        .scoreDocs
                        .map { sc -> sc.doc to sc.score.toDouble() }
                        .toMap()
                    container.docId to result
                }.toMap()
        val entityFeatures = entityContainers.map { entityContainer -> entityContainer.docId }

        scoreBothSeparate(sf, entityFeatures, docToEntityFeature,
                { entityId, (docId, docQuery) ->
                    val docToEntityScore = docQuery[entityId] ?: 0.0
                    val entityToDocScore = entityToDocFeature[entityId]?.get(docId) ?: 0.0
                    docToEntityScore to entityToDocScore
                })
    }


//    private fun querySDMAbstract(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
//        // Parse query and retrieve a language model for it
//        val paragraphGramAnalyzer = GramAnalyzer(paragraphSearcher)
//        val documentFeatures = paragraphContainers.map {  container ->
//            val doc = container.doc
//
//            val tokens = AnalyzerFunctions.createTokenList(queryString, useFiltering = true,
//                    analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)
//            val cleanQuery = tokens.toList().joinToString(" ")
//
//            val queryCorpus = paragraphGramAnalyzer.getCorpusStatContainer(cleanQuery)
//            val weights = listOf(0.9285990421606605, 0.070308081629, -0.0010928762)
//
//            entityDocuments.forEachIndexed { index, doc ->
//                val docStat = LanguageStatContainer.createLanguageStatContainer(doc)
//                val (uniLike, biLike, windLike) = gramAnalyzer.getQueryLikelihood(docStat, queryCorpus, 4.0)
//                val score = uniLike * weights[0] + biLike * weights[1] + windLike * weights[2]
//                sf.entityScores[index] = score
//            }
//
//        }
//    }



    fun addSharedBM25Abstract(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (SHARED_BM25, wt, norm, this::sharedBM25)


    fun addSharedEntityLinks(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(SHARED_LINKS, wt, norm, this::sharedFeatLinks)

    fun addSharedRdf(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(SHARED_RDF, wt, norm, this::sharedRdf)

    fun addSharedMeta(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(SHARED_META, wt, norm, this::sharedMeta)

    fun addSharedUnigramLikelihood(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(SHARED_UNI_LIKE, wt, norm, this::sharedUnigramLikelihood)



    fun addSharedDirichlet(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (SHARED_DIRICHLET, wt, norm, this::sharedDirichlet)

    fun addSharedBoostedUnigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(SHARED_BOOSTED_UNIGRAM, wt, norm) { qd, sf -> sharedBoostedGram(qd, sf, GramStatType.TYPE_UNIGRAM) }

    fun addSharedBoostedBigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(SHARED_BOOSTED_BIGRAM, wt, norm) { qd, sf -> sharedBoostedGram(qd, sf, GramStatType.TYPE_BIGRAM) }

    fun addSharedBoostedWindowed(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(SHARED_BOOSTED_WINDOWED, wt, norm) { qd, sf -> sharedBoostedGram(qd, sf, GramStatType.TYPE_BIGRAM_WINDOW) }



}
