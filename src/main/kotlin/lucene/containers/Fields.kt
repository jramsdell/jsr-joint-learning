package lucene.containers

import language.GramStatType


enum class FieldNames(val field: String) {
    FIELD_TEXT("text"),
    FIELD_ABSTRACT("abstract"),
    FIELD_UNIGRAMS(GramStatType.TYPE_UNIGRAM.indexField),
    FIELD_BIGRAMS(GramStatType.TYPE_BIGRAM.indexField),
    FIELD_WINDOWED_BIGRAMS(GramStatType.TYPE_BIGRAM_WINDOW.indexField),
    FIELD_RDF("rdf"),
    FIELD_PID("pid"),
    FIELD_NAME("name")
}