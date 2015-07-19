#!/bin/bash
pushd dist 2&>1 /dev/null
chmod -R 755 example_scenarios
SCENARIOS=$(find ./example_scenarios/ -iname "*scenario*.txt")

RETVAL=0


check() {
    RETVAL=$?
    if [ "$RETVAL" -ne "0" ]
    then
        echo "Call failed"
        exit $RETVAL
    fi
}

while read scenario; do

    mkdir -p ./tmp-output/
    BASENAME=$(basename $scenario)

    #SMAC Mode
    SEED=$RANDOM
    ./smac --scenario $scenario --output-dir ./tmp-output/  --rungroup $BASENAME --validation true --seed $SEED
    check

    #ROAR Mode
    SEED=$(($SEED + 1))
    ./smac --scenario $scenario --output-dir ./tmp-output/  --rungroup $BASENAME --validation true --exec-mode ROAR --seed $SEED
    check

    #Classic Trajectory Files
    ./smac-validate --scenario $scenario --trajectory-files ./tmp-output/$BASENAME/traj-run-*.txt --num-validation-runs 5 --num-run 1
    check

    #Classic Trajectory Files
    ./smac-validate --scenario $scenario --trajectory-files ./tmp-output/$BASENAME/detailed-*.csv --num-validation-runs 5 --num-run 1
    check

done <<< "$SCENARIOS"

