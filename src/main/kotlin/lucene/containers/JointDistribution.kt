package lucene.containers

import features.subobject.SubObjectFeatures
import lucene.indexers.IndexFields
import utils.misc.groupOfSets
import utils.misc.identity
import utils.misc.mapOfLists
import utils.stats.countDuplicates
import utils.stats.normalize
import java.lang.Double.sum


class JointDistribution(val parToEnt: Map<Int, Map<Int, Double>>, val entToPar: Map<Int, Map<Int, Double>>) {
    companion object {
        fun createJointDistribution(entityContainers: List<EntityContainer>, paragraphContainers: List<ParagraphContainer>): JointDistribution {
            val entIdMap = entityContainers.mapIndexed { index, container -> container.name to index }.toMap()
            val inverseMaps =
                    entityContainers.mapIndexed { index, _ -> index to HashMap<Int, Double>() }
                        .toMap()

            val parFreqMap = HashMap<Int, Map<Int, Double>>()
            val smoothingFactor = 0.4


            paragraphContainers.forEachIndexed { index, container ->
                // Retrieve entities from neighborhood (and also add in own entities, increasing probability of those)
                val entities = container.doc().get(IndexFields.FIELD_NEIGHBOR_ENTITIES.field).split(" ")
//                val entities = container.doc.get(IndexFields.FIELD_ENTITIES.field).split(" ")

                val baseFreqs =
                        entities.mapNotNull { id -> entIdMap[id] }
                            .countDuplicates()
                            .normalize()
                            .map { it.key to it.value * (1.0 - smoothingFactor) } // Smoothing part

                val otherFreqs =
                        entIdMap.values.map { it to (1.0 / entIdMap.size.toDouble()) * (smoothingFactor) }

                // Since frequencies can contain duplicates, merge them all together so there's exactly one freq
                // for each of the entities in our pool.
                val freqMap = (baseFreqs + otherFreqs)
                    .groupingBy { (entityId, _) -> entityId }
                    .fold(0.0) { acc, (_, freq) -> acc + freq }

                freqMap.forEach { (entityId, freq) ->
                    inverseMaps[entityId]!!.merge(index, freq, ::sum)
                }

                parFreqMap[index] = freqMap
            }

            val entFreqMap = inverseMaps.mapValues { it.value.normalize() }
            return JointDistribution(
                    entToPar = entFreqMap,
                    parToEnt = parFreqMap as Map<Int, Map<Int, Double>>
            )
        }

        fun createFromFunctor(qd: QueryData): JointDistribution {
            val sf1 = SubObjectFeatures.scoreByEntityLinks(qd)
            val sf2 = SubObjectFeatures.scoreByField(qd,
                    paragraphField = IndexFields.FIELD_UNIGRAM, entityField = IndexFields.FIELD_UNIGRAM)

            val weights = listOf(
                    sf1 to 0.36937909605728453)
//                    sf2 to 0.5689430348900053)

            val foldOverScoringFunctions = { pContainer: ParagraphContainer, eContainer: EntityContainer ->
                weights.sumByDouble { (sf, weight) -> sf(pContainer, eContainer) * weight }
            }

            val parFreqMap = qd.paragraphContainers.map { pContainer ->
                val pMap = qd.entityContainers.map { eContainer ->
                    eContainer.index to foldOverScoringFunctions(pContainer, eContainer) }
                    .toMap()
                    .normalize()
                pContainer.index to pMap
            }.toMap()

            val inverseMap = parFreqMap.entries.flatMap { (pIndex, pMap) ->
                pMap.entries.map { (eIndex, eScore) -> eIndex to (pIndex to eScore) } }
                .groupBy { (eIndex, _) -> eIndex }
                .mapValues { (_, entityToPar) ->
                    entityToPar.map { it.second }.toMap().normalize() }

            return JointDistribution( parToEnt = parFreqMap, entToPar = inverseMap )
        }
    }

}