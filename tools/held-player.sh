#!/usr/bin/env bash
# held-player — make a held-autotest player able to do a real block right-click
# (`_qtdev clickheld <pos>`) without dying.
#
#   tools/held-player.sh <player> <x> <y> <z> <server log>
#   e.g. tools/held-player.sh Dev 2 64 2 /tmp/run.log    (stand on the ground next to the click target)
#
# Why (2026-10-23): a held autotest login joins as a spectator at an arena viewpoint
# inside terrain. A spectator's clickheld logs `-> PASS` and does nothing; an empty
# hand logs `has nothing in the main hand`; a survival player there suffocates in
# seconds, and switching to survival while still mid-air at the viewpoint kicks the
# player ("Flying is not enabled on this server"). This applies peaceful + Resistance
# 255, moves the player to the given ground position, then survival + an item in hand,
# each fed through tools/ffeed.sh and confirmed in the log where vanilla prints a line.
# Needs a live held server with the console FIFO (-Dquestown.tp.fifo=true) and the
# player online.
set -u
USAGE="usage: held-player.sh <player> <x> <y> <z> <server log>"
P=${1:?$USAGE}
X=${2:?$USAGE}
Y=${3:?$USAGE}
Z=${4:?$USAGE}
LOG=${5:?$USAGE}
FF="$(cd "$(dirname "$0")" && pwd)/ffeed.sh"
fail=0

"$FF" feed "difficulty peaceful" --expect "difficulty" "$LOG" || fail=1
"$FF" feed "effect give $P minecraft:resistance 3000 255 true" --expect "Applied effect" "$LOG" || fail=1
"$FF" feed "tp $P $X $Y $Z" --expect "Teleported $P" "$LOG" || fail=1
# No --expect: vanilla prints nothing when the mode is already survival.
"$FF" feed "gamemode survival $P" >/dev/null || fail=1
"$FF" feed "give $P minecraft:paper" --expect "Gave 1" "$LOG" || fail=1

if [ "$fail" -ne 0 ]; then
    echo "held-player: a step was not confirmed (is $P online? is the FIFO console enabled?)" >&2
    exit 1
fi
echo "held-player: $P is survival, peaceful, Resistance 255, holding paper"
