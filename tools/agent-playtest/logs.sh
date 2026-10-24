#!/bin/bash
# logs.sh <extended-regex> [since "DDMonYYYY HH:MM:SS"] [file=latest]: grep the server log across
# the midnight rotation (run/logs/*.log.gz, then latest.log), oldest first. A bare grep of
# latest.log drops everything before midnight, and an awk '$2>="00:28"' filter keeps every 23:xx
# line from the day before (r16). `since` compares the full date+time, e.g. "24Oct2026 00:28:00".
# Pass file=debug to search run/logs/debug.log (not rotated) instead.
set -u
REPO=$(cd "$(dirname "$0")/../.." && pwd)
cd "$REPO"
pat=${1:?usage: logs.sh <regex> [since "24Oct2026 00:28:00"] [debug]}
since=${2:-}
if [ "${3:-}" = debug ]; then
  src() { cat run/logs/debug.log; }
else
  src() { for f in $(ls -tr run/logs/*.log.gz 2>/dev/null); do zcat "$f"; done; cat run/logs/latest.log; }
fi
src | grep -a -E -- "$pat" | python3 -I -c '
import sys, datetime, signal
signal.signal(signal.SIGPIPE, signal.SIG_DFL)
since = sys.argv[1]
fmt = "%d%b%Y %H:%M:%S"
cut = datetime.datetime.strptime(since, fmt) if since else None
for line in sys.stdin:
    if cut is not None:
        try:
            ts = datetime.datetime.strptime(line[1:19], fmt)
        except ValueError:
            continue
        if ts < cut:
            continue
    sys.stdout.write(line)
' "$since"
