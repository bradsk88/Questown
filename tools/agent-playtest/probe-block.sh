#!/bin/bash
# probe-block.sh <block-id> <x y z> [<x y z> ...]: print "x y z: passed|failed" for whether each
# position holds the block (e.g. questown:job_board_block). Block ids: the registry name, not the
# item's display name (the flag is questown:cobblestone_flag_base).
set -u
REPO=$(cd "$(dirname "$0")/../.." && pwd)
cd "$REPO"
id=${1:?usage: probe-block.sh <block-id> <x y z> ...}; shift
LOG=run/logs/latest.log
n=$(grep -ac 'Test passed\|Test failed' "$LOG"); k=0; pos=()
while [ $# -ge 3 ]; do
  run/ffeed.sh feed "execute if block $1 $2 $3 $id" >/dev/null; pos+=("$1 $2 $3"); k=$((k + 1)); shift 3
done
timeout 10 sh -c "until [ \$(grep -ac 'Test passed\|Test failed' $LOG) -ge $((n + k)) ]; do sleep 0.3; done"
i=0
grep -a 'Test passed\|Test failed' "$LOG" | tail -"$k" | sed 's/.*]: //' | while read -r _ result; do
  echo "${pos[$i]}: $result"; i=$((i + 1))
done
