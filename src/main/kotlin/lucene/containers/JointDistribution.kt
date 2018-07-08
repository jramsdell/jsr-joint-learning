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
            val smoothingFactor = 0


            paragraphContainers.forEachIndexed { index, container ->
                // Retrieve entities from neighborhood (and also add in own entities, increasing probability of those)
                val entities = container.doc().get(IndexFields.FIELD_NEIGHBOR_ENTITIES.field).split(" ")
//                val entities = container.doc.get(IndexFields.FIELD_ENTITIES.field).split(" ")

                val baseFreqs =
                        entities.mapNotNull { id -> entIdMap[id] }
                            .countDuplicates()
//                            .normalize()
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

//            val entFreqMap = inverseMaps.mapValues { it.value.normalize() }
            val entFreqMap = inverseMaps.mapValues { it.value }
            return JointDistribution(
                    entToPar = entFreqMap,
                    parToEnt = parFreqMap as Map<Int, Map<Int, Double>>
            )
        }

        fun createFromFunctor(qd: QueryData): JointDistribution {
            val sf1 = SubObjectFeatures.scoreByEntityLinks(qd)
            val sf2 = SubObjectFeatures.scoreByField(qd,
                    paragraphField = IndexFields.FIELD_NEIGHBOR_UNIGRAMS, entityField = IndexFields.FIELD_CATEGORIES_UNIGRAMS)
            val sf3 = SubObjectFeatures.scoreByField(qd,
                    paragraphField = IndexFields.FIELD_NEIGHBOR_UNIGRAMS, entityField = IndexFields.FIELD_INLINKS_UNIGRAMS)
            val sf4 = SubObjectFeatures.scoreByField(qd,
                    paragraphField = IndexFields.FIELD_NEIGHBOR_UNIGRAMS, entityField = IndexFields.FIELD_UNIGRAM)
            val sf5 = SubObjectFeatures.scoreByField(qd,
                    paragraphField = IndexFields.FIELD_NEIGHBOR_UNIGRAMS, entityField = IndexFields.FIELD_REDIRECTS_UNIGRAMS)
            val sf6 = SubObjectFeatures.scoreByField(qd,
                    paragraphField = IndexFields.FIELD_NEIGHBOR_UNIGRAMS, entityField = IndexFields.FIELD_DISAMBIGUATIONS_UNIGRAMS)
            val sf7 = SubObjectFeatures.scoreByField(qd,
                    paragraphField = IndexFields.FIELD_NEIGHBOR_UNIGRAMS, entityField = IndexFields.FIELD_SECTION_UNIGRAM)
            val sf8 = SubObjectFeatures.scoreByField(qd,
                    paragraphField = IndexFields.FIELD_NEIGHBOR_UNIGRAMS, entityField = IndexFields.FIELD_OUTLINKS_UNIGRAMS)

//            val sf2 = SubObjectFeatures.scoreByField(qd,
//                    paragraphField = IndexFields.FIELD_UNIGRAM, entityField = IndexFields.FIELD_UNIGRAM)
            // 0.06829399869281763 ,0.08641083044323171 ,-0.04567514341156267



//            val weights = listOf(
//                    sf1 to 0.36031379874240177)

            val functors = listOf(sf1, sf2, sf3, sf4, sf5, sf6, sf7, sf8)
//            val functors = listOf(sf1)
            val weights = listOf(0.6630831055545302 ,0.029002278148547393 ,0.048180881651334204 ,0.194275658942431 ,0.020536045586523537 ,-0.006614006135361941 ,0.0019897497946394743 ,0.036318274186632214

            )
            val joined = functors.zip(weights)

//            val foldOverScoringFunctions = { pContainer: ParagraphContainer, eContainer: EntityContainer ->
//                joined.sumByDouble { (sf, weight) -> sf(pContainer, eContainer) * weight }
//            }

//            val parFreqMap = qd.paragraphContainers.map { pContainer ->
//                val pMap = qd.entityContainers.map { eContainer ->
//                    eContainer.index to foldOverScoringFunctions(pContainer, eContainer) }
//                    .toMap()
//                    .normalize()
//                pContainer.index to pMap
//            }.toMap()

            val parFreqMap = qd.paragraphContainers.map { pContainer ->
                val bigMap = HashMap<Int, Double>()
                joined.map { (functor, weight) ->
                    qd.entityContainers.map { eContainer -> eContainer.index to functor(pContainer, eContainer) }
                        .toMap()
                        .normalize()
                        .forEach { bigMap.merge(it.key, it.value * weight, ::sum) } }
//                        .forEach { bigMap.merge(it.key, it.value * weight, ::sum) } }

//                pContainer.index to bigMap.normalize()
                pContainer.index to bigMap
            }.toMap()


            val inverseMap = parFreqMap.entries.flatMap { (pIndex, pMap) ->
                pMap.entries.map { (eIndex, eScore) -> eIndex to (pIndex to eScore) } }
                .groupBy { (eIndex, _) -> eIndex }
                .mapValues { (_, entityToPar) ->
                    entityToPar.map { it.second }.toMap().normalize() }
//                        entityToPar.map { it.second }.toMap() }

            return JointDistribution( parToEnt = parFreqMap, entToPar = inverseMap )
        }

        fun createEmpty() = JointDistribution(emptyMap(), emptyMap())
    }

}