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
  pin=$(python3 -c "import json;c=json.load(open('$MAN'))['canvas'];print(c['width'],c['height'])")
  if [ -n "$pin" ] && [ "$g" != "$pin" ]; then
    echo "WARN: both sides are [$g] but the manifest pin is [$pin] - the bank is off-pin; the per-screen center rects (derived for $pin) may be wrong. Re-capture at the pinned size or update the manifest deliberately."
  fi
  echo "canvas: $g (identical)"
  fail=0
  noted=0
  for s in $(screens); do
    # fixture-drift check: banks carry .json sidecars with world-age-min
    if [ -f "$GG/$s.png.json" ] && [ -f "$LL/$s.png.json" ]; then
      d=$(python3 -c "
import json
ga = json.load(open('$GG/$s.png.json')).get('world-age-min')
la = json.load(open('$LL/$s.png.json')).get('world-age-min')
if ga is None or la is None: print('')
elif abs(ga - la) > 30: print('DRIFT: world ages differ by %d min (golden %s, live %s) - fixture content may have moved; rebank for a clean diff' % (abs(ga - la), ga, la))
" 2>/dev/null)
      [ -n "$d" ] && { echo "   $d"; fail=1; }
    elif [ ! -f "$GG/$s.png.json" ] && [ ! -f "$LL/$s.png.json" ] && [ -f "$GG/$s.png" ] && [ -f "$LL/$s.png" ] && [ $noted -eq 0 ]; then
      echo "   (no sidecars - drift check disabled; bank predates sidecars, re-bank to record world age)"
      noted=1
    fi
    if [ -f "$GG/$s.png" ] && [ -f "$LL/$s.png" ]; then
      r=$(cv diff "$GG/$s.png" "$LL/$s.png" 2>/dev/null || true)
      echo "-- $s: $r"
      c=$(python3 -c "import json;[print(*[v for v in s.get('center',[])]) for s in json.load(open('$MAN'))['screens'] if s['name']=='$s']")
      if [ -n "$c" ]; then
        set -- $c
        python3 "$TOOL/crop.py" "$GG/$s.png" /tmp/porttest-center-$s-g.png $1 $2 $3 $4 >/dev/null
        python3 "$TOOL/crop.py" "$LL/$s.png" /tmp/porttest-center-$s-l.png $1 $2 $3 $4 >/dev/null
        rc=$(cv diff /tmp/porttest-center-$s-g.png /tmp/porttest-center-$s-l.png 2>/dev/null || true)
        echo "   center [$c]: $rc"
      fi
    else
      echo "-- $s: MISSING (golden=$([ -f "$GG/$s.png" ] && echo ok || echo no) live=$([ -f "$LL/$s.png" ] && echo ok || echo no))"
      fail=1
    fi
  done
  [ $fail -eq 0 ] || { echo "(diff not clean: missing screens and/or fixture drift above - capture first, then 'run.sh diff')"; return 1; }
}

case "${1:-all}" in
  check) check ;;
  diff) diff_all ;;
  all) check && diff_all ;;
  *) echo "usage: run.sh [check|diff|all]"; exit 1 ;;
esac
