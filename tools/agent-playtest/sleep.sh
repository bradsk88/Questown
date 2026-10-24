#!/bin/bash
# sleep.sh <campfire x y z> <stand x y z> [wand hotbar slot, default 3]: put the town wand in
# hand, stand by the campfire and use the wand on it until the night is skipped (max 3 uses:
# after a night the first use relights the fire). The campfire must sit at the flag's own Y.
set -u
REPO=$(cd "$(dirname "$0")/../.." && pwd)
cd "$REPO"
cx=${1:?usage: sleep.sh <campfire x y z> <stand x y z> [wand-slot]}; cy=${2:?}; cz=${3:?}
sx=${4:?usage: sleep.sh <campfire x y z> <stand x y z> [wand-slot]}; sy=${5:?}; sz=${6:?}; slot=${7:-3}
L=run/logs/latest.log
# Beds only work from daytime 12542 to 23459; the nightfall bed capture logs at 11500, and a run
# started on that line used to fail "too early (isDay)" (r16). Wait for the window, up to a day.
for _ in $(seq 1 45); do
  t=$(tools/ffeed.sh feed "time query daytime" --capture $L "The time is" | sed 's/.*The time is //')
  [ "$t" -ge 23460 ] 2>/dev/null && { echo "daytime $t: too close to dawn to sleep" >&2; exit 1; }
  [ "$t" -ge 12542 ] 2>/dev/null && break
  echo "waiting for night (daytime $t)" >&2; sleep 15
done
before=$(grep -ac 'SleepFinishedTimeEvent' $L)
tools/ffeed.sh feed "item replace entity Dev weapon.mainhand from entity Dev hotbar.$slot" >/dev/null
tools/ffeed.sh feed "tp Dev $sx $sy $sz" >/dev/null; sleep 1
for _ in 1 2 3; do
  tools/ffeed.sh feed "_qtdev clickheld $cx $cy $cz" >/dev/null; sleep 4
  if [ "$(grep -ac 'SleepFinishedTimeEvent' $L)" -gt "$before" ]; then
    grep -a 'SleepFinishedTimeEvent' $L | tail -1 | sed 's/.*newTime=/slept, newTime=/'
    exit 0
  fi
done
echo "not asleep after 3 uses: too early (isDay), wand not in slot $slot, or too far from the campfire" >&2
exit 1
