package lucene

import browser.BrowserPage
import browser.BrowserParagraph
import browser.BrowserSection
import experiment.KotlinRanklibFormatter
import experiment.NormType
import lucene.containers.ParagraphContainer
import lucene.containers.SectionContainer
import lucene.containers.*
import lucene.indexers.IndexFields
import utils.AnalyzerFunctions
import utils.lucene.getTypedSearcher
import utils.misc.sharedRand
import java.io.File


class RanklibWriter(val formatter: KotlinRanklibFormatter, val omitArticleLevel: Boolean) {

    fun writeHtml() = with(formatter) {
        queryContainers.forEachIndexed { qIndex, qContainer ->
            qContainer.sections.forEach(SectionContainer::rescore)
            qContainer.paragraphs.forEach(ParagraphContainer::rescore)
            val topSections = qContainer
                .sections
                .sortedByDescending(SectionContainer::score)
                .take(5)

            val seen = HashSet<Int>()

            val browserSections = topSections.mapIndexed { index, section ->
                val sIndex = section.index
                val results = qContainer.paragraphs.map {
                    it to it.score * (qContainer.jointDistribution.parToSec[it.index]?.get(sIndex) ?: 0.0)
                }
                    .asSequence()
                    .sortedByDescending { it.second }
                    .filter { it.second > 0.0 && it.first.index !in seen }
                    .take(5)
                    .map { it.first }
                    .toList()
                    .onEach { seen.add(it.index) }
                section to results
            }.map { (section, paragraphs) ->
                val browserParagraphs = paragraphs.map { BrowserParagraph(it.doc().text()) }
                BrowserSection(section.doc().id(), browserParagraphs)
            }

            val qname = qContainer.query
            BrowserPage(qname, browserSections)
                .write("html_pages/$qIndex.html")


        }
    }

    fun writeEntitiesToFile2(queries: List<QueryContainer>) {
        val writer = File("entity_results.run").bufferedWriter()
        queries.forEach { container ->
            val seen = HashSet<String>()
            val query = container.query
            container.entities.forEach(EntityContainer::rescore)
            container.entities
                .groupBy { it.name }
                .mapValues { it.value.maxBy { it.score }!!.score }
                .toList()
                .sortedByDescending { it.second }
                .forEachIndexed { index, entity ->
                    val id = "enwiki:" + entity.first.replace("_", "%20")
                    writer.write("${query} Q0 $id ${index + 1} ${entity.second} Entity\n")
                }
        }
        writer.flush()
        writer.close()
    }

    fun writeEntitiesToFile(queries: List<QueryContainer>) {
        val writer = File("entity_results.run").bufferedWriter()
        queries
            .run { if (omitArticleLevel) filter { it.query.contains("/") } else this }
            .forEach { container ->
            val seen = HashSet<String>()
            val query = container.query
            container.entities.forEach(EntityContainer::rescore)
            container.entities
                .filter { entity -> seen.add(entity.name) }
                .sortedByDescending(EntityContainer::score)
                .forEachIndexed { index, entity ->
                    val id = "enwiki:" + entity.name.replace("_", "%20")
                    writer.write("${query} Q0 $id ${index + 1} ${entity.score} Entity\n")
                }
        }
        writer.flush()
        writer.close()
    }

    fun writeParagraphsToFile(queries: List<QueryContainer>) {
        val writer = File("paragraph_results.run").bufferedWriter()
        queries
            .run { if (omitArticleLevel) filter { it.query.contains("/") } else this }
            .forEach { container ->
            val seen = HashSet<String>()
            val query = container.query
            container.paragraphs.forEach(ParagraphContainer::rescore)
            container.paragraphs
//                .filter { paragraph -> seen.add(paragraph.name) }
                .sortedByDescending(ParagraphContainer::score)
                .forEachIndexed { index, paragraph ->
                    writer.write("${query} Q0 ${paragraph.name} ${index + 1} ${paragraph.score} Paragraph\n")
                }
        }
        writer.flush()
        writer.close()
    }


    fun writeOriginParagraphsToFile(queries: List<QueryContainer>) {
        val writer = File("origin_paragraph_results.run").bufferedWriter()
        queries.forEach { rescoreOrigins(it) }
        queries.forEach { container ->
            val seen = HashSet<String>()
            val query = container.query
//            container.originParagraphs.forEach(ParagraphContainer::rescore)
            container.originParagraphs
                .filter { paragraph -> seen.add(paragraph.name) }
                .sortedByDescending(ParagraphContainer::score)
                .forEachIndexed { index, paragraph ->
                    writer.write("${query} Q0 ${paragraph.name} ${index + 1} ${paragraph.score} Paragraph\n")
                }
        }
        writer.flush()
        writer.close()
    }

