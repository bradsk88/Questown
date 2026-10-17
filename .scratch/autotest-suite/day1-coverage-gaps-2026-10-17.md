---
title: 'village/day1 vs the 2026-10-17 playtest: what it could have caught'
status: needs-triage
origin: user ask 2026-10-17 — "look at the autotest we built yesterday and see if it could have caught the classes of problems I caught today"
---

## Verdict per problem class

| Playtest problem (today) | Could day1 catch it? | Why / what was missing |
|---|---|---|
| Diner stall → carrot take/put loop, gatherer standing at chest | **Yes — the symptom was in its log** (`stuck in state 2 … reseeding` at the flag, day1-8) | Checks were eventual-outcome only; the 20000-tick self-heal hid it. **Fixed: `LogTripwire`** |
| Hunter stuck after root change (stale mat state) | Partly | day1 converts gatherer→hunter, but the gatherer had not left the mat terminal in that run. A tripwire catches it when it happens; a deterministic check would be "a converted villager leaves COLLECTING_SUPPLIES within N ticks of the change" |
| Chest-state mismatch ERROR on chunk reload | **Yes** — it's an ERROR line; day1 has a reload step | Now tripped by `no-questown-errors` |
| Missed morning while away (BOP change never applied) | No | The reload step never crosses a dawn with the flag asleep. Needs a step: queue a BOP change, move the player away before dusk, return after the next noon, assert the change applied |
| "0 days" / warp yield (29 items in a morning) | No | day1 never warps (player never leaves long enough). Same away step + assert summary text / yield bound |
| Hunter starving with a full chest (NO_SPACE) | No | day1 never fills storage. A "no villager in NO_SPACE / starving for > N ticks" watchdog would |
| Red dots, tab focus order, change-job tab focus | Partly | These are server-side decisions (`FlagInfo`, `FlagTabs.initialFlagTab`, `VillagerTabs.initialTab`) day1 could assert at each beat without a client |
| "Villager Villager 1", duplicate quest announcement, "A new villager arrives" copy | Partly | Broadcasts are logged; day1 could assert on message keys/args (e.g. no arg starting with "Villager" into a "Villager %s" template; one announcement per morning). Copy judgement needs a human |
| Job board blank line, economics tooltip width | No | Pixels — `tools/porttest` / gui-lint, not day1 |

## Done now

`LogTripwire` (+ `LogTripwireTest`), day1 only. First run: 31/33 — two real open items:

1. **Job board stuck at state 1** — `(-19,65,-15)` (job_board_block) reseeded 5× from state 1.
   Likely `seeking_work` interrupted mid-cycle (cf. `.scratch/day1-playtest-2026-10-11/seeking-work-leaver-sees-empty-board-room.md`).
2. **`Clobbered time on block at (1,64,0) from 1990 to 2000`** (×2) — a different-value timer
   re-add (the same-value case was demoted to debug on 10-15).

## Next candidates (cheapest first)

- Assert tab/attention decisions server-side at the arrival and BOP beats.
- "Converted villager is working within N ticks" check after each BOP-spend beat.
- An away-across-dawn step (missed morning + warp summary + yield bound).
