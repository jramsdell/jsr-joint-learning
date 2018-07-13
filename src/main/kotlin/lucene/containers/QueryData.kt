package lucene.containers

import entity.EntityDatabase
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs

data class QueryData(
        val queryString: String,
        val queryTokens: List<String>,
        val queryBoolean: BooleanQuery,
        val queryBooleanTokens: List<Any>,

        val paragraphSearcher: ParagraphSearcher,
        val entitySearcher: EntitySearcher,
        val sectionSearcher: SectionSearcher,

        val entityContainers: List<EntityContainer>,
        val paragraphContainers: List<ParagraphContainer>,
        val sectionContainers: List<SectionContainer>,
//        val containers: TypedMapCollection<ArrayList<Any>>,
        val isJoint: Boolean,
        val tops: TopDocs,
        val entityDb: EntityDatabase ) {

//    val paragraphContainers: List<ParagraphContainer> get() {
//        val def = containers[DocumentContainerType.TYPE_PARAGRAPH] ?: emptyList()
//        @Suppress("UNCHECKED_CAST")
//        return def as List<ParagraphContainer>
//    }
//
//    val entityContainers: List<EntityContainer> get() {
//        val def = containers[DocumentContainerType.TYPE_ENTITY] ?: emptyList()
//        @Suppress("UNCHECKED_CAST")
//        return def as List<EntityContainer>
//    }
//
//    val sectionContainers: List<SectionContainer> get() {
//        val def = containers[DocumentContainerType.TYPE_SECTION] ?: emptyList()
//        @Suppress("UNCHECKED_CAST")
//        return def as List<SectionContainer>
//    }
}
