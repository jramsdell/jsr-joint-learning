package lucene.containers

import entity.EntityDatabase
import org.apache.lucene.document.Document
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs

data class QueryData(
        val queryString: String,
        val queryTokens: List<String>,
        val queryBoolean: BooleanQuery,
        val queryBooleanTokens: List<Any>,

        val paragraphSearcher: IndexSearcher,
        val entitySearcher: IndexSearcher,
        val proximitySearcher: IndexSearcher,

        val entityContainers: List<EntityContainer>,
        val paragraphContainers: List<ParagraphContainer>,
        val tops: TopDocs,
        val entityDb: EntityDatabase )
