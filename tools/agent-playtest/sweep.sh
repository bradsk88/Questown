#!/bin/bash
# sweep.sh <flag x y z> [from-line]: summarize the server log since a line, then print each
# townie's stats and the clock. from-line defaults to build/agent-playtest/mark; the sweep then
# moves the mark to the end of the log, so repeated sweeps cover consecutive windows.
set -u
REPO=$(cd "$(dirname "$0")/../.." && pwd)
cd "$REPO"
fx=${1:?usage: sweep.sh <flag x y z> [from-line]}; fy=${2:?}; fz=${3:?}
L=run/logs/latest.log
MARK=build/agent-playtest/mark
from=${4:-$(cat "$MARK" 2>/dev/null || echo 1)}
echo "--- job changes (counts)"
tail -n +"$from" $L | grep -a 'Job changed to' \
  | sed -E "s/.*rootId='([a-z_]+)', jobId='([a-z_]+)'.*/\1:\2/" | sort | uniq -c | sort -rn | head -12
echo "--- extracts / quests / arrivals"
tail -n +"$from" $L | grep -aE "LOOTTRACE\] extract '[^']+' vs LOOT quest [^ ]+ -> true|Broadcasting message|Registered entity with town|Nightfall" \
  | sed -E 's/^\[[^]]*\] \[[^]]*\] \[[^]]*\]: //' | cut -c1-150 | uniq -c | tail -15
n=$(wc -l < $L)
tools/ffeed.sh feed "_qtdev villager_stats $fx $fy $fz" >/dev/null
tools/ffeed.sh feed "time query daytime" >/dev/null
sleep 2
echo "--- now"
tail -n +"$n" $L | grep -aE 'villager_stats\] Villager|The time is' | sed -E 's/^.*\]: //' | cut -c1-160
mkdir -p build/agent-playtest; wc -l < $L > "$MARK"
