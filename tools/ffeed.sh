#!/usr/bin/env bash
# ffeed — the ONE FIFO recipe for this box. Never write your own.
#
# Why: a bare `echo … > run/server-cmd.fifo` blocks the WRITER indefinitely
# when nothing has the fifo READ side open (2026-10-04: one such write hung a
# bash tool call 7532 s, server up the whole time). The proven recipe (from
# run/suff-batch-fast.sh, whose 200 t/s delivery is pace-verified) is a holder
# that opens the fifo READ-WRITE (`<>`), which keeps the read end open so
# writers never block.
#
# Usage:
#   tools/ffeed.sh setup                                    # fifo + idempotent holder
#   tools/ffeed.sh feed <cmd...> [--expect <substr> <log...>]
#                                              # bounded write; with --expect, also
#                                              # greps the log(s) for <substr> (3
#                                              # tries) and reports delivered/not
#
# Exit: 0 = wrote (and, with --expect, confirmed in log) · 1 = timeout/not found
#       · 2 = usage
set -u
FIFO="$(cd "$(dirname "$0")/.." && pwd)/run/server-cmd.fifo"

case "${1:-}" in

setup)
  if [ ! -p "$FIFO" ]; then rm -f "$FIFO"; mkfifo "$FIFO"; fi
  find_holder() {
    for p in $(pgrep -f 'sleep 999999' 2>/dev/null); do
      for fd in $(ls "/proc/$p/fd" 2>/dev/null); do
        t=$(readlink "/proc/$p/fd/$fd" 2>/dev/null) || continue
        case "$t" in *"$FIFO"*) case "$t" in *"(deleted)") ;; *) echo "$p"; return 0;; esac;; esac
      done
    done
    return 1
  }
  h=$(find_holder) && { echo "holder=$h (existing)"; exit 0; }
  # NOTE: `<>` = read-write holder — the read end is what keeps writers unblocked.
  nohup sh -c "sleep 999999 <> '$FIFO'" > /dev/null 2>&1 &
  sleep 1
  h=$(find_holder) && { echo "holder=$h (started)"; exit 0; }
  echo "ffeed: FATAL — holder not open on $FIFO" >&2; exit 1
  ;;

feed)
  shift
  CMD=""; EXPECT=""; LOGS=(); ARGS=("$@"); n=${#ARGS[@]}; i=0
  while [ $i -lt $n ]; do
    a="${ARGS[$i]}"
    if [ "$a" = "--expect" ]; then
      [ $i -lt $((n-1)) ] || { echo "usage: feed <cmd...> [--expect <substr> <log...>]" >&2; exit 2; }
      EXPECT="${ARGS[$((i+1))]}"
      if [ $i -lt $((n-2)) ]; then LOGS=("${ARGS[@]:$((i+2))}"); fi
      break
    fi
    if [ -n "$CMD" ]; then CMD="$CMD $a"; else CMD="$a"; fi
    i=$((i+1))
  done
  [ -z "$CMD" ] && { echo "usage: feed <cmd...> [--expect <substr> <log...>]" >&2; exit 2; }
  # Confirm only against lines written AFTER this feed: a whole-log grep let an older line
  # "confirm" a command that never parsed (2026-10-23: the scenario's own 'Town shutdown started'
  # confirmed a 'qt flag shutdown <pos>' that died on 'Expected integer').
  BEFORE=()
  for L in "${LOGS[@]}"; do BEFORE+=("$(wc -l < "$L" 2>/dev/null || echo 0)"); done
  # Bounded: a blocked write dies at 15 s (the 7532 s trap is gone).
  # Args passed positionally — no shell-quoting of the command inside the write.
  if ! timeout 15 bash -c 'echo "$0" > "$1"' "$CMD" "$FIFO"; then
    echo "ffeed: WRITE TIMED OUT (15 s) — read end not open: is runServer up? (pgrep -af '[r]unServer')" >&2
    exit 1
  fi
  if [ -n "$EXPECT" ] && [ ${#LOGS[@]} -gt 0 ]; then
    for _ in 1 2 3; do
      for k in "${!LOGS[@]}"; do
        tail -n +"$(( ${BEFORE[$k]} + 1 ))" "${LOGS[$k]}" 2>/dev/null | grep -qa "$EXPECT" \
          && { echo "delivered (confirmed: '$EXPECT' in a new log line)"; exit 0; }
      done
      sleep 3
    done
    echo "ffeed: wrote, but '$EXPECT' NOT in the log — likely not executed (parse failure / wrong build)" >&2
    exit 1
  fi
  echo "wrote (unverified — no --expect given)"
  exit 0
  ;;

*)
  echo "usage: tools/ffeed.sh setup | feed <cmd...> [--expect <substr> <log...>]" >&2; exit 2
  ;;

esac
