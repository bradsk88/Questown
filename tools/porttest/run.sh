#!/usr/bin/env bash
# Run the port-test diff stage. Captures are driven by the staged
# capture.sh (one side at a time; see PORTTEST.md) - this script only
# verifies and diffs.
#
#   run.sh check          content gate on both dirs (check.sh)
#   run.sh diff           dimension invariant + cv diff per screen
#   run.sh                both, in order
#
# Screen names + thresholds come from manifest.json.
set -uo pipefail
TOOL=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$TOOL/../.." && pwd)
MAN=$TOOL/manifest.json
GG=$TOOL/goldens/1.19.2
LL=$TOOL/live/1.20.6

screens() { python3 -c "import json;[print(s['name']) for s in json.load(open('$MAN'))['screens']]"; }
dims() { # w h (empty if missing)
  [ -f "$1.png" ] || return 0
  python3 -c "
import struct
d = open('$1.png','rb').read(24)
print(*struct.unpack('>II', d[16:24]))"
}

check() {
  echo "=== content gate (check.sh) ==="
  local rc1 rc2
  "$TOOL/check.sh" "$GG" "$MAN"; rc1=$?
  "$TOOL/check.sh" "$LL" "$MAN"; rc2=$?
  [ $rc1 -eq 0 ] && [ $rc2 -eq 0 ] || { echo "content gate failed - re-capture before diffing"; return 1; }
}

diff_all() {
  echo "=== diff (golden 1.19.2 -> target 1.20.6) ==="
  g=$(dims "$GG/parent"); l=$(dims "$LL/parent")
  if [ "$g" != "$l" ]; then
    echo "FATAL: canvas mismatch - golden [$g] vs live [$l]; a diff is meaningless, re-capture"
    return 1
  fi
  echo "canvas: $g (identical)"
  fail=0
  for s in $(screens); do
    if [ -f "$GG/$s.png" ] && [ -f "$LL/$s.png" ]; then
      r=$(cv diff "$GG/$s.png" "$LL/$s.png" 2>/dev/null || true)
      echo "-- $s: $r"
    else
      echo "-- $s: MISSING (golden=$([ -f "$GG/$s.png" ] && echo ok || echo no) live=$([ -f "$LL/$s.png" ] && echo ok || echo no))"
      fail=1
    fi
  done
  [ $fail -eq 0 ] || { echo "(screens missing - capture first, then 'run.sh diff')"; return 1; }
}

case "${1:-all}" in
  check) check ;;
  diff) diff_all ;;
  all) check && diff_all ;;
  *) echo "usage: run.sh [check|diff|all]"; exit 1 ;;
esac
