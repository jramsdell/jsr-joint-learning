package lucene.implementations

import org.apache.lucene.index.LeafReaderContext
import org.apache.lucene.queries.function.FunctionQuery
import org.apache.lucene.queries.function.FunctionScoreQuery
import org.apache.lucene.queries.function.FunctionValues
import org.apache.lucene.queries.function.ValueSource
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.search.Weight


class WeightedGramFunction(val queries: List<Query>): Query() {
    override fun hashCode(): Int {
        return queries.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return queries == other
    }

    override fun toString(field: String?): String {
        return ""
    }

    override fun createWeight(searcher: IndexSearcher?, needsScores: Boolean, boost: Float): Weight {
        return super.createWeight(searcher, needsScores, boost)
    }

}

