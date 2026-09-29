#!/usr/bin/env bash
# Port-test capture driver, one branch at a time. STAGED: every stage is
# a fast command (<=60s) so nothing runs as a multi-minute black box.
# Launch stages return immediately; wait stages poll <=60s and are
# idempotent - re-invoke them until they pass.
#
#   capture.sh <stage> <project-dir> <server-port> <client-game-dir> [out-dir] [name]
#
# Stages:
#   prep           sweep old JVMs, wipe run/world, check level-type, FIFO writer
#   launch-server  setsid nohup runServer -Dquestown.tp.fifo=true (returns fast)
#   wait-server    <=60s: "Done (" + gradle-version match + port LISTEN check
#   launch-client  setsid nohup runClient -Pautojoin -Pautoport=<port>
#   wait-client    <=60s: "qtdiag] client hook active" in client log
#   connect        write 'connect localhost <port>' to .qtdiag-cmd
#   joined         <=60s: "logged in with entity id" in server log; prints spawn
#   fixture        _qtdev townplace + townspawn at the logged spawn (no tp)
#   shot <name>    death/desync guard, then openvillager <type> + shot <name>
#   collect        copy diags/*.png -> out-dir
#   check          run check.sh on out-dir (content gate; run before banking)
#
# The screen list and expect blocks come from manifest.json.
set -uo pipefail
STAGE=${1:?stage}
PROJ=${2:?project dir}
PORT=${3:?server port}
GAME=${4:?client gameDir relative to project}
OUT=${5:-/tmp/porttest-coll}
NAME=${6:-}

QJDK=/home/retro/.gradle/jdks/jdk-17.0.20+8
TOOL=$(cd "$(dirname "$0")" && pwd)
MAN=$TOOL/manifest.json
SLOG=/tmp/porttest-$(basename "$PROJ")-server.log
CLOG=/tmp/porttest-$(basename "$PROJ")-client.log
SLOG4=$PROJ/run/logs/latest.log
FIFO=$PROJ/run/server-cmd.fIFO

say() { echo "[capture:$STAGE] $*" >&2; }
die() { echo "[capture:$STAGE] FATAL: $*" >&2; exit 1; }
fifo_cmd() { echo "$1" > "$FIFO"; }
file_cmd() { echo "$1" > "$PROJ/$GAME/.qtdiag-cmd"; }
port_hex() { printf '%X' "$PORT"; }
port_listening() { # verified pattern: local=field2 state=field4 in /proc/net/tcp{,6}
  local n
  n=$(awk -v h=":$(port_hex)" '$2 ~ h"$" && $4 == "0A"' /proc/net/tcp /proc/net/tcp6 2>/dev/null | wc -l)
  [ "$n" -gt 0 ]
}

case $STAGE in
prep)
  say "sweep"
  "$TOOL/sweep.sh" "$PROJ"
  rm -rf "$PROJ/run/world"
  say "world wiped (fresh normal world will generate)"
  if grep -q '^level-type=flat\b' "$PROJ/run/server.properties" 2>/dev/null; then
    die "level-type=flat: flat-world spawn is broken here (server y=-60, client Y desyncs ~64, player dies). Use a normal world."
  fi
  if [ ! -p "$FIFO" ]; then
    say "creating FIFO writer"
    setsid nohup sh -c "sleep 999999 > $FIFO" >/dev/null 2>&1 &
  fi
  say "ok"
  ;;

launch-server)
  say "plain server on :$PORT (no autotest) -> $SLOG"
  setsid nohup env JAVA_HOME=$QJDK LANG=C.UTF-8 "$PROJ/gradlew" -p "$PROJ" \
    runServer -Dquestown.tp.fifo=true > "$SLOG" 2>&1 &
  say "launched (pid $!); run 'wait-server'"
  ;;

wait-server)
  t=0
  until LC_ALL=C grep -aq "Done (" "$SLOG4" 2>/dev/null; do
    if LC_ALL=C grep -aq "BUILD FAILED" "$SLOG"; then
      die "gradle failed: $(LC_ALL=C grep -am1 'error\|FAILED' "$SLOG" | cut -c1-140)"
    fi
    t=$((t+5)); [ $t -ge 60 ] && { say "not up yet (60s); re-run wait-server"; exit 0; }
    sleep 5
  done
  want=$(grep -o 'gradle-[0-9.]*-bin' "$PROJ/gradle/wrapper/gradle-wrapper.properties" 2>/dev/null | sed 's/gradle-//;s/-bin//')
  got=$(LC_ALL=C grep -aom1 'Welcome to Gradle [0-9.]*' "$SLOG" | grep -o '[0-9.]*$')
  if [ -n "$want" ] && [ "$got" != "$want" ]; then
    die "gradle version mismatch: wanted $want (wrapper of $PROJ), log says $got - wrong project was launched"
  fi
  port_listening || die "nothing LISTEN on :$PORT (hex $(port_hex)) after 'Done ('"
  say "server up: gradle $got listening on :$PORT"
  ;;

