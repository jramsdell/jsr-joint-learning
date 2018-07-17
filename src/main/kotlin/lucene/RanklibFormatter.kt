package experiment

import browser.BrowserPage
import browser.BrowserParagraph
import browser.BrowserSection
import features.shared.SharedFeature
import lucene.*
import lucene.containers.*
import lucene.indexers.IndexFields
import org.apache.lucene.search.TopDocs
import java.io.File
import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarStyle
import utils.*
import utils.lucene.getTypedSearcher
import utils.misc.CONTENT
import utils.misc.filledArray
import utils.misc.sharedRand
import utils.parallel.forEachParallelQ
import utils.stats.countDuplicates
import utils.stats.defaultWhenNotFinite
import java.lang.Double.sum
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


// .18 / .13
// 5859
//MAP on training data: 0.3467
//MAP on validation data: 0.2412

// 1777 / 1301


// 0.6618 / 0.4096

// MAP on training data: 0.3231
//MAP on validation data: 0.3105

// 0.2390 / 0.1692


/**
 * Enum: lucene.NormType
 * Description: Determines if the the values of an added feature should be normalized
 */
enum class NormType {
    NONE,           // No normalization should be used
    SUM,            // Value is divided by total sum of all values in lucene
    ZSCORE,         // Zscore is calculated for all values in lucene
    LINEAR          // Value is normalized to: (value - min) / (max - min)
}

enum class FeatureType {
    PARAGRAPH,
    PARAGRAPH_TO_ENTITY,
    ENTITY,
    ENTITY_TO_PARAGRAPH,
    SHARED,
    PARAGRAPH_FUNCTOR,
    SECTION
}

/**
 * Class: lucene.KotlinRanklibFormatter
 * Description: Used to apply scoring functions to queries (from .cbor file) and print results as features.
 *              The results file is compatible with RankLib.
 * @param queries: List of lucene string/Topdocs (obtained by QueryRetriever)
 * @param paragraphQrelLoc: Location of the .qrels file (if none is given, then paragraphs won't be marked as relevant)
 * @param indexSearcher: An IndexSearcher for the Lucene index directory we will be querying.
 */
