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
import utils.stats.normalize


object SubObjectFeatures {
    private fun entityConditionalExpectation(qd: QueryData, sf: SharedFeature,
                                                     conditionFunction: (ParagraphContainer, EntityContainer) -> Double,
                                                     paragraphFeatureIndex: Int = 0) {

        qd.paragraphContainers.forEachIndexed { pIndex, pContainer ->
            val conditionalDist =
                    qd.entityContainers
                        .mapIndexed { eIndex, eContainer -> eIndex to conditionFunction(pContainer, eContainer) }
                        .toMap()
                        .normalize()

//            val paragraphScore = pContainer.features[paragraphFeatureIndex].score
            val paragraphScore = sf.paragraphScores[pIndex]
            conditionalDist.forEach { eIndex, probabilityOfEntityGivenParagraph ->
                sf.entityScores[eIndex] += paragraphScore * probabilityOfEntityGivenParagraph }
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
                    val searchResult = qd.entitySearcher.search(fieldQuery, 20000)

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

    fun addPUnigramToERedirects(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_UNIGRAM_REDIRECTS, wt, norm,
                    bindScoreByField(paragraphField = FIELD_UNIGRAM, entityField = FIELD_REDIRECTS_UNIGRAMS))

    fun addPUnigramToESection(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_UNIGRAM_SECTON, wt, norm,
                    bindScoreByField(paragraphField = FIELD_UNIGRAM, entityField = FIELD_SECTION_UNIGRAM))

    fun addPUnigramToEDisambig(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_UNIGRAM_DISAMBIG, wt, norm,
                    bindScoreByField(paragraphField = FIELD_UNIGRAM, entityField = FIELD_DISAMBIGUATIONS_UNIGRAMS))

    fun addLinkFreq(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_LINK_FREQ, wt, norm, { qd: QueryData, sf: SharedFeature ->
                val result = scoreByEntityLinks(qd)
                entityConditionalExpectation(qd, sf, result) } )

}