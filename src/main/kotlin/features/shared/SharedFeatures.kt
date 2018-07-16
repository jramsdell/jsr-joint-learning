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
import utils.misc.toArrayList
import utils.stats.takeMostFrequent
import kotlin.math.max


data class SharedFeature(val paragraphScores: ArrayList<Double>, val entityScores: ArrayList<Double>, val sectionScores: ArrayList<Double>) {
    fun makeCopy(): SharedFeature {
        val pScores = paragraphScores.map { it }.toArrayList()
        val eScores = entityScores.map { it }.toArrayList()
        val sScores = sectionScores.map { it }.toArrayList()
        return SharedFeature(pScores, eScores, sScores)
    }
}
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



    // Similarity based on proportion of entity links from paragraph to entity
    private fun sharedFeatLinks(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val documentFeatures =
                paragraphContainers.map { container ->
                    val doc = container.doc()
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
                    val unigrams = container.doc().get(IndexFields.FIELD_NEIGHBOR_UNIGRAMS.field)
                        .run { AnalyzerFunctions.createTokenList(this) }

                    val q = FieldQueryFormatter()
                        .addWeightedQueryTokens(unigrams, IndexFields.FIELD_NAME)
                        .addWeightedQueryTokens(unigrams, IndexFields.FIELD_CATEGORIES_UNIGRAMS)
                        .addWeightedQueryTokens(unigrams, IndexFields.FIELD_REDIRECTS_UNIGRAMS)
                        .addWeightedQueryTokens(unigrams, IndexFields.FIELD_INLINKS_UNIGRAMS)
                        .createBooleanQuery()


                    val result = entitySearcher.search(q, 1000)
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
                paragraphContainers.map { container ->
                    val doc = container.doc()
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
                 paragraphContainers.map{ container ->
                     val doc = container.doc()
                     val docQuery = getDocumentGramQuery(doc.doc, gramStatType)
                     val searchResult = entitySearcher.search(docQuery, 1000)
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



    // Similarity based on BM25 (from paragraph text to entity abstract)
    private fun sharedBM25(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val documentFeatures =
                paragraphContainers.map { container ->
                    val doc = container.doc()
                    val text = doc.get(CONTENT)
                    val query = AnalyzerFunctions.createQuery(text, field = IndexFields.FIELD_TEXT.field)
                    entitySearcher.search(query, 1000)
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
        entitySearcher.setSimilarity(LMDirichletSimilarity(1.0f))
        val documentFeatures =
                paragraphContainers.map { container ->
                    val doc = container.doc()
                    val text = doc.get(CONTENT)
                    val query = AnalyzerFunctions.createQuery(text, field = IndexFields.FIELD_TEXT.field)
                    entitySearcher.search(query, 1000)
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




    fun addSharedBM25Abstract(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (SHARED_BM25, wt, norm, this::sharedBM25)


    fun addSharedEntityLinks(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(SHARED_LINKS, wt, norm, this::sharedFeatLinks)


    fun addSharedMeta(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(SHARED_META, wt, norm, this::sharedMeta)




    fun addSharedDirichlet(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (SHARED_DIRICHLET, wt, norm, this::sharedDirichlet)

    fun addSharedBoostedUnigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(SHARED_BOOSTED_UNIGRAM, wt, norm) { qd, sf -> sharedBoostedGram(qd, sf, GramStatType.TYPE_UNIGRAM) }

    fun addSharedBoostedBigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(SHARED_BOOSTED_BIGRAM, wt, norm) { qd, sf -> sharedBoostedGram(qd, sf, GramStatType.TYPE_BIGRAM) }

    fun addSharedBoostedWindowed(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(SHARED_BOOSTED_WINDOWED, wt, norm) { qd, sf -> sharedBoostedGram(qd, sf, GramStatType.TYPE_BIGRAM_WINDOW) }



}
