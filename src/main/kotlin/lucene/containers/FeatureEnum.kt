package lucene.containers

import experiment.FeatureType
import experiment.FeatureType.*
import experiment.KotlinRanklibFormatter
import experiment.NormType
import features.document.DocumentRankingFeatures
import features.entity.EntityRankingFeatures
import features.shared.SharedFeatures
import features.subobject.SubObjectFeatures

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

    DOC_JOINT_UNIGRAM_FIELD(text = "doc_joint_unigram_field",
            type = PARAGRAPH,
            func = DocumentRankingFeatures::addJointUnigramField),

    DOC_JOINT_BIGRAM_FIELD(text = "doc_joint_bigram_field",
            type = PARAGRAPH,
            func = DocumentRankingFeatures::addJointBigramField),

    DOC_JOINT_WINDOWED_FIELD(text = "doc_joint_windowed_field",
            type = PARAGRAPH,
            func = DocumentRankingFeatures::addJointWindowedField),

    DOC_UNION_UNIGRAM_FIELD(text = "doc_union_unigram_field",
            type = PARAGRAPH,
            func = DocumentRankingFeatures::addUnionUnigramField),

    DOC_UNION_BIGRAM_FIELD(text = "doc_union_bigram_field",
            type = PARAGRAPH,
            func = DocumentRankingFeatures::addUnionBigramField),

    DOC_UNION_WINDOWED_FIELD(text = "doc_union_windowed_field",
            type = PARAGRAPH,
            func = DocumentRankingFeatures::addUnionWindowedField),

    DOC_JOINT_ENTITIES_FIELD(text = "doc_joint_entities_field",
            type = PARAGRAPH,
            func = DocumentRankingFeatures::addJointEntityField),

    SHARED_LINKS(text = "shared_links",
            type = SHARED,
            func = SharedFeatures::addSharedEntityLinks),

    SHARED_BM25(text = "shared_bm25",
            type = SHARED,
            func = SharedFeatures::addSharedBM25Abstract),

    SHARED_META(text = "shared_meta",
            type = SHARED,
            func = SharedFeatures::addSharedMeta),

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

//    ENTITY_SDM(text = "entity_sdm_abstract",
//            type = ENTITY,
//            func = EntityRankingFeatures::addSDMAbstract),

    ENTITY_INLINKS_FIELD(text = "entity_inlinks_field",
            type = ENTITY,
            func = EntityRankingFeatures::addInlinksField),

    ENTITY_OUTLINKS_FIELD(text = "entity_outlinks_field",
            type = ENTITY,
            func = EntityRankingFeatures::addOutlinksField),

    ENTITY_REDIRECTS_FIELD(text = "entity_redirects_field",
            type = ENTITY,
            func = EntityRankingFeatures::addRedirectField),

    ENTITY_CATEGORIES_FIELD(text = "entity_categories_field",
            type = ENTITY,
            func = EntityRankingFeatures::addCategoriesField),

    ENTITY_SECTIONS_FIELD(text = "entity_sections_field",
            type = ENTITY,
            func = EntityRankingFeatures::addSections),

    ENTITY_DISAMBIG_FIELD(text = "entity_disambig_field",
            type = ENTITY,
            func = EntityRankingFeatures::addDisambigField),

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

    PFUNCTOR_UNIGRAM_UNIGRAM(text = "functor_punigram_eunigram",
            type = PARAGRAPH_FUNCTOR,
            func = SubObjectFeatures::addPUnigramToEUnigram),

    PFUNCTOR_UNIGRAM_CATEGORY(text = "functor_punigram_ecategory",
            type = PARAGRAPH_FUNCTOR,
            func = SubObjectFeatures::addPUnigramToECategory),

    PFUNCTOR_UNIGRAM_INLINKS(text = "functor_punigram_einlinks",
            type = PARAGRAPH_FUNCTOR,
            func = SubObjectFeatures::addPUnigramToEInlinks),

    PFUNCTOR_UNIGRAM_OUTLINKS(text = "functor_punigram_eoutlinks",
            type = PARAGRAPH_FUNCTOR,
            func = SubObjectFeatures::addPUnigramToEOutlinks),

    PFUNCTOR_UNIGRAM_SECTON(text = "functor_punigram_esection",
            type = PARAGRAPH_FUNCTOR,
            func = SubObjectFeatures::addPUnigramToESection),

    PFUNCTOR_UNIGRAM_DISAMBIG(text = "functor_punigram_edisambig",
            type = PARAGRAPH_FUNCTOR,
            func = SubObjectFeatures::addPUnigramToEDisambig),

    PFUNCTOR_UNIGRAM_REDIRECTS(text = "functor_punigram_eredirects",
            type = PARAGRAPH_FUNCTOR,
            func = SubObjectFeatures::addPUnigramToERedirects),


    PFUNCTOR_LINK_FREQ(text = "functor_punigram_einlinks",
            type = PARAGRAPH_FUNCTOR,
            func = SubObjectFeatures::addLinkFreq),
}