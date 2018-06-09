package lucene.containers

import edu.unh.cs.treccar_v2.Data
import org.apache.lucene.document.Document
import java.util.concurrent.locks.ReentrantLock


data class LuceneDocumentContainer(val doc: Document = Document(),
                                   val page: Data.Page,
                                   val paragraph: Data.Paragraph? = null, val lock: ReentrantLock = ReentrantLock()) {
}