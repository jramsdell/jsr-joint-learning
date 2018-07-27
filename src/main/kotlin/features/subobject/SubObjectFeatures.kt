package features.subobject

import edu.unh.cs.treccar_v2.Data
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
import lucene.indexers.getString
import org.apache.lucene.index.Term
import org.apache.lucene.search.IndexSearcher
import utils.AnalyzerFunctions
import utils.lucene.explainScore
import utils.misc.toHashMap
import utils.stats.*
import java.lang.Double.max
import java.lang.Double.sum
import java.lang.Math.log


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
//        val uniform = qd.entityContainers.map { (it.index to (1 / sf.entityScores.size.toDouble()) * (smoothFactor) )}
        val parFreqMap = getParagraphConditionMap(qd, conditionFunction, inverseMaps)
//        val parFreqMap = gParCondMap(qd, conditionFunction)
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
                inverseMaps[eContainer.index]!!.forEach { pIndex, probabilityOfParagraphGivenEntity ->
//                sf.paragraphScores[pIndex] += entityScore * (probabilityOfParagraphGivenEntity * (1.0 - smoothFactor)) + entityScore * ((1/sf.paragraphScores.size.toDouble()) * smoothFactor)
                sf.paragraphScores[pIndex] += entityScore  * probabilityOfParagraphGivenEntity
            }
        }


    }


    fun getParagraphConditionMap(qd: QueryData, conditionFunction: (ParagraphContainer, EntityContainer) -> Double, inverseMaps: Map<Int, HashMap<Int, Double>>): Map<Int, Map<Int, Double>> {
        val parFreqMap = qd.paragraphContainers.map { pContainer ->
            val dist = qd.entityContainers
                .map { eContainer ->
                    val conditionScore = conditionFunction(pContainer, eContainer)
                    inverseMaps[eContainer.index]!!.merge(pContainer.index, conditionScore, ::sum)
                    eContainer.index to conditionScore
                }.toMap()
            pContainer.index to dist
        }.toMap()
        return parFreqMap
    }

    private fun gParCondMap(qd: QueryData, conditionFunction: (ParagraphContainer, EntityContainer) -> Double): Map<Int, Map<Int, Double>> {
        val parFreqMap = qd.paragraphContainers.map { pContainer ->
            val dist = qd.entityContainers
                .map { eContainer ->
                    val conditionScore = conditionFunction(pContainer, eContainer)
                    eContainer.index to conditionScore
                }.toMap()
            pContainer.index to dist
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
            }



    fun scoreByField(qd: QueryData, paragraphField: IndexFields, entityField: IndexFields): (ParagraphContainer, EntityContainer) -> Double {
        val scoreMap =
                qd.paragraphContainers.mapIndexed { pIndex, pContainer ->
                    val fieldContents = pContainer.doc().load(paragraphField)
                    val fieldQuery = AnalyzerFunctions.createQuery(fieldContents, entityField.field)
                    val searchResult = qd.entitySearcher.search(fieldQuery, 500)

                    val parToEntScores =
                            searchResult.scoreDocs.map { sc -> sc.doc to sc.score.toDouble() }
                    pContainer.docId to parToEntScores.toMap()
                }.toMap()


        val conditionFunction = createConditionFunctionUsingScores(scoreMap)
        return conditionFunction
    }

    fun scoreByFieldContext(qd: QueryData, paragraphField: IndexFields, entityField: IndexFields): (ParagraphContainer, EntityContainer) -> Double {
        val scoreMap =
                qd.paragraphContainers.mapIndexed { pIndex, pContainer ->
                    val fieldContents = pContainer.doc().load(paragraphField)
                    val entities = pContainer.doc().spotlightEntities().split(" ").toSet()
                    val fieldQuery = AnalyzerFunctions.createQuery(fieldContents, entityField.field)
                    val searchResult = qd.contextEntitySearcher.search(fieldQuery, 500)

                    val parToEntScores =
                            searchResult.scoreDocs.map { sc ->
                                val name = qd.contextEntitySearcher.getIndexDoc(sc.doc).name()
                                var score = 0.0f
                                sc.doc to if (name in entities) sc.score.toDouble()  else 0.0
                            }
                    pContainer.docId to parToEntScores.toMap()
                }.toMap()


        val conditionFunction = createConditionFunctionUsingScores(scoreMap)
        return conditionFunction
    }

    fun scoreByEntityContextField(qd: QueryData, paragraphField: IndexFields, entityField: IndexFields): (ParagraphContainer, EntityContainer) -> Double {
        val scoreMap =
                qd.paragraphContainers.mapIndexed { pIndex, pContainer ->
                    val fieldContents = pContainer.doc().load(paragraphField)

                    val fieldQuery = AnalyzerFunctions.createQuery(fieldContents, entityField.field)
                    val searchResult = qd.contextEntitySearcher.search(fieldQuery, 500)

                    val parToEntScores =
                            searchResult.scoreDocs
                                .map { sc ->
                                    val doc = qd.contextEntitySearcher.getIndexDoc(sc.doc)
                                    doc.name().toLowerCase() to sc.score.toDouble() }
                                .groupBy { it.first }
                                .mapValues { it.value.sumByDouble { it.second } }
                    pContainer.docId to parToEntScores.toMap()
                }.toMap()


        return { pContainer: ParagraphContainer, eContainer: EntityContainer ->
            (scoreMap[pContainer.docId]?.get(eContainer.name.toLowerCase()) ?: 0.0)
        }
    }

    fun scoreByEntityContextFieldToParagraph(qd: QueryData, paragraphField: IndexFields, entityField: IndexFields): (ParagraphContainer, EntityContainer) -> Double {
        val scoreMap =
                qd.entityContainers.mapIndexed { pIndex, eContainer ->
                    val q = AnalyzerFunctions.createQuery(eContainer.name, IndexFields.FIELD_NAME.field)
                    val fieldContents = qd.contextEntitySearcher.search(q, 10)
                        .scoreDocs
                        .flatMap { sd ->
                            val doc = qd.contextEntitySearcher.getIndexDoc(sd.doc)
                            doc.load(entityField).split(" ") }
                        .countDuplicates()
                        .toList()
                        .sortedByDescending { it.first }
                        .take(15)
                        .map { it.first }
                        .joinToString(" ")

                    val fieldQuery = AnalyzerFunctions.createQuery(fieldContents, paragraphField.field)
                    val searchResult = qd.paragraphSearcher.search(fieldQuery, 500)

                    val entToParResults =
                            searchResult.scoreDocs
                                .map { sc ->
                                    sc.doc to sc.score.toDouble() }
                    eContainer.name.toLowerCase() to entToParResults.toMap()
                }.toMap()


        return { pContainer: ParagraphContainer, eContainer: EntityContainer ->
            (scoreMap[eContainer.name.toLowerCase()]?.get(pContainer.docId) ?: 0.0)
        }
    }

    fun scoreByEntityContextFieldWeights(qd: QueryData, fields: List<Triple<IndexFields, IndexFields, Double>>): (ParagraphContainer, EntityContainer) -> Double {
        val scoreMap =
                qd.paragraphContainers.mapIndexed { pIndex, pContainer ->
                    val qf = FieldQueryFormatter()
                    fields.forEach { (pField, eField, weight) ->
                        val fieldContents = pContainer.doc().load(pField)
                        val tokens = AnalyzerFunctions.createTokenList(fieldContents)
                        qf.addWeightedQueryTokens(tokens, eField, weight)
                    }

                    val searchResult = qd.contextEntitySearcher.search(qf.createBooleanQuery(), 500)

                    val parToEntScores =
                            searchResult.scoreDocs
                                .map { sc ->
                                    val doc = qd.contextEntitySearcher.getIndexDoc(sc.doc)
                                    doc.name().toLowerCase() to sc.score.toDouble() }
                                .groupBy { it.first }
                                .mapValues { it.value.sumByDouble { it.second } }
                    pContainer.docId to parToEntScores.toMap()
                }.toMap()


        return { pContainer: ParagraphContainer, eContainer: EntityContainer ->
            (scoreMap[pContainer.docId]?.get(eContainer.name.toLowerCase()) ?: 0.0)
        }
    }

    fun getNormalizedTerm(term: String, field: IndexFields, searcher: IndexSearcher): Double {
        val corpusTermFreq = searcher.indexReader.totalTermFreq(Term(field.field, term))
        val corpusTotalFreq = searcher.indexReader.getSumDocFreq(field.field)
        val ratio = corpusTermFreq.toDouble() / corpusTotalFreq.toDouble()
        return ratio
    }

    fun scoreByLikelihood(qd: QueryData, paragraphField: IndexFields, entityField: IndexFields): (ParagraphContainer, EntityContainer) -> Double {
        val memEntities = HashMap<String, Double>()
        val memParagraphs = HashMap<String, Double>()

        val eDocs = qd.entityContainers.map { eContainer ->
            val eDist = eContainer.doc().load(entityField).split(" ")
                .countDuplicates()
                .normalize()

//            val corpusDist = eDist.keys.map { term ->
//                term to memEntities.computeIfAbsent(term) { getNormalizedTerm(term, entityField, qd.entitySearcher) }
//            }.toMap().normalize()

//            val combined = eDist.mapValues { (term, freq) ->
//                0.5 *freq + 0.5 * corpusDist[term]!!
//            }

            eContainer.docId to eDist
        }


        val scoreMap =
                qd.paragraphContainers.mapIndexed { pIndex, pContainer ->
                    val grams =
                            pContainer.doc().load(paragraphField).split(" ")
                                .countDuplicates()
                                .normalize()
//                        .mapValues { (term, freq) ->
//                            val termFreq = memParagraphs.computeIfAbsent(term) { getNormalizedTerm(term, paragraphField, qd.paragraphSearcher)  }
//                            freq * 0.1 + termFreq * 0.9
//                        }

//                    val corpusDist = grams.keys.map { term ->
//                        term to memParagraphs.computeIfAbsent(term) { getNormalizedTerm(term, paragraphField, qd.paragraphSearcher) }
//                    }.toMap().normalize()

//                    val combined = grams.mapValues { (term, freq) ->
//                        0.5 * freq + 0.5 * corpusDist[term]!!
//                    }



                    val eDist = eDocs.map { (eDocId, eGrams) ->
                        val keys = grams.keys + eGrams.keys

                        val pCorpusDist = keys.map { term ->
                            val freq = memParagraphs.computeIfAbsent(term) { getNormalizedTerm(term, paragraphField, qd.paragraphSearcher) }
                            term to freq }
                            .toMap().normalize()

                        val eCorpusDist = keys.map { term ->
                            val freq = memEntities.computeIfAbsent(term) { getNormalizedTerm(term, entityField, qd.entitySearcher) }
                            term to freq }
                            .toMap().normalize()

                        val pDist = keys.map { term -> term to (grams[term] ?: pCorpusDist[term]!!) }
                            .toMap().normalize()

                        val eDist = keys.map { term -> term to (eGrams[term] ?: eCorpusDist[term]!!) }
                            .toMap().normalize()


                        val score = keys.sumByDouble { term ->
//                            val eProb = eGrams[term] ?: 0.5 * memEntities.computeIfAbsent(term) {getNormalizedTerm(term, entityField, qd.entitySearcher) }
//                            val pProb = combined[term] ?: 0.5 * memParagraphs.computeIfAbsent(term) { getNormalizedTerm(term, paragraphField, qd.paragraphSearcher) }
                            val eProb = eDist[term]!!
                            val pProb = pDist[term]!!
//                            log(eProb * pProb).defaultWhenNotFinite(0.0)
                            (eProb * pProb).defaultWhenNotFinite(0.0)
                        }

//                        val score = grams.entries.sumByDouble { (k, v) ->
//                            log((eGrams[k] ?: 0.001) * v).defaultWhenNotFinite(0.0) }
                        eDocId to score

                    }.toMap()
                    pContainer.docId to eDist
                }.toMap()


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
//        qd: QueryData, sf: SharedFeature ->
//        val result = scoreByLikelihood(qd, paragraphField, entityField)
        entityConditionalExpectation(qd, sf, result)
    }

    fun bindEntityContextField(paragraphField: IndexFields, entityField: IndexFields) = {
        qd: QueryData, sf: SharedFeature ->
        val result = scoreByEntityContextField(qd, paragraphField, entityField)
        entityConditionalExpectation(qd, sf, result)
    }

    fun bindEntityContextFieldToPar(paragraphField: IndexFields, entityField: IndexFields) = {
        qd: QueryData, sf: SharedFeature ->
        val result = scoreByEntityContextFieldToParagraph(qd, paragraphField, entityField)
        entityConditionalExpectation(qd, sf, result)
    }

    fun bindEntityContextFieldWeighted(fields: List<Triple<IndexFields, IndexFields, Double>>) = {
        qd: QueryData, sf: SharedFeature ->
        val result = scoreByEntityContextFieldWeights(qd, fields)
        entityConditionalExpectation(qd, sf, result)
    }

//    fun bindCombined(paragraphField: IndexFields, entityField: IndexFields) = {
//        qd: QueryData, sf: SharedFeature ->
//        val result = scoreByEntityContextField(qd, paragraphField, entityField)
//        val result2 = scoreByEntityContextFieldToParagraph(qd, paragraphField, entityField)
//        val combinedFunc = { pContainer: ParagraphContainer, eContainer: EntityContainer ->
//            result(pContainer, eContainer) + result2(pContainer, eContainer)
//        }
//        entityConditionalExpectation(qd, sf, combinedFunc)
//    }




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
                bindScoreByField(paragraphField = FIELD_ENTITIES, entityField = FIELD_OUTLINKS))

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

    fun addPUnigramToContextUnigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_ENTITY_CONTEXT_UNIGRAMS, wt, norm,
                    bindEntityContextField(paragraphField = FIELD_UNIGRAM, entityField = FIELD_UNIGRAM))

    fun addPUnigramToContextBigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_ENTITY_CONTEXT_BIGRAMS, wt, norm,
                    bindEntityContextField(paragraphField = FIELD_BIGRAM, entityField = FIELD_BIGRAM))

    fun addPUnigramToContextBigramToParagraph(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_ENTITY_CONTEXT_BIGRAMS, wt, norm,
                    bindEntityContextFieldToPar(paragraphField = FIELD_BIGRAM, entityField = FIELD_BIGRAM))

