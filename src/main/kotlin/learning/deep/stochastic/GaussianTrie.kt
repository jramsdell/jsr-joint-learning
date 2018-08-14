package learning.deep.stochastic

import com.google.common.util.concurrent.AtomicDoubleArray
import lucene.Trie
import lucene.containers.*
import utils.misc.toArrayList
import utils.parallel.forEachParallel
import utils.parallel.forEachParallelQ
import utils.parallel.pmap
import utils.stats.defaultWhenNotFinite
import utils.stats.normalize
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue
import kotlin.math.pow

class GaussianTrie(val tries: Trie<QueryContainer>, nFeatures: Int) {
//    var weights = emptyList<Double>()
//    val descent = StochasticDescent(nFeatures, (distanceMapper()), onlyPos = true)
    val descent = StochasticDescent(nFeatures, (normalMapper()), onlyPos = false)
//    val descent = StochasticDescent(nFeatures, this::maxMap)
//    val descent = StochasticDescent(nFeatures, this::getDistances)

    fun myPushFunc(weights: List<Double>, qc: QueryContainer, paragraph: ParagraphContainer,
                   children: List<Pair<QueryContainer, ParagraphContainer>>) {
        children.mapIndexed { index, child ->
            val score = child.second.features.zip(weights).sumByDouble { (f, w) -> f.score * w }
            index to score
        }.toMap()
            .forEach { (i, v) -> paragraph.dist2[i] = v }
    }

    fun myPushFunc2(weights: List<Double>, qc: QueryContainer, paragraph: ParagraphContainer,
                   children: List<Pair<QueryContainer, ParagraphContainer>>) {
        children.mapIndexed { index, child ->
            val score = child.second.features.zip(weights).sumByDouble { (f, w) -> f.score * w }
            index to score
        }.toMap()
            .forEach { (i, v) -> paragraph.dist2[i] = v }
    }

    fun getScoreMap(qc: QueryContainer) {
        val scoreMap = HashMap<String, Double>()
        qc.paragraphs.forEach { paragraph ->
            val score = paragraph.score
            paragraph.doc().unigrams().split(" ")
                .forEach { gram ->
                    if (gram !in scoreMap)
                        scoreMap[gram] = 0.0
                    scoreMap[gram] = score + scoreMap[gram]!!
                }
        }
        qc.termDist = scoreMap.normalize()
    }


    fun assignScoreMaps(): ArrayList<() -> Unit> {
        val functions = ArrayList<() -> Unit>()
        tries.traverse { path, curNodeKey, d, children ->
            functions += { getScoreMap(d) }
        }

        return functions
    }

    fun pushForward2(pushFunc: (List<Double>, QueryContainer, ParagraphContainer, List<Pair<QueryContainer, ParagraphContainer>>) -> Unit): ArrayList<(List<Double>) -> Unit> {
        val functions = ArrayList<(List<Double>) -> Unit>()
        tries.traverse { path, curNodeKey, d, children ->
            if (children.isEmpty()) {
                return@traverse
            }

            d.paragraphs.forEachIndexed { pIndex, paragraph ->
                val pChildren = children.mapNotNull {it.data?.let { qc -> qc to qc.paragraphs[pIndex] } }
                val f = { weights: List<Double> ->
                    pushFunc(weights, d, paragraph, pChildren)
                }
                functions.add(f)
            }
        }

        return functions
    }

    fun marginalFunction() {
        tries.traverse { path, curNodeKey, d, children ->
            if (children.isEmpty())
                return@traverse
            val firstPath = !path.contains("/")
            val c = children.filter { it.data != null }

            d.paragraphs.forEachIndexed { pIndex, paragraph ->
                val score = if (firstPath) { paragraph.isRelevant.toDouble() } else paragraph.score
                paragraph.dist2.forEach { i, d ->  c[i].data!!.paragraphs[pIndex].score = d * score }
            }
        }
    }

    fun getDiscrep(): ArrayList<() -> List<Double>> {
        val functions = ArrayList<() -> List<Double>>()
        tries.traverse { path, curNodeKey, d, children ->
            if (children.isEmpty())
                return@traverse

            val f = {
                d.paragraphs.withIndex()
                    .filter { (_, p) -> p.isRelevant > 0 }
                    .map { (pIndex, paragraph) ->
                        val childrenPar = children.mapNotNull { it.data?.paragraphs?.get(pIndex) }
                        val relChild = childrenPar.filter { it.isRelevant > 0 }
                        if (childrenPar.size != children.size) {
                            println("Problem: ${paragraph.name} : ${d.query}")
                            println(children.size)
                            println(childrenPar.size)
                        }
                        if (relChild.isEmpty() ) 0.0 else
                            relChild.first().score * paragraph.score
//                        val parScore = childrenPar.map { it.score }.average()
//                        parScore
//                    (1 / (paragraph.score - parScore).absoluteValue).defaultWhenNotFinite(0.0)
//                    (parScore.max()!! - parScore.min()!!).absoluteValue
//                      childrenPar.map { child ->
//                        (paragraph.score - child.score).absoluteValue }
//                        .max()!!
                    }
            }
            functions.add(f)
        }

        return functions
    }

