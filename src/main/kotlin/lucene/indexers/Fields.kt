package lucene.indexers

import language.GramStatType
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.Term
import org.apache.lucene.search.BoostQuery
import org.apache.lucene.search.TermQuery

fun IndexFields.getString(doc: Document) = doc.get(field)
fun IndexFields.getDouble(doc: Document) = doc.get(field).toDouble()
fun IndexFields.getList(doc: Document) = doc.get(field).split(" ")

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
    FIELD_SECTION_BIGRAM("section_${GramStatType.TYPE_BIGRAM.indexField}"),
    FIELD_SECTION_WINDOWED_BIGRAM("section_${GramStatType.TYPE_BIGRAM_WINDOW.indexField}"),
    FIELD_UNIGRAM(GramStatType.TYPE_UNIGRAM.indexField),
    FIELD_BIGRAM(GramStatType.TYPE_BIGRAM.indexField),
    FIELD_WINDOWED_BIGRAM(GramStatType.TYPE_BIGRAM_WINDOW.indexField),
    FIELD_RDF("rdf"),
    FIELD_PID("paragraphid"),
    FIELD_NAME("name"),
    FIELD_INLINKS("inlinks"),
    FIELD_OUTLINKS("outlinks"),
    FIELD_CATEGORIES("categories"),
    FIELD_DISAMBIGUATIONS("disambiguations"),
    FIELD_REDIRECTS("redirects"),
    FIELD_ENTITIES("spotlight")
//    FIELD_PARAGRAPH_OUTLINKS("paragraph_outlinks")

}