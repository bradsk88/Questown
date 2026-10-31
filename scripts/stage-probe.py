#!/usr/bin/env python3
"""stage-probe — "which stage is stuck?" from any Questown held-run server log.

Usage:
  scripts/stage-probe.py <serverlog>            e.g. /tmp/tier4-server.log
  scripts/stage-probe.py <serverlog> --top 10   more ProductionStatus rows

Why this exists
---------------
A soft-lock is held by exactly ONE stage of the pipeline. The tempting read is
the stage with the most *activity* (a 2,000-line REJECT@canFit / seeking_work
loop) — but that is often a working upstream stage hit out-of-window, a *symptom*,
not the cause. The stuck stage is the one whose QUEST never reaches COMPLETED
while a downstream stage reports NO_JOBSITE / NO_SPACE / NO_SUPPLIES. So:

  * identify the stuck stage by terminal state  (COMPLETED vs ACTIVE, never-completed)
  * then read that stage's ProductionStatus     (staffing vs supply) —
    NO_JOBSITE = no workable block, NO_SPACE = can't deposit, NO_SUPPLIES = lacks the item

A high activity count at an already-COMPLETED stage (e.g. the hunter's lates-day
canFit REJECT loop) is a cap / side-symptom, not the bottleneck.

The same signal is now emitted live per watch-window by the scenario
(village/self_sufficiency "[village] ... STAGES" line); this script is the
offline version for any past / ad-hoc server log.
"""
import re
import sys
from collections import Counter, defaultdict

# ROOM / JOB_CHANGE / LOOT / ITEM / ... with an OFFERED | ACTIVE | COMPLETED state
# and a namespace (questown | minecraft). The id may contain underscores.
QUEST = re.compile(
    r'\b(ROOM|JOB_CHANGE|LOOT|ITEM|CRAFT|FARM|FISH|SMELT)\b'
    r'/(OFFERED|ACTIVE|COMPLETED)'
    r':(?P<ns>questown|minecraft)'
    r':(?P<id>[A-Za-z0-9_]+)'
)
STATUS = re.compile(r"name='([A-Z_]+)'")
REJECT = re.compile(r'REJECT@canFit')
ACCEPT = re.compile(r'\bACCEPT\b')

STATE_ORDER = {'OFFERED': 1, 'ACTIVE': 2, 'COMPLETED': 3}


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__.strip(), file=sys.stderr)
        return 2
    path = sys.argv[1]
    top_n = 8
    if '--top' in sys.argv:
        i = sys.argv.index('--top')
        top_n = int(sys.argv[i + 1])

    best = {}                       # quest fully-qualified id -> max state seen
    counts = defaultdict(Counter)   # id -> Counter[state]
    status = Counter()
    n_reject = n_accept = 0

    with open(path, 'r', errors='replace') as fh:
        for line in fh:
            for m in QUEST.finditer(line):
                qid = f"{m.group('ns')}:{m.group('id')}"
                st = m.group(2)
                counts[qid][st] += 1
                if best.get(qid, 0) < STATE_ORDER[st]:
                    best[qid] = STATE_ORDER[st]
            status.update(STATUS.findall(line))
            if 'JobCycler' in line or '[jobcyc]' in line:
                n_reject += len(REJECT.findall(line))
                n_accept += len(ACCEPT.findall(line))

    def key(qid):
        ns, _, name = qid.partition(':')
        return (0 if ns == 'questown' else 1, name)

    completed = sorted([q for q in best if best[q] == 3], key=key)
    stuck = sorted([q for q in best if best[q] == 2], key=key)      # ACTIVE, never COMPLETED
    offered_only = sorted([q for q in best if best[q] == 1], key=key)

    print(f"== stage-probe: {path}")
    print(f"   hunter/canFit gate: ACCEPT={n_accept}  REJECT@canFit={n_reject}  "
          f"(activity is a SYMPTOM — see STUCK below for the actual bottleneck stage)")
    print()

    print("STUCK (reached ACTIVE, NEVER COMPLETED) — the stuck stage is among these:")
    if stuck:
        for q in stuck:
            c = counts[q]
            print(f"  ! {q}   {dict(c)}")
    else:
        print("  (none — every active quest completed)")
    print()

    if offered_only:
        print("OPEN (OFFERED, never even ACTIVE):")
        for q in offered_only:
            print(f"  ? {q}   {dict(counts[q])}")
        print()

    print(f"COMPLETED ({len(completed)}):")
    for q in completed:
        c = counts[q]
        print(f"    {q}   {dict(c)}")
    print()

    print(f"ProductionStatus (top {top_n}) across all townies/jobs:")
    for name, n in status.most_common(top_n):
        print(f"  {n:>8}  {name}")
    print()
    print("Reading: a STUCK quest + NO_JOBSITE = no workable block (staffing/room).")
    print("          A STUCK quest + NO_SPACE = can't deposit. NO_SUPPLIES = lacks the item.")
    print("          A completed upstream stage with a big REJECT/loop = lates-day cap, not the cause.")
    return 0


if __name__ == '__main__':
    sys.exit(main())
