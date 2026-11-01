#!/usr/bin/env python3
"""Compress a held-run log's KNOWN dumps into per-state one-liners.

The KNOWN probe (VillageGrowthExecutor) writes every ~2s:
  - one line per townie:   [village] KNOWN townie <uuid8> job=<root>/<job> need=...
  - one quests line:       [village] KNOWN quests open=[Quest{id=...}, Quest{id=...}] done=[...]

In logs predating the xN collapse, the quests line is a raw wall — one
entry per handle instance, 200+ entries ≈ a 100KB line. Reading
membership in that wall by eye is how the 214-phantom JOB_UNLOCK spam
got misread as "day-1 quests re-opened" (2026-10-11, closed as
misdiagnosis in .scratch/mat-gated-arrival/). Count per id first.

Usage:
  scripts/known-dumps.py <logfile> [HH:MM[:SS]] [HH:MM[:SS]]

Prints one OPEN/DONE pair per distinct quest state (counts per id) and
one ROSTER line per distinct townie set, with the first timestamp the
state was seen.
"""
import re
import sys

quests_re = re.compile(r'^\[(\d\d:\d\d:\d\d)\] .*KNOWN quests open=(.*) done=(.*)$')
townie_re = re.compile(r'^\[(\d\d:\d\d:\d\d)\] .*KNOWN townie ([0-9a-f]{8}) job=(\S+)')


def counts(blob):
    out = {}
    for tok in blob.split(', Quest'):
        m = re.search(r'id=([^,]+)', tok)
        key = m.group(1).split(':', 1)[-1] if m else '?'
        out[key] = out.get(key, 0) + 1
    return out


def fmt(c):
    return ' '.join(f'{k}x{v}' for k, v in c.items()) or '-'


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    path = sys.argv[1]
    lo = sys.argv[2] if len(sys.argv) > 2 else '00:00'
    hi = sys.argv[3] if len(sys.argv) > 3 else '99:99'

    prev_q = None
    roster = {}
    prev_roster = None
    with open(path, errors='replace') as f:
        for line in f:
            ts = line[1:9]
            if not (lo <= ts <= hi):
                continue
            m = quests_re.match(line)
            if m:
                oc, dc = counts(m.group(2)), counts(m.group(3))
                if (oc, dc) != prev_q:
                    print(f'{m.group(1)} OPEN({sum(oc.values())}): {fmt(oc)}')
                    print(f'         DONE({sum(dc.values())}): {fmt(dc)}')
                    prev_q = (oc, dc)
                continue
            t = townie_re.match(line)
            if t:
                roster[t.group(2)] = (t.group(3), t.group(1))
                key = frozenset(roster)
                if key != prev_roster:
                    jobs = ' '.join(f'{u}={j[0]}' for u, j in sorted(roster.items()))
                    print(f'{t.group(1)} ROSTER({len(key)}): {jobs}')
                    prev_roster = key


if __name__ == '__main__':
    main()
