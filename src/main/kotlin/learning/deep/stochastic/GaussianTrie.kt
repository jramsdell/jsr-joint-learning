package learning.deep.stochastic

import com.google.common.util.concurrent.AtomicDoubleArray
import lucene.Trie
import lucene.containers.ParagraphContainer
import lucene.containers.QueryContainer
import utils.misc.toArrayList
import utils.parallel.pmap
import utils.stats.normalize
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue
import kotlin.math.pow


class GaussianTrie(val tries: Trie<QueryContainer>, nFeatures: Int) {
//    var weights = emptyList<Double>()
    val descent = StochasticDescent(nFeatures, (distanceMapper()), onlyPos = true)
//    val descent = StochasticDescent(nFeatures, this::getDistances)


    fun getMAP(weights: List<Double>): Double {
        val maps = ConcurrentHashMap<String, Double>()
        tries.traverse { path, curNodeKey, d, children ->
            var apScore = 0.0
            var totalRight = 0.0

            d.paragraphs.forEach { p ->
                p.features.zip(weights).forEach { (f, w) -> f.weight = w }
                p.rescore()
            }

            d.paragraphs.sortedByDescending { it.score }
                .forEachIndexed { rank, p ->
                    totalRight += p.isRelevant
                    if (p.isRelevant > 0.0) {
                        apScore += totalRight / (rank + 1).toDouble()
                    }
                }
            maps[path.joinToString("/")] = apScore / Math.max(1.0, d.nRel)
        }

        return maps.values.average()
    }

    fun meanAverage(children: List<QueryContainer>): (List<Double>) -> Double {
        val fMap = children.map { it.paragraphs.map { it.features.map { it.score } } }

        return { weights: List<Double> ->
            val av = (0 until fMap.first().size).map { 0.0 }.toArrayList()
            val results = ArrayList<List<Double>>()
            fMap.forEach { childParagraphs ->
                val fResult = childParagraphs.mapIndexed { index, paragraph ->
                    val result = paragraph.zip(weights).sumByDouble { (w, s) -> w * s }
                    result
                }
                fResult.forEachIndexed { index, d -> av[index] += d  }
                results.add(fResult)

            }

//            av.map { it / children.size }.normalize().forEachIndexed { index, d -> av[index] = d  }
            av.forEachIndexed { index, d -> av[index] = av[index] / children.size  }

            var total = 0.0
            results.forEach { result ->
                result.forEachIndexed { index, d -> total += (d - av[index]).absoluteValue  }
            }
            total / children.size
        }

    }

    fun meanMap(qc: QueryContainer): (List<Double>) -> Double {
        val fMap = qc.paragraphs.map { it.isRelevant to it.features.map { it.score } }
        val nRel = qc.nRel


        return { weights: List<Double> ->
            var apScore = 0.0
            var totalRight = 0.0
            val results = fMap.map { (rel, feats) -> rel.toDouble() to feats.zip(weights).sumByDouble { (f, w) -> f * w } }
                .sortedByDescending { it.second }
                .forEachIndexed { rank, p ->
                    totalRight += p.first
                    if (p.first > 0.0) {
                        apScore += totalRight / (rank + 1).toDouble()
                    }
                }
            apScore / Math.max(nRel, 1.0)
        }

    }

    fun bindModule(): ArrayList<(List<Double>) -> Double> {
        val functions = ArrayList<(List<Double>) -> Double>()
        tries.traverse { path, curNodeKey, d, children ->
            if (children.size > 1) {
                val c = children.filter { it.data != null }
                    .map { it.data!! }
                if (c.isNotEmpty())
                    functions.add(meanAverage(c))
            }
        }

        return functions
    }

//    fun bindModule2(): List<(List<Double>) -> List<Double>> {
//        val functions = ArrayList<(List<Double>) -> Double>()
//        tries.traverse { path, curNodeKey, d, children ->
//            functions.add(meanMap(d))
//        }
//
//
//        val chunks = functions.chunked(50)
//
//        return chunks.map { chunk ->
//            { weights: List<Double> -> chunk.map { it(weights) }}
//        }
//    }

    fun bindModule2(): ArrayList<(List<Double>) -> Double> {
        val functions = ArrayList<(List<Double>) -> Double>()
        tries.traverse { path, curNodeKey, d, children ->
            functions.add(meanMap(d))
        }
        return functions
    }

    fun distanceMapper(): (List<Double>) -> Double {
        val module = bindModule()
        val module2 = bindModule2()
        return { weights: List<Double> ->
            val myRes = module.pmap { f -> f(weights) }.average()
//            val myRes2 = module2.pmap { f -> f(weights) }.average()
//            val otherRes = getMAP(weights)
//            println("Mine: $myRes / Other: $otherRes")
            myRes
        }

    }

    fun getDistances(weights: List<Double>): Double {
        val maps = ConcurrentHashMap<String, Double>()
        tries.traverseBottomUp { path, curNodeKey, d, children ->
            var apScore = 0.0
            var totalRight = 0.0

            d.paragraphs.forEach { p ->
                p.features.zip(weights).forEach { (f, w) -> f.weight = w }
                p.rescore()
            }

            if (children.isNotEmpty()) {
                val scores = (0 until d.paragraphs.size).map { 0.0 }.toArrayList()
                children.forEach { child ->

                    if (child.data != null) {
                        child.data!!.paragraphs.forEachIndexed { index, p -> scores[index] += p.score }
                    }

                }

                scores.forEachIndexed { index, score -> scores[index] = score / children.size.toDouble() }
                var total = 0.0

                children.forEach { child ->
                    if (child.data != null) {
                        child.data!!.paragraphs.forEachIndexed { index, p -> total += (scores[index] - p.score).pow(2.0) }
                    }
                }
                maps[path.joinToString("/")] = total / children.size

            }
        }

        return maps.values.average()
    }

}