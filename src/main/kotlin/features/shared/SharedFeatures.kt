package features.shared

import entity.EntityDatabase
import experiment.FeatureType
import experiment.KotlinRanklibFormatter
import experiment.NormType
import experiment.NormType.ZSCORE
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
                tops.scoreDocs.map { scoreDoc ->
                    val doc = paragraphSearcher.doc(scoreDoc.doc)
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


    // Similarity based on BM25 (from paragraph text to entity abstract)
    private fun sharedBM25(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val documentFeatures =
                paragraphDocuments.map { doc ->
                    val text = doc.get(CONTENT)
                    AnalyzerFunctions.createQuery(text, field = "abstract")
                }
        val entityFeatures = entityContainers.map { entityContainer -> entityContainer.docId }
        scoreBoth(sf, entityFeatures, documentFeatures,
                { entityId, docQuery ->
                    entityDb.searcher.explainScore(docQuery, entityId)
                })
    }
//    fun addSharedBM25Abstract(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
//            fmt.addFeature3 (SHARED_, wt, norm, this::sharedBM25)

    fun addSharedEntityLinks(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(SHARED_LINKS, wt, norm, this::sharedFeatLinks)

    fun addSharedRdf(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(SHARED_RDF, wt, norm, this::sharedRdf)

    fun addSharedUnigramLikelihood(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3(SHARED_UNI_LIKE, wt, norm, this::sharedUnigramLikelihood)


}
