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
#   shot <name>    death/desync guard (grep + Health probe), then openvillager
#                  <type> + shot <name>
#   unstick <proj> <port> <game> [player]  stuck-death reset in the verified
#                  order: stop server, kill ALL client javas, wipe
#                  playerdata/<player>.dat while the server is down, verify,
#                  boot fresh server + ONE client. The death state (Health
#                  0.0f + DeathTime) replays on every rejoin and only clears
#                  with process restart + wipe - the order above is the fix,
#                  so it lives as one stage
#   collect        copy diags/*.png -> out-dir
#   check          run check.sh on out-dir (content gate; run before banking)
#   bank <dir>     copy <proj>/<game>/diags/*.png -> <dir> + write <name>.png.json
#                  sidecars (captured-at, world-age, cursor, branch, commit)
#                  (usage: capture.sh bank <proj> <bank-dir>)
#   state          verified state summary (server pid/port, client CWD, FIFO
#                  writer, stale .qtdiag-cmd, fresh-shot mtime) for this branch
#
# The screen list and expect blocks come from manifest.json.
set -uo pipefail
STAGE=${1:?stage}
PROJ=${2:-}
PORT=${3:-}
GAME=${4:-}
OUT=${5:-/tmp/porttest-coll}
NAME=${6:-}
# `state` and `bank` have their own arg shapes; the rest need PROJ/PORT.
case $STAGE in
  state) ;;
  bank) [ -n "$PROJ" ] || die "bank stage needs a project dir" ;;
  *) [ -n "$PROJ" ] && [ -n "$PORT" ] || die "usage: capture.sh <stage> <project-dir> <server-port> [client-game-dir] [out-dir] [name]" ;;
esac

QJDK=/home/retro/.gradle/jdks/jdk-17.0.20+8
TOOL=$(cd "$(dirname "$0")" && pwd)
MAN=$TOOL/manifest.json
SLOG=/tmp/porttest-$(basename "$PROJ")-server.log
CLOG=/tmp/porttest-$(basename "$PROJ")-client.log
SLOG4=$PROJ/run/logs/latest.log
FIFO=$PROJ/run/server-cmd.fifo

say() { echo "[capture:$STAGE] $*" >&2; }
die() { echo "[capture:$STAGE] FATAL: $*" >&2; exit 1; }
fifo_cmd() { echo "$1" > "$FIFO"; }
# client game dir may differ from the server dir (wip: server=run, client=run-client)
file_cmd() { echo "$1" > "${QT_DIAG_DIR:-$PROJ/$GAME}/.qtdiag-cmd"; }
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
  date +%s > "$PROJ/run/.world-prep-at"
  say "world wiped (fresh normal world will generate)"
  if grep -q '^level-type=flat\b' "$PROJ/run/server.properties" 2>/dev/null; then
    die "level-type=flat: flat-world spawn is broken here (server y=-60, client Y desyncs ~64, player dies). Use a normal world."
  fi
  if [ ! -p "$FIFO" ]; then
    say "creating FIFO writer (O_RDWR: the build's O_RDONLY read-open plus an O_WRONLY '>' writer deadlocks; '<>' opens non-blocking and holds the write fd first - verified 2026-10-01)"
    setsid nohup sh -c "sleep 999999 <> $FIFO" >/dev/null 2>&1 &
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
  # Port LISTEN is the real gate: "Done (" in $SLOG4 can be an OLD line
  # from a previous run (latest.log persists across restarts), and a
  # fresh world's generation takes minutes before the port binds.
  t=0
  until port_listening; do
    if LC_ALL=C grep -aq "BUILD FAILED" "$SLOG"; then
      die "gradle failed: $(LC_ALL=C grep -am1 'error\|FAILED' "$SLOG" | cut -c1-140)"
    fi
    t=$((t+5)); [ $t -ge 60 ] && { say "not up yet (60s); re-run wait-server"; exit 0; }
    sleep 5
  done
  want=$(grep -o 'gradle-[0-9.]*-bin' "$PROJ/gradle/wrapper/gradle-wrapper.properties" 2>/dev/null | sed 's/gradle-//;s/-bin//')
  got=$(LC_ALL=C grep -aom1 'Welcome to Gradle [0-9.]*' "$SLOG" | grep -o '[0-9.]*$')
  if [ -n "$got" ] && [ -n "$want" ] && [ "$got" != "$want" ]; then
    die "gradle version mismatch: wanted $want (wrapper of $PROJ), log says $got - wrong project was launched"
  fi
  say "server up: listening on :$PORT${got:+ (gradle $got)}"
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
  # townplace can silently no-op while the spawn chunks are still
  # generating right after join - retry until the flag's initializer
  # chain appears in the log.
  t=0
  while ! LC_ALL=C grep -aq 'Running initializer' "$SLOG4"; do
    fifo_cmd "_qtdev townplace $X $YB $Z"
    t=$((t+10)); [ $t -ge 50 ] && die "townplace: flag never initialized after ${t}s"
    sleep 10
  done
  sleep 2
  fifo_cmd "_qtdev townspawn $X $YB $Z"
  sleep 6
  LC_ALL=C grep -a '\[townspawn\]' "$SLOG4" | tail -1 | cut -c1-120 || die "no [townspawn] log line - check $SLOG4"
  say "fixture placed at $X $YB $Z"
  ;;

