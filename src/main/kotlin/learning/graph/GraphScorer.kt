package learning.graph

import org.jgrapht.alg.util.UnionFind
import org.jgrapht.traverse.ClosestFirstIterator
import org.jgrapht.traverse.RandomWalkIterator
import org.jgrapht.*
import org.jgrapht.alg.util.UnorderedPair
import org.jgrapht.graph.*
import utils.misc.*
import utils.stats.*
import kotlin.math.absoluteValue


class GraphScorer(val g: Graph<String, DefaultWeightedEdge>) {
    val hubs = HashMap<String, Double>()
    val edgeHubs = HashMap<Pair<String, String>, Double>()
    var hubDistances = HashMap<String, HashMap<String, Double>>()
    val nodeDists = HashMap<String, Pair<String, Double>>()
    var otherDists = HashMap<Pair<String, String>, Pair<Pair<String, String>, Double>>()
    val algorithms = GraphScorerAlgorithms(this)
    var finalNodeDists: HashMap<StringPair, HashMap<StringPair, Double>> = HashMap()

    fun diffusionWalk() {
        val nodes = g.vertexSet().toList()
        val counter = HashMap<String, Double>()

        (0 until 500).forEach {
            val walk = RandomWalkIterator(g, nodes.sampleRandom(), true,
                    1000)

            walk.forEachRemaining { node ->
                if (node !in counter) {
                    counter[node] = 0.0
                }
                counter[node] = counter[node]!! + 1.0
            }
        }

        counter.normalize()
            .entries
            .sortedByDescending { it.value }
            .take(50)
            .forEach { (hub, weight) ->
                hubs[hub] = weight
            }

        println(hubs)
    }

    fun getFirst(walker: ClosestFirstIterator<String, DefaultWeightedEdge>): Pair<Pair<String, String>, Double> {
        (0 until 500).forEach {
            if (walker.hasNext()) {
                val nextNode = walker.next()
                val nextEdge = walker.getSpanningTreeEdge(nextNode)
                val dist = walker.getShortestPathLength(nextNode)

                if (nextEdge != null) {
                    val key = g.getEdgeSource(nextEdge) to g.getEdgeTarget(nextEdge)
                    if (key in otherDists) {
                        return key to dist + otherDists[key]!!.second
                    }

//                    val key2 = key.second to key.first
//                    if (key2 in otherDists) {
//                        return key2 to dist + otherDists[key2]!!.second
//                    }
                }
            }
        }
        return ("" to "") to 0.0
    }

    fun getClosestEdgeHub(source: String): Pair<Pair<String, String>, Double>? {
        val edgeReversed = EdgeReversedGraph(g)
        val walkers = listOf(
                ClosestFirstIterator(g, source),
                ClosestFirstIterator(edgeReversed, source)
        )

        val best = walkers.map { walker -> getFirst(walker) }
            .filter { it.first.first != "" }
            .maxBy { it.second }
        return best

    }

    fun getTotalDists() {
        var notFound = 0
        val results = g.vertexSet().toList().map {
            val result = getClosestEdgeHub(it)
            if (result != null) {
                result.second
            } else {
                notFound += 1
                0.0
            } }

        val highest = g.edgeSet().sumByDouble { g.getEdgeWeight(it) }
        val total = results.sumByDouble { it / highest }
        println("Total: $total")


        println("Not found: $notFound")
    }

    fun highestDegreeEdges() {
        var counter = 0
        val seen = HashSet<String>()
        val bestEdges = g.edgeSet().map { edge ->
            val source = g.getEdgeSource(edge)
            val target = g.getEdgeTarget(edge)
            val combinedOut = g.outDegreeOf(source) + g.outDegreeOf(target)
            val combinedIn = g.inDegreeOf(source) + g.inDegreeOf(target)
            val sourceProb = g.incomingEdgesOf(source).sumByDouble { g.getEdgeWeight(it) }
            val targetProb = g.incomingEdgesOf(target).sumByDouble { g.getEdgeWeight(it) }
//            (source to target) to g.getEdgeWeight(edge) * Math.log((g.degreeOf(source).toDouble() * g.degreeOf(target)))  }
//        (source to target) to g.getEdgeWeight(edge) + Math.log(sourceProb) + Math.log(targetProb)  }
        (source to target) to g.getEdgeWeight(edge) + Math.log(sourceProb + targetProb)  }
//            (source to target) to g.degreeOf(source) * g.degreeOf(target) }
            .sortedByDescending { it.second }
            .forEach {
                if (it.first.first !in seen && it.first.second !in seen) {
//                    if (true) {
                    println(it)
                    edgeHubs[it.first] = 1.0
                    counter += 1
                    seen.add(it.first.first)
                    seen.add(it.first.second)
                    if (counter > 200)
                        return
                }

            }



    }

