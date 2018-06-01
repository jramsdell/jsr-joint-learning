package features.shared

import entity.EntityDatabase
import lucene.containers.QueryData
import utils.AnalyzerFunctions
import utils.lucene.explainScore
import utils.misc.CONTENT
import utils.stats.countDuplicates
import utils.stats.normalize
import kotlin.math.absoluteValue


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
    fun sharedRdf(qd: QueryData, sf: SharedFeature, db: EntityDatabase): Unit = with(qd) {
        val entityFeatures = entityContainers.map { entity -> db.getEntityByID(entity.docId).rdf }
        val documentFeatures =
                tops.scoreDocs.map { scoreDoc ->
                    val doc = paragraphSearcher.doc(scoreDoc.doc)
                    doc.getValues("spotlight")
                        .mapNotNull { docEntity -> db.getEntity(docEntity) }
                        .flatMap { docEntity -> docEntity.rdf }
                        .countDuplicates()
                }

        scoreBoth(sf, entityFeatures, documentFeatures,
                { rdfs, rdfCount ->
                    rdfs.map { rdf ->
                        rdfCount.getOrDefault(rdf, 0).toDouble() }
                        .sum()
                })
    }

    fun sharedFeatLinks(qd: QueryData, sf: SharedFeature): Unit = with(qd) {
        val documentFeatures =
                paragraphDocuments.map { doc ->
                    doc.getValues("spotlight")
                        .toList()
                        .countDuplicates()
                        .normalize()
                }
        val entityFeatures = entityContainers.map { entityContainer -> entityContainer.name }
        scoreBoth(sf, documentFeatures, entityFeatures,
                { docLinks, entityName ->
                    docLinks.getOrDefault(entityName, 0.0)
                })
    }

    fun sharedUnigramLikelihood(qd: QueryData, sf: SharedFeature, db: EntityDatabase): Unit = with(qd) {
        val documentFeatures =
                paragraphDocuments.map { doc ->
                    doc.get("unigram")
                        .split(" ")
                        .countDuplicates()
                        .normalize()
                }
        val entityFeatures = entityContainers.map { entityContainer ->
            db.getEntityByID(entityContainer.docId).unigram
        }
        scoreBoth(sf, documentFeatures, entityFeatures,
                { docUnigrams, entityUnigrams ->
                    val combinedKeys = (docUnigrams.keys + entityUnigrams.keys).toSet()
                    combinedKeys
                        .map { key ->
                            val docFreq = docUnigrams.getOrDefault(key, 0.0)
                            val entityFreq = entityUnigrams.getOrDefault(key, 0.0)
                            (docFreq - entityFreq).absoluteValue }
                        .sum()
                })
    }

    fun sharedBM25(qd: QueryData, sf: SharedFeature, db: EntityDatabase): Unit = with(qd) {
        val documentFeatures =
                paragraphDocuments.map { doc ->
                    val text = doc.get(CONTENT)
                    AnalyzerFunctions.createQuery(text, field = "abstract")
                }
        val entityFeatures = entityContainers.map { entityContainer -> entityContainer.docId }
        scoreBoth(sf, documentFeatures, entityFeatures,
                { docQuery, entityId ->
                    db.searcher.explainScore(docQuery, entityId)
                })
    }
}