shot)
  [ -n "$NAME" ] || die "shot stage needs a screen name: capture.sh shot $PROJ $PORT $GAME $OUT <name>"
  # full-log grep: latest.log starts at this server's boot, so any
  # death/desync event is caught regardless of the villager-log spam
  # that buries a tail-N window ("Fullness changed" lines, ~20/s).
  guard=$(LC_ALL=C grep -ac 'moved too quickly\|died with\|slain by\|[Kk]illed' "$SLOG4" || true)
  [ "$guard" -eq 0 ] || die "death/desync lines in log: $guard (incl. 'slain by' zombie kills) - do NOT shoot; run 'capture.sh unstick' and re-read the PORTTEST.md guard"
  # health probe: the death-state persist replays with NO new death log line, so
  # the grep above passes on a dead player. Health 0.0f at join is the dead
  # state; a non-zero value is alive.
  PNAME=${7:-Dev}
  hline=""
  for i in 1 2 3 4; do
    timeout 15 sh -c "echo 'data get entity $PNAME Health' > $FIFO" >/dev/null 2>&1 || true
    sleep 5
    hline=$(LC_ALL=C grep -a 'following entity data: ' "$SLOG4" | tail -1)
    case "$hline" in
      *entity\ data:\ 0.0f*) ;;                 # still dead - retry
      *entity\ data:\[0-9]*) break;;            # alive - done
    esac
  done
  case "$hline" in
    *entity\ data:\ 0.0f*) [ -n "$hline" ] && die "player $PNAME Health = 0.0f ~25s after join - stuck death state, not a zombie. Run: capture.sh unstick $PROJ $PORT $GAME $PNAME";;
  esac
  fifo_cmd "list"
  sleep 3
  LC_ALL=C tail -200 "$SLOG4" | grep -Eq 'There are [1-9][0-9]* of a max' || die "'list' does not show a player online - client not in game"
  open=$(python3 -c "
import json,sys
m=json.load(open('$MAN'))
print([s['open'] for s in m['screens'] if s['name']=='$NAME'][0])")
  fifo_cmd "_qtdev $open"
  sleep 3
  rm -f "${QT_DIAG_DIR:-$PROJ/$GAME}/diags/$NAME.png"
  # Park the pointer at the window corner so hover-dependent render state
  # (advancements-pane tooltip fade, cursor under a widget) is identical
  # across captures and branches.
  file_cmd "cursor 1 1"
  sleep 1
  file_cmd "shot $NAME"
  t=0
  until [ -e "${QT_DIAG_DIR:-$PROJ/$GAME}/diags/$NAME.png" ]; do
    t=$((t+2)); [ $t -ge 60 ] && die "shot $NAME never landed (check $CLOG for the hook)"
    sleep 2
  done
  # canvas pin: the bank's reference size. The tiling WM can give a client
  # a different window width (this session: 420 vs 634 in the same run);
  # a shot at the wrong size is silently useless against the bank, so die
  # here, at capture time, instead of corrupting the bank.
  want=$(python3 -c "import json;c=json.load(open('$MAN'))['canvas'];print(c['width'],c['height'])")
  got=$(python3 -c "import struct;d=open('${QT_DIAG_DIR:-$PROJ/$GAME}/diags/$NAME.png','rb').read(24);print(*struct.unpack('>II',d[16:24]))")
  [ "$want" = "$got" ] || die "canvas mismatch: shot $NAME is [$got], manifest pin is [$want] - client window was resized (tiling WM); restore the window size or update manifest.json canvas + center rects deliberately"
  say "shot $NAME landed"
  ;;

collect)
  mkdir -p "$OUT"
  cp -v "${QT_DIAG_DIR:-$PROJ/$GAME}"/diags/*.png "$OUT"/ >/dev/null
  say "collected: $(ls "$OUT"/*.png | wc -l) pngs -> $OUT"
  ;;

check)
  "$TOOL/check.sh" "$OUT" "$MAN"
  ;;

bank)
  [ -n "$PROJ" ] || die "bank stage needs a project dir: capture.sh bank $PROJ <bank-dir>"
  BANKDIR=${3:-}
  [ -n "$BANKDIR" ] || die "bank stage needs a bank dir: capture.sh bank $PROJ <bank-dir>"
  GAME=${4:-run-client}
  DIAGS=${QT_DIAG_DIR:-$PROJ/$GAME}/diags
  mkdir -p "$BANKDIR"
  branch=$(git -C "$PROJ" branch --show-current 2>/dev/null || echo '?')
  commit=$(git -C "$PROJ" rev-parse --short HEAD 2>/dev/null || echo '?')
  now=$(date +%s)
  stamp=$(cat "$PROJ/run/.world-prep-at" 2>/dev/null || echo '')
  age_json='null'
  [ -n "$stamp" ] && age_json=$(( (now - stamp) / 60 ))
  n=0
  for png in "$DIAGS"/*.png; do
    [ -e "$png" ] || continue
    name=$(basename "$png")
    # dimension guard: don't mix capture widths in one bank. The tiling WM
    # can give each client a different window width (this session: wip
    # 634, a11 420), and a 420 shot banked next to a 634 one makes every
    # later diff garbage. If a same-named banked PNG exists at a different
    # canvas size, refuse to overwrite it.
    if [ -f "$BANKDIR/$name" ]; then
      d1=$(python3 -c "import struct;d=open('$png','rb').read(24);print(*struct.unpack('>II',d[16:24]))")
      d2=$(python3 -c "import struct;d=open('$BANKDIR/$name','rb').read(24);print(*struct.unpack('>II',d[16:24]))")
      [ "$d1" = "$d2" ] || die "canvas mismatch $name: fresh [$d1] vs banked [$d2] - client window size changed; re-bank from a clean bank or confirm the resize deliberately"
    fi
    # only bank screens the manifest knows about (test artifacts like
    # v1.png stay out of the bank)
    python3 -c "import json,sys; sys.exit(0 if any(s['name']=='${name%.png}' for s in json.load(open('$MAN'))['screens']) else 1)" || continue
    cp "$png" "$BANKDIR/$name"
    cat > "$BANKDIR/$name.json" <<EOF
{
  "screen": "$(basename "$name" .png)",
  "captured-at": "$(date -Iseconds -d "@$([ -f "$png" ] && stat -c %Y "$png")" 2>/dev/null)",
  "world-age-min": $age_json,
  "cursor": "parked 1,1 (shot stage)",
  "branch": "$branch",
  "commit": "$commit",
  "source": "$png"
}
EOF
    n=$((n+1))
  done
  say "banked $n screens + sidecars -> $BANKDIR"
  ;;

unstick)
  # The stuck-death reset. A player death persists as Health 0.0f + DeathTime
  # and the server replays the death screen on EVERY rejoin; data merge heals,
  # tp, and playerdata wipes do NOT clear it while a server that saw the death
  # (or a stale auto-join client java) is still alive to re-save it.
  # The ORDER is the fix - run it in one stage, not by hand:
  # (own arg shape: capture.sh unstick <proj> <port> <game> [player])
  PNAME=${5:-Dev}
  say "stop server (graceful)"
  timeout 15 sh -c "echo 'stop' > $FIFO" >/dev/null 2>&1 || true
  t=0
  until ! port_listening; do
    t=$((t+2)); [ $t -ge 30 ] && {
      say "port still up; killing server java (cwd $PROJ/run)"
      pgrep -f bootstraplauncher | while read -r p; do
        [ "$(readlink /proc/$p/cwd 2>/dev/null)" = "$PROJ/run" ] && { kill -9 "$p"; say "killed server $p"; };
      done
      t=0; sleep 3; until ! port_listening; do sleep 2; [ $t -ge 30 ] && die "server would not die; investigate by hand"; done
    }
    sleep 2
  done
  say "kill stale client javas (cwd under $PROJ)"
  pgrep -f bootstraplauncher | while read -r p; do
    cwd=$(readlink /proc/$p/cwd 2>/dev/null || true)
    case "$cwd" in "$PROJ"/*) kill -9 "$p" && say "killed $p ($cwd)";; esac
  done
  sleep 2
  say "wipe player data (while server is DOWN)"
  # playerdata files are named by UUID, never by player name - a literal
  # $PNAME.dat wipe proves nothing here (the 09-30 'fresh playerdata' trap;
  # the latch sat in 380df991-…-9527.dat the whole time). These fixture worlds
  # hold one human player, so wiping all of them IS the reset: server is down,
  # nothing will re-save the dead state.
  for d in "$PROJ/run/world/playerdata"/*.dat "$PROJ/run/world/playerdata"/*.dat_old; do
    [ -e "$d" ] && rm -f "$d"
  done
  ls "$PROJ/run/world/playerdata/"*.dat* >/dev/null 2>&1 \
    && { say "surviving player dats:"; ls -la "$PROJ/run/world/playerdata/"; die "wipe failed: player .dat survived"; }
  say "verify: port closed, playerdata clear - clean"
  say "boot server (fresh process)"
  setsid nohup env JAVA_HOME=$QJDK LANG=C.UTF-8 "$PROJ/gradlew" -p "$PROJ" \
    runServer -Dquestown.tp.fifo=true > "$SLOG" 2>&1 &
  say "wait for the port (server first, then client - a client that boots
  while the port is down autojoin-fails and does not retry)"
  t=0
  until port_listening; do
    t=$((t+5)); [ $t -ge 60 ] && { say "server not up yet; run wait-server, THEN kill the client java (cwd under $PROJ) and re-launch it with: capture.sh launch-client $PROJ $PORT ${3:-run-client}"; exit 0; }
    sleep 5
  done
  say "boot ONE client"
  setsid nohup env JAVA_HOME=$QJDK LANG=C.UTF-8 "$PROJ/gradlew" -p "$PROJ" \
    runClient -Pautojoin -Pautoport=$PORT > "$CLOG" 2>&1 &
  say "now: wait-client, joined - in that order (the 1.20.6 hook has no
  'connect' command; autojoin handles the connection once the port is up)"
  ;;

state)
  python3 "$TOOL/state.py"
  ;;

*)
  die "unknown stage '$STAGE'"
  ;;
esac