    fun rescoreOrigins(query: QueryContainer) {
        query.contextEntities.forEach { cEntity ->
            cEntity.rescore()
            val q = AnalyzerFunctions.createQuery(cEntity.doc().unigrams(), IndexFields.FIELD_UNIGRAM.field)
            val scoreMap = query.originParagraphs.first().searcher.search(q, 5000)
                .scoreDocs
                .map { sd ->
                    sd.doc to sd.score.toDouble() }
                .toMap()
            query.originParagraphs.forEach { origin ->
                origin.score += scoreMap[origin.docId] ?: 0.0
            }
        }


    }

    fun writeSectionsToFile(queries: List<QueryContainer>) {
        val writer = File("section_results.run").bufferedWriter()
        queries.forEach { container ->
            val seen = HashSet<String>()
            val query = container.query
            container.sections.forEach(SectionContainer::rescore)
            container.sections
                .filter { section -> seen.add(section.name) }
                .sortedByDescending(SectionContainer::score)
                .forEachIndexed { index, section ->
                    writer.write("${query} Q0 ${section.name} ${index + 1} ${section.score} Section\n")
                }
        }
        writer.flush()
        writer.close()
    }

    fun renormalizeFeatures(entities: List<EntityContainer>) {
        with(formatter) {
            if (entities.isEmpty()) return

            val nFeatures = entities.first().features.size
            (0 until nFeatures).forEach { fIndex ->
                entities.map { it.features[fIndex].unnormalizedScore }
                    .run { normalizeResults(this, NormType.ZSCORE) }
                    .forEachIndexed { eIndex, newScore -> entities[eIndex].features[fIndex].score = newScore }
            }
            entities.map(EntityContainer::rescore)

        }
    }

    fun sendParagraphToEntity(qContainer: QueryContainer) {
        qContainer.entities.forEach { it.rescore() }
        qContainer.paragraphs.forEach { pContainer ->
            val newScore = qContainer.jointDistribution
                .parToEnt[pContainer.index]!!
                .entries.sumByDouble { (eIndex, freq) ->
                qContainer.entities[eIndex].score * freq
            }

            pContainer.features.forEach { feature -> feature.score = newScore; feature.weight = 1 / pContainer.features.size.toDouble() }
        }
    }

    fun sendSectionToEntity(qContainer: QueryContainer) {
        qContainer.entities.forEach { it.rescore() }
        qContainer.sections.forEach { sContainer ->
            val newScore = qContainer.jointDistribution
                .secToPar[sContainer.index]!!
                .entries.sumByDouble { (pIndex, pFreq) ->
                qContainer.jointDistribution.parToEnt[pIndex]!!
                    .entries
                    .sumByDouble { (eIndex, eFreq) ->
                        qContainer.entities[eIndex].score * eFreq * pFreq
                    }
            }

            sContainer.features.forEach { feature -> feature.score = newScore; feature.weight = 1 / sContainer.features.size.toDouble() }
        }
    }


    fun foldOverFeature(fIndex: Int, f: (List<Double>) -> List<Double>) = with(formatter) {
        val pFeatures = queryContainers.flatMap { qContainer -> qContainer.paragraphs.map { it.features[fIndex] } }
        val eFeatures = queryContainers.flatMap { qContainer -> qContainer.entities.map { it.features[fIndex] } }
        val sFeatures = queryContainers.flatMap { qContainer -> qContainer.sections.map { it.features[fIndex] } }

        listOf(pFeatures, eFeatures, sFeatures)
            .forEach { featureList ->

                featureList.map { it.unnormalizedScore }
                    .run(f)
                    .zip(featureList)
                    .forEach { (newScore, feature) -> feature.score = newScore }

            }
    }


    fun retrieveOrigin(queryData: QueryData) {
        queryData.entityContainers.forEach { eContainer ->
            eContainer.rescore()
            val score = eContainer.score

        }

    }

