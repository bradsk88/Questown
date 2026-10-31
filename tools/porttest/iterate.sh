#!/usr/bin/env bash
# Per-screen iterate: re-shoot the FRESH capture, name it, gate it, bank it,
# diff it. Built so the "measured the stale bank" trap is impossible by
# construction: every verdict below prints the exact path + mtime of the file
# it measured, and banking only happens after the gate passes.
#
# Stages mirror capture.sh discipline: each stage <=60s, re-invocable, and
# 'all' is just the three chained (re-invoke to resume if one times out).
#
#   iterate.sh <stage> <screen> <project> <server-port> <client-game-dir> [version]
#
# Stages:
#   shoot   capture.sh shot, then print the fresh capture's path + mtime +
#           dims, plus cv diff vs the last banked copy (if one exists)
#   gate    content gate (check.sh) on the FRESH capture in the diags dir
#   bank    copy fresh -> goldens/1.19.2 (version 1.19.2) or live/<version>,
#           then cv diff against the 1.19.2 golden
#   all     shoot, gate, bank in order
#
# QT_DIAG_DIR overrides the client game dir (wip client is run-client/).
set -uo pipefail
STAGE=${1:?stage}
SCREEN=${2:?screen name}
PROJ=${3:?project dir}
PORT=${4:?server port}
GAME=${5:?client gameDir relative to project}
VER=${6:-1.20.6}

TOOL=$(cd "$(dirname "$0")" && pwd)
DIAGS=${QT_DIAG_DIR:-$PROJ/$GAME}/diags
GG=$TOOL/goldens/1.19.2
case $VER in
  1.19.2) BANK=$GG ;;
  *) BANK=$TOOL/live/$VER ;;
esac
say() { echo "[iterate:$STAGE] $*" >&2; }
die() { echo "[iterate:$STAGE] FATAL: $*" >&2; exit 1; }

nameit() { # <png> - print the path, mtime, dims of the file actually measured
  python3 - "$1" <<'PYEOF'
import struct, sys, time, os
p = sys.argv[1]
d = open(p, 'rb').read(24)
w, h = struct.unpack('>II', d[16:24])
print('  file: %s' % p)
print('  mtime: %s' % time.strftime('%H:%M:%S', time.localtime(os.path.getmtime(p))))
print('  dims: %dx%d' % (w, h))
PYEOF
}

shoot() {
  "$TOOL/capture.sh" shot "$PROJ" "$PORT" "$GAME" /tmp/collect "$SCREEN"
  f=$DIAGS/$SCREEN.png
  [ -f "$f" ] || die "no fresh capture at $f"
  nameit "$f"
  if [ -f "$BANK/$SCREEN.png" ]; then
    r=$(cv diff "$f" "$BANK/$SCREEN.png" 2>/dev/null || true)
    say "vs last banked ($BANK/$SCREEN.png): $r"
  else
    say "no banked copy yet ($BANK/$SCREEN.png)"
  fi
}

gate() {
  f=$DIAGS/$SCREEN.png
  [ -f "$f" ] || die "no fresh capture to gate - run 'shoot' first"
  "$TOOL/check.sh" "$DIAGS" "$SCREEN"
}

bank() {
  f=$DIAGS/$SCREEN.png
  [ -f "$f" ] || die "no fresh capture to bank - run 'shoot' then 'gate' first"
  mkdir -p "$BANK"
  cp -f "$f" "$BANK/$SCREEN.png"
  say "banked -> $BANK/$SCREEN.png"
  nameit "$BANK/$SCREEN.png"
  if [ -f "$GG/$SCREEN.png" ] && [ "$BANK" != "$GG" ]; then
    r=$(cv diff "$GG/$SCREEN.png" "$BANK/$SCREEN.png" 2>/dev/null || true)
    say "diff vs golden 1.19.2: $r"
  elif [ "$BANK" = "$GG" ]; then
    say "banked into goldens; no diff"
  fi
}

case $STAGE in
  shoot) shoot ;;
  gate)  gate ;;
  bank)  bank ;;
  all)   shoot; gate; bank ;;
  *) die "unknown stage '$STAGE' (shoot|gate|bank|all)" ;;
esac
