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
import utils.stats.defaultWhenNotFinite
import utils.stats.normalize


object SubObjectFeatures {
    private fun entityConditionalExpectation(qd: QueryData, sf: SharedFeature,
                                                     conditionFunction: (ParagraphContainer, EntityContainer) -> Double,
                                                     paragraphFeatureIndex: Int = 0) {

        val smoothFactor = 0.4
        val uniform = qd.entityContainers.map { (it.index to 1 / sf.paragraphScores.size.toDouble() * (smoothFactor) )}
        val parFreqMap = qd.paragraphContainers.map { pContainer ->
            val pMap = qd.entityContainers.map { eContainer ->
                eContainer.index to conditionFunction(pContainer, eContainer) }
                .toMap()
                .normalize().map { it.key to it.value * (1 - smoothFactor) }
                .run { (this + uniform).groupBy { it.first }.mapValues { it.value.sumByDouble { it.second } } }
                .normalize()
            pContainer.index to pMap
        }.toMap()

        val pScores = sf.paragraphScores.map { it }
        val eScores = sf.entityScores.map { it }
        sf.entityScores.fill(0.0)
        sf.paragraphScores.fill(0.0)

//        val entityTotal = parFreqMap.flatMap { (_, pMap) ->
//            pMap.toList() }
//            .groupBy { (eIndex, _) -> eIndex }
//            .mapValues { it.value.sumByDouble { it.second } }

        val inverseMap = parFreqMap.entries.flatMap { (pIndex, pMap) ->
//            pMap.entries.map { (eIndex, eScore) -> eIndex to (pIndex to (eScore / entityTotal[eIndex]!!).defaultWhenNotFinite(0.0)) } }
        pMap.entries.map { (eIndex, eScore) -> eIndex to (pIndex to eScore) } }
            .groupBy { (eIndex, _) -> eIndex }
            .mapValues { (_, entityToPar) ->
                entityToPar.map { it.second }.toMap().normalize() }
//            entityToPar.map { it.second }.toMap() }


        qd.paragraphContainers.forEachIndexed { pIndex, pContainer ->
//            val conditionalDist =
//                    qd.entityContainers
//                        .mapIndexed { eIndex, eContainer -> eIndex to conditionFunction(pContainer, eContainer) }
//                        .toMap()
//                        .normalize()


//            val paragraphScore = pContainer.features[paragraphFeatureIndex].score
            val paragraphScore = pScores[pIndex]
            parFreqMap[pContainer.index]!!.forEach { eIndex, probabilityOfEntityGivenParagraph ->
                sf.entityScores[eIndex] += paragraphScore * probabilityOfEntityGivenParagraph
            }
        }

        qd.entityContainers.forEachIndexed { eIndex, eContainer ->
            val entityScore = eScores[eIndex]
            inverseMap[eContainer.index]!!.forEach { pIndex, probabilityOfParagraphGivenEntity ->
                sf.paragraphScores[pIndex] += entityScore * probabilityOfParagraphGivenEntity
            }
        }


    }

    private fun createConditionFunctionUsingScores(scoreMap: Map<Int, Map<Int, Double>>) =
            { pContainer: ParagraphContainer, eContainer: EntityContainer ->
                scoreMap[pContainer.docId]?.get(eContainer.docId) ?: 0.0
            }


    fun scoreByField(qd: QueryData, paragraphField: IndexFields, entityField: IndexFields): (ParagraphContainer, EntityContainer) -> Double {
        val scoreMap =
                qd.paragraphContainers.mapIndexed { pIndex, pContainer ->
                    val fieldTokens = paragraphField.getList(pContainer.doc())
                    val fieldQuery = FieldQueryFormatter()
                        .addWeightedQueryTokens(fieldTokens, entityField, 1.0)
                        .createBooleanQuery()
                    val searchResult = qd.entitySearcher.search(fieldQuery, 2000)

                    val parToEntScores =
                            searchResult.scoreDocs.map { sc -> sc.doc to sc.score.toDouble() }
                    pContainer.docId to parToEntScores.toMap()
                }.toMap()

        val conditionFunction = createConditionFunctionUsingScores(scoreMap)
        return conditionFunction
    }

    fun scoreByEntityLinks(qd: QueryData): (ParagraphContainer, EntityContainer) -> Double {
        val joint = JointDistribution.createJointDistribution(qd.entityContainers, qd.paragraphContainers)
//        val conditionFunction = createConditionFunctionUsingScores(joint.parToEnt)
        val scoreByIndex =
                { pContainer: ParagraphContainer, eContainer: EntityContainer ->
                    joint.parToEnt[pContainer.index]?.get(eContainer.index) ?: 0.0
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
                    bindScoreByField(paragraphField = FIELD_NEIGHBOR_UNIGRAMS, entityField = FIELD_UNIGRAM))

    fun addPUnigramToECategory(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_UNIGRAM_CATEGORY, wt, norm,
                    bindScoreByField(paragraphField = FIELD_NEIGHBOR_UNIGRAMS, entityField = FIELD_CATEGORIES_UNIGRAMS))

    fun addPUnigramToEInlinks(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_UNIGRAM_INLINKS, wt, norm,
                    bindScoreByField(paragraphField = FIELD_NEIGHBOR_UNIGRAMS, entityField = FIELD_INLINKS_UNIGRAMS))

    fun addPUnigramToEOutlinks(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_UNIGRAM_OUTLINKS, wt, norm,
                bindScoreByField(paragraphField = FIELD_NEIGHBOR_UNIGRAMS, entityField = FIELD_OUTLINKS_UNIGRAMS))

    fun addPUnigramToERedirects(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_UNIGRAM_REDIRECTS, wt, norm,
                    bindScoreByField(paragraphField = FIELD_NEIGHBOR_UNIGRAMS, entityField = FIELD_REDIRECTS_UNIGRAMS))

    fun addPUnigramToESection(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_UNIGRAM_SECTON, wt, norm,
                    bindScoreByField(paragraphField = FIELD_NEIGHBOR_UNIGRAMS, entityField = FIELD_SECTION_UNIGRAM))

    fun addPUnigramToEDisambig(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_UNIGRAM_DISAMBIG, wt, norm,
                    bindScoreByField(paragraphField = FIELD_NEIGHBOR_UNIGRAMS, entityField = FIELD_DISAMBIGUATIONS_UNIGRAMS))

    fun addPBigramToEBigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_BIGRAM_BIGRAM, wt, norm,
                    bindScoreByField(paragraphField = FIELD_NEIGHBOR_BIGRAMS, entityField = FIELD_BIGRAM))

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
                    bindScoreByField(paragraphField = FIELD_NEIGHBOR_WINDOWED, entityField = FIELD_WINDOWED_BIGRAM))

    fun addLinkFreq(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_LINK_FREQ, wt, norm, { qd: QueryData, sf: SharedFeature ->
                val result = scoreByEntityLinks(qd)
                entityConditionalExpectation(qd, sf, result) } )

}