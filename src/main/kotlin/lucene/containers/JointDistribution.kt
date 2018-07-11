package lucene.containers

import features.subobject.SubObjectFeatures
import lucene.indexers.IndexFields
import lucene.indexers.getList
import utils.misc.groupOfSets
import utils.misc.identity
import utils.misc.mapOfLists
import utils.misc.toHashMap
import utils.stats.countDuplicates
import utils.stats.normalize
import java.lang.Double.sum

fun getSubMap(paragraphContainers: List<ParagraphContainer>, entityContainers: List<EntityContainer>): Pair<Map<Int, HashSet<Int>>, Map<Int, HashSet<Int>>> {
    val parToEntity = paragraphContainers.map { it.index to HashSet<Int>() }.toMap()
    val entToPar = entityContainers.map { it.index to HashSet<Int>() }.toMap()
    val nameMap = entityContainers.map { it.name.toLowerCase() to it.index }.toMap()

    paragraphContainers.forEach { pContainer ->
//        val entities = IndexFields.FIELD_ENTITIES.getList(pContainer.doc()).map(String::toLowerCase)
        val entities = (pContainer.doc().entities().split(" ")).map(String::toLowerCase)
        entities.forEach { entity ->
            if (entity in nameMap) {
                parToEntity[pContainer.index]!!.add(nameMap[entity]!!)
                entToPar[nameMap[entity]]!!.add(pContainer.index)
            }
        }


    }
    return parToEntity to entToPar
}