    fun diffusionWalkEdges() {
        val nodes = g.vertexSet().toList()
//        val counter = HashMap<Set<String>, Double>()
        val counter = HashMap<Pair<String, String>, Double>()
//        val counter = HashMap<Unon<String>, Double>()
//        val counter = HashMap, Double>()
        val edgeReversed = EdgeReversedGraph(g)
        val lst = listOf(g, edgeReversed)

        (0 until 8000).forEach {
            val origin = nodes.sampleRandom()
            val reversed = sharedRand.nextInt(2)
            var prev = origin
            val walk = RandomWalkIterator(lst[reversed], origin, true,
                    5000)

            walk.forEachRemaining { node ->
                val edge = if (reversed == 1) node to prev else prev to node
//                val edge = setOf(prev, node)
                if (prev != node) {
                    if (edge !in counter) {
                        counter[edge] = 0.0
                    }
                    counter[edge] = counter[edge]!! + 1.0
                }
                prev = node
            }
        }

//        val seen = HashSet<String>()
//        var times = 0
//        counter.normalize()
//            .entries
//            .sortedByDescending { it.value }
//            .forEach { (h, freq) ->
//            if (h.first !in seen && h.second !in seen) {
////                    if (true) {
//                println(h)
//                edgeHubs[h] = freq
//                times += 1
//                seen.add(h.first)
//                seen.add(h.second)
//                if (times > 40)
//                    return
//            }
//        }

        counter.normalize()
            .entries
            .sortedByDescending { it.value }
            .take(300)
            .forEach { (hub, weight) ->
                edgeHubs[hub] = weight
            }

        edgeHubs.entries.sortedBy {it.value }
            .printEach()
    }


    fun computeBranchingDistances() {
        val nodeMaps = HashMap<String, HashMap<String, Double>>()
        val linked = HashSet<String>()
        val walkers = hubs.keys.map { hub -> hub to ClosestFirstIterator(g, hub) }
        (0 until 500).forEach {
            walkers
                .filter { it.first !in linked }
                .forEach { (hub, walker) ->
                    val node = walker.next()
                    val dist = walker.getShortestPathLength(node)
                    if (node in nodeMaps) {
//                        linked += hub
                    }

                    if (node !in nodeMaps)
                        nodeMaps[node] = HashMap()
                    nodeMaps[node]!![hub] = dist
                }
        }
        println(nodeMaps.size)
        val union = UnionFind<String>(hubs.keys)
//        nodeMaps.entries.sortedBy { it.value.values.first() }
//            .forEach { println(it) }

//        finalNodeDists = hubs.keys.map { it to HashMap<String, Double>() }.toHashMap()
//        (0 until 2).forEach {
//            finalNodeDists.keys.forEach { hub ->
//                val targets = finalNodeDists[hub]!!
//                val wee = targets.entries.map { it.key to it.value }.toMap()
//                wee.forEach { (other, dist) ->
//                    val otherDist = finalNodeDists[other]!!
//                    otherDist.entries.forEach { (otherOther, dist2) ->
//                        if (otherOther !in targets)
//                            targets[otherOther] = 999999.0
//                        targets[otherOther] = Math.min(targets[otherOther]!!, dist2 + dist)
//                    }
//                }
//            }
//        }
//
//        finalNodeDists.forEach { node -> node.value[node.key] = 0.0 }
//        hubDistances = finalNodeDists

//        nodeMaps.entries.filter { it.value.size > 1 }
//            .forEach { (key, results) ->
//                val h = results.keys
//                val first = h.first()
//                h.drop(1).forEach { other ->
//                    union.union(first, other)
//                }
//            }
//
//
//        hubs.keys.map { union.find(it) to it }
//            .groupOfLists()
//            .forEach { println(it) }
    }

