#!/bin/bash

if [ "$#" -ne 1 ]; then
    echo "Usage: ./run_frog.sh <frog_file>"
    exit 1
fi

SOURCE_FILE="$1"
BYTECODE_FILE="${SOURCE_FILE%.frog}.frogc"

if [ ! -f "$SOURCE_FILE" ]; then
    echo "Error: File '$SOURCE_FILE' not found"
    exit 1
fi

CODE=$(cat "$SOURCE_FILE")

./gradlew run --args "'$CODE' $BYTECODE_FILE" > /dev/null 2>&1

if [ $? -ne 0 ]; then
    echo "Compilation failed"
    exit 1
fi

frogitovm/build/frogvm run "$BYTECODE_FILE"
