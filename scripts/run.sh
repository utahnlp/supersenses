#!/bin/bash

SCRIPT_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
SUPERSENSES_HOME=$SCRIPT_DIR/..

CMD="edu.utah.cs.learnlab.supersensesv2.Main"

CP=$SUPERSENSES_HOME/lib/*:$SUPERSENSES_HOME/target/scala-2.11/classes

# First pull all bundles
for dir in $(ls $SUPERSENSES_HOME/lib_managed/bundles); do
    d=$SUPERSENSES_HOME/lib_managed/bundles/$dir
    for child in $(ls $d | grep -v scalatest); do
        dc=$d/$child
        file=$(ls $dc | tail -n1)
        CP=$CP:$dc/$file
    done 
done

# Next pull all the jars, but ignore sbt jars. dirty hacks here.
for dir in $(ls $SUPERSENSES_HOME/lib_managed/jars); do
    d=$SUPERSENSES_HOME/lib_managed/jars/$dir
    for child in $(ls $d | grep -v scalatest | grep -v slf4j-nop | grep -v scala-compiler | grep -v 2.10); do
        dc=$d/$child
        if [ $child == "stanford-corenlp" ]; then
            for file in $(ls $dc); do
                CP=$CP:$dc/$file
            done
        else 
            file=$(ls $dc | tail -n1)
            CP=$CP:$dc/$file
        fi
    done 
done

MEMORY="-Xmx20g"


time nice java $MEMORY -cp $CP -Dcache.enable=false  $CMD ${1+"$@"}

# See
# https://stackoverflow.com/questions/743454/space-in-java-command-line-arguments
# for the reason for the weird ${1+"$@"} at the end.
