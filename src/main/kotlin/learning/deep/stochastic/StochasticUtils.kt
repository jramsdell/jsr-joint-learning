package learning.deep.stochastic

import lucene.Trie
import lucene.containers.*
import lucene.indexers.IndexFields
import utils.AnalyzerFunctions
import utils.parallel.forEachParallel
import utils.stats.normalize


object StochasticUtils {

    fun scoreSectionWithParagraph(paragraph: ParagraphContainer, qc: QueryContainer, sections: List<Triple<List<String>, String, QueryContainer>>, field: IndexFields): Map<QueryContainer, Double> {
        val pField = AnalyzerFunctions.createQuery(paragraph.doc().load(field), field.field)
        val tokenScores = HashMap<String, Double>()
        val docs = qc.queryData.contextSectionSearcher.search(pField, 1)
            .scoreDocs
            .map { qc.queryData.contextSectionSearcher.getIndexDoc(it.doc) to it.score.toDouble() }
            .map {
//                val neighbors = (it.first.unigrams().replace("[ ]+".toRegex(), " ").split(" ")) + (it.first.neighborSections().replace("[ ]+".toRegex(), " ").split(" "))
                val neighbors = (it.first.unigrams().replace("[ ]+".toRegex(), " ").split(" "))
                neighbors.forEach { n ->
                    if (n !in tokenScores) {
                        tokenScores[n] = 0.0
                    }

//                    tokenScores[n] = tokenScores[n]!! + it.second
                    tokenScores[n] = tokenScores[n]!! + 1.0
                }
            }

        return sections
            .filter { (_, _, sQC) ->
                sQC.paragraphs.size > paragraph.index && sQC.paragraphs[paragraph.index].docId == paragraph.docId
            }
            .map { (section, _, sQC) ->
            sQC to section.sumByDouble { tokenScores[it] ?: 0.0 }
        }.toMap().normalize()
    }


    fun createSectionMappings(tries: Trie<QueryContainer>): ArrayList<Triple<List<String>, String, QueryContainer>> {
        val sectionList = ArrayList<Triple<List<String>, String, QueryContainer>>()
        tries.traverse { path, curNodeKey, d, children ->
            val tokens = path.flatMap { it.replace("%20", " ")
                .replace("enwiki:", "")
                .replace("[ ]+" .toRegex(), " ")
                .run { AnalyzerFunctions.createTokenList(this, AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED) }
            }
            sectionList.add(Triple(tokens, path.joinToString("/"), d))
        }

        return sectionList
    }


    fun scoreSections(tries: Trie<QueryContainer>) {
        val sectionList = createSectionMappings(tries)
        val qc = tries.children.values.drop(30).first().data!!
        println(qc.nRel)
        qc.paragraphs.forEach { paragraph ->
            val result = scoreSectionWithParagraph(paragraph, qc, sectionList, IndexFields.FIELD_BIGRAM)
            val wee = result.entries.withIndex().maxBy { (index, entry) -> entry.value  }?.let { it.index to it.value.value }
            println("$wee")
            result.entries
                .forEachIndexed {index,  r ->
                    val rel = r.key.paragraphs.size > paragraph.index && r.key.paragraphs[paragraph.index].isRelevant > 0 && (r.key.paragraphs[paragraph.index].docId == paragraph.docId)
                    if (rel)
                        println("$rel ($index) : ${r.value}")
                }
        }


    }

    fun filterBySections(tries: Trie<QueryContainer>) {
        val seen = HashMap<Int, Boolean>()
        val sectionList = createSectionMappings(tries)
        tries.traverse { path, curNodeKey, d, children ->
            d.paragraphs.forEachParallel { paragraph ->
                if (paragraph.docId in seen) {
                    if (!seen[paragraph.docId]!!) {
                        paragraph.features.forEach { it.score = 0.0; it.unnormalizedScore = 0.0 }
                        paragraph.score = 0.0
                    }
                } else {
                    val result = scoreSectionWithParagraph(paragraph, d, sectionList, IndexFields.FIELD_BIGRAM)
                    val wee = result.entries.withIndex().maxBy { (index, entry) -> entry.value }?.let { it.index to it.value.value }
                    val bad = (wee == null) || wee.second == 0.0
                    seen[paragraph.docId] = !bad
                }
            }
        }


    }
}