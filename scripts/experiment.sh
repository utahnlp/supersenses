#!/bin/bash -e

S=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

function usage {
    echo "Usage: experiment.sh --id [goldid | autoid] --parse [goldsyn | autosyn]"
    exit 1    
}

ID=""
SYN=""
while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
        --id)
            ID="$2"
            shift
            shift
            ;;
        --parse)
            SYN="$2"
            shift
            shift
            ;;
        *)
            usage
            ;;
    esac
done

case "$ID" in
    (goldid|autoid) ;;
    (*) usage ;;
esac


case "$SYN" in
    (goldsyn|autosyn) ;;
    (*) usage ;;
esac


STREUSLE_DIR=data/streusle

TRAIN_JSON=$STREUSLE_DIR/input/train/streusle.ud_train.$ID.$SYN.json
DEV_JSON=$STREUSLE_DIR/input/dev/streusle.ud_dev.$ID.$SYN.json
TEST_JSON=$STREUSLE_DIR/input/test/streusle.ud_test.$ID.$SYN.json

HIERARCHY_FILE=data/hierarchy.json

EXPERIMENTS_DIR=experiments/output
EXPERIMENT_OUT=$EXPERIMENTS_DIR/$ID.$SYN

RUN=$S/run.sh

# First preprocess the data to the right format. Also add stanford
# NE (and any other extra info)
$RUN preprocess -m "Preprocessing data" \
     --train-json-file $TRAIN_JSON \
     --dev-json-file $DEV_JSON \
     --test-json-file $TEST_JSON \
     --preprocess-dir $EXPERIMENT_OUT \
     --hierarchy-file $HIERARCHY_FILE

# Extract features in liblinear format
$RUN extract -m "Extracting features" \
     --train-json-file $TRAIN_JSON \
     --dev-json-file $DEV_JSON \
     --test-json-file $TEST_JSON \
     --preprocess-dir $EXPERIMENT_OUT \
     --hierarchy-file $HIERARCHY_FILE

# Next train the scene and function models after cross validating on
# the dev set. The location of the liblinear formatted files will be
# printed in the output of the extract command. Below, the file
# names are constructed in code, but we could also manually copy the
# file names from the output of extract.


# Clear out any old models and predictions
rm -rf $EXPERIMENT_OUT/*.model
rm -rf $EXPERIMENT_OUT/*.predictions
rm -rf $EXPERIMENT_OUT/cv.*

for label in "scene" "function"; do

    train_file_name=$(basename $TRAIN_JSON json)
    train_file=$EXPERIMENT_OUT/features/$train_file_name$label
    
    dev_file_name=$(basename $DEV_JSON json)
    dev_file=$EXPERIMENT_OUT/features/$dev_file_name$label

    test_file_name=$(basename $TEST_JSON json)
    test_file=$EXPERIMENT_OUT/features/$test_file_name$label
    
    echo "Cross validating for the $label classifier using the dev file at $dev_file"

    best_c=$(for c in 0.0001 0.001 0.01 0.1 1 10 100 1000; do
                 cv_perf=$(train -v 5 -c $c $dev_file | grep Cross)
                 echo $c" "$cv_perf
             done | sed 's/%//g' | sort -k6gr | tee $EXPERIMENT_OUT/cv.$label | head -n1 | awk '{print $1;}')

    echo "Best c via cross validation = $best_c"
    echo "Full cross validation results saved at $EXPERIMENT_OUT/cv.$label"

    MODEL_FILE=$EXPERIMENT_OUT/$label.model
    PREDICTIONS_FILE=$EXPERIMENT_OUT/$label.predictions

    train -c $best_c $train_file $MODEL_FILE
    echo "Model saved at $MODEL_FILE"
    
    predict $test_file $MODEL_FILE $PREDICTIONS_FILE
    echo "Test set predictions saved at $PREDICTIONS_FILE"
done


FUNCTION_PREDICTIONS=$EXPERIMENT_OUT/function.predictions
SCENE_PREDICTIONS=$EXPERIMENT_OUT/scene.predictions
JSON_OUTPUT=$EXPERIMENTS_DIR/$SYN.$ID.json

$RUN write-json -m "Converting liblinear predictions to JSON for $SYN, $ID" \
     --train-json-file $TRAIN_JSON \
     --dev-json-file $DEV_JSON \
     --test-json-file $TEST_JSON \
     --preprocess-dir $EXPERIMENT_OUT \
     --hierarchy-file $HIERARCHY_FILE \
     --scene-predictions $SCENE_PREDICTIONS \
     --function-predictions $FUNCTION_PREDICTIONS \
     --output $JSON_OUTPUT


# Finally, evaluate the output using the psseval script
eval=$STREUSLE_DIR/psseval.py
gold=$STREUSLE_DIR/input/test/streusle.ud_test.goldid.goldsyn.json
$eval $gold $JSON_OUTPUT
