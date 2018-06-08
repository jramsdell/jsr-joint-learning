package lucene.containers

import org.apache.lucene.document.Document


class LuceneDocumentContainer(val content: String) {
    val doc = Document()
}