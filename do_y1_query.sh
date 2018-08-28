#./run.sh query run \
    #--method $1 \
    #--indexPath index/ \
    #--queryPath ~/data/benchmark/benchmarkY1/benchmarkY1-train/train.pages.cbor-outlines.cbor 

#surround() {
    #argumentsArray=( "$@" )
    #$argumentsArray
#}

#./gradlew run -PappArgs="['query', '']"

#argumentsArray=( ""$@"" )




JAR=/home/jsc57/data/shared/final_runs/project/jsr-joint-learning/build/libs/program.jar


java -jar $JAR submission_query run \
    --method $1 \
    --queryPath /home/jsc57/data/benchmark/benchmarkY1/benchmarkY1-train/train.pages.cbor-outlines.cbor  \
    --indexPath /home/jsc57/data/shared/final_runs/run_indexes/public_test_Y2_indexes/paragraph/ \
    --entityIndex /home/jsc57/data/shared/final_runs/run_indexes/train_test_Y1_indexes/page/ \
    --contextEntityIndex /home/jsc57/data/shared/final_runs/run_indexes/train_test_Y1_indexes/entity_context/ \
    --omitArticleLevel false


#    --entityQrel ~/data/benchmark/benchmarkY1/benchmarkY1-train/train.pages.cbor-hierarchical.entity.qrels
    #--entityIndex entity_index_2/
    #--indexPath index/ \



    #--indexPath /speedy/jsc57/extractions2/paragraph \
    #--queryPath ~/data/benchmark/benchmarkY1/benchmarkY1-train/train.pages.cbor-outlines.cbor  \
    #--entityIndex /speedy/jsc57/extractions2/page\
    #--sectionIndex /speedy/jsc57/extractions2/section\
    #--contextEntityIndex /speedy/jsc57/extractions2/entity_context