    fun marginalScoreMap() {
        tries.traverse { path, curNodeKey, d, children ->
            if (children.isEmpty())
                return@traverse
            val firstPath = !path.contains("/")
            val c = children.filter { it.data != null }

            d.paragraphs.withIndex().forEachParallel { (pIndex, paragraph) ->
                val score = if (firstPath) { paragraph.isRelevant.toDouble() } else paragraph.score
                val grams = paragraph.doc().unigrams().split(" ")
                val childScores = c.mapIndexed { index, trie ->
                    val qc = trie.data!!
                    val childScore = grams.sumByDouble { gram -> qc.termDist[gram] ?: 0.0 }
                    index to childScore
                }.toMap().normalize()

                c.forEachIndexed { index, trie ->
                    trie.data!!.paragraphs[pIndex].score = childScores[index]!! * score
                }
            }
        }
    }

    fun assignBest() {
        val best = HashMap<Int, Double>()
        tries.traverse { path, curNodeKey, d, children ->
            if (children.isNotEmpty())
                return@traverse

            d.paragraphs.forEachIndexed { index, paragraph ->
                if (index !in best)
                    best[index] = 0.0
                best[index] = Math.max(paragraph.score, best[index]!!)
            }
        }

        tries.traverse { path, curNodeKey, d, children ->
            if (children.isNotEmpty())
                return@traverse

            d.paragraphs.forEachIndexed { index, paragraph ->
                if (paragraph.score != best[index]!!) {
                    paragraph.score = 0.0
                }
            }
        }

    }


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
        val relIndex = HashSet<Int>()
        children.forEach { child ->
            child.paragraphs.forEachIndexed { index, paragraph ->
                if (paragraph.isRelevant > 0.0) {
                    relIndex.add(index)
                }
            }
        }


        val fMap = children.map { it.paragraphs.filterIndexed { index, p ->
            index in relIndex
        }.map { it.features.map { it.score } } }

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



    fun runMap(qcAcessor: (QueryContainer) -> List<DocContainer<*>>, nRelAccessor: (QueryContainer) -> Double): ArrayList<() -> Double> {
        val functions = ArrayList<() -> Double>()

        tries.traverse { path, curNodeKey, d, children ->
            val f = {
                val nRel = nRelAccessor(d)
                var totalRight = 0.0
                var apScore = 0.0
                qcAcessor(d)
                    .sortedByDescending { it.score }
                    .forEachIndexed { rank, p ->
                        totalRight += p.isRelevant
                        if (p.isRelevant > 0.0) {
                            apScore += totalRight / (rank + 1).toDouble()
                        }
                    }
                apScore / Math.max(nRel, 1.0)
            }
            functions.add(f)
        }

        return functions
    }

    fun runMapAtLeaves(): ArrayList<() -> Double> {
        val functions = ArrayList<() -> Double>()

        tries.traverse { path, curNodeKey, d, children ->
            if (children.isNotEmpty())
                return@traverse

            val f = {
                val nRel = d.nRel
                var totalRight = 0.0
                var apScore = 0.0
                d.paragraphs.sortedByDescending { it.score }
                    .forEachIndexed { rank, p ->
                        totalRight += p.isRelevant
                        if (p.isRelevant > 0.0) {
                            apScore += totalRight / (rank + 1).toDouble()
                        }
                    }
                apScore / Math.max(nRel, 1.0)
            }
            functions.add(f)
        }

        return functions
    }

    fun maxScore(): ArrayList<() -> Unit> {
        val functions = ArrayList<() -> Unit>()
        tries.children.forEach { page ->
            val f = {
                page.value.traverseBottomUp { path, curNodeKey, d, children ->

                    if (children.isNotEmpty()) {
                        d.paragraphs.withIndex().forEach { indexer ->
                            val pIndex = indexer.index
                            val paragraph = indexer.value
                            val nScore = Math.max(paragraph.score, 0.001)
                            paragraph.score = nScore * children.fold(1.0) { acc, child ->
                                val res = Math.max((child.data?.let { qc -> qc.paragraphs[pIndex].score } ?: 0.001), 0.001)
                                acc * res
                            }
                        }
                    }
                }
            }
            functions.add(f)
        }
        return functions
    }

    fun updateWeights(qcAccessor: (QueryContainer) -> List<DocContainer<*>>): ArrayList<(List<Double>) -> Unit> {
        val functions = ArrayList<(List<Double>) -> Unit>()

        tries.traverse { path, curNodeKey, d, children ->
            val f = { weights: List<Double> ->
                qcAccessor(d).forEach { paragraph ->
                    paragraph.score = paragraph.features.zip(weights).sumByDouble { (f, w) -> f.score * w }
                }
            }
            functions.add(f)
        }
        return functions
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



    fun bindModule2(): ArrayList<(List<Double>) -> Double> {
        val functions = ArrayList<(List<Double>) -> Double>()
        tries.traverse { path, curNodeKey, d, children ->
            functions.add(meanMap(d))
        }
        return functions
    }

//    fun distanceMapper(): (List<Double>) -> Double {
//        val updateFunction = updateWeights()
////        val addFunction = maxScore()
//        val mapFunction = runMapAtLeaves()
//
//        val f = { weights: List<Double> ->
//            updateFunction.forEachParallel { it(weights) }
////            assignBest()
//            mapFunction.pmap { it() }.average()
//        }
//
//        return f
//    }

    fun normalMapper(): (List<Double>) -> Double {
        val accessor = { qc: QueryContainer -> qc.paragraphs }
        val nRelAccessor = { qc: QueryContainer -> qc.nRel }
        val updateFunction = updateWeights(accessor)
        val mapFunction2 = runMap(accessor, nRelAccessor)

        return { weights: List<Double> ->
            updateFunction.forEachParallel { it(weights) }
//            val res2 = Math.log(mapFunction2.pmap { it() }.average())
//            res1 + res2
            mapFunction2.pmap { it() }.average()
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