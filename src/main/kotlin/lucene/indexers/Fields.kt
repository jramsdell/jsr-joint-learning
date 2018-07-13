package lucene.indexers

import language.GramStatType
import lucene.containers.IndexDoc
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.Term
import org.apache.lucene.search.BoostQuery
import org.apache.lucene.search.TermQuery

fun IndexFields.getString(doc: Document) = doc.get(field) ?: ""
fun IndexFields.getDouble(doc: Document) = doc.get(field).toDouble()
fun IndexFields.getList(doc: Document) = doc.get(field).split(" ")

fun IndexFields.getString(doc: IndexDoc<*>) = doc.get(field) ?: ""
fun IndexFields.getDouble(doc: IndexDoc<*>) = doc.get(field).toDouble()
fun IndexFields.getList(doc: IndexDoc<*>) = doc.get(field).split(" ")

fun IndexFields.setTextField(doc: Document, fieldValue: String) =
        doc.add(TextField(field, fieldValue, Field.Store.YES))
fun IndexFields.setStringField(doc: Document, fieldValue: String) =
        doc.add(StringField(field, fieldValue, Field.Store.YES))

fun IndexFields.termQuery(term: String) =
        TermQuery(Term(field, term))

fun IndexFields.boostedTermQuery(term: String, weight: Double) =
        BoostQuery(TermQuery(Term(field, term)), weight.toFloat())




enum class IndexFields(val field: String) {
    FIELD_TEXT("text"),
    FIELD_ABSTRACT("abstract"),
    FIELD_SECTION_TEXT("section_text"),
    FIELD_SECTION_UNIGRAM("section_${GramStatType.TYPE_UNIGRAM.indexField}"),
    FIELD_UNIGRAM(GramStatType.TYPE_UNIGRAM.indexField),
    FIELD_BIGRAM(GramStatType.TYPE_BIGRAM.indexField),
    FIELD_WINDOWED_BIGRAM(GramStatType.TYPE_BIGRAM_WINDOW.indexField),
    FIELD_RDF("rdf"),
    FIELD_PID("paragraphid"),
    FIELD_NAME("name"),
    FIELD_NAME_UNIGRAMS("name_unigrams"),
    FIELD_INLINKS("inlinks"),
    FIELD_INLINKS_UNIGRAMS("inlinks_unigrams"),
    FIELD_OUTLINKS("outlinks"),
    FIELD_OUTLINKS_UNIGRAMS("outlinks_unigrams"),
    FIELD_CATEGORIES("categories"),
    FIELD_CATEGORIES_UNIGRAMS("categories_unigrams"),
    FIELD_DISAMBIGUATIONS("disambiguations"),
    FIELD_DISAMBIGUATIONS_UNIGRAMS("disambiguations_unigrams"),
    FIELD_REDIRECTS("redirects"),
    FIELD_REDIRECTS_UNIGRAMS("redirects_unigrams"),
    FIELD_NEIGHBOR_ENTITIES("neighbor_entities"),
    FIELD_NEIGHBOR_ENTITIES_UNIGRAMS("neighbor_entities_unigrams"),
    FIELD_NEIGHBOR_UNIGRAMS("neighbor_unigrams"),
    FIELD_NEIGHBOR_BIGRAMS("neighbor_bigrams"),
    FIELD_NEIGHBOR_WINDOWED("neighbor_windowed"),
    FIELD_ENTITIES("spotlight"),
    FIELD_ENTITIES_EXTENDED("entities_extended"),
    FIELD_ENTITIES_INLINKS("entities_inlinks"),
    FIELD_ENTITIES_UNIGRAMS("spotlight_unigrams"),
    FIELD_JOINT_ENTITIES("joint_entities"),
    FIELD_JOINT_ENTITIES_UNIGRAMS("joint_entities_unigrams"),
    FIELD_JOINT_UNIGRAMS("joint_unigrams"),
    FIELD_JOINT_BIGRAMS("joint_bigrams"),
    FIELD_JOINT_WINDOWED("joint_windowed"),
    FIELD_NEIGHBOR_SECTIONS("neighbor_sections"),
    FIELD_SECTION_ID("section_id"),
    FIELD_SECTION_HEADING("section_heading"),
    FIELD_SECTION_PATH("section_heading"),
    FIELD_CHILDREN_IDS("children_ids")
//    FIELD_PARAGRAPH_OUTLINKS("paragraph_outlinks")

}