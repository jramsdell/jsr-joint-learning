package lucene.containers

import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs

data class QueryData(
        val queryString: String,
        val queryTokens: List<String>,
        val queryBoolean: BooleanQuery,
        val queryBooleanTokens: List<BooleanQuery>,
        val indexSearcher: IndexSearcher,
        val queryEntities: List<Pair<String, Double>>,
        val documentEntities: List<List<String>>,
        val candidateEntities: List<Pair<String, ArrayList<Double>>>,
        val tops: TopDocs)
