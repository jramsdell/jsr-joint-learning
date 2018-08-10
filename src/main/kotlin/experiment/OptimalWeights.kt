package experiment

enum class OptimalWeights(val weights: List<Double>) {
    //0.1611

    ONLY_ENTITY_WITH_NO_SHARED(listOf(
//            0.1956510262603367 ,0.05435998340339653 ,0.7335632412554884 ,0.0015415177358212536 ,0.014884231344957161
//            0.12205348163843155, 0.0017501027323305607, 0.7723595499992371, 0.0017237368738278747, 0.10211317241191864
//            0.12165789853972875 ,0.08944102588504195 ,0.5729872942806236 ,0.0011804417667321465 ,0.055703286685797986 ,-0.059791612511680386 ,1.480083780035103E-4 ,-2.0488257986124757E-4 ,8.92583566527143E-4 ,0.0013857359870333894 ,0.001111359813115724 ,0.0750116970983432 ,-0.0035072148507435765 ,0.016084374490240208 ,8.92583566527143E-4
//            0.19264347851276398, 0.0053751710802316666, 0.4121803939342499, 0.00574200414121151, 0.0981157198548317, 0.008068409748375416, 1.9652937771752477E-4, 7.124053663574159E-4, 0.21194632351398468, 4.486272155190818E-5, 0.0012888390338048339, 0.023365402594208717, 9.283454273827374E-4, 6.083272455725819E-5, 0.03933131694793701
//            0.192643940448761, 0.005375207867473364, 0.41218018531799316, 0.005742000415921211, 0.09811565279960632, 0.008068549446761608, 1.965287810890004E-4, 7.12405948434025E-4, 0.21194574236869812, 4.486264879233204E-5, 0.0012888438068330288, 0.023365529254078865, 9.28346358705312E-4, 6.0832466260762885E-5, 0.039331354200839996
//            0.31966498494148254, 0.17385582625865936, 0.04706937074661255, 0.019933432340621948, 0.14248180389404297, 4.744218585983617E-6, 1.7170192379012406E-8, 7.061466749291867E-5, 0.0035319882445037365, 1.2767543466907227E-8, 2.7208838560000004E-7, 0.2925257086753845, 2.726629020344262E-7, 2.0554469665512443E-4, 6.55480835121125E-4
//            0.09000281244516373, 0.023815875872969627, 0.6611737012863159, 0.025309648364782333, 0.10421055555343628, 0.003153363475576043, 6.226506666280329E-4, 0.00861051119863987, 0.0031729319598525763, 3.1701530679129064E-4, 1.220254271174781E-4, 0.07442799210548401, 7.980096852406859E-4, 0.0036741220392286777, 5.88806695304811E-4
//            0.3669366240501404, 0.1953139752149582, 0.09239710122346878, 0.05420924723148346, 0.06759243458509445, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.22355061769485474, 0.0, 0.0, 0.0
//            0.3225465416908264, 0.19450576603412628, 0.15672610700130463, 0.04794332757592201, 0.0784013494849205, 1.4581400251356404E-18, 3.598418601449591E-24, 3.920001064672979E-18, 0.0, 1.0033484875651117E-26, 6.434346091958122E-28, 0.19987691938877106, 1.092859372882857E-22, 2.698647176747354E-18, 0.0
//            BEST: 0.20626967500977578 ,0.06789250355612644 ,0.5884787395138055 ,-0.009351554319159235 ,0.0046123869508436265 ,0.0018502051736588455 ,0.0015348493766956565 ,0.0017567442300209294 ,0.004862895301289406 ,-0.020439537444889196 ,9.08557585154395E-4 ,0.07020298234050766 ,-0.012113578595494438 ,0.004862895301289406 ,0.004862895301289406

//            0.22660967815556307, 0.07154690284629608, 0.4881999387817141, 0.004451739907753595, 1.734365973220617E-4, 1.4365418347945727E-13, 6.725465692444053E-8, 2.98120036303434E-6, 0.005305418255030699, 5.9111596561259805E-6, 2.5220182541293495E-10, 0.06000908106563874, 1.5737374943644985E-5, 7.957009079334999E-12, 0.1436791071407596
//            0.2459674133512394, 0.10905617635208775, 0.47842007685404453, 1.707115813189381E-4, 1.0818295585278496E-6, 2.0766238946287206E-7, 8.03381173420124E-13, 6.20611944543255E-5, 0.10796568250622822, 4.563255576567428E-8, 5.0434944161426054E-12, 0.054545948749745624, 1.9752880997350764E-7, 0.0038103967517204964, 0.0
//            0.20281120573450895, 0.07724661995194199, 0.3100940242557886, 0.0018362268922252512, 0.0, 1.773018019860569E-4, 2.1545167625350633E-7, 2.635450489806665E-11, 7.443815845309205E-5, 7.378987075871831E-5, 2.4854219304290066E-6, 0.042103549999716446, 5.0803752647977E-4, 1.742813130206675E-4, 0.3648978235951592
            // MYBEST: 0.24230298565926572, 0.08282769488684166, 0.382379566888524, 0.0015855412580913547, 0.00508391031718507, -0.019538367095005806, -0.020885073054813724, 0.0013720412713375267, 0.08842701277832152, 5.19264593053088E-4, 7.747426399366996E-4, 0.06254206202642201, -0.013522397138614094, 0.0027044913363738305, 0.18342652363308107
//            0.18338127993583928 ,0.0615141216013998 ,0.5494973059802268 ,0.00886062290629041 ,0.00880875264413798 ,-0.03761613826043471 ,-0.01630186235114604 ,-6.564695676001567E-4 ,0.004988868902758491 ,-0.0017675895106894044 ,-0.0024965223094329226 ,0.08206641156248277 ,-0.025567416688071276 ,0.011487768876731311 ,0.004988868902758491
//            0.27082949043038307 ,0.7291705095696169
//            0.35457518089641726 ,0.4542460404735731 ,0.052360440370105184 ,-0.048289046760023754 ,-0.0011126391692378996 ,-0.0012266393387111642 ,-0.0012203940797489155 ,-0.02249591090805647 ,0.014973638216031652 ,0.03515509518799149 ,-1.9583736709368607E-4 ,-5.3363774292611693E-5 ,0.014095773458716796
//            0.4623527818166497 ,0.5376472181833504
            0.4623527818166497 ,0.5376472181833504

            //0.1550




















//            0.9790314139745411, 0.006494268590105853, 0.002558088046162031, -0.004733910947138985, -0.013729812360227899, 0.009827381426081853, 5.529622398452846E-5, 0.0012127158803710053, 0.0020903830171202504, -3.084921848544158E-4, 0.01295870369354209, -0.006327261028491197, 0.002967909320657998, 0.007903316348145797






            // 0.1632 / 0.3501


    )),










    PARAGRAPH_FUNCTOR_WEIGHTS(listOf(
            -0.021814890438123964 ,0.051572619645896364 ,0.31448270034987696 ,0.08533412673206037 ,0.17105399638994492 ,0.018986039583917674 ,6.794410602549933E-4 ,0.018140549564523323 ,0.030638582028237236 ,0.23270654407984268 ,-0.03008358275338458 ,0.016689740559518614 ,0.007817186814418215
    )),




}