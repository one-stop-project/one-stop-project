#!/usr/bin/env bash
set -euo pipefail
SCRIPT=${1:?Usage: scripts/run-one.sh <k6-script.js>}
mkdir -p results
NAME=$(basename "$SCRIPT" .js)
TS=$(date +%Y%m%d-%H%M%S)
SUMMARY_FILE="results/${TS}-${NAME}-summary.json" \
k6 run --summary-export "results/${TS}-${NAME}-summary-export.json" "$SCRIPT" | tee "results/${TS}-${NAME}.log"
