package features.shared

import entity.EntityDatabase
import experiment.FeatureType
import experiment.KotlinRanklibFormatter
import experiment.NormType
import experiment.NormType.ZSCORE
import language.GramAnalyzer
import language.GramStatType
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
import org.apache.lucene.document.Document
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.similarities.LMDirichletSimilarity
import utils.stats.takeMostFrequent


data class SharedFeature(val paragraphScores: ArrayList<Double>, val entityScores: ArrayList<Double>)
private fun<A, B> scoreBoth(sf: SharedFeature, entityList: List<A>, paragraphList: List<B>,
                            scoreFunction: (A, B) -> Double) {
    entityList.mapIndexed { entityIndex, a ->
        paragraphList.mapIndexed { paragraphIndex, b ->
            val score = scoreFunction(a, b)
            sf.entityScores[entityIndex] += score
            sf.paragraphScores[paragraphIndex] += score
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
                    doc.getValues("spotlight")
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
                    doc.getValues("spotlight")
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
                     entityDb.searcher.search(docQuery, 1000)
                         .scoreDocs
                         .map { sc -> sc.doc to sc.score.toDouble() }
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
                paragraphDocuments.map { doc ->
                    val text = doc.get(CONTENT)
                    val query = AnalyzerFunctions.createQuery(text, field = "abstract")
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
                    val query = AnalyzerFunctions.createQuery(text, field = "abstract")
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
    fun addSharedBM25Abstract(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (SHARED_BM25, wt, norm, this::sharedBM25)

    fun addSharedDirichlet(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (SHARED_DIRICHLET, wt, norm, this::sharedDirichlet)

    fun addSharedEntityLinks(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(SHARED_LINKS, wt, norm, this::sharedFeatLinks)

    fun addSharedRdf(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(SHARED_RDF, wt, norm, this::sharedRdf)

    fun addSharedUnigramLikelihood(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(SHARED_UNI_LIKE, wt, norm, this::sharedUnigramLikelihood)

    fun addSharedBoostedUnigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(SHARED_BOOSTED_UNIGRAM, wt, norm) { qd, sf -> sharedBoostedGram(qd, sf, GramStatType.TYPE_UNIGRAM) }

    fun addSharedBoostedBigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(SHARED_BOOSTED_BIGRAM, wt, norm) { qd, sf -> sharedBoostedGram(qd, sf, GramStatType.TYPE_BIGRAM) }

    fun addSharedBoostedWindowed(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(SHARED_BOOSTED_WINDOWED, wt, norm) { qd, sf -> sharedBoostedGram(qd, sf, GramStatType.TYPE_BIGRAM_WINDOW) }


}
