#!/usr/bin/env bash
# sweep — one village/self_sufficiency held-run, end to end, with verified pacing.
#
# The scratch copies of this sequence live in run/ (gitignored: run/onerun.sh and
# friends) and will rot; this is the committed one. It exists because the
# launch-flag set is a HARD requirement, not a style choice:
#
#   -Dquestown.tp.fifo=true     build.gradle only wires standardInput=<fifo> for
#                               runServer when this is present (the tasks.matching
                               # block at the "Gated ONLY on -Dquestown.tp.fifo"
#                               comment). Without it EVERY console feed is silently
#                               dropped: the rate never applies, and the run burns
                               # wall time at the 20 t/s default. (2026-10-21: one
                               # run segment sat at the default until the rate was
                               # applied; the pace-guard does NOT catch this — see
                               # its header and the skill's TPS section.) build.gradle now warns on config — but only
                               # if that line is still there.
#
# The 200 t/s pace is NOT an assertion of its own: the log MUST contain
# `Set the target tick rate to 200.0 per second` and the pace self-check must
# measure ~200 t/s (worldTimeAtSleep deltas) — a missing echo means the console
# path is dead, whatever this script claims.
#
# Verdict (from the held-run skill, verified 2026-10-21):
#   - `[autotest] RESULT: 0/0 passed (...)` is NORMAL here — the scenario is a
#     report-only watchtower ("contributes no assertions", AutoTestRunner).
#     0/0 is "no assertions ran", not "a failure". The verdict is the scenario's
#     own lines:
#       no `[lock]` lines            — no UNMET_ITEM / NO_WORK_POSSIBLE soft-lock
#       no `[journal] UNMET_ITEM`    — no unmet gather requirement
#       `[self_suff] milestones`     — the town progressed
#       `cookUnlocked=true`          — at least one (any) cook-skill unlocked
# The scenario is REALTIME (24000 real ticks per day, `no warp` per the
# executor Javadoc) — do not "speed it up" by adding warp; the held-run skill's
# TPS section is the only sanctioned pace lever, and it needs the FIFO console
# above, which is why this flag is non-negotiable here.
#
# Status (2026-10-21): guards verified live (usage/exit 20; a live-server pre-
# block at 21 — untested — same shape; the build-failure detect path fired and
# cleaned up correctly). The happy boot→feed→watch→RESULT path is NOT yet
# proven to run end-to-end — the sequence it encodes ran twice manually this
# session (both cleared; see .scratch/village-self-sufficiency/unseededMat-test-
# defect-map.md), but a full scripted run is still owed.
#
# Usage:    scripts/sweep.sh <seconds-to-watch>
#            (e.g. 900 for a 15-min run; the scenario reports on its own clock,
#             so a longer watch just gives more villages the chance to hit the
#             stall window — see the 2026-10-21 defect map in
#             .scratch/village-self-sufficiency/unseededMat-test-defect-map.md)
# Requires:  tools/ffeed.sh (tracked), run/pace-guard.sh (scratch, untracked — recreate from
#            .pi/skills/questown-held-run/SKILL.md if absent), Java 21 on PATH
#            for this branch's Gradle 8.14.
#
# Exit: 0 clean (no lock, cookUnlocked, milestones, banner present) ·
#       1 lock/UNMET observed · 2 run did not reach the RESULT banner ·
#       20 usage · 21 a Questown server is already running (not ours to kill).

set -u
REPO=${REPO:-/workspace/IdeaProjects/Questown}
cd "$REPO" || exit 20
WATCH="${1:-900}"
case "$WATCH" in ''|*[!0-9]*) echo "usage: $0 <seconds-to-watch>" >&2; exit 20;; esac

# Guard: never start on top of a live server (the 2026-10 trap from the
# questown-held-run skill — a racing build/client is how a held run dies).
if pgrep -af 'BootstrapLauncher' >/dev/null 2>&1; then
  echo "a Questown server is already running — kill it (pgrep -af BootstrapLauncher) and re-run" >&2
  exit 21
fi

