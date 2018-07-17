package lucene.containers

import features.subobject.SubObjectFeatures
import lucene.indexers.IndexFields
import lucene.indexers.getList
import utils.AnalyzerFunctions
import utils.misc.groupOfSets
import utils.misc.identity
import utils.misc.mapOfLists
import utils.misc.toHashMap
import utils.stats.countDuplicates
import utils.stats.defaultWhenNotFinite
import utils.stats.normalize
import java.lang.Double.sum
import java.lang.Math.log
import kotlin.math.pow

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


class JointDistribution(val parToEnt: Map<Int, Map<Int, Double>>, val entToPar: Map<Int, Map<Int, Double>>,
                        val secToPar: Map<Int, Map<Int, Double>> = emptyMap(),
                        val parToSec: Map<Int, Map<Int, Double>> = emptyMap(),
                        val secToEnt: Map<Int, Map<Int, Double>> = emptyMap(),
                        val entToSec: Map<Int, Map<Int, Double>> = emptyMap()) {
    companion object {

        fun createExperimental(qd: QueryData): JointDistribution {

            val secToPar = HashMap<Int, Map<Int, Double>>()
            val parToSec = qd.paragraphContainers.mapIndexed { index, pContainer ->
                index to HashMap<Int, Double>() }
                .toMap()
            val parToEnt = HashMap<Int, Map<Int, Double>>()
            val entToPar = qd.entityContainers.mapIndexed { index, eContainer ->
                index to HashMap<Int, Double>() }
                .toMap()

            val totalParagraphs = qd.paragraphContainers.map { pContainer ->
                val text = pContainer.doc().text()
                val dist = AnalyzerFunctions.createTokenList(text, AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)
                    .countDuplicates()
                pContainer.index to dist
            }

            val totalEntities = qd.entityContainers.map { eContainer ->
//                val inlinks = eContainer.doc().inlinks().split(" ")
//                val outlinks = eContainer.doc().outlinks().split(" ")
                val outlinks = eContainer.doc().unigrams().split(" ")
                val dist = (outlinks)
                    .countDuplicates()
                    .toMap()
                eContainer.index to dist
            }

            qd.sectionContainers.forEach { section ->
                val pSet = section.doc().paragraphs().split(" ").toSet()
                val children = qd.paragraphContainers.filter { it.name in pSet }
                    .map { paragraph ->
                        val text = paragraph.doc().text()
                        val tokens = AnalyzerFunctions.createTokenList(text, AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)
                            .toList()
                        paragraph.index to tokens
                    }


                val sTotal = children.flatMap { it.second }
                    .countDuplicates()
                    .toList()
                    .sortedByDescending { it.second }
                    .take(15)
                    .toMap()


                val sCond = totalParagraphs.map { (child, dist) ->
                    child to dist.entries.sumBy { (token, freq) -> (sTotal[token] ?: 0) * freq }.toDouble() }
                    .toMap()


                secToPar[section.index] = sCond.normalize()
                sCond.forEach { (pIndex, freq) -> parToSec[pIndex]!!.merge(section.index, freq, ::sum)  }
            }

            qd.paragraphContainers.forEach { paragraph ->
                val eSet = paragraph.doc().entities().split(" ").toSet()
                val children = qd.entityContainers.filter { it.name in eSet }
                    .map { totalEntities[it.index]!! }


                val sTotal = children.flatMap { it.second.map { (link, freq) -> link to freq } }
                    .groupBy { it.first }
                    .mapValues { it.value.sumBy { it.second } }
                    .toList()
                    .sortedByDescending { it.second }
                    .take(15)
                    .toMap()

                val sCond = totalEntities.map { (child, dist) ->
                    child to dist.entries.sumBy { (token, freq) -> (sTotal[token] ?: 0) * freq }.toDouble() }
                    .toMap()

                parToEnt[paragraph.index] = sCond.normalize()
                sCond.forEach { (eIndex: Int, freq: Double) -> entToPar[eIndex]!!.merge(paragraph.index, freq, ::sum)  }
            }


            return JointDistribution(
                    parToSec = parToSec.map { it.key to it.value.normalize() }.toMap(),
                    secToPar = secToPar,
                    secToEnt = qd.sectionContainers.map { it.index to emptyMap<Int, Double>() }.toMap(),
                    entToSec = qd.entityContainers.map { it.index to emptyMap<Int, Double>() }.toMap(),
                    parToEnt = parToEnt,
                    entToPar = entToPar.map { it.key to it.value.normalize() }.toMap()
            )
        }


        fun createJointDistribution(qd: QueryData): JointDistribution {

            val entityContainers = qd.entityContainers
            val paragraphContainers = qd.paragraphContainers
            val sectionContainers = qd.sectionContainers

            val entIdMap = entityContainers.mapIndexed { index, container -> container.name to index }.toMap()
            val parIdMap = paragraphContainers.mapIndexed { index, container -> container.name to index }.toMap()
            val inverseMaps =
                    entityContainers.mapIndexed { index, _ -> index to HashMap<Int, Double>() }
                        .toMap()

            val paragraphInverseMap =
                    paragraphContainers.mapIndexed { index, _ -> index to HashMap<Int, Double>() }
                        .toMap()

            val parFreqMap = HashMap<Int, Map<Int, Double>>()
            val secToParMap = HashMap<Int, Map<Int, Double>>()
            val smoothingFactor = 0.0

//            val (ptoE, etoP) = getSubMap(paragraphContainers, entityContainers)


            paragraphContainers.forEachIndexed { index, container ->
//                val entities = container.doc().neighborEntities().split(" ")
                val entities = container.doc().spotlightEntities().split(" ")
//                val uniform = entityContainers.mapIndexed { i, _ -> i to 0.1  }

                val baseFreqs =
                        entities.mapNotNull { id -> entIdMap[id] }
                            .map { it to 1.0 }
//                            .normalize()
//                            .map { it.key to it.value * (1.0 - smoothingFactor) } // Smoothing part

//                val combined = (baseFreqs + uniform).groupBy { it.first }.mapValues { it.value.sumByDouble { it.second } }
//                    .toMap()
                val combined = baseFreqs.toMap().normalize()

//                val otherFreqs =
//                        entIdMap.values.map { it to (1.0 / entIdMap.size.toDouble()) * (smoothingFactor) }

                // Since frequencies can contain duplicates, merge them all together so there's exactly one freq
                // for each of the entities in our pool.
//                val freqMap = (baseFreqs + otherFreqs)
//                    .groupingBy { (entityId, _) -> entityId }
//                    .fold(0.0) { acc, (_, freq) -> acc + freq }

                combined.forEach { (entityIndex, freq) ->
                    inverseMaps[entityIndex]!!.merge(index, freq, ::sum)
                }


                parFreqMap[index] = combined
            }

            sectionContainers.forEachIndexed { index, container ->
                val paragraphs = container.doc().paragraphs().split(" ")
//                val uniform = paragraphContainers.mapIndexed { i, _ -> i to 0.1  }

                val baseFreqs =
                        paragraphs.mapNotNull { id -> parIdMap[id]  }
                            .map { it to 1.0 }

//                val combined = (baseFreqs + uniform).groupBy { it.first }.mapValues { it.value.sumByDouble { it.second } }
//                    .toMap()
                val combined = baseFreqs.toMap().normalize()

                combined.forEach { (parIndex, freq) ->
                    paragraphInverseMap[parIndex]!!.merge(index, freq, ::sum)
                }

                secToParMap[index] = combined
            }



            val entFreqMap = inverseMaps.mapValues { it.value.normalize() }
            val parToSecMap = paragraphInverseMap.mapValues { it.value.normalize() }

//            val secToEnt = secToParMap.flatMap { (secIndex, secMapToPar) ->
//                secMapToPar.flatMap { (parIndex, parFreq) ->
//                    parFreqMap[parIndex]!!.map { (entityIndex, entFreq) ->
//                        secIndex to (entityIndex to entFreq * parFreq)
//                    }
//                }
//            }.groupBy { (secIndex, _) -> secIndex }
//                .mapValues { it.value.map { it.second }.toMap() }
//
//            val entToSec = inverseMaps.flatMap { (entityIndex, entToParMap) ->
//                entToParMap.flatMap { (parIndex, parFreq) ->
//                    parToSecMap[parIndex]!!.map { (secIndex, secFreq) ->
//                        entityIndex to (secIndex to secFreq * parFreq)
//                    }
//                }
//            }.groupBy { (entIndex, _) -> entIndex }
//                .mapValues { it.value.map { it.second }.toMap() }





            return JointDistribution(
                    entToPar = entFreqMap,
                    parToEnt = parFreqMap as Map<Int, Map<Int, Double>>,
                    parToSec = parToSecMap,
                    secToPar = secToParMap
//                    secToEnt = secToEnt,
//                    entToSec = entToSec
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

        fun createMonoidDistribution(query: String, qd: QueryData): JointDistribution {
            val tokens = AnalyzerFunctions.createTokenList(query, AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED, useFiltering = true)
//                .flatMap { it.windowed(2) }
                .countDuplicates()

            val secToPar = qd.sectionContainers.map { it.index to emptyMap<Int, Double>() }.toHashMap()
            val parToEnt = qd.paragraphContainers.map { it.index to emptyMap<Int, Double>() }.toHashMap()
            val parToSec = qd.paragraphContainers.map { it.index to HashMap<Int, Double>() }.toMap()
            val entToPar = qd.entityContainers.map { it.index to HashMap<Int, Double>() }.toMap()


            qd.sectionContainers.forEach { sContainer ->
                val newMap = tokens.mapNotNull { (token, freq) ->
                    sContainer.dist[token]?.times(freq)?.to(token) }


                val condMap = qd.paragraphContainers.map { pContainer ->
//                    val score1 = newMap.sumByDouble { (freq, unigram) ->
//                        pContainer.dist[unigram]?.times(freq) ?: 0.1 }

                    val pnorm = pContainer.dist.normalize()
                    val snorm = sContainer.dist.normalize()

                    val score2 = snorm.entries.sumByDouble { (unigram, freq) ->
                        log((pContainer.dist[unigram] ?: 0.1) * freq ).defaultWhenNotFinite(0.0)
//                        (pnorm[unigram] ?: 0.0) * freq
                    }

//                    val self =  Math.sqrt( snorm.values.sumByDouble { it.pow(2.0) })
//                    val self2 = Math.sqrt( pnorm.values.sumByDouble { it.pow(2.0) })
//                    val score = score2 / (self * self2)
                    val score = score2

                    parToSec[pContainer.index]!!.merge(sContainer.index, score, ::sum)
                    pContainer.index to score }
                    .toMap()

                secToPar[sContainer.index] = condMap
            }



            return JointDistribution(parToEnt = parToEnt, entToPar = entToPar, secToPar = secToPar,
                    parToSec = parToSec)
        }
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


fun analyzeStuff(qd: QueryData) {
    val q = "diet"
    val tokens = AnalyzerFunctions.createTokenList(q, AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED, useFiltering = true)
        .countDuplicates()
        .toMap()
        .mapValues { it.value.toDouble() }

    fun getMostCommon(children: List<ParagraphContainer>): Map<String, Double> {
        val all = children.flatMap { it.dist.keys }.toSet()
        return all.map { term ->
            var timesSeen = children.count { child -> term in child.dist }
            term to timesSeen
        }.toMap().normalize()
    }

    qd.sectionContainers.forEach { sContainer ->
        val children = sContainer.doc().paragraphs().split(" ").toSet()
            .run { qd.paragraphContainers.filter { it.name in this } }

        val mCommon = getMostCommon(children)

        val sDist = sContainer.dist.normalize()
        val scores = children.map { pContainer ->
            val likelihood = pContainer.dist.entries.sumByDouble { (k,v) ->
                log(sContainer.dist[k]!! * v).defaultWhenNotFinite(0.0)
            }

            val rareness = children.filter { it.name != pContainer.name }
                .sumByDouble { other ->
                    val oDist = other.dist.normalize()
                    pContainer.dist.normalize().entries.sumByDouble { (k, v) ->
                        log((oDist[k] ?: 0.1) * v * sDist[k]!!).defaultWhenNotFinite(0.0)
//                    }
                    }}
//                } / Math.max(1.0, other.dist.values.sum())
//                } / sContainer.dist.values.sum()

//            pContainer to likelihood / rareness.defaultWhenNotFinite(0.001) }
        pContainer to -rareness }
            .sortedByDescending { it.second }

        println("====${sContainer.name}====")
        val scoreDist = scores.mapIndexed { index, (_, score) -> index to score}
            .toMap()
            .normalize()

        val lengthDist = scores.mapIndexed { index, (child, _) ->
            index to child.doc().text().length.toDouble() }
            .toMap()
            .normalize()

        scores.forEachIndexed { index, (child, score) ->
//            val t = AnalyzerFunctions.createTokenList(child.doc().text(), AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)
//                .countDuplicates()
//                .toSortedMap()

//            println("$score [${scoreDist[index]}] : ${child.doc().text().length} [${lengthDist[index]}] :  ${Math.exp(score)} : ${child.doc().text()}")
            val others = children.filter { it.name != child.name }
            val inCommon = child.dist.entries.sumByDouble { (k,v) ->
                others.sumByDouble { other -> (other.dist[k] ?: 0.0) * v } }

            val inCommon2 = child.dist.entries.sumByDouble { (k, v) ->
                (mCommon[k] ?: 0.0) * v
            }
            println("$inCommon : $inCommon2 : $score [${scoreDist[index]}] : ${child.doc().text().length} [${lengthDist[index]}] :  ${Math.exp(score)} : ${child.doc().text()}")
        }
        println()


    }


//    qd.sectionContainers.map { sContainer ->
//
//        val self =  Math.sqrt( sContainer.dist.entries.sumByDouble { (k,v) ->
//            log((v * (tokens[k] ?: 0.5)).pow(2.0)) }
//        )
//
//        val result = qd.paragraphContainers.map { pContainer ->
//            val self2 = Math.sqrt( pContainer.dist.entries.sumByDouble { (k,v) ->
//                log((v * (tokens[k] ?: 0.5)).pow(2.0)) })
//
//            val goodness = tokens.entries.sumByDouble { (k,v) ->
//                ((pContainer.dist[k] ?: 0.1) * (sContainer.dist[k] ?: 0.1) * v).defaultWhenNotFinite(0.0)
//            } / (pContainer.dist.size.toDouble() * sContainer.dist.size)
//
//            val jointScore = sContainer.dist.entries
//                .sumByDouble { (k,v) ->
//                    log((pContainer.dist[k]?: 0.1) * v).defaultWhenNotFinite(0.0)
//                } / (pContainer.dist.size.toDouble() )
////            val normed = (jointScore/(self * self2)).defaultWhenNotFinite(0.0)
//            val normed = jointScore * (-log(goodness)).defaultWhenNotFinite(1.0)
//            pContainer to normed
//        }.maxBy { it.second }!!
////            ?.run { println("${sContainer.name} (${this.second}) : ${this.first.doc().text()}")}
//        sContainer to result
//    }.sortedBy { it.second.second }
//        .forEach { (sContainer, result) ->
//            println("${sContainer.name} (${result.second}) : ${result.first.doc().text()}")
//            println()
//        }
}