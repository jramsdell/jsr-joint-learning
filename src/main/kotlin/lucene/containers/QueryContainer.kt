package lucene.containers

import kotlinx.coroutines.experimental.yield
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
                          val queryData: QueryData,
                          val entities: List<EntityContainer>, val sections: List<SectionContainer>,
                          var jointDistribution: JointDistribution = JointDistribution.createEmpty()) {
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

        val paragraphEntities = ArrayList<EntityContainer>()
        val sectionEntities = ArrayList<EntityContainer>()
        paragraphs.forEach { pContainer ->
            val entity = EntityContainer(
                    name = pContainer.name,
                    docType = ENTITY::class.java,
                    isRelevant = pContainer.isRelevant,
                    docId = pContainer.docId,
                    score = 0.0,
                    index = pContainer.index,
                    searcher = pContainer.searcher,
                    query = pContainer.query,
                    qid = entityQid )
            paragraphEntities.add(entity)
        }

        sections.forEach { sContainer ->
            val entity = EntityContainer(
                    name = sContainer.name,
                    docType = ENTITY::class.java,
                    isRelevant = sContainer.isRelevant,
                    docId = sContainer.docId,
                    score = 0.0,
                    index = sContainer.index,
                    searcher = sContainer.searcher,
                    query = sContainer.query,
                    qid = entityQid )
            sectionEntities.add(entity)
        }


        (0 until nFeatures).forEach { featureIndex ->
            val weight = entities.first().features[featureIndex].weight
            paragraphEntities.forEach { pEntity ->
                val weightedAverage = jointDistribution
                    .parToEnt[pEntity.index]
                    ?.entries
                    ?.sumByDouble { (k,freq) ->
//                        entities[k]!!.features[featureIndex]!!.getUnormalizedAdjusted() * freq
                        entities[k]!!.features[featureIndex]!!.unnormalizedScore * freq
                    } ?: 0.0

                pEntity.features.add(FeatureContainer(weightedAverage, weightedAverage, weight, FeatureEnum.SECTION_QUERY_DIST))
            }

            sectionEntities.forEach { sEntity ->
                val weightedAverage = jointDistribution
                    .secToPar[sEntity.index]!!
                    .entries
                    .sumByDouble { (pIndex,pFreq) ->
                        jointDistribution.parToEnt[pIndex]!!
                            .entries
                            .sumByDouble { (eIndex, eFreq) ->
//                                entities[eIndex]!!.features[featureIndex]!!.getUnormalizedAdjusted() * eFreq * pFreq
                                entities[eIndex]!!.features[featureIndex]!!.unnormalizedScore * eFreq * pFreq
                            }
                    } ?: 0.0

                sEntity.features.add(FeatureContainer(weightedAverage, weightedAverage,weight, FeatureEnum.SECTION_QUERY_DIST))
            }
        }

//        val combined = paragraphEntities.toList() + sectionEntities.toList() + entities

        return Triple(paragraphEntities.toList(), sectionEntities.toList(), entities)

    }
}

