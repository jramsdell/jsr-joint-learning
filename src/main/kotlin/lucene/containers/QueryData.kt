package lucene.containers

import entity.EntityDatabase
import lucene.PseudoDocumentDatabase
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs

data class QueryData(
        val queryString: String,
        val sectionPaths: List<List<String>>,

        val paragraphSearcher: ParagraphSearcher,
        val entitySearcher: EntitySearcher,
        val sectionSearcher: SectionSearcher,
        val originSearcher: ParagraphSearcher,
        val contextEntitySearcher: ContextEntitySearcher,
        val contextSectionSearcher: ContextSectionSearcher,

        val entityContainers: List<EntityContainer>,
        val paragraphContainers: List<ParagraphContainer>,
        val originParagraphContainers: List<ParagraphContainer>,
        val sectionContainers: List<SectionContainer>,
        val contextEntityContainers: List<ContextEntityContainer>,
//        val containers: TypedMapCollection<ArrayList<Any>>,
        val isJoint: Boolean) {

//    val pseudo = PseudoDocumentDatabase.buildDatabase(queryString,
//            paragraphContainers, sectionContainers, entityContainers )

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
