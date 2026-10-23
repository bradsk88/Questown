#!/bin/bash
# restart.sh <name>: restart the agent playtest in this checkout (client down by cwd, server stop
# via the FIFO only if it is alive, then server, then client) and wait for the join. Rebuilds as a
# side effect, so run it after a code change. Logs: build/agent-playtest/<name>-{server,client}.log
set -u
REPO=$(cd "$(dirname "$0")/../.." && pwd)
cd "$REPO"
name=${1:?usage: restart.sh <name>}
for p in $(pgrep java); do
  [ "$(readlink /proc/$p/cwd)" = "$REPO/run-client" ] && kill $p && echo "killed client $p"
done
for p in $(pgrep java); do
  tr '\0' ' ' < /proc/$p/cmdline | grep -q 'GradleWrapperMain.*runClient' \
    && [ "$(readlink /proc/$p/cwd)" = "$REPO" ] && kill $p && echo "killed client wrapper $p"
done
sleep 3
alive() { for p in $(pgrep java); do [ "$(readlink /proc/$p/cwd)" = "$REPO/run" ] && return 0; done; return 1; }
# Only a live server reads the FIFO: a stop fed to a dead one waits there and kills the next one.
alive && tools/ffeed.sh feed stop >/dev/null 2>&1
for i in $(seq 1 60); do alive || break; sleep 1; done
alive && { echo "server still up after 60 s; not relaunching"; exit 1; }
echo "server down"
mkdir -p build/agent-playtest
tools/ffeed.sh setup >/dev/null 2>&1
LANG=C.UTF-8 setsid nohup ./gradlew runServer -Dquestown.tp.fifo=true > "build/agent-playtest/$name-server.log" 2>&1 &
until grep -q -E 'Done \(|FAILED' "build/agent-playtest/$name-server.log"; do sleep 2; done
grep -E 'Done \(|FAILED|error:' "build/agent-playtest/$name-server.log" | head -5
grep -q 'Done (' "build/agent-playtest/$name-server.log" || exit 1
LANG=C.UTF-8 setsid nohup ./gradlew runClient -Pautojoin -Pautoport=25585 > "build/agent-playtest/$name-client.log" 2>&1 &
until grep -q 'joined the game' run/logs/latest.log || grep -q 'FAILED' "build/agent-playtest/$name-client.log"; do sleep 3; done
grep -q 'joined the game' run/logs/latest.log && echo "client joined" || { echo "client FAILED"; exit 1; }