launch-client)
  say "client (hook) -> $CLOG"
  setsid nohup env JAVA_HOME=$QJDK LANG=C.UTF-8 "$PROJ/gradlew" -p "$PROJ" \
    runClient -Pautojoin -Pautoport=$PORT > "$CLOG" 2>&1 &
  say "launched (pid $!); run 'wait-client'"
  ;;

wait-client)
  t=0
  until LC_ALL=C grep -aq "qtdiag\] client hook active" "$CLOG" 2>/dev/null; do
    if LC_ALL=C grep -aq "BUILD FAILED" "$CLOG"; then
      die "client gradle failed: $(LC_ALL=C grep -am1 'error\|FAILED' "$CLOG" | cut -c1-140)"
    fi
    t=$((t+5)); [ $t -ge 60 ] && { say "no hook yet (60s); re-run wait-client"; exit 0; }
    sleep 5
  done
  say "hook active"
  ;;

connect)
  file_cmd "connect localhost $PORT"
  say "connect requested; run 'joined'"
  ;;

joined)
  t=0
  until LC_ALL=C grep -aq "logged in with entity id" "$SLOG4" 2>/dev/null; do
    t=$((t+5)); [ $t -ge 60 ] && { say "no join yet (60s); re-run joined"; exit 0; }
    sleep 5
  done
  line=$(LC_ALL=C grep -a "logged in with entity id" "$SLOG4" | tail -1)
  say "joined: $(echo "$line" | cut -c1-100)"
  ;;

fixture)
  line=$(LC_ALL=C grep -a "logged in with entity id" "$SLOG4" | tail -1)
  XYZ=$(echo "$line" | sed -n 's/.*at (\([^)]*\)).*/\1/p')
  read X Y Z YB <<< "$(python3 -c "
x, y, z = [float(v) for v in '$XYZ'.replace(',', ' ').split()]
print(int(x), int(y), int(z), int(y) - 1)")"
  [ -n "$X" ] && [ -n "$YB" ] || die "could not parse spawn from: $line"
  fifo_cmd "_qtdev townplace $X $YB $Z"
  sleep 6
  fifo_cmd "_qtdev townspawn $X $YB $Z"
  sleep 4
  tail -5 "$SLOG4" | grep -a '_qtdev\|town' || say "check $SLOG4 for the townplace/townspawn results"
  say "fixture placed at $X $YB $Z"
  ;;

shot)
  [ -n "$NAME" ] || die "shot stage needs a screen name: capture.sh shot $PROJ $PORT $GAME $OUT <name>"
  guard=$(LC_ALL=C tail -80 "$SLOG4" | grep -ac 'moved too quickly\|has died\|died with\|left the game' || true)
  [ "$guard" -eq 0 ] || die "death/desync signal in log tail: $guard line(s) - do NOT shoot; re-read PORTTEST.md guard"
  fifo_cmd "list"
  sleep 2
  LC_ALL=C tail -6 "$SLOG4" | grep -q "1 players online" || die "'list' does not show 1 player - client not in game"
  open=$(python3 -c "
import json,sys
m=json.load(open('$MAN'))
print([s['open'] for s in m['screens'] if s['name']=='$NAME'][0])")
  fifo_cmd "_qtdev $open"
  sleep 3
  rm -f "$PROJ/$GAME/diags/$NAME.png"
  file_cmd "shot $NAME"
  t=0
  until [ -e "$PROJ/$GAME/diags/$NAME.png" ]; do
    t=$((t+2)); [ $t -ge 60 ] && die "shot $NAME never landed (check $CLOG for the hook)"
    sleep 2
  done
  say "shot $NAME landed"
  ;;

collect)
  mkdir -p "$OUT"
  cp -v "$PROJ/$GAME"/diags/*.png "$OUT"/ >/dev/null
  say "collected: $(ls "$OUT"/*.png | wc -l) pngs -> $OUT"
  ;;

check)
  "$TOOL/check.sh" "$OUT" "$MAN"
  ;;

*)
  die "unknown stage '$STAGE'"
  ;;
esac
