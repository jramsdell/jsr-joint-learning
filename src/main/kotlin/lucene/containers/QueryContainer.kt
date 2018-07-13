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
data class QueryContainer(val query: String, val tops: TopDocs, val paragraphs: List<ParagraphContainer>,
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
}

