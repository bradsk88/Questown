#!/bin/bash
# autoshot.sh <client log> <prefix>: on each "New quests are available" chat line or path batch
# post, open the flag's quest tab, shoot it (and page 2), close, and print the shot paths.
# The flag is the growth driver's (0 64 0); edit the flagtab line for another town.
cd "$(dirname "$0")/../.."
n=0
tail -n 0 -F "$1" 2>/dev/null | grep -E --line-buffered "New quests are available|Quest completed: (Train|Level)" | while read -r line; do
  n=$((n+1)); sleep 1
  echo "flagtab quests 0 64 0" >> run-client/.qtdiag-cmd; sleep 2
  PARK="1 1" tools/agent-playtest/look.sh "$2-$n-a" 1 | head -1
  echo "click 393 266" >> run-client/.qtdiag-cmd; sleep 1
  PARK="1 1" tools/agent-playtest/look.sh "$2-$n-b" 1 | head -1
  echo "close" >> run-client/.qtdiag-cmd
  echo "EVENT $n: $(echo "$line" | cut -c1-12,60-160)"
done