//    fun addCombinedContext(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
//            fmt.addFeature3 (FeatureEnum.PFUNCTOR_ENTITY_CONTEXT_BIGRAMS, wt, norm,
//                    bindCombined(paragraphField = FIELD_BIGRAM, entityField = FIELD_BIGRAM))

    fun addPUnigramToContextJointBigram(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_ENTITY_CONTEXT_JOINT_BIGRAMS, wt, norm,
                    bindEntityContextField(paragraphField = FIELD_JOINT_BIGRAMS, entityField = FIELD_BIGRAM))

    fun addPUnigramToContextWindowed(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_ENTITY_CONTEXT_WINDOWED, wt, norm,
                    bindEntityContextField(paragraphField = FIELD_WINDOWED_BIGRAM, entityField = FIELD_WINDOWED_BIGRAM))

    fun addPUnigramToContextEntities(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
            fmt.addFeature3 (FeatureEnum.PFUNCTOR_ENTITY_CONTEXT_ENTITIES, wt, norm,
                    bindEntityContextField(paragraphField = FIELD_UNIGRAM, entityField = FIELD_ENTITIES_UNIGRAMS))
//
//    fun addCombinedContext(fmt: KotlinRanklibFormatter, wt: Double = 1.0, norm: NormType = ZSCORE) =
//            fmt.addFeature3 (FeatureEnum.PFUNCTOR_ENTITY_CONTEXT_ENTITIES, wt, norm,
//                    bindEntityContextFieldWeighted(
//                            listOf(Triple(FIELD_BIGRAM, FIELD_BIGRAM, 1.0),
//                                    Triple(FIELD_WINDOWED_BIGRAM, FIELD_WINDOWED_BIGRAM, 1.0))
//                    ))

}