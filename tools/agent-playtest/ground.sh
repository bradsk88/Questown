#!/bin/bash
# ground.sh <x> <z> [top=72] [bottom=58]: print the y of the highest non-air block in the column
# (the block to `_qtdev clickheld` to place on top). Trees and walls count as non-air.
set -u
REPO=$(cd "$(dirname "$0")/../.." && pwd)
cd "$REPO"
x=${1:?usage: ground.sh <x> <z> [top] [bottom]}; z=${2:?}; top=${3:-72}; bottom=${4:-58}
LOG=run/logs/latest.log
n=$(grep -ac 'Test passed\|Test failed' "$LOG")
count=$((top - bottom + 1))
for y in $(seq "$top" -1 "$bottom"); do tools/ffeed.sh feed "execute if block $x $y $z minecraft:air" >/dev/null; done
timeout 15 sh -c "until [ \$(grep -ac 'Test passed\|Test failed' $LOG) -ge $((n + count)) ]; do sleep 0.3; done"
y=$top
grep -a 'Test passed\|Test failed' "$LOG" | tail -"$count" | sed 's/.*]: //' | while read -r _ result; do
  if [ "$result" = "failed" ]; then echo "$y"; exit 0; fi
  y=$((y - 1))
done