class JointDistribution(val parToEnt: Map<Int, Map<Int, Double>>, val entToPar: Map<Int, Map<Int, Double>>) {
    companion object {
        fun createJointDistribution(entityContainers: List<EntityContainer>, paragraphContainers: List<ParagraphContainer>): JointDistribution {
            val entIdMap = entityContainers.mapIndexed { index, container -> container.name to index }.toMap()
            val inverseMaps =
                    entityContainers.mapIndexed { index, _ -> index to HashMap<Int, Double>() }
                        .toMap()

            val parFreqMap = HashMap<Int, Map<Int, Double>>()
            val smoothingFactor = 0.0

//            val (ptoE, etoP) = getSubMap(paragraphContainers, entityContainers)


            paragraphContainers.forEachIndexed { index, container ->
                // Retrieve entities from neighborhood (and also add in own entities, increasing probability of those)
                val entities = container.doc().get(IndexFields.FIELD_NEIGHBOR_ENTITIES.field).split(" ")
//                val entities = container.doc().get(IndexFields.FIELD_ENTITIES.field).split(" ")
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
//            val entFreqMap = inverseMaps.mapValues { it.value }
            return JointDistribution(
                    entToPar = entFreqMap,
                    parToEnt = parFreqMap as Map<Int, Map<Int, Double>>
//                            entToPar = etoP.map { it.key to it.value.map { it to 1.0 }.toMap() }.toMap(),
//                    parToEnt = ptoE.map { it.key to it.value.map { it to 1.0 }.toMap() }.toMap()
            )
        }

        fun createFromFunctor(qd: QueryData): JointDistribution {
            val sf1 = SubObjectFeatures.scoreByEntityLinks(qd)
//            val sf2 = SubObjectFeatures.scoreByField(qd,
//                    paragraphField = IndexFields.FIELD_UNIGRAM, entityField = IndexFields.FIELD_CATEGORIES_UNIGRAMS)
            val sf3 = SubObjectFeatures.scoreByField(qd,
                    paragraphField = IndexFields.FIELD_UNIGRAM, entityField = IndexFields.FIELD_INLINKS_UNIGRAMS)
            val sf4 = SubObjectFeatures.scoreByField(qd,
                    paragraphField = IndexFields.FIELD_UNIGRAM, entityField = IndexFields.FIELD_UNIGRAM)
//            val sf5 = SubObjectFeatures.scoreByField(qd,
//                    paragraphField = IndexFields.FIELD_UNIGRAM, entityField = IndexFields.FIELD_REDIRECTS_UNIGRAMS)
//            val sf6 = SubObjectFeatures.scoreByField(qd,
//                    paragraphField = IndexFields.FIELD_UNIGRAM, entityField = IndexFields.FIELD_DISAMBIGUATIONS_UNIGRAMS)
////            val sf7 = SubObjectFeatures.scoreByField(qd,
////                    paragraphField = IndexFields.FIELD_NEIGHBOR_UNIGRAMS, entityField = IndexFields.FIELD_SECTION_UNIGRAM)
//            val sf8 = SubObjectFeatures.scoreByField(qd,
//                    paragraphField = IndexFields.FIELD_UNIGRAM, entityField = IndexFields.FIELD_OUTLINKS_UNIGRAMS)

//
//            val sf9 = SubObjectFeatures.scoreByField(qd,
//                    paragraphField = IndexFields.FIELD_JOINT_WINDOWED, entityField = IndexFields.FIELD_WINDOWED_BIGRAM)

//            val sf10 = SubObjectFeatures.scoreByField(qd,
//                    paragraphField = IndexFields.FIELD_JOINT_BIGRAMS, entityField = IndexFields.FIELD_BIGRAM)

//            val sf2 = SubObjectFeatures.scoreByField(qd,
//                    paragraphField = IndexFields.FIELD_UNIGRAM, entityField = IndexFields.FIELD_UNIGRAM)
            // 0.06829399869281763 ,0.08641083044323171 ,-0.04567514341156267



//            val weights = listOf(
//                    sf1 to 0.36031379874240177)

//            val functors = listOf(sf1)
            val functors = listOf(sf1, sf3, sf4)
//            val functors = listOf(sf3)
            val weights = listOf(0.8646424114134189 ,0.05187387093956956 ,0.0834837176470115
            )
            val joined = functors.zip(weights)

//            val (ptoE, etoP) = getSubMap(qd.paragraphContainers, qd.entityContainers)

            val smoothFactor = 0.0

            val uniform = qd.entityContainers.map { it.index to smoothFactor * (1.0 / qd.entityContainers.size.toDouble()) }

            val parFreqMap = qd.paragraphContainers.map { pContainer ->
                val bigMap = HashMap<Int, Double>()
//                val contained = ptoE[pContainer.index]!!.map { qd.entityContainers[it]!! }

                joined.map { (functor, weight) ->
                    uniform.forEach { (k,v) -> bigMap.merge(k, v * weight, ::sum) }
                    qd.entityContainers.map { eContainer -> eContainer.index to functor(pContainer, eContainer)  }
//                    contained.map { eContainer -> eContainer.index to functor(pContainer, eContainer)  }
                        .toMap()
                        .normalize()
                        .mapValues { it.value * (1.0 - smoothFactor) }
                        .forEach { bigMap.merge(it.key, it.value * weight, ::sum) }
                }

                pContainer.index to bigMap.normalize()
            }.toMap()


            val inverseMap = parFreqMap.entries.flatMap { (pIndex, pMap) ->
                pMap.entries.map { (eIndex, eScore) -> eIndex to (pIndex to eScore) } }
                .groupBy { (eIndex, _) -> eIndex }
                .mapValues { (_, entityToPar) ->
                    entityToPar.map { it.second }.toMap().normalize() }
//                        entityToPar.map { it.second }.toMap() }

//            val uniform2 = qd.paragraphContainers.map { it.index to smoothFactor * (1.0 / qd.paragraphContainers.size.toDouble()) }
//            val inverseMap = qd.entityContainers.map { eContainer ->
//                val bigMap = HashMap<Int, Double>()
//                joined.map { (functor, weight) ->
//                    uniform2.forEach { (k, v) -> bigMap.merge(k, v * weight, ::sum) }
//                    qd.paragraphContainers.map { pContainer -> pContainer.index to functor(pContainer, eContainer)  }
//                        .toMap()
//                        .normalize()
//                        .mapValues { it.value * (1.0 - smoothFactor) }
//                        .forEach { bigMap.merge(it.key, it.value * weight, ::sum) }
//                }
//                eContainer.index to bigMap.normalize()
//            }.toMap()


            return JointDistribution( parToEnt = parFreqMap, entToPar = inverseMap )
//            return JointDistribution( parToEnt = parFreqMap, entToPar = etoP.map { it.key to it.value.map { it to 1.0 }.toMap() }.toMap() )
        }

        fun createEmpty() = JointDistribution(emptyMap(), emptyMap())
    }

}

private fun getParagraphConditionMap(qd: QueryData, conditionFunction: (ParagraphContainer, EntityContainer) -> Double, smoothFactor: Double): Map<Int, Map<Int, Double>> {
    val parFreqMap = qd.paragraphContainers.map { pContainer ->
        val baseMap = qd.entityContainers.map { it.index to (1/qd.entityContainers.size.toDouble()) * smoothFactor }.toHashMap()
        qd.entityContainers
            .map { eContainer ->
                val conditionScore = conditionFunction(pContainer, eContainer)
                eContainer.index to conditionScore * (1.0 - smoothFactor)
            }
            .forEach { (k, v) -> baseMap.merge(k, v, ::sum) }
        pContainer.index to baseMap.normalize()
    }.toMap()
    return parFreqMap
}

private fun getEntityConditionMap(qd: QueryData, uniform: List<Pair<Int, Double>>, conditionFunction: (ParagraphContainer, EntityContainer) -> Double, smoothFactor: Double): Map<Int, Map<Int, Double>> {
    val parFreqMap = qd.entityContainers.map { eContainer ->
        val baseMap = qd.paragraphContainers.map { it.index to (1/qd.paragraphContainers.size.toDouble()) * smoothFactor }.toHashMap()
        qd.paragraphContainers
            .map { pContainer ->
                val conditionScore = conditionFunction(pContainer, eContainer)
                pContainer.index to conditionScore * (1.0 - smoothFactor)
            }
            .forEach { (k, v) -> baseMap.merge(k, v, ::sum) }
        eContainer.index to baseMap.normalize()
    }.toMap()
    return parFreqMap
}
