#!/bin/bash
# Retry a content-gate screen until it PASSes (the vision provider stales
# intermittently; successes land on most attempts within ~6 tries).
# Usage: gate-retry.sh <screens-dir> <screen> [tries]
DIR=${1:?dir}; SCREEN=${2:?screen}; TRIES=${3:-8}
TOOL=$(cd "$(dirname "$0")" && pwd)
for i in $(seq 1 $TRIES); do
  out=$("$TOOL/check.sh" "$DIR" "$SCREEN" 2>&1)
  rc=$?
  echo "try $i: $out"
  echo "$out" | grep -q '^PASS' && exit 0
  sleep 25
done
echo "gate-retry: $SCREEN failed after $TRIES tries"
exit 1
