#!/bin/bash
# look.sh <name> [maxlines]: one observation of the agent playtest.
# Requests a client screenshot through the QtDiagClient file channel, waits until the PNG is
# complete (a partial file breaks readers), prints its path, then prints the server log lines
# written since the previous look (multi-line [WHY] dump bodies filtered out).
#   PLAY_LOG   server log to tail (default: <repo>/run/logs/latest.log)
# State: <repo>/build/agent-playtest/offset (log line reached by the last look).
set -u
REPO=$(cd "$(dirname "$0")/../.." && pwd)
LOG=${PLAY_LOG:-$REPO/run/logs/latest.log}
STATE=$REPO/build/agent-playtest; mkdir -p "$STATE"; OFF=$STATE/offset
name=${1:?usage: look.sh <name> [maxlines]}; max=${2:-40}
f=$REPO/run-client/diags/$name.png
echo "shot $name" >> "$REPO/run-client/.qtdiag-cmd"
prev=-1
for i in $(seq 1 60); do
  if [ -f "$f" ]; then
    sz=$(stat -c %s "$f")
    [ "$sz" = "$prev" ] && [ "$(head -c 4 "$f" | tail -c 3)" = "PNG" ] && break
    prev=$sz
  fi
  sleep 0.25
done
[ -f "$f" ] && echo "SHOT $f" || echo "SHOT MISSING (is the client up with -Dquestown.diag=true?)"
start=$(cat "$OFF" 2>/dev/null || echo 0); end=$(wc -l < "$LOG"); echo "$end" > "$OFF"
sed -n "$((start+1)),${end}p" "$LOG" | grep -vE '^\s|gate\[|else →|predicate|TakeFromSlot|^\}|^\{' | cut -c1-230 | tail -"$max"
