package lucene.containers

import lucene.indexers.IndexFields
import lucene.indexers.IndexFields.*
import lucene.indexers.getString
import org.apache.lucene.document.Document


data class IndexDoc<out A: IndexType>(private val searcher: TypedSearcher<A>, val docId: Int) {
    //    fun get(field: String) = doc.get(field)
    fun load(field: IndexFields): String {
        val doc = searcher.doc(docId, setOf(field.field))
        return field.getString(doc)
    }

    fun load(fieldString: String): String {
        val doc = searcher.doc(docId, setOf(fieldString))
        return doc.get(fieldString)
    }
}


typealias ParagraphDoc = IndexDoc<IndexType.PARAGRAPH>
typealias EntityDoc = IndexDoc<IndexType.ENTITY>
typealias SectionDoc = IndexDoc<IndexType.SECTION>
typealias ContextEntityDoc = IndexDoc<IndexType.CONTEXT_ENTITY>
typealias ContextSectionDoc = IndexDoc<IndexType.CONTEXT_SECTION>

//val ParagraphDoc.pid: String get() = IndexFields.FIELD_PID.getString(doc)
fun ParagraphDoc.pid(): String = load(FIELD_PID)
fun ParagraphDoc.text():String =  load(FIELD_TEXT)
fun ParagraphDoc.entities(): String  = load(FIELD_ENTITIES)
fun ParagraphDoc.unigrams(): String  = load(FIELD_UNIGRAM)
fun ParagraphDoc.bigrams(): String  = load(FIELD_BIGRAM)
fun ParagraphDoc.windowed(): String  = load(FIELD_WINDOWED_BIGRAM)
fun ParagraphDoc.neighborBigrams(): String  = load(FIELD_NEIGHBOR_BIGRAMS)
fun ParagraphDoc.neighborWindowed(): String  = load(FIELD_NEIGHBOR_BIGRAMS)
fun ParagraphDoc.neighborUnigrams(): String  = load(FIELD_NEIGHBOR_UNIGRAMS)
fun ParagraphDoc.neighborEntities(): String  = load(FIELD_NEIGHBOR_ENTITIES)
fun ParagraphDoc.spotlightEntities(): String  = load(FIELD_ENTITIES_EXTENDED)
    .split(" ")
    .toSet()
    .toList()
    .joinToString(" ")
fun ParagraphDoc.spotlightInlinks(): String  = load(FIELD_ENTITIES_INLINKS)

//@JvmName("we")fun IndexDoc<IndexType.ENTITY>.pid() = IndexFields.FIELD_PID.getString(doc)
@JvmName("eName")
fun EntityDoc.name() = load(FIELD_NAME)
@JvmName("eInlinks")
fun EntityDoc.inlinks() = load(FIELD_INLINKS)
@JvmName("eInlinksU")
fun EntityDoc.inlinksUnigrams() = load(FIELD_INLINKS_UNIGRAMS)
@JvmName("eOutlinks")
fun EntityDoc.outlinks() = load(FIELD_OUTLINKS)
@JvmName("eOutlinksU")
fun EntityDoc.outlinksUnigrams() = load(FIELD_OUTLINKS_UNIGRAMS)
@JvmName("eCategories")
fun EntityDoc.categories() = load(FIELD_CATEGORIES)
@JvmName("eCategoriesU")
fun EntityDoc.categoriesUnigrams() = load(FIELD_CATEGORIES_UNIGRAMS)
@JvmName("eRedirects")
fun EntityDoc.redirects() = load(FIELD_REDIRECTS)
@JvmName("eRedirectsU")
fun EntityDoc.redirectsUnigrams() = load(FIELD_REDIRECTS_UNIGRAMS)
@JvmName("eDisam")
fun EntityDoc.disambig() = load(FIELD_DISAMBIGUATIONS)
@JvmName("eDisamU")
fun EntityDoc.disambigUnigrams() = load(FIELD_DISAMBIGUATIONS_UNIGRAMS)
@JvmName("eUnigrams")
fun EntityDoc.unigrams() = load(FIELD_UNIGRAM)
@JvmName("eBigrams")
fun EntityDoc.bigrams() = load(FIELD_BIGRAM)
@JvmName("eWindowed")
fun EntityDoc.windowed() = load(FIELD_WINDOWED_BIGRAM)

@JvmName("sHeader")
fun SectionDoc.heading() = load(FIELD_SECTION_HEADING)
@JvmName("sId")
fun SectionDoc.id() = load(FIELD_SECTION_ID)
@JvmName("sUnigrams")
fun SectionDoc.unigrams() = load(FIELD_UNIGRAM)
@JvmName("sBigrams")
fun SectionDoc.bigrams() = load(FIELD_BIGRAM)
@JvmName("sWindowed")
fun SectionDoc.windowed() = load(FIELD_WINDOWED_BIGRAM)
@JvmName("sChildren")
fun SectionDoc.paragraphs() = load(FIELD_CHILDREN_IDS)


@JvmName("ceEntities")
fun ContextEntityDoc.entities() = load(FIELD_ENTITIES)
@JvmName("ceEntitiesUnigram")
fun ContextEntityDoc.entitiesUnigrams() = load(FIELD_ENTITIES_UNIGRAMS)
@JvmName("ceUnigram")
fun ContextEntityDoc.unigrams() = load(FIELD_UNIGRAM)
@JvmName("ceBigram")
fun ContextEntityDoc.bigrams() = load(FIELD_BIGRAM)
@JvmName("ceWindowed")
fun ContextEntityDoc.windowed() = load(FIELD_WINDOWED_BIGRAM)
@JvmName("ceName")
fun ContextEntityDoc.name() = load(FIELD_NAME)

@JvmName("csName")
fun ContextSectionDoc.name() = load(FIELD_NAME)
@JvmName("csEntities")
fun ContextSectionDoc.entities() = load(FIELD_ENTITIES)
@JvmName("csEntitiesUnigram")
fun ContextSectionDoc.entitiesUnigram() = load(FIELD_ENTITIES_UNIGRAMS)
@JvmName("csUnigram")
fun ContextSectionDoc.unigrams() = load(FIELD_UNIGRAM)
@JvmName("csBigram")
fun ContextSectionDoc.bigrams() = load(FIELD_BIGRAM)
@JvmName("csWindowed")
fun ContextSectionDoc.windowed() = load(FIELD_WINDOWED_BIGRAM)
@JvmName("csNeighborSections")
fun ContextSectionDoc.neighborSections() = load(FIELD_NEIGHBOR_SECTIONS)
