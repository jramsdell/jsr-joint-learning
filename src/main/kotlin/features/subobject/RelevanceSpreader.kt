package features.subobject

import lucene.containers.QueryData


object RelevanceSpreader {

//}
//    fun scoreByField(qd: QueryData, paragraphField): (ParagraphContainer, EntityContainer) -> Double {
//        val scoreMap =
//                qd.paragraphContainers.mapIndexed { pIndex, pContainer ->
//                    val fieldTokens = paragraphField.getList(pContainer.doc())
//                    val fieldQuery = FieldQueryFormatter()
//                        .addWeightedQueryTokens(fieldTokens, entityField, 1.0)
//                        .createBooleanQuery()
//                    val searchResult = qd.entitySearcher.search(fieldQuery, 2000)
//
//                    val parToEntScores =
//                            searchResult.scoreDocs.map { sc -> sc.doc to sc.score.toDouble() }
//                    pContainer.docId to parToEntScores.toMap()
//                }.toMap()
//    }

}
