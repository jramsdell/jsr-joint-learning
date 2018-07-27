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

typealias indexProbMap = Map<Int, Map<Int, Double>>

class JointDistribution(val parToEnt: indexProbMap, val entToPar: indexProbMap,
                        val secToPar: indexProbMap = emptyMap(),
                        val parToSec: indexProbMap = emptyMap(),
                        val entToContextEnt: indexProbMap = emptyMap(),
                        val contextEntToEnt: indexProbMap = emptyMap()) {
    companion object {



        fun createJointDistribution(qd: QueryData): JointDistribution {

            val entityContainers = qd.entityContainers
            val paragraphContainers = qd.paragraphContainers
            val sectionContainers = qd.sectionContainers
            val contextEntityContainers = qd.contextEntityContainers

            val entIdMap = entityContainers.mapIndexed { index, container -> container.name to index }.toMap()
            val parIdMap = paragraphContainers.mapIndexed { index, container -> container.name to index }.toMap()
            val inverseMaps =
                    entityContainers.mapIndexed { index, _ -> index to HashMap<Int, Double>() }.toMap()

            val paragraphInverseMap =
                    paragraphContainers.mapIndexed { index, _ -> index to HashMap<Int, Double>() }.toMap()

            val contextInverseMap =
                    contextEntityContainers.mapIndexed { index, _ -> index to HashMap<Int, Double>() }.toMap()

            val parFreqMap = HashMap<Int, Map<Int, Double>>()
            val secToParMap = HashMap<Int, Map<Int, Double>>()
            val entToContextEntMap = HashMap<Int, Map<Int, Double>>()



            paragraphContainers.forEachIndexed { index, container ->
                val entities = container.doc().spotlightEntities().split(" ")
                val baseFreqs =
                        entities.mapNotNull { id -> entIdMap[id] }.map { it to 1.0 }

                val combined = baseFreqs.toMap()
                combined.forEach { (entityIndex, freq) ->
                    inverseMaps[entityIndex]!!.merge(index, freq, ::sum) }

                parFreqMap[index] = combined
            }

            sectionContainers.forEachIndexed { index, container ->
                val paragraphs = container.doc().paragraphs().split(" ")
                val baseFreqs =
                        paragraphs.mapNotNull { id -> parIdMap[id]  }.map { it to 1.0 }

                val combined = baseFreqs.toMap()
                combined.forEach { (parIndex, freq) ->
                    paragraphInverseMap[parIndex]!!.merge(index, freq, ::sum)
                }

                secToParMap[index] = combined
            }

            entityContainers.forEachIndexed { index, container ->

                val baseFreqs =
                        qd.contextEntityContainers.mapNotNull { cContainer ->
                            if (cContainer.name.toLowerCase() == container.name.toLowerCase())
                                cContainer.index to 1.0 else null }

                val combined = baseFreqs.toMap()
                combined.forEach { (contIndex, freq) ->
                    contextInverseMap[contIndex]!!.merge(index, freq, ::sum)
                }

                entToContextEntMap[index] = combined
            }


            val entFreqMap = inverseMaps.mapValues { it.value }
            val parToSecMap = paragraphInverseMap.mapValues { it.value }
            val contToEntMap = contextInverseMap.mapValues { it.value }



            return JointDistribution(
                    entToPar = entFreqMap,
                    parToEnt = parFreqMap as Map<Int, Map<Int, Double>>,
                    parToSec = parToSecMap,
                    secToPar = secToParMap,
                    entToContextEnt = entToContextEntMap,
                    contextEntToEnt = contToEntMap)
        }

        fun createNew(qd: QueryData): JointDistribution {
            val base = createJointDistribution(qd)

//            val scorer1 = SubObjectFeatures.scoreByField(qd, IndexFields.FIELD_UNIGRAM, IndexFields.FIELD_INLINKS_UNIGRAMS)
//            val scorer2 = SubObjectFeatures.scoreByField(qd, IndexFields.FIELD_UNIGRAM, IndexFields.FIELD_OUTLINKS_UNIGRAMS)
            val scorer3 = SubObjectFeatures.scoreByEntityContextField(qd, IndexFields.FIELD_BIGRAM, IndexFields.FIELD_BIGRAM)
//            val scorer4 = SubObjectFeatures.scoreByFieldContext(qd, IndexFields.FIELD_BIGRAM, IndexFields.FIELD_BIGRAM)
//            val scorer4 = SubObjectFeatures.scoreByEntityContextField(qd, IndexFields.FIELD_WINDOWED_BIGRAM, IndexFields.FIELD_WINDOWED_BIGRAM)
//            val scorerEntity = SubObjectFeatures.scoreByEntityContextFieldToParagraph(qd, IndexFields.FIELD_BIGRAM, IndexFields.FIELD_BIGRAM)
//            val finalScorer = {pContainer: ParagraphContainer, eContainer: EntityContainer ->
//                scorer(pContainer, eContainer) + scorerEntity(pContainer, eContainer)
//            }

//            val weights = listOf(
//                    0.3111721925625245 ,-0.18064698522369768 ,0.500945353849402 ,0.007235468364375822
//
//            )

//            val combined = { pContainer: ParagraphContainer, eContainer: EntityContainer ->
//                scorer1(pContainer, eContainer) * weights[0] +
//                        scorer2(pContainer, eContainer) * weights[1] +
//                        scorer3(pContainer, eContainer) * weights[2] +
//                        scorer4(pContainer, eContainer) * weights[3]
//            }


            val inverseMaps = qd.entityContainers.map { it.index to HashMap<Int, Double>() }
                .toMap()
            val pDist = SubObjectFeatures.getParagraphConditionMap(qd, scorer3, inverseMaps)
//            val eDist = qd.entityContainers.map { eContainer ->
//                val dist = qd.paragraphContainers.map { pContainer ->
//                    pContainer.index to scorerEntity(pContainer, eContainer) }
//                eContainer.index to dist.toMap() }
//                .toMap()



            return JointDistribution(
                    secToPar = base.secToPar,
                    parToSec = base.parToSec,
                    parToEnt = pDist,
                    entToPar = inverseMaps
            )


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


}