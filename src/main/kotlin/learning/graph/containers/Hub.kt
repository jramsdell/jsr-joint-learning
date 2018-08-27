package learning.graph.containers


data class Hub<T>(
        val element: GraphElement<T>,
        var score: Double = 0.0,
        val neighborHubDists: HashMap<Hub<T>, Double> = HashMap()
)