    fun computeDistanceFromNodeMap(nodeMaps: HashMap<StringPair, HashMap<StringPair, ArrayList<Double>>>) {
        finalNodeDists =
                nodeMaps.map { it.key to it.value.map { it.key to it.value.first() }.toHashMap() }.toHashMap()


//        val hDists = edgeHubs.keys.map { it to HashMap<StringPair, ArrayList<Double>>() }.toMap()
        val hDists = edgeHubs.keys.map { it to HashMap<StringPair, Double>() }.toMap()

        finalNodeDists.forEach { (_, dists) ->
            dists.entries.toList().pairwise().forEach { (e1, e2) ->
                val e1Dist = hDists[e1.key]!!
                val e2Dist = hDists[e2.key]!!
                val dist = Math.abs(e1.value - e2.value)

//                e1Dist.computeIfAbsent(e2.key) { ArrayList() }.add(dist)
//                e2Dist.computeIfAbsent(e1.key) { ArrayList() }.add(dist)
                e1Dist.putMin(e2.key, dist)
                e2Dist.putMin(e1.key, dist)
            }
        }

        hDists.forEach { (k,v) ->
//            val sorted = v.entries.map { it.key to it.value.average() }
            val sorted = v.entries.map { it.key to it.value }
                .sortedBy { it.second }
            println("$k : $sorted")
        }
    }

    fun computeBranchingEdgeDistances() {
        val nodeMaps = HashMap<Pair<String, String>, HashMap<Pair<String, String>, ArrayList<Double>>>()
//        val linked = HashSet<String>()
        val edgeReversed = EdgeReversedGraph(g)
        val walkers =
                edgeHubs.keys.map { hub -> hub to listOf(
                        ClosestFirstIterator(g, hub.first) to false,
                        ClosestFirstIterator(g, hub.second) to false,
                        ClosestFirstIterator(edgeReversed, hub.first) to true,
                        ClosestFirstIterator(edgeReversed, hub.second) to true
                        ) }

        walkers
            .forEach { (hub, walkers) ->
                walkers.forEachIndexed {index,  (walker, isReversed) ->
//                    var prev = hub.first to 0.0
                    (0 until 50000).forEach {
                        if (walker.hasNext()) {
                            val nextNode = walker.next()
                            val nextEdge = walker.getSpanningTreeEdge(nextNode)
                            val dist = walker.getShortestPathLength(nextNode)

//                            val node = nextNode to walker.getShortestPathLength(nextNode)
//                            if (prev.first != node.first) {
                            if (nextEdge != null) {
                                val key = g.getEdgeSource(nextEdge) to g.getEdgeTarget(nextEdge)
                                if (key !in nodeMaps)
                                    nodeMaps[key] = HashMap()
                                val nMap = nodeMaps[key]!!
                                if (hub !in nMap)
                                    nMap[hub] = arrayListOf(0.0, 0.0, 0.0, 0.0)
//                                nMap[hub]!![index] = (prev.second + node.second)
                                nMap[hub]!![index] = dist
                            }
//                            prev = node
                        }
                    }
                }

            }
        println("\n======\nResults:\n")
        nodeMaps.entries.forEach { (node, scores) ->
            scores.entries.forEach { (hub, results) ->
                val forward = Math.max(results[0], results[1])
                val reverse = Math.max(results[2], results[3])
                results.clear()
                if (forward == 0.0)
                    results.add(reverse)
                else if (reverse == 0.0)
                    results.add(forward)
                else
//                    results.add(Math.min(forward, reverse))
                results.add(Math.min(forward, reverse))
//                results.add(forward * reverse)
//                results.add((forward + reverse) / 2.0)
            }
        }
        nodeMaps.entries.sortedBy { it.value.values.map { it.first() }.min()!! }
            .asSequence()
            .take(100)
            .toList()
            .map { it.key to it.value.entries.sortedBy { it.value.first() }.map { it.key to it.value.first().toString().take(20) } }
            .printEach()

        println(nodeMaps.filter { it.value.size > 1 }.size)
        println(nodeMaps.size)
        println(g.edgeSet().size)

        val union = UnionFind<String>(hubs.keys)
        nodeMaps.forEach { (k, nMap) ->
            otherDists[k] = nMap.minBy { it.value.first() }!!.let { result -> result.key to result.value.first() }
        }

        (0 until 10).forEach {
            println("=========")
            val test = nodeMaps.keys.toList().sampleRandom()
            println("Target: $test")
            val testValues = nodeMaps[test]!!.map { it.key to it.value.first() }.toMap().inverseNormalize()
            nodeMaps.entries.map { (k, v) ->
                val vMap = v.map { it.key to it.value.first() }.toMap().inverseNormalize()
                val r = testValues.entries.sumByDouble { (a, b) ->
                    //                val bd = b.first()
                    (b - (vMap[a] ?: 0.0)).absoluteValue
//                (bd - (v[a]?.first() ?: 0.0)).absoluteValue
                }
                k to r
            }.sortedBy { it.second }
                .take(10)
                .printEach()
        }

//        computeDistanceFromNodeMap(nodeMaps)

//        finalNodeDists =
//                nodeMaps.map { it.key to it.value.map { it.key to it.value.first() }.toHashMap() }.toHashMap()
//
//        val edges = edgeHubs.keys.toList()
//        (0 until 2).forEach {
//            finalNodeDists.keys.forEach { hubKey ->
//                val hub = finalNodeDists[hubKey]!!
//                edges.forEach { targetEdge ->
//                    edges.filter { it in hub && targetEdge in finalNodeDists[it]!! }
//                        .forEach { neighborEdge ->
//                            val dist = hub[neighborEdge]!! + finalNodeDists[neighborEdge]!![targetEdge]!!
//                            hub[targetEdge] = if(targetEdge !in hub) dist else Math.min(dist, hub[targetEdge]!!)
//                        }
//                }
//            }
//        }
////
//        finalNodeDists.forEach { node -> node.value[node.key] = 0.0 }
//        finalNodeDists.forEach { println("${it.key} : ${it.value}") }
//        hubDistances = finalNodeDists

//        nodeMaps.entries.filter { it.value.size > 1 }
//            .forEach { (key, results) ->
//                val h = results.keys
//                val first = h.first()
//                h.drop(1).forEach { other ->
//                    union.union(first, other)
//                }
//            }
//
//
//        hubs.keys.map { union.find(it) to it }
//            .groupOfLists()
//            .forEach { println(it) }
    }

