package lucene.containers

import experiment.FeatureType
import kotlinx.coroutines.experimental.yield
import lucene.PseudoDatabase
import lucene.PseudoDocumentDatabase
import lucene.containers.IndexType.ENTITY
import org.apache.lucene.search.TopDocs
import kotlin.coroutines.experimental.buildIterator
import kotlin.coroutines.experimental.buildSequence

data class ExtractedFeature(val name: String,
                            val query: String,
                            val paragraphs: List<Pair<String, Double>>,
                            val entities: List<Pair<String, Double>> )

/**
 * Class: QueryContainer
 * Description: One is created for each of the lucene strings in the lucene .cbor file.
 *              Stores corresponding lucene string and TopDocs (obtained from BM25)
 */
data class QueryContainer(val query: String, val paragraphs: List<ParagraphContainer>,
                          val contextEntities: List<ContextEntityContainer>,
                          val queryData: QueryData,
                          val entities: List<EntityContainer>, val sections: List<SectionContainer>,
                          var jointDistribution: JointDistribution = JointDistribution.createEmpty(),
                          val originParagraphs: List<ParagraphContainer>,
                          val nRel: Double = 0.0) {
//    val jointDistribution = if (queryData.isJoint) JointDistribution.createFromFunctor(queryData)
//    val jointDistribution = if (queryData.isJoint) JointDistribution.createJointDistribution(queryData)
//    val jointDistribution = if (queryData.isJoint) JointDistribution.createExperimental(queryData)
//    else JointDistribution.createEmpty()

    fun retrieveFeatures(): Sequence<ExtractedFeature> {
        val nFeatures = paragraphs.firstOrNull()?.features?.size ?: 0
        return buildSequence<ExtractedFeature> {
            (0 until nFeatures).forEach { index ->
                val featureName = paragraphs.first().features[index].type.text
                val paragraphScores = paragraphs.map { paragraph ->
                    paragraph.name to paragraph.features[index].getAdjustedScore() }
                val entityScores = entities.map { entity ->
                    entity.name to entity.features[index].getAdjustedScore() }
                yield(ExtractedFeature(featureName, query, paragraphScores, entityScores))
            }
        }
    }

    fun transformFeatures(): Triple<List<EntityContainer>, List<EntityContainer>, List<EntityContainer>> {
        if (entities.isEmpty()) {
            println("Something is wrong at $query!")
            return Triple(emptyList(), emptyList(), emptyList())
        }
        val nFeatures = entities.first().features.size
        val entityQid = entities.first().qid
        val eSearcher = entities.first().searcher

        val paragraphEntities = ArrayList<EntityContainer>()
        val sectionEntities = ArrayList<EntityContainer>()
        paragraphs.forEach { pContainer ->
//            pContainer.rescore()
            val entity = EntityContainer(
                    name = pContainer.name,
                    docType = ENTITY::class.java,
                    isRelevant = pContainer.isRelevant,
                    docId = pContainer.docId,
                    score = 0.0,
                    index = pContainer.index,
                    searcher = eSearcher,
                    query = pContainer.query,
                    qid = pContainer.qid )
                .apply { features = pContainer.features }
            paragraphEntities.add(entity)
        }

        sections.forEach { sContainer ->
//            sContainer.rescore()
            val entity = EntityContainer(
                    name = sContainer.name,
                    docType = ENTITY::class.java,
                    isRelevant = sContainer.isRelevant,
                    docId = sContainer.docId,
                    score = 0.0,
                    index = sContainer.index,
                    searcher = eSearcher,
                    query = sContainer.query,
                    qid = sContainer.qid )
                .apply { features = sContainer.features }
            sectionEntities.add(entity)
        }


        (0 until nFeatures).forEach { featureIndex ->
            val weight = entities.first().features[featureIndex].weight
            val type = entities.first().features[featureIndex].type
            paragraphEntities.forEach { pEntity ->
//                val oldScore = pEntity.features[featureIndex].score
                val oldScore = 1.0
                var weightedAverage = jointDistribution
                    .parToEnt[pEntity.index]
                    ?.entries
                    ?.sumByDouble { (k,freq) ->
//                        entities[k]!!.features[featureIndex]!!.getUnormalizedAdjusted() * freq
//                        entities[k]!!.features[featureIndex]!!.unnormalizedScore * freq
                        entities[k]!!.features[featureIndex]!!.score * freq * oldScore
                    } ?: 0.0

                if (type != FeatureType.ENTITY) {
                    weightedAverage = pEntity.features[featureIndex].score
                }

//                val score = if (type == FeatureType.PARAGRAPH_FUNCTOR) s

//                pEntity.features.add(FeatureContainer(weightedAverage, weightedAverage, weight, FeatureEnum.SECTION_QUERY_DIST))
                pEntity.features[featureIndex]
                    .apply {
                        score = weightedAverage
                        unnormalizedScore = weightedAverage
                    }
            }

            sectionEntities.forEach { sEntity ->
                val type = entities.first().features[featureIndex].type
                var weightedAverage = jointDistribution
                    .secToPar[sEntity.index]!!
                    .entries
                    .sumByDouble { (pIndex,pFreq) ->
                        jointDistribution.parToEnt[pIndex]!!
                            .entries
                            .sumByDouble { (eIndex, eFreq) ->
//                                entities[eIndex]!!.features[featureIndex]!!.getUnormalizedAdjusted() * eFreq * pFreq
//                                entities[eIndex]!!.features[featureIndex]!!.unnormalizedScore * eFreq * pFreq
                                entities[eIndex]!!.features[featureIndex]!!.score * eFreq * pFreq
                            }
                    } ?: 0.0

                if (type != FeatureType.ENTITY)
                    weightedAverage = sEntity.features[featureIndex].score

//                sEntity.features.add(FeatureContainer(weightedAverage, weightedAverage,weight, FeatureEnum.SECTION_QUERY_DIST))
                sEntity.features[featureIndex]
                    .apply {
                        score = weightedAverage
                        unnormalizedScore = weightedAverage
                    }
            }
        }

//        val combined = paragraphEntities.toList() + sectionEntities.toList() + entities

        return Triple(paragraphEntities.toList(), sectionEntities.toList(), entities)

    }
}