    /**
     * Function: writeToRankLibFile
     * Desciption: Writes features to a RankLib-compatible file.
     * @param outName: Name of the file to write the results to.
     */
    fun writeToRankLibFile(outName: String) = with(formatter) {
        val file = File(outName).bufferedWriter()
        val onlyParagraph = File("ony_paragraph.txt").bufferedWriter()
        val onlyEntity = File("ony_entity.txt").bufferedWriter()
        val onlySection = File("ony_section.txt").bufferedWriter()


        queryContainers
            .shuffled(sharedRand)
            .flatMap { queryContainer ->
                //                val (pars, secs, ents) = queryContainer.transformFeatures()
//                val combined = listOf(queryContainer.entities.map(EntityContainer::toString),
//                        pars.map { it.toString() },
//                        secs.map { it.toString() })
                val combined = listOf(queryContainer.entities.map(EntityContainer::toString),
                        queryContainer.paragraphs.map(ParagraphContainer::toString))
//                        queryContainer.sections.map(SectionContainer::toString))
                    .shuffled()
                    .flatten()
//                if (isHomogenous) combined.shuffled() else combined
//                (queryContainer.transformFeatures() + queryContainer.entities)
//                    .map(EntityContainer::toString)
//                    .shuffled()
//                val (pars, secs, ents) = queryContainer.transformFeatures()
//                val combined = pars + secs + ents
//                renormalizeFeatures(combined)
//                combined.shuffled()
//                    .map(EntityContainer::toString)
                combined
            }
            .joinToString(separator = "\n")
            .let { file.write(it + "\n"); }

        queryContainers
            .shuffled(sharedRand)
//            .flatMap { queryContainer ->
//                val (pars, secs, ents) = queryContainer.transformFeatures()
//                renormalizeFeatures(pars)
//                pars.map { it.toString() }  }
            .flatMap { queryContainer -> queryContainer.paragraphs.shuffled(sharedRand).map(ParagraphContainer::toString) }
            .joinToString(separator = "\n")
            .let { onlyParagraph.write(it + "\n"); }


        queryContainers
            .flatMap { queryContainer -> queryContainer.entities.sortedByDescending { it.name } }
            .joinToString(separator = "\n", transform = EntityContainer::toString)
            .let { onlyEntity.write(it) }
        //Avg.	|   0.1641	|  0.2245

        queryContainers
            .flatMap { queryContainer -> queryContainer.sections }
            .joinToString(separator = "\n", transform = SectionContainer::toString)
            .let { onlySection.write(it) }

//        queryContainers.forEach(featureDatabase::writeFeatures)
//        featureDatabase.writeFeatures(queryContainers)
//        if (!useSavedFeatures)
//            featureDatabase.writeSharedFeatures()
        file.close()
        onlyEntity.close()
        onlyParagraph.close()
        onlySection.close()
//        writeSimulation()
    }

    fun writeSimulation() = with(formatter) {
        val onlySim = File("ony_sim.txt").bufferedWriter()

        val qc = queryContainers.take(10)
            .map {
                it.entities.groupBy { it.name }
            }



        (0 until 10).forEach { index ->
            qc.forEach { docs ->
                docs.map { it.value.shuffled(sharedRand).first().toCustomString(index) }
                    .joinToString(separator = "\n")
                    .let { onlySim.write(it) }
            }
        }

        onlySim.close()
    }

    fun writeTargetParagraphs() {
    }


    /**
     * Function: writeQueriesToFile
     * Desciption: Uses lucene formatter to write current queries to trec-car compatible file
     * @param outName: Name of the file to write the results to.
     */
    fun writeQueriesToFile(outName: String) = with(formatter) {
        //        queryContainers.forEach { qContainer ->
//            val (pars, secs, ents) = qContainer.transformFeatures()
//            val combined = pars + secs + ents
//            renormalizeFeatures(combined)
//            renormalizeFeatures(secs)
//            renormalizeFeatures(pars)
//            secs.forEach { sec ->
//                qContainer.sections[sec.index].features = sec.features
//            }
//            pars.forEach { par ->
//                qContainer.paragraphs[par.index].features = par.features
//            }

//        }

//        queryContainers.forEach { qContainer ->
//            sendParagraphToEntity(qContainer)
//            sendSectionToEntity(qContainer)
//        }

        writeParagraphsToFile(queryContainers)
        writeEntitiesToFile(queryContainers)
//        writeSectionsToFile(queryContainers)
//        writeOriginParagraphsToFile(queryContainers)
    }
}


