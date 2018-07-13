package features.subobject

import experiment.KotlinRanklibFormatter
import experiment.NormType
import experiment.NormType.*
import features.shared.SharedFeature
import lucene.FieldQueryFormatter
import lucene.containers.*
import lucene.containers.FeatureEnum
import lucene.containers.FeatureEnum.*
import lucene.indexers.IndexFields
import lucene.indexers.IndexFields.*
import lucene.indexers.getList
import utils.misc.toHashMap
import utils.stats.countDuplicates
import utils.stats.defaultWhenNotFinite
import utils.stats.normalize
import java.lang.Double.max
import java.lang.Double.sum

fun getSubMap(qd: QueryData): Pair<Map<Int, HashSet<Int>>, Map<Int, HashSet<Int>>> {
    val parToEntity = qd.paragraphContainers.map { it.index to HashSet<Int>() }.toMap()
    val entToPar = qd.entityContainers.map { it.index to HashSet<Int>() }.toMap()
    val nameMap = qd.entityContainers.map { it.name.toLowerCase() to it.index }.toMap()

    qd.paragraphContainers.forEach { pContainer ->
//        val entities = FIELD_ENTITIES.getList(pContainer.doc()).map(String::toLowerCase)
        val entities = pContainer.doc().entities().split(" ").map(String::toLowerCase)
        entities.forEach { entity ->
            if (entity in nameMap) {
                parToEntity[pContainer.index]!!.add(nameMap[entity]!!)
                entToPar[nameMap[entity]]!!.add(pContainer.index)
            }
        }


    }
    return parToEntity to entToPar
}

object SubObjectFeatures {
    private fun entityConditionalExpectation(qd: QueryData, sf: SharedFeature,
                                                     conditionFunction: (ParagraphContainer, EntityContainer) -> Double,
                                                     paragraphFeatureIndex: Int = 0) {


        val pScores = sf.paragraphScores.map { it }
        val eScores = sf.entityScores.map { it }
        sf.entityScores.fill(0.0)
        sf.paragraphScores.fill(0.0)

        val smoothFactor = 0.0
        val inverseMaps = qd.entityContainers.mapIndexed { _, entity ->  entity.index to HashMap<Int, Double>() }.toMap()
        val uniform = qd.entityContainers.map { (it.index to (1 / sf.entityScores.size.toDouble()) * (smoothFactor) )}
        val parFreqMap = getParagraphConditionMap(qd, uniform, conditionFunction, smoothFactor, inverseMaps)
//        val entFreqMap = getEntityConditionMap(qd, uniform, conditionFunction, smoothFactor)


//        val entityTotal = parFreqMap.flatMap { (_, pMap) ->
//            pMap.toList() }
//            .groupBy { (eIndex, _) -> eIndex }
//            .mapValues { it.value.sumByDouble { it.second } }

//        val inverseMap = parFreqMap.entries.flatMap { (pIndex, pMap) ->
//        pMap.entries.map { (eIndex, eScore) -> eIndex to (pIndex to eScore) } }
//            .groupBy { (eIndex, _) -> eIndex }
//            .mapValues { (_, entityToPar) ->
//                entityToPar.map { it.second }.toMap().normalize() }
////            entityToPar.map { it.second }.toMap() }


        val entFreqMap = inverseMaps.mapValues { it.value.normalize() }
//        val (parToEnt, entToPar) = getSubMap(qd)


//        qd.paragraphContainers.forEachIndexed { pIndex, pContainer ->
//            val subset = parToEnt[pIndex]!!.toList().map { qd.entityContainers[it] }
////            val probMap = subset.map { it.index to conditionFunction(pContainer, it) }
//            val probMap = subset.map {it.index to parFreqMap[pIndex]!!.get(it.index)!!}
//                .toMap()
//                .normalize()
//
//            probMap.forEach { (child, prob) ->
//                sf.entityScores[child] += prob * pScores[pContainer.index]
//            }
//        }
//
//        qd.entityContainers.forEachIndexed { eIndex, eContainer ->
//            val subset = entToPar[eIndex]!!.toList().map { qd.paragraphContainers[it] }
////            val probMap = subset.map { it.index to conditionFunction(it, eContainer) }
//            val probMap = subset.map { it.index to entFreqMap[eIndex]!!.get(it.index)!! }
//                .toMap()
//                .normalize()
//
//            probMap.forEach { (child, prob) ->
//                sf.paragraphScores[child] += prob * eScores[eContainer.index]
//            }
//        }

        qd.paragraphContainers.forEachIndexed { pIndex, pContainer ->
            val paragraphScore = pScores[pContainer.index]
            parFreqMap[pContainer.index]!!.forEach { eIndex, probabilityOfEntityGivenParagraph ->
                sf.entityScores[eIndex] += paragraphScore  * probabilityOfEntityGivenParagraph
            }
        }

        qd.entityContainers.forEachIndexed { eIndex, eContainer ->
            val entityScore = eScores[eContainer.index]
//            inverseMap[eContainer.index]!!.forEach { pIndex, probabilityOfParagraphGivenEntity ->
                entFreqMap[eContainer.index]!!.forEach { pIndex, probabilityOfParagraphGivenEntity ->
//                sf.paragraphScores[pIndex] += entityScore * (probabilityOfParagraphGivenEntity * (1.0 - smoothFactor)) + entityScore * ((1/sf.paragraphScores.size.toDouble()) * smoothFactor)
                sf.paragraphScores[pIndex] += entityScore  * probabilityOfParagraphGivenEntity
            }
        }


    }


    private fun getParagraphConditionMap(qd: QueryData, uniform: List<Pair<Int, Double>>, conditionFunction: (ParagraphContainer, EntityContainer) -> Double, smoothFactor: Double, inverseMaps: Map<Int, HashMap<Int, Double>>): Map<Int, Map<Int, Double>> {
        val parFreqMap = qd.paragraphContainers.map { pContainer ->
            val baseMap = uniform.toHashMap()
            qd.entityContainers
                .map { eContainer ->
                    val conditionScore = conditionFunction(pContainer, eContainer)
                    eContainer.index to conditionScore * (1.0 - smoothFactor)
                }
                .forEach { (k, v) -> baseMap.merge(k, v, ::sum) }
            baseMap.normalize().forEach { (k, v) -> inverseMaps[k]!!.merge(pContainer.index, v, ::sum) }
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

    private fun createConditionFunctionUsingScores(scoreMap: Map<Int, Map<Int, Double>>) =
            { pContainer: ParagraphContainer, eContainer: EntityContainer ->
                (scoreMap[pContainer.docId]?.get(eContainer.docId) ?: 0.0)
//                        (entMap[eContainer.docId]?.get(pContainer.docId) ?: 0.0)
            }


    fun scoreByField(qd: QueryData, paragraphField: IndexFields, entityField: IndexFields): (ParagraphContainer, EntityContainer) -> Double {
        val scoreMap =
                qd.paragraphContainers.mapIndexed { pIndex, pContainer ->
//                    val fieldTokens = paragraphField.getList(pContainer.doc())
                    val fieldTokens = paragraphField.getList(pContainer.doc().doc)
                    val fieldQuery = FieldQueryFormatter()
                        .addWeightedQueryTokens(fieldTokens, entityField, 1.0)
                        .createBooleanQuery()
                    val searchResult = qd.entitySearcher.search(fieldQuery, 2000)

                    val parToEntScores =
                            searchResult.scoreDocs.map { sc -> sc.doc to sc.score.toDouble() }
                    pContainer.docId to parToEntScores.toMap()
                }.toMap()

//        val entMap =
//                qd.entityContainers.mapIndexed { eIndex, eContainer ->
//                    val fieldTokens = entityField.getList(eContainer.doc())
//                    val fieldQuery = FieldQueryFormatter()
//                        .addWeightedQueryTokens(fieldTokens, paragraphField, 1.0)
//                        .createBooleanQuery()
//                    val searchResult = qd.paragraphSearcher.search(fieldQuery, 2000)
//
//                    val entToParScores =
//                            searchResult.scoreDocs.map { sc -> sc.doc to sc.score.toDouble() }
//                    eContainer.docId to entToParScores.toMap()
//                }.toMap()

        val conditionFunction = createConditionFunctionUsingScores(scoreMap)
        return conditionFunction
    }

    fun scoreByEntityLinks(qd: QueryData): (ParagraphContainer, EntityContainer) -> Double {
        val joint = JointDistribution.createJointDistribution(qd)
//        val conditionFunction = createConditionFunctionUsingScores(joint.parToEnt)
        val scoreByIndex =
                { pContainer: ParagraphContainer, eContainer: EntityContainer ->
                    (joint.parToEnt[pContainer.index]?.get(eContainer.index) ?: 0.0)
//                            (joint.entToPar[eContainer.index]?.get(pContainer.index) ?: 0.0)
                }

        return scoreByIndex
    }

    fun bindScoreByField(paragraphField: IndexFields, entityField: IndexFields) = {
        qd: QueryData, sf: SharedFeature ->
            val result = scoreByField(qd, paragraphField, entityField)
        entityConditionalExpectation(qd, sf, result)
    }




    fun addPUnigramToEUnigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_UNIGRAM_UNIGRAM, wt, norm,
                    bindScoreByField(paragraphField = FIELD_UNIGRAM, entityField = FIELD_UNIGRAM))

    fun addPUnigramToECategory(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_UNIGRAM_CATEGORY, wt, norm,
                    bindScoreByField(paragraphField = FIELD_UNIGRAM, entityField = FIELD_CATEGORIES_UNIGRAMS))

    fun addPUnigramToEInlinks(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_UNIGRAM_INLINKS, wt, norm,
                    bindScoreByField(paragraphField = FIELD_UNIGRAM, entityField = FIELD_INLINKS_UNIGRAMS))

    fun addPUnigramToEOutlinks(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_UNIGRAM_OUTLINKS, wt, norm,
                bindScoreByField(paragraphField = FIELD_UNIGRAM, entityField = FIELD_OUTLINKS_UNIGRAMS))

//    fun addPUnigramToERedirects(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
//            fmt.addFeature3 (FeatureEnum.PFUNCTOR_UNIGRAM_REDIRECTS, wt, norm,
//                    bindScoreByField(paragraphField = FIELD_UNIGRAM, entityField = FIELD_REDIRECTS_UNIGRAMS))

    fun addPUnigramToERedirects(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_UNIGRAM_REDIRECTS, wt, norm,
                    bindScoreByField(paragraphField = FIELD_UNIGRAM, entityField = FIELD_REDIRECTS_UNIGRAMS))

    fun addPUnigramToESection(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_UNIGRAM_SECTON, wt, norm,
                    bindScoreByField(paragraphField = FIELD_UNIGRAM, entityField = FIELD_SECTION_UNIGRAM))

    fun addPUnigramToEDisambig(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_UNIGRAM_DISAMBIG, wt, norm,
                    bindScoreByField(paragraphField = FIELD_UNIGRAM, entityField = FIELD_DISAMBIGUATIONS_UNIGRAMS))

    fun addPBigramToEBigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_BIGRAM_BIGRAM, wt, norm,
                    bindScoreByField(paragraphField = FIELD_BIGRAM, entityField = FIELD_BIGRAM))

    fun addPJointUnigramToEUnigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_JOINTUNIGRAM_UNIGRAM, wt, norm,
                    bindScoreByField(paragraphField = FIELD_JOINT_UNIGRAMS, entityField = FIELD_UNIGRAM))

    fun addPJointBigramToEBigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_JOINTBIGRAM_BIGRAM, wt, norm,
                    bindScoreByField(paragraphField = FIELD_JOINT_BIGRAMS, entityField = FIELD_UNIGRAM))

    fun addPJointWindowedToEWindowed(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_JOINTWINDOWED_WINDOWED, wt, norm,
                    bindScoreByField(paragraphField = FIELD_JOINT_WINDOWED, entityField = FIELD_WINDOWED_BIGRAM))

    fun addPWindowedToEWindowed(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_WINDOWED_WINDOWED, wt, norm,
                    bindScoreByField(paragraphField = FIELD_WINDOWED_BIGRAM, entityField = FIELD_WINDOWED_BIGRAM))

    fun addPEntityToOutlinks(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_ENTITY_OUTLINKS, wt, norm,
                    bindScoreByField(paragraphField = FIELD_NEIGHBOR_ENTITIES_UNIGRAMS, entityField = FIELD_OUTLINKS_UNIGRAMS))

    fun addPEntityToInlinks(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_ENTITY_INLINKS, wt, norm,
                    bindScoreByField(paragraphField = FIELD_NEIGHBOR_ENTITIES_UNIGRAMS, entityField = FIELD_INLINKS_UNIGRAMS))

    fun addLinkFreq(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_LINK_FREQ, wt, norm, { qd: QueryData, sf: SharedFeature ->
                val result = scoreByEntityLinks(qd)
                entityConditionalExpectation(qd, sf, result) } )

}