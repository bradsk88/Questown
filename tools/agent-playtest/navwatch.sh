#!/bin/bash
# navwatch.sh <server-log> <villager-uuid>...: feed `_qtdev nav <uuid>` for each villager every 20 s
# and print a villager's probe line when its status / walkTarget / path target / canReach changes
# (position alone is not a change). "no villager" / "no level" answers are printed too, once per
# change: r20 dropped them and read an unloaded town (the player dead on the respawn screen) as
# "nothing happening". Run it under a Monitor; pipe through `grep --line-buffered 'canReach=false'`
# to see only stuck paths. <server-log> is restart.sh's build/agent-playtest/<name>-server.log:
# pass the new one after every restart (r21: a watch kept reading the previous run's log).
set -u
REPO=$(cd "$(dirname "$0")/../.." && pwd)
cd "$REPO" || exit 1
LOG=${1:?usage: navwatch.sh <server-log> <villager-uuid>...}
shift
[ $# -gt 0 ] || { echo "usage: navwatch.sh <server-log> <villager-uuid>..."; exit 1; }
declare -A last
while true; do
  for u in "$@"; do tools/ffeed.sh feed "_qtdev nav $u" >/dev/null 2>&1; done
  sleep 3
  for u in "$@"; do
    line=$(grep -a -E "\[_qtdev nav\] ($u |no villager $u|no level)" "$LOG" | tail -1)
    [ -n "$line" ] || continue
    key=$(sed -E 's/^\[[0-9:]+\] //; s/pos=BlockPos\{[^}]*\} //; s/ node=[0-9]+\/[0-9]+//; s/ cantReachSince=[0-9]+//' <<<"$line")
    if [ "${last[$u]:-}" != "$key" ]; then
      last[$u]=$key
      echo "$line"
    fi
  done
  sleep 17
done
