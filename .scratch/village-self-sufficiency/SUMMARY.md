# village/self_sufficiency — overnight batch (5-day runs)

**Date:** 2026-07-25 (night), 05:35 → 16:17 UTC
**What ran:** `village/self_sufficiency`, **6 complete 5-day cycles** (each a fresh
world, server + autojoined dev client). 6/6 finished their watch to the
`[autotest] RESULT` + `WATCH complete` banner; the driver's 110-min cap expired
~1-2 min after the RESULT in most runs — the cap is cosmetic here, the data is
complete (each run-N.md has exactly one RESULT and one WATCH-complete line).

**Read first:**
- `run-1.md` … `run-6.md` — per-run harvest (roster, milestones, blocker lines)
- `BLOCKERS.md` — cumulative, raw; NOISY (the driver's grep also catches the
  `watch day=…` heartbeat lines that carry `UNMET_ITEM` — curation pending, see below)
- this file — the overnight state contract

## The signal (what the 6 runs say)

| run | roster | firstNight | cook unlocked | farmer unlocked | NO_FORWARD fires |
|-----|--------|-----------|---------------|-----------------|------------------|
| 1   | 4      | yes       | no            | no              | yes (late)       |
| 2   | 4      | yes       | no            | no              | yes              |
| 3   | 4      | yes       | no            | no              | yes              |
| 4   | 5      | yes       | no            | no              | yes              |
| 5   | 4      | yes       | **yes**       | no              | **none**         |
| 6   | 6      | yes       | no            | no              | yes              |

- **Growth works:** roster 4,4,4,5,4,6 across runs — villager population grows
  within the week.
- **Cook rarely unlocks:** 5/6 `cookUnlocked=false`; only run 5 (the only run
  with zero late-stall fires) got a cook.
- **Farmer never unlocks:** 6/6 `farmerUnlocked=false`.
- **Stall detector mostly fires:** `NO_FORWARD_PROGRESS` fires in 5/6 runs —
  the village hits a wall and the autotest's own watchdog trips. Run 5 (clean
  run, cook unlocked) is the exception.
- **One soft-lock line in every run** (`[autotest] soft-lock …`) and one
  `NO_WORK_POSSIBLE` in run 3.
- **Zero exceptions / zero FATAL** in any of the 6 harvests — nothing crashed.

## Candidate blockers to investigate next session (raw, unverified)

1. **Soup/mushroom item gate.** Runs 1 and 3 log `NO_WORK_POSSIBLE` /
   `UNMET_ITEM #c:mushrooms` — a cook-path job blocked on a `#c:mushrooms` tag
   item the farmer never produced in 5 days. (Verify tag + which job holds it.)
2. **Farmer job never unlocks** — 6/6. Check the unlock condition; it may
   depend on the same farm-chain the cook stall hints at.
3. **Late `NO_FORWARD_PROGRESS` fires** (5/6) — the village stalls in the latter
   half of the week. Need the timestamp + which townie/job was idle to name the
   real blocker (the raw log lines are in each run-N.md).
4. **Roster 4 → up to 6** — growth is bounded; check what caps it (beds? rooms?
   the flag's radius?).

> These are *leads from log strings*, not confirmed root causes. The next
> session should start by reading the timestamped blocker lines in run-1.md and
> run-3.md around the first NO_WORK_POSSIBLE / NO_FORWARD fire.

## Driver note (do not trust the "cut" labels without checking the log)

The driver's 110-min per-run cap is **too short** for this scenario: real
duration is ~seed (5-15 min) + 5×10 min days ≈ 60-80 min, but with client
join + world load it crossed 110 in several runs. Because
`questown.autotest.hold=false`, the server halts after RESULT — so a "cut"
run that already printed RESULT is a **completed** run; the kill is harmless.
Bump the cap to ~150 min if re-running.

## How to check (next session, in ~2s)

```bash
cd .scratch/village-self-sufficiency
tail -3 BATCH.log; ls run-*.md
# per-run: grep -n 'roster=\|Unlocked\|NO_FORWARD\|soft-lock\|NO_WORK_POSSIBLE' run-N.md
```