class KotlinRanklibFormatter(paragraphQueryLoc: String,
                             paragraphIndexLoc: String,
                             paragraphQrelLoc: String,
                             entityIndexLoc: String,
                             entityQrelLoc: String = "",
                             sectionIndexLoc: String = "",
                             sectionQrelLoc: String = "",
                             contextEntityLoc: String = ""
                             ) {

    /**
     * @param indexLoc: A string pointing to location of index (used to create IndexSearcher if none is given)
     */
//    constructor(queryLocation: String, qrelLoc: String, indexLoc: String) :
//            this(queryLocation, qrelLoc, getIndexSearcher(indexLoc))


    val useJointDist = false
    val useSavedFeatures = false
    var limit: Int? = null
    val isHomogenous = false
    val paragraphSearcher = getTypedSearcher<IndexType.PARAGRAPH>(paragraphIndexLoc)
    val entitySearcher = getTypedSearcher<IndexType.ENTITY>(entityIndexLoc)
    val sectionSearcher  =
//            if ( sectionIndexLoc == "") paragraphSearcher
             getTypedSearcher<IndexType.SECTION>(sectionIndexLoc)

    val contextEntitySearcher =  getTypedSearcher<IndexType.CONTEXT_ENTITY>(contextEntityLoc)


    val queryContainers = createQueryContainers( paragraphQueryLoc, paragraphQrelLoc, entityQrelLoc, sectionQrelLoc, isHomogenous)



//    val queryRetriever = QueryRetriever(paragraphSearcher, false, limit = limit)
//    val queries = queryRetriever.getSectionQueries(paragraphQueryLoc, doBoostedQuery = false)
//    val queries = queryRetriever.getPageQueries(paragraphQueryLoc, doBoostedQuery = false)
//    val paragraphRetriever = ParagraphRetriever(paragraphSearcher, queries, paragraphQrelLoc, false,
////            doFiltered = paragraphQrelLoc != "")
//            doFiltered = false)
//    val entityRetriever = EntityRetriever(entitySearcher, paragraphSearcher, queries, entityQrelLoc, paragraphRetrieve = paragraphRetriever)
//    val sectionRetriever = SectionRetriever(sectionSearcher, paragraphSearcher, queries, sectionQrelLoc, paragraphRetrieve = paragraphRetriever)
//        .apply { paragraphRetriever.updateParagraphContainers(this) }
    val featureDatabase = FeatureDatabase2()



//    private val queryContainers =
//        queries.withIndex().pmap { indexedQuery ->
//
//            val index = indexedQuery.index
//            val (query, tops) = indexedQuery.value
//            val paragraphContainers = paragraphRetriever.paragraphContainers[index]
//            val entityContainers = entityRetriever.entityContainers.get(index)
//            val sectionContainers = sectionRetriever.sectionContainers.get(index)
////            val entityContainers = emptyList<EntityContainer>()
//
//            QueryContainer(
//                    query = query,
//                    paragraphs = paragraphContainers,
//                    entities = entityContainers,
//                    sections = sectionContainers,
//                    queryData = createQueryData(query, tops,
//                            paragraphContainers = paragraphContainers,
//                            entityContainers = entityContainers,
//                            isJoint = useJointDist,
//                            sectionContainers = sectionContainers)
//            )
//        }



    private fun createQueryContainers(queryLocation: String,
                                      paragraphQrelLoc: String, entityQrelLoc: String, sectionQrelLoc: String,
                                      isHomogenous: Boolean): List<QueryContainer> {
        val retriever = CombinedRetriever(
                paragraphSearcher = paragraphSearcher,
                sectionSearcher = sectionSearcher,
                entitySearcher = entitySearcher,
                limit = limit,
                entityQrelLoc = entityQrelLoc,
                paragraphQrelLoc = paragraphQrelLoc,
                sectionQrelLoc = sectionQrelLoc,
                isHomogenous = isHomogenous,
                contextEntitySearcher = contextEntitySearcher
        )

        return retriever.createQueryContainers(queryLocation)
    }


    /**
     * Function: normSum
     * Description: Normalizes list of doubles by each value equal to itself divided by the total
     * @see NormType
     */
    private fun normSum(values: List<Double>): List<Double> =
            values.sum()
                .let { total -> values.map { value ->  value / total } }


    /**
     * Function: normZscore
     * Description: Calculates zscore for doubles in list
     * @see NormType
     */
    private fun normZscore(values: List<Double>): List<Double> {
        val mean = values.average()
        val std = Math.sqrt(values.sumByDouble { Math.pow(it - mean, 2.0) })
        return values.map { ((it - mean) / std) }
    }

    /**
     * Function: normLinear
     * Description: Linearizes each value: (value - min) / (max - min)
     * @see NormType
     */
    private fun normLinear(values: List<Double>): List<Double> {
        val minValue = values.min() ?: return emptyList()
        val maxValue = values.max() ?: return emptyList()
        return values.map { value -> (value - minValue) / (maxValue - minValue) }
    }


    /**
     * Function: normalizeResults
     * Description: Normalizes a list of doubles according to the lucene.NormType
     * @see NormType
     */
    private fun normalizeResults(values: List<Double>, normType: NormType): List<Double> {
        return when (normType) {
            NormType.NONE   -> values
            NormType.SUM    -> normSum(values)
            NormType.ZSCORE -> normZscore(values)
            NormType.LINEAR -> normLinear(values)
        }.map { it.defaultWhenNotFinite(0.0) }
    }


    /**
     * Function: addFeature
     * Description: Accepts a functions that take a string (lucene string) and TopDocs (from BM25 lucene)
     *              The function must return a list of Doubles in the same order as the documents they score
     *              The values are stored as a feature for later reranking or for creating a RankLib file.
     *
     * Note: The function, f, is mapped onto all of the queries in parallel. Make sure it is thread-safe.
     *
     * @param f: Function (or method reference) that scores each document in TopDocs and returns it as a list of doubles
     * @param normType: lucene.NormType determines the type of normalization (if any) to apply to the new document scores.
     * @param weight: The final list of doubles is multiplies by this weight
     */


    fun addFeature3(featureEnum: FeatureEnum, weight:Double = 1.0,
                    normType: NormType = NormType.NONE, f: (QueryData, SharedFeature) -> Unit) {

        println("Weight was: $weight")
//        val featureMap = if (useSavedFeatures) featureDatabase.getFeature(featureEnum) else null
        val bar = ProgressBar("Feature Progress", queryContainers.size.toLong(), ProgressBarStyle.ASCII)
        bar.start()
        val lock = ReentrantLock()
//        val curSim = paragraphSearcher.getSimilarity(true)
//        val curSimEntity = entitySearcher.getSimilarity(true)

        queryContainers.forEachParallelQ(60, 80) { qc ->

            // Apply feature and update counter
            var sf = SharedFeature(paragraphScores = filledArray(qc.paragraphs.size, 0.0),
                    entityScores = filledArray(qc.entities.size, 0.0),
                    sectionScores = filledArray(qc.sections.size, 0.0))


            if (featureEnum.type == FeatureType.PARAGRAPH_FUNCTOR) {
                qc.paragraphs.forEachIndexed { index, paragraphContainer ->
                    sf.paragraphScores[index] = paragraphContainer.isRelevant.toDouble()
                }

                qc.entities.forEachIndexed { index, entityContainer ->
                    sf.entityScores[index] = entityContainer.isRelevant.toDouble()
                }
            }

//            if (featureMap != null)
//                sf = featureMap[qc.query]!!
//            else
//                f(qc.queryData, sf)

//            if (!useSavedFeatures) {
//                featureDatabase.storeFeature(qc.query,featureEnum, sf.makeCopy())
//            }


            // Turn paragraph features into entity features
            if (featureEnum.type == FeatureType.PARAGRAPH && useJointDist) {
                sf.paragraphScores.forEachIndexed { index, score ->
                    qc.jointDistribution.parToEnt[index]!!
                        .entries
                        .forEach { (entityIndex, freq) ->
                            sf.entityScores[entityIndex] += freq * sf.paragraphScores[index]
                        }

                    qc.jointDistribution.parToSec[index]!!
                        .entries
                        .forEach { (secIndex, freq) ->
                            sf.sectionScores[secIndex] += freq * sf.paragraphScores[index]
                        }
                }

            }

            // Turn section features into other features
            if (featureEnum.type == FeatureType.SECTION && useJointDist) {
                val tempScores = sf.sectionScores.map { it }
//                sf.sectionScores.fill(0.0)

                sf.sectionScores.forEachIndexed { index, score ->
                    qc.jointDistribution.secToPar[index]!!
                        .entries
                        .forEach { (parIndex, freq) ->
                            sf.paragraphScores[parIndex] += freq * sf.sectionScores[index]
                        }

                }

                sf.paragraphScores.forEachIndexed { index, score ->
                    qc.jointDistribution.parToEnt[index]!!
                        .entries
                        .forEach { (entityIndex, freq) ->
                            sf.entityScores[entityIndex] += freq * sf.paragraphScores[index]
                        }
                }



            }

            // Turn entity features into paragraph features
            if (featureEnum.type == FeatureType.ENTITY && useJointDist) {
                sf.entityScores.forEachIndexed { index, score ->
                    qc.jointDistribution.entToPar[index]!!
                        .entries
                        .forEach { (parIndex, freq) ->
                            sf.paragraphScores[parIndex] += freq * sf.entityScores[index]
                        }
                }

                sf.paragraphScores.forEachIndexed { index, score ->
                    qc.jointDistribution.parToSec[index]!!
                        .entries
                        .forEach { (secIndex, freq) ->
                            sf.sectionScores[secIndex] += freq * sf.paragraphScores[index]
                        }
                }

            }



            addBothScores(qc, sf, weight, normType, featureEnum)
            lock.withLock { bar.step() }
        }
        bar.stop()
//        paragraphSearcher.setSimilarity(curSim)
//        entitySearcher.setSimilarity(curSimEntity)
    }



    private fun addBothScores(qc: QueryContainer, sf: SharedFeature, weight: Double, normType: NormType,
                              featureEnum: FeatureEnum) {


        val normEntities = normalizeResults(sf.entityScores, normType)
        val normParagraphs = normalizeResults(sf.paragraphScores, normType)
        val normSection = normalizeResults(sf.sectionScores, normType)

        qc.entities.forEachIndexed { index, eContainer ->
            val score = normEntities[index]
            val unnormalizedScore = sf.entityScores[index]
            eContainer.features.add(FeatureContainer(score, unnormalizedScore, weight, featureEnum))
        }

        qc.paragraphs.forEachIndexed { index, pContainer ->
            val score = normParagraphs[index]
            val unnormalizedScore = sf.paragraphScores[index]
            pContainer.features.add(FeatureContainer(score, unnormalizedScore, weight, featureEnum))
        }

        qc.sections.forEachIndexed { index, sContainer ->
            val score = normSection[index]
            val unnormalizedScore = sf.sectionScores[index]
            sContainer.features.add(FeatureContainer(score, unnormalizedScore, weight, featureEnum))
        }

//        qc.entities
//            .zip(sf.entityScores.run { normalizeResults(this, normType) })
//            .forEach { (entity, score) ->
//                entity.features += FeatureContainer(score, 0.0, weight, featureEnum)}
//
//        qc.paragraphs
//            .zip(sf.paragraphScores.run { normalizeResults(this, normType) })
//            .forEach { (paragraph, score) ->
//                paragraph.features += FeatureContainer(score, 0.0, weight, featureEnum)
//            }
//
//        qc.sections
//            .zip(sf.sectionScores.run { normalizeResults(this, normType) })
//            .forEach { (section, score) ->
//                section.features += FeatureContainer(score, 0.0, weight, featureEnum)
//            }
    }





    /**
     * Function: writeToRankLibFile
     * Desciption: Writes features to a RankLib-compatible file.
     * @param outName: Name of the file to write the results to.
     */
    fun writeToRankLibFile(outName: String) {
        val file = File(outName).bufferedWriter()
        val onlyParagraph = File("ony_paragraph.txt").bufferedWriter()
        val onlyEntity = File("ony_entity.txt").bufferedWriter()
        val onlySection = File("ony_section.txt").bufferedWriter()


        queryContainers.shuffled(sharedRand)
            .flatMap { queryContainer ->
                val combined = queryContainer.entities.map(EntityContainer::toString)  +
                        queryContainer.paragraphs.map(ParagraphContainer::toString) +
            queryContainer.sections.map(SectionContainer::toString)
                if (isHomogenous) combined.shuffled() else combined
//                (queryContainer.transformFeatures() + queryContainer.entities)
//                    .map(EntityContainer::toString)
//                    .shuffled()
//                val (pars, secs, ents) = queryContainer.transformFeatures()
//                val combined = pars + secs + ents
//                renormalizeFeatures(combined)
//                combined.shuffled()
//                    .map(EntityContainer::toString)


            }
            .joinToString(separator = "\n")
            .let { file.write(it + "\n"); }

        queryContainers.shuffled(sharedRand)
            .flatMap { queryContainer -> queryContainer.paragraphs.map(ParagraphContainer::toString)  }
            .joinToString(separator = "\n")
            .let { onlyParagraph.write(it + "\n"); }


        queryContainers.shuffled(sharedRand)
            .flatMap { queryContainer -> queryContainer.entities  }
            .joinToString(separator = "\n", transform = EntityContainer::toString)
            .let { onlyEntity.write(it) }

        queryContainers.shuffled(sharedRand)
            .flatMap { queryContainer -> queryContainer.sections  }
            .joinToString(separator = "\n", transform = SectionContainer::toString)
            .let { onlySection.write(it) }

//        queryContainers.forEach(featureDatabase::writeFeatures)
//        featureDatabase.writeFeatures(queryContainers)
        if (!useSavedFeatures)
            featureDatabase.writeSharedFeatures()
        file.close()
        onlyEntity.close()
        onlyParagraph.close()
        onlySection.close()
    }



    /**
     * Function: writeQueriesToFile
     * Desciption: Uses lucene formatter to write current queries to trec-car compatible file
     * @param outName: Name of the file to write the results to.
     */
    fun writeQueriesToFile(outName: String) {
//        queryContainers.forEach { qContainer ->
//            val (pars, secs, ents) = qContainer.transformFeatures()
//            val combined = pars + secs + ents
//            renormalizeFeatures(combined)
////            renormalizeFeatures(secs)
////            renormalizeFeatures(pars)
//            secs.forEach { sec ->
//                qContainer.sections[sec.index].features = sec.features
//            }
//            pars.forEach { par ->
//                qContainer.paragraphs[par.index].features = par.features
//            }
//
//        }

//        queryContainers.forEach { qContainer ->
//            sendParagraphToEntity(qContainer)
//            sendSectionToEntity(qContainer)
//        }

        writeParagraphsToFile(queryContainers)
        writeEntitiesToFile(queryContainers)
        writeSectionsToFile(queryContainers)
    }

    fun doJointDistribution() {
        if (!useJointDist)
            return

        queryContainers.forEach { qContainer ->
            //                val jointDistribution = JointDistribution.createFromFunctor(qContainer.queryData)
                val jointDistribution =  JointDistribution.createJointDistribution(qContainer.queryData)
//            val jointDistribution =  JointDistribution.createMonoidDistribution(qContainer.query, qContainer.queryData)
            qContainer.jointDistribution = jointDistribution
//            analyzeStuff(qContainer.queryData)
        }
    }


    fun doPullback(field: IndexFields) {
//        queryContainers.forEach { qContainer ->
//            qContainer.entities.forEach { eContainer ->
//                val unigrams = eContainer.doc().unigrams().split(" ")
//                    .flatMap { it.windowed(2) }
//                val umap = unigrams.countDuplicates().mapValues { it.value.toDouble() }
//                umap.forEach { (unigram, freq) -> eContainer.dist.merge(unigram, freq, ::sum) }
//            }

        queryContainers.forEach { qContainer ->
            qContainer.paragraphs.forEach { pContainer ->
//                val unigrams = pContainer.doc().unigrams().split(" ")
                val text = pContainer.doc().text()
                val unigrams = AnalyzerFunctions.createTokenList(text, AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)
                val umap = unigrams.countDuplicates().mapValues { it.value.toDouble() }
                umap.forEach { (unigram, freq) -> pContainer.dist.merge(unigram, freq, ::sum) }
            }

            val eIds = qContainer.entities.mapIndexed { index, docContainer -> docContainer.name to index  }
                .toMap()

            val pIds = qContainer.paragraphs.mapIndexed { index, docContainer -> docContainer.name to index  }
                .toMap()

//            val sIds = qContainer.sections.mapIndexed { index, docContainer -> docContainer.name to index  }
//                .toMap()

//            qContainer.paragraphs.forEach { pContainer ->
//                val entities = pContainer.doc().entities().split(" ")
//                    entities.mapNotNull { entity -> eIds[entity] }
//                        .map { eIndex ->
//                            val entity = qContainer.entities[eIndex]
//                            entity.dist.forEach { (unigram, freq) -> pContainer.dist.merge(unigram, freq, ::sum) }
//                        }
//            }

            qContainer.sections.forEach { sContainer: SectionContainer ->
                val paragraphs = sContainer.doc().paragraphs().split(" ")
                paragraphs.mapNotNull { paragraph: String -> pIds[paragraph] }
                    .map { pIndex: Int ->
                        val paragraph = qContainer.paragraphs[pIndex]
                        paragraph.dist.forEach { (unigram, freq) -> sContainer.dist.merge(unigram, freq, ::sum) }
                    }
            }

//            qContainer.sections.forEach { sContainer ->
//                sContainer.dist = sContainer.dist.normalize().toList().toHashMap()
//            }
//
//            qContainer.paragraphs.forEach { sContainer ->
//                sContainer.dist = sContainer.dist.normalize().toList().toHashMap()
//            }


        }
    }

    fun initialize() {
//        doPullback(IndexFields.FIELD_UNIGRAM)
        doJointDistribution()
    }

    fun writeHtml() {
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
                val results = qContainer.paragraphs.map { it to it.score * (qContainer.jointDistribution.parToSec[it.index]?.get(sIndex) ?: 0.0) }
                    .asSequence()
                    .sortedByDescending { it.second }
                    .filter { it.second > 0.0 && it.first.index !in seen }
                    .take(5 )
                    .map { it.first }
                    .toList()
                    .onEach { seen.add(it.index) }
                section to results
            }.map { (section, paragraphs) ->
                val browserParagraphs = paragraphs.map { BrowserParagraph(it.doc().text()) }
                BrowserSection(section.doc().id(), browserParagraphs) }

            val qname = qContainer.query
            BrowserPage(qname, browserSections)
                .write("html_pages/$qIndex.html")



        }
    }

    fun writeEntitiesToFile(queries: List<QueryContainer>) {
        val writer = File("entity_results.run").bufferedWriter()
        queries.forEach { container ->
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
        queries.forEach { container ->
            val seen = HashSet<String>()
            val query = container.query
            container.paragraphs.forEach(ParagraphContainer::rescore)
            container.paragraphs
                .filter { paragraph -> seen.add(paragraph.name) }
                .sortedByDescending(ParagraphContainer::score)
                .forEachIndexed { index, paragraph ->
                    writer.write("${query} Q0 ${paragraph.name} ${index + 1} ${paragraph.score} Paragraph\n")
                }
        }
        writer.flush()
        writer.close()
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
        if (entities.isEmpty()) return
        return

        val nFeatures = entities.first().features.size
        (0 until nFeatures).forEach { fIndex ->
            entities.map { it.features[fIndex].unnormalizedScore }
                .run { normalizeResults(this, NormType.ZSCORE) }
                .forEachIndexed { eIndex, newScore -> entities[eIndex].features[fIndex].score = newScore  }
        }
        entities.map(EntityContainer::rescore)

    }

    fun sendParagraphToEntity(qContainer: QueryContainer) {
        qContainer.entities.forEach { it.rescore() }
        qContainer.paragraphs.forEach { pContainer ->
            val newScore = qContainer.jointDistribution
                .parToEnt[pContainer.index]!!
                .entries.sumByDouble { (eIndex, freq) ->
                    qContainer.entities[eIndex].score * freq }

            pContainer.features.forEach { feature -> feature.score = newScore; feature.weight = 1/pContainer.features.size.toDouble() }
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
                    }}

            sContainer.features.forEach { feature -> feature.score = newScore; feature.weight = 1/sContainer.features.size.toDouble() }
        }
    }

}



