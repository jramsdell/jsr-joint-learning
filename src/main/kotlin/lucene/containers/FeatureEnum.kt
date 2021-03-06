package lucene.containers

import lucene.FeatureType
import lucene.FeatureType.*
import lucene.joint.JointRunner
import lucene.NormType
import features.context.ContextEntityRankingFeatures
import features.document.DocumentRankingFeatures
import features.entity.EntityRankingFeatures
import features.section.SectionRankingFeatures
import features.shared.SharedFeatures
import features.subobject.SubObjectFeatures
import lucene.KotlinRanklibFormatter

enum class FeatureEnum(val text: String, val type: FeatureType, val func: (KotlinRanklibFormatter, Double, NormType) -> Unit) {
    DOC_BM25(text = "doc_bm25",
            type = PARAGRAPH,
            func = DocumentRankingFeatures::addBM25Document),

    DOC_SDM(text = "doc_sdm",
            type = PARAGRAPH,
            func = DocumentRankingFeatures::addSDMDocument),

    DOC_QUERY_DIST(text = "doc_query_dist",
            type = PARAGRAPH,
            func = DocumentRankingFeatures::addQueryDist),

    DOC_CONSTANT_SCORE(text = "doc_constant_score",
            type = PARAGRAPH,
            func = DocumentRankingFeatures::addConstantScore),

    DOC_SECTION_FREQ(text = "doc_section_freq",
            type = PARAGRAPH,
            func = DocumentRankingFeatures::addSectionFreq),

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

//    DOC_DIST_SCORE(text = "dist_score",
//            type = PARAGRAPH,
//            func = DocumentRankingFeatures::addDistScore),

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

    ENTITY_CONTEXT_UNIGRAMS(text = "entity_context_unigrams",
            type = ENTITY,
            func = EntityRankingFeatures::addContextUnigram),

    ENTITY_CONTEXT_BIGRAMS(text = "entity_context_bigrams",
            type = ENTITY,
            func = EntityRankingFeatures::addContextBigram),

    ENTITY_CONTEXT_WINDOWED(text = "entity_context_windowed",
            type = ENTITY,
            func = EntityRankingFeatures::addContextWindowed),

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

    PFUNCTOR_JOINTUNIGRAM_UNIGRAM(text = "functor_pjointunigram_unigram",
            type = PARAGRAPH_FUNCTOR,
            func = SubObjectFeatures::addPJointUnigramToEUnigram),

    PFUNCTOR_JOINTBIGRAM_BIGRAM(text = "functor_pjointbigram_bigram",
            type = PARAGRAPH_FUNCTOR,
            func = SubObjectFeatures::addPJointBigramToEBigram),

    PFUNCTOR_JOINTWINDOWED_WINDOWED(text = "functor_pjointwindowed_windowed",
            type = PARAGRAPH_FUNCTOR,
            func = SubObjectFeatures::addPJointWindowedToEWindowed),

    PFUNCTOR_BIGRAM_BIGRAM(text = "functor_pbigram_ebigram",
            type = PARAGRAPH_FUNCTOR,
            func = SubObjectFeatures::addPBigramToEBigram),

    PFUNCTOR_WINDOWED_WINDOWED(text = "functor_pwindowed_ewindowed",
            type = PARAGRAPH_FUNCTOR,
            func = SubObjectFeatures::addPWindowedToEWindowed),

    PFUNCTOR_ENTITY_OUTLINKS(text = "functor_entity_outlinks",
            type = PARAGRAPH_FUNCTOR,
            func = SubObjectFeatures::addPEntityToOutlinks),

    PFUNCTOR_ENTITY_INLINKS(text = "functor_entity_inlinks",
            type = PARAGRAPH_FUNCTOR,
            func = SubObjectFeatures::addPEntityToInlinks),

    PFUNCTOR_ENTITY_CONTEXT_UNIGRAMS(text = "functor_entity_context_unigrams",
            type = PARAGRAPH_FUNCTOR,
            func = SubObjectFeatures::addPUnigramToContextUnigram),

    PFUNCTOR_ENTITY_CONTEXT_ENTITIES(text = "functor_entity_context_entities_unigrams",
            type = PARAGRAPH_FUNCTOR,
            func = SubObjectFeatures::addPUnigramToContextEntities),

    PFUNCTOR_ENTITY_CONTEXT_BIGRAMS(text = "functor_entity_context_bigrams",
            type = PARAGRAPH_FUNCTOR,
            func = SubObjectFeatures::addPUnigramToContextBigram),

    PFUNCTOR_ENTITY_CONTEXT_JOINT_BIGRAMS(text = "functor_entity_context_joint_bigrams",
            type = PARAGRAPH_FUNCTOR,
            func = SubObjectFeatures::addPUnigramToContextBigram),

    PFUNCTOR_ENTITY_CONTEXT_WINDOWED(text = "functor_entity_context_windowed",
            type = PARAGRAPH_FUNCTOR,
            func = SubObjectFeatures::addPUnigramToContextWindowed),


    PFUNCTOR_LINK_FREQ(text = "functor_punigram_einlinks",
            type = PARAGRAPH_FUNCTOR,
            func = SubObjectFeatures::addLinkFreq),

    SECTION_UNIGRAMS(text = "section_unigrams",
            type = SECTION,
            func = SectionRankingFeatures::addUnigrams),

    SECTION_BIGRAMS(text = "section_bigrams",
            type = SECTION,
            func = SectionRankingFeatures::addBigrams),

    SECTION_HEADING(text = "section_heading",
            type = SECTION,
            func = SectionRankingFeatures::addHeading),

    SECTION_WINDOWED(text = "section_windowed",
            type = SECTION,
            func = SectionRankingFeatures::addWindowed),

    SECTION_PATH(text = "section_path",
            type = SECTION,
            func = SectionRankingFeatures::addPath),

    SECTION_CONSTANT_SCORE(text = "section_constant_score",
            type = SECTION,
            func = SectionRankingFeatures::addConstantScore),

    SECTION_QUERY_DIST(text = "section_query_dist",
            type = SECTION,
            func = SectionRankingFeatures::addQueryDist),

    CONTEXT_ENTITY_UNIGRAM(text = "context_entity_unigram",
            type = CONTEXT_ENTITY,
            func = ContextEntityRankingFeatures::addUnigram),

    CONTEXT_ENTITY_BIGRAM(text = "context_entity_bigram",
            type = CONTEXT_ENTITY,
            func = ContextEntityRankingFeatures::addBigram),

    CONTEXT_ENTITY_WINDOWED(text = "context_entity_windowed",
            type = CONTEXT_ENTITY,
            func = ContextEntityRankingFeatures::addWindowed),

}