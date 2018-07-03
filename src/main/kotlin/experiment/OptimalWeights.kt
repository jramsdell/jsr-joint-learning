package experiment

enum class OptimalWeights(val weights: List<Double>) {
    ONLY_ENTITY_WITH_SHARED(listOf(
//            0.14927631053141815 ,0.12140356138550638 ,8.809640384891394E-4 ,0.08945848148364932 ,-0.05100408771768025 ,0.5263576706960283 ,-0.0077321865943399265 ,-0.02671000821141297 ,0.023783998769427498 ,0.0033927305720479802
            0.019904695663031418 ,0.019904695663031418 ,0.019904695663031418 ,0.019904695663031418 ,0.1095521854228281 ,0.032432916749246185 ,0.006672296028049988 ,0.10487634005768144 ,-0.02369059288145093 ,0.5675349017601977 ,-0.04187433607047378 ,0.011050170505689546 ,0.02269747787225665

    )),

    ONLY_ENTITY_WITH_NO_SHARED(listOf(
//            0.1609855521004067 ,0.032498760431313695 ,0.06443911826057205 ,0.6115928807756953 ,0.04977990858939205 ,0.01715601593925749 ,0.055234469622958984 ,-0.00831329428040384
            0.021203052388878738 ,0.18339606081984178 ,0.15185420954898646 ,0.0542864881540511 ,0.019170799366388516 ,0.012673516829921017 ,0.21871394293652152 ,-0.10155170624573562 ,0.0067049848101060575 ,-0.0056255285319847075 ,0.17329093735856316 ,0.019735269252762557 ,0.019149114096395078 ,0.012644389659863727






    )),

    JOINT_SHARED(listOf(
            -0.021814890438123964 ,0.051572619645896364 ,0.31448270034987696 ,0.08533412673206037 ,0.17105399638994492 ,0.018986039583917674 ,6.794410602549933E-4 ,0.018140549564523323 ,0.030638582028237236 ,0.23270654407984268 ,-0.03008358275338458 ,0.016689740559518614 ,0.007817186814418215
    )),

    JOINT_SHARED_BEST(listOf(
            -0.021814890438123964 ,0.051572619645896364 ,0.31448270034987696 ,0.08533412673206037 ,0.17105399638994492 ,0.018986039583917674 ,6.794410602549933E-4 ,0.018140549564523323 ,0.030638582028237236 ,0.23270654407984268 ,-0.03008358275338458 ,0.016689740559518614 ,0.007817186814418215
    )),


    JOINT_SHARED_SUBSET(listOf(
//            0.3379680912966052 ,-0.16961827016364284 ,2.7861219387357076E-4 ,0.26970757629007447 ,-0.09354548005447237 ,0.03296887219133199 ,0.03197103260333317 ,0.03197103260333317 ,0.03197103260333317
            0.004565485131889652 ,0.016485835890821966 ,0.011490316098152124 ,0.1700460140703157 ,0.06471051527975297 ,0.001807978751778677 ,0.06970912338049737 ,-0.028331093576128782 ,-0.028212524881777678 ,0.4835830156094663 ,-0.023321305608383407 ,0.04935937325399385 ,0.04837741846704152








    )),

    JOINT_SHARED_SUBSET_SYMMETRIC(listOf(
    )),

}