package lucene.containers

import lucene.indexers.IndexFields
import lucene.indexers.getString
import org.apache.lucene.document.Document


data class IndexDoc<out A: IndexType>(val doc: Document, val docId: Int) {
    fun get(field: String) = doc.get(field)
}

typealias ParagraphDoc = IndexDoc<IndexType.PARAGRAPH>
typealias EntityDoc = IndexDoc<IndexType.ENTITY>
typealias SectionDoc = IndexDoc<IndexType.SECTION>

//val ParagraphDoc.pid: String get() = IndexFields.FIELD_PID.getString(doc)
fun ParagraphDoc.pid(): String = IndexFields.FIELD_PID.getString(doc)
fun ParagraphDoc.text():String =  IndexFields.FIELD_TEXT.getString(doc)
fun ParagraphDoc.entities(): String  = IndexFields.FIELD_ENTITIES.getString(doc)
fun ParagraphDoc.unigrams(): String  = IndexFields.FIELD_UNIGRAM.getString(doc)
fun ParagraphDoc.bigrams(): String  = IndexFields.FIELD_BIGRAM.getString(doc)
fun ParagraphDoc.windowed(): String  = IndexFields.FIELD_WINDOWED_BIGRAM.getString(doc)
fun ParagraphDoc.neighborBigrams(): String  = IndexFields.FIELD_NEIGHBOR_BIGRAMS.getString(doc)
fun ParagraphDoc.neighborWindowed(): String  = IndexFields.FIELD_NEIGHBOR_WINDOWED.getString(doc)
fun ParagraphDoc.neighborUnigrams(): String  = IndexFields.FIELD_NEIGHBOR_UNIGRAMS.getString(doc)
fun ParagraphDoc.neighborEntities(): String  = IndexFields.FIELD_NEIGHBOR_ENTITIES.getString(doc)
fun ParagraphDoc.spotlightEntities(): String  = IndexFields.FIELD_ENTITIES_EXTENDED.getString(doc)
    .split(" ")
    .toSet()
    .toList()
    .joinToString(" ")
fun ParagraphDoc.spotlightInlinks(): String  = IndexFields.FIELD_ENTITIES_INLINKS.getString(doc)

//@JvmName("we")fun IndexDoc<IndexType.ENTITY>.pid() = IndexFields.FIELD_PID.getString(doc)
@JvmName("eName")
fun EntityDoc.name() = IndexFields.FIELD_NAME.getString(doc)
@JvmName("eInlinks")
fun EntityDoc.inlinks() = IndexFields.FIELD_INLINKS.getString(doc)
@JvmName("eInlinksU")
fun EntityDoc.inlinksUnigrams() = IndexFields.FIELD_INLINKS_UNIGRAMS.getString(doc)
@JvmName("eOutlinks")
fun EntityDoc.outlinks() = IndexFields.FIELD_OUTLINKS.getString(doc)
@JvmName("eOutlinksU")
fun EntityDoc.outlinksUnigrams() = IndexFields.FIELD_OUTLINKS_UNIGRAMS.getString(doc)
@JvmName("eCategories")
fun EntityDoc.categories() = IndexFields.FIELD_CATEGORIES.getString(doc)
@JvmName("eCategoriesU")
fun EntityDoc.categoriesUnigrams() = IndexFields.FIELD_CATEGORIES_UNIGRAMS.getString(doc)
@JvmName("eRedirects")
fun EntityDoc.redirects() = IndexFields.FIELD_REDIRECTS.getString(doc)
@JvmName("eRedirectsU")
fun EntityDoc.redirectsUnigrams() = IndexFields.FIELD_REDIRECTS_UNIGRAMS.getString(doc)
@JvmName("eDisam")
fun EntityDoc.disambig() = IndexFields.FIELD_DISAMBIGUATIONS.getString(doc)
@JvmName("eDisamU")
fun EntityDoc.disambigUnigrams() = IndexFields.FIELD_DISAMBIGUATIONS_UNIGRAMS.getString(doc)
@JvmName("eUnigrams")
fun EntityDoc.unigrams() = IndexFields.FIELD_UNIGRAM.getString(doc)
@JvmName("eBigrams")
fun EntityDoc.bigrams() = IndexFields.FIELD_BIGRAM.getString(doc)
@JvmName("eWindowed")
fun EntityDoc.windowed() = IndexFields.FIELD_WINDOWED_BIGRAM.getString(doc)

@JvmName("sHeader")
fun SectionDoc.heading() = IndexFields.FIELD_SECTION_HEADING.getString(doc)
@JvmName("sId")
fun SectionDoc.id() = IndexFields.FIELD_SECTION_ID.getString(doc)
@JvmName("sUnigrams")
fun SectionDoc.unigrams() = IndexFields.FIELD_UNIGRAM.getString(doc)
@JvmName("sBigrams")
fun SectionDoc.bigrams() = IndexFields.FIELD_BIGRAM.getString(doc)
@JvmName("sWindowed")
fun SectionDoc.windowed() = IndexFields.FIELD_WINDOWED_BIGRAM.getString(doc)
@JvmName("sChildren")
fun SectionDoc.paragraphs() = IndexFields.FIELD_CHILDREN_IDS.getString(doc)
