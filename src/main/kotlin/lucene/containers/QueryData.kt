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

        val paragraphDocuments: List<Document>,
        val entityDocuments: List<Document>,
        val entityContainers: List<EntityContainer>,
        val paragraphContainers: List<ParagraphContainer>,
        val tops: TopDocs,
        val entityToParagraph: Map<Int, Map<Int, Double>>,
        val paragraphToEntity: Map<Int, Map<Int, Double>>,
        val entityDb: EntityDatabase )
