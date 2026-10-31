#!/bin/bash
# logwatch.sh <log> <regex>: print new log lines matching <regex> (grep -E), polling every 5 s from
# the log's end at start. For a Monitor watch on a playtest: on 2026-10-25 a Monitor running
# `tail -F | grep --line-buffered` delivered nothing for 30 min (the same pipeline worked from Bash),
# and a trailing `cut` buffers too. Prove a new watch with `tools/ffeed.sh feed "say qt-watch-test"`
# and a regex that includes qt-watch-test, before telling anyone it is watching.
log=${1:?usage: logwatch.sh <log> <regex>}
re=${2:?usage: logwatch.sh <log> <regex>}
off=$(stat -c %s "$log")
while true; do
  size=$(stat -c %s "$log" 2>/dev/null || echo 0)
  [ "$size" -lt "$off" ] && off=0
  if [ "$size" -gt "$off" ]; then
    tail -c +$((off + 1)) "$log" | head -c $((size - off)) | grep -a -E "$re" | grep -a -v 'PROBE:'
    off=$size
  fi
  sleep 5
done