    fun computeHubDistances() {
        val dist = org.jgrapht.alg.shortestpath.DijkstraShortestPath(g)
        val hubKeys = hubs.keys.toList()
        hubKeys.map { source ->
            val result = hubKeys.map { target -> target to dist.getPathWeight(source, target).defaultWhenNotFinite(1.0)}
                .toHashMap()
            hubDistances[source] = result
        }
    }

    fun getVoronoi() {
        val nodes = g.vertexSet().toList()

        nodes.forEachIndexed { index, node ->
            nodeDists[node] = getClosestHub(node)
            if (index % 1000 == 0) {
                println(index)
            }
        }

//        (listOf("dna_made", "endoderm_mesoderm", "member_group", "allel_frequenc", "marriag_consid", "feminist_movement")).forEach { randNode ->
        (0 until 5).forEach {
            val randNode = nodes.sampleRandom()
            getClosestNodes(randNode, nodes)
        }
    }

    fun getClosestNodes(source: String, nodes: List<String>) {

        println("To: $source : ${nodeDists[source]}")
        nodes.map { node -> node to getDist(source, node) }
            .sortedBy { it.second }
            .take(10)
            .map { it.first to it.second.toString().take(5) }
            .apply { println(this) }
        println()
    }

    fun getDist(source: String, target: String): Double {
        val (h1, d1) = nodeDists[source]!!
        val (h2, d2) = nodeDists[target]!!

        val h1Toh2 = hubDistances[h1]!![h2] ?: 9999.0
        val h2Toh1 = hubDistances[h2]!![h1] ?: 9999.0
        val h1Dist = hubDistances[h1]!!
        val h2Dist = hubDistances[h2]!!

        return Math.max(h1Toh2, h2Toh1) + d1 + d2

    }


    fun getClosestHub(source: String): Pair<String, Double> {
        if (source in hubs)
            return source to 0.0

        val walker = ClosestFirstIterator(g, source)
        var counter = 0


        walker.forEach { node ->
            if (node in hubs)
                return node to walker.getShortestPathLength(node)

            counter += 1
        }

        return "" to 0.0
    }

}