package lucene.containers

import kotlinx.coroutines.experimental.yield
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
                          val entities: List<EntityContainer>) {
    val jointDistribution = JointDistribution.createFromFunctor(queryData)
//    val jointDistribution = JointDistribution.createEmpty()

    fun retrieveFeatures(): Sequence<ExtractedFeature> {
        val nFeatures = paragraphs.firstOrNull()?.queryFeatures?.size ?: 0
        return buildSequence<ExtractedFeature> {
            (0 until nFeatures).forEach { index ->
                val featureName = paragraphs.first().queryFeatures[index].type.text
                val paragraphScores = paragraphs.map { paragraph ->
                    paragraph.pid to paragraph.queryFeatures[index].getAdjustedScore() }
                val entityScores = entities.map { entity ->
                    entity.name to entity.queryFeatures[index].getAdjustedScore() }
                yield(ExtractedFeature(featureName, query, paragraphScores, entityScores))
            }
        }
    }
}

