package lucene.containers

import experiment.FeatureType
import experiment.FeatureType.*
import experiment.KotlinRanklibFormatter
import experiment.NormType
import features.document.DocumentRankingFeatures
import features.entity.EntityRankingFeatures
import features.shared.SharedFeatures

enum class FeatureEnum(val text: String, val type: FeatureType, val func: (KotlinRanklibFormatter, Double, NormType) -> Unit) {
    DOC_BM25(text = "doc_bm25",
            type = PARAGRAPH,
            func = DocumentRankingFeatures::addBM25Document),

    DOC_SDM(text = "doc_sdm",
            type = PARAGRAPH,
            func = DocumentRankingFeatures::addSDMDocument),

    DOC_BOOSTED_UNIGRAM(text = "doc_boosted_unigram",
            type = PARAGRAPH,
            func = DocumentRankingFeatures::addBM25BoostedUnigram),

    DOC_BOOSTED_BIGRAM(text = "doc_boosted_bigram",
            type = PARAGRAPH,
            func = DocumentRankingFeatures::addBM25BoostedBigram),

    DOC_BOOSTED_WINDOW(text = "doc_boosted_window",
            type =  PARAGRAPH,
            func = DocumentRankingFeatures::addBM25BoostedWindowedBigram),

    DOC_BOOSTED_COMBINED(text = "doc_boosted_window",
            type = PARAGRAPH,
            func = DocumentRankingFeatures::addCombinedBoostedGram),

    SHARED_LINKS(text = "shared_links",
            type = SHARED,
            func = SharedFeatures::addSharedEntityLinks),

    SHARED_BM25(text = "shared_bm25",
            type = SHARED,
            func = SharedFeatures::addSharedBM25Abstract),

    SHARED_DIRICHLET(text = "shared_dirichlet",
            type = SHARED,
            func = SharedFeatures::addSharedDirichlet),

    SHARED_BOOSTED_UNIGRAM(text = "shared_boosted_unigram",
            type = SHARED,
            func = SharedFeatures::addSharedBoostedUnigram),

    SHARED_BOOSTED_BIGRAM(text = "shared_boosted_bigram",
            type = SHARED,
            func = SharedFeatures::addSharedBoostedBigram),

    SHARED_BOOSTED_WINDOWED(text = "shared_boosted_windowed",
            type = SHARED,
            func = SharedFeatures::addSharedBoostedWindowed),

    SHARED_RDF(text = "shared_rdf",
            type = SHARED,
            func = SharedFeatures::addSharedRdf),

    SHARED_UNI_LIKE(text = "shared_unigram_likelihood",
            type = SHARED,
            func = SharedFeatures::addSharedUnigramLikelihood),

    ENTITY_BM25(text = "entity_bm25_abstract",
            type = ENTITY,
            func = EntityRankingFeatures::addBM25Abstract),

    ENTITY_DIRICHLET(text = "entity_bm25_abstract",
            type = ENTITY,
            func = EntityRankingFeatures::addDirichletAbstract),

    ENTITY_SDM(text = "entity_sdm_abstract",
            type = ENTITY,
            func = EntityRankingFeatures::addSDMAbstract),

    ENTITY_TOP25_FREQ(text = "entity_top25_freq",
            type = ENTITY,
            func = EntityRankingFeatures::addTop25Freq),


    ENTITY_BOOSTED_UNIGRAM(text = "entity_boosted_unigram",
            type = ENTITY,
            func = EntityRankingFeatures::addBM25BoostedUnigram),

    ENTITY_BOOSTED_BIGRAM(text = "entity_boosted_bigram",
            type = ENTITY,
            func = EntityRankingFeatures::addBM25BoostedBigram),

    ENTITY_BOOSTED_WINDOW(text = "entity_boosted_window",
            type = ENTITY,
            func = EntityRankingFeatures::addBM25BoostedWindowedBigram),
}