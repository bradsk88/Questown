#!/usr/bin/env bash
# Full deterministic bank refresh: both branches from freshly prepped
# worlds, in parallel. Per branch: prep -> launch-server -> wait-server ->
# launch-client -> wait-client -> connect -> joined -> fixture ->
# shot x6 (each parks the cursor) -> gate -> bank (PNG + .json sidecars
# with world-age, so a later diff can detect fixture drift without pixel
# forensics).
#
#   rebank.sh [wip|a11|both]
#
# Re-run a single branch after a partial failure: rebank.sh wip.
set -uo pipefail
TOOL=$(cd "$(dirname "$0")" && pwd)
CAP=$TOOL/capture.sh
WIP=/workspace/IdeaProjects/Questown;       WIP_PORT=25565
A11=/workspace/IdeaProjects/Questown-119;   A11_PORT=25571
GAME=run-client
OUT=/tmp/porttest-coll
BANKROOT=$WIP/tools/porttest
SCREENS=(parent quests stats skills economics bop)
only=${1:-both}

# stage <retries> <stage> <proj> <port> [extra args...]
# Run a capture.sh stage; poll <=retries times while it reports
# "re-run" / "not yet" (the wait stages' timed-out shape). FATAL never
# retries past the limit.
stage() {
  local retries=$1 st=$2 proj=$3 port=$4; shift 4
  local i out
  for i in $(seq 1 "$retries"); do
    out=$(bash "$CAP" "$st" "$proj" "$port" "$GAME" "$OUT" "$@" 2>&1 || true)
    case $out in
      *FATAL*)
        echo "[rebank] $st $proj failed: $(echo "$out" | grep FATAL | tail -1 | cut -c1-140)" >&2
        return 1 ;;
      *"re-run"*)
        [ $i -eq "$retries" ] && { echo "[rebank] $st $proj: no progress after $i x 60s" >&2; return 1; }
        sleep 5; continue ;;
      *)
        return 0 ;;
    esac
  done
  return 1
}

run_branch() { # <proj> <port> <bank-dir>
  local proj=$1 port=$2 bank=$3
  local name s out
  name=$(basename "$proj")
  echo "[rebank:$name] start"
  stage 2  prep "$proj" "$port" || return 1
  stage 1  launch-server "$proj" "$port" || return 1
  stage 30 wait-server "$proj" "$port" || return 1
  stage 1  launch-client "$proj" "$port" || return 1
  stage 30 wait-client "$proj" "$port" || return 1
  sleep 10  # -Pautojoin connects on its own
  # connect is the FALLBACK only: issuing it while autojoin is still
  # mid-handshake opens a second connection and can stall the client
  # (hit on 1.19.2: two concurrent handshakes, the second never completes).
  if ! LC_ALL=C grep -aq 'logged in with entity id' "$proj/run/logs/latest.log" 2>/dev/null; then
    stage 1  connect "$proj" "$port" || return 1
  fi
  stage 30 joined "$proj" "$port" || return 1
  stage 3  fixture "$proj" "$port" || return 1
  for s in "${SCREENS[@]}"; do
    stage 2 shot "$proj" "$port" "$s" || return 1
  done
  out=$(bash "$TOOL/check.sh" "$proj/$GAME/diags" 2>&1)
  echo "$out" | sed 's/^/[rebank:'"$name"'] /'
  if ! grep -q '^FAIL' <<<"$out"; then :; else
    echo "[rebank:$name] gate FAILED - do not bank" >&2
    return 1
  fi
  bash "$CAP" bank "$proj" "$bank" || return 1
  echo "[rebank:$name] done"
}

case $only in
  wip) run_branch "$WIP" "$WIP_PORT" "$BANKROOT/live/1.20.6" ;;
  a11) run_branch "$A11" "$A11_PORT" "$BANKROOT/goldens/1.19.2" ;;
  both)
    run_branch "$WIP" "$WIP_PORT" "$BANKROOT/live/1.20.6" & wp=$!
    run_branch "$A11" "$A11_PORT" "$BANKROOT/goldens/1.19.2" & ap=$!
    wait $wp; wr=$?
    wait $ap; ar=$?
    [ $wr -eq 0 ] && [ $ar -eq 0 ] || exit 1
    ;;
  *) echo "usage: rebank.sh [wip|a11|both]"; exit 2 ;;
esac

echo "[rebank] banks refreshed; diffing:"
bash "$TOOL/run.sh" diff 2>&1 | tail -16