rm -rf run/world run/mods
mkdir -p run
[ -p run/server-cmd.fifo ] || { rm -f run/server-cmd.fifo; mkfifo run/server-cmd.fifo; }
LOG="${LOG:-/tmp/questown-sweep-$(date +%H%M%S).log}"
# Per-logger DEBUG xml if present (skill's per-logger example); plain log4j2 otherwise.
EXTRA_ARGS=""
[ -f run/log4j2.sweep.xml ] && EXTRA_ARGS="-Dquestown.log4j.config=file://$PWD/run/log4j2.sweep.xml"

# Hold the FIFO open from the start (a dead writer = a hung server stdin).
nohup sh -c "sleep 999999 > run/server-cmd.fifo" > /dev/null 2>&1 &
FIFO_WRITER=$!
SERVER_PID=""
cleanup(){
  [ -n "$SERVER_PID" ] && kill -9 "$SERVER_PID" 2>/dev/null
  # The writer's sleep child does NOT carry the fifo path in its cmdline, so a
  # pkill -f on the path misses it; kill it by parent (precise — never touches
  # the Questown-119 sibling's writer).
  for c in $(ps -o pid= --ppid "$FIFO_WRITER" 2>/dev/null); do kill -9 "$c" 2>/dev/null; done
  kill -9 "$FIFO_WRITER" 2>/dev/null
}
trap cleanup EXIT

echo "launching runServer (autotest hold, self_sufficiency, 50s boot)…"
nohup ./gradlew runServer \
  -Dquestown.autotest=true \
  -Dquestown.autotest.hold=true \
  -Dquestown.autotest.only=village/self_sufficiency \
  -Dquestown.tp.fifo=true \
  $EXTRA_ARGS \
  > "$LOG" 2>&1 &

for i in $(seq 1 10); do
  if grep -aq 'Done\|Flag block entity live' "$LOG" 2>/dev/null; then break; fi
  if grep -aq 'BUILD FAILED' "$LOG" 2>/dev/null; then echo "BUILD FAILED — see $LOG" >&2; exit 2; fi
  sleep 5
done
grep -aq 'Done\|Flag block entity live' "$LOG" || { echo "boot not confirmed in 50s — see $LOG" >&2; exit 2; }
SERVER_PID=$(pgrep -af 'BootstrapLauncher' | awk '{print $1}' | head -1 || true)
[ -n "$SERVER_PID" ] || { echo "no server java process after boot" >&2; exit 2; }
echo "server pid $SERVER_PID"

# Raise the tick rate and VERIFY it landed (not blind: a missing echo = dead console).
timeout 90 tools/ffeed.sh feed "/tick rate 200" --expect "Set the target tick rate" "$LOG" || {
  echo "CONSOLE DEAD — rate never applied (see the -Dquestown.tp.fifo trap in this script's header)" >&2; exit 2; }

# Watch to the deadline (or the RESULT banner, whichever comes first).
DEADLINE=$(( $(date +%s) + WATCH ))
while :; do
  if grep -aq 'RESULT: [0-9]*/[0-9]* passed' "$LOG" 2>/dev/null; then
    echo "RESULT banner at $(date +%H:%M:%S) — harvesting"; break; fi
  if grep -aq '\[lock\]\|UNMET_ITEM' "$LOG" 2>/dev/null; then
    echo "LOCK/UNMET detected at $(date +%H:%M:%S) — harvesting early"; break; fi
  [ "$(date +%s)" -ge "$DEADLINE" ] && { echo "watch deadline reached at $(date +%H:%M:%S) — harvesting"; break; }
  sleep 20
done

# Harvest the verdict lines (full trail also in $LOG). A lock/UNMET beats a
# missing banner: it is the verdict we are hunting for.
echo "──── verdict lines ────"
grep -aE '\[lock\]|UNMET_ITEM|cookUnlocked|milestones|RESULT: [0-9]*/[0-9]*' "$LOG" | tail -20
RC=0
grep -aq 'RESULT: [0-9]*/[0-9]* passed' "$LOG" || RC=2
grep -aq '\[lock\]\|UNMET_ITEM' "$LOG" && RC=1
exit $RC
