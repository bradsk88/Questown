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

1. **Job board stuck at state 1** — `(-19,65,-15)` (job_board_block) reseeded 5× from state 1
   (also present in day1-8, before today's changes). `seeking_work` (`WorkSeekerJob`) has
   `MAX_STATE = 1` (JOBS_AVAILABLE) and no logged write of state 1 at that pos, so it is reached
   through the job's own advance. Reading: the seeker reaches 1, then leaves seeking_work (board
   gives it a job, or the 1000-tick max-ticks fallback to downtime) before extracting, and the
   per-villager store keeps the 1 until the villager seeks work again; if that is >20000 ticks
   away, the self-heal fires. Possibly benign (the leftover only matters when it seeks again, and
   then "jobs available" is re-checked at extraction) — **design question for the dev**: should
   leaving seeking_work reset its board state, or should the tripwire ignore reseeds of spots the
   owner is not currently working? Until decided, day1 stays red on this check.
2. **`Clobbered time on block at (1,64,0) from 1990 to 2000`** (×2) — a different-value timer
   re-add (the same-value case was demoted to debug on 10-15).

## Next candidates (cheapest first)

- Assert tab/attention decisions server-side at the arrival and BOP beats.
- "Converted villager is working within N ticks" check after each BOP-spend beat.
- An away-across-dawn step (missed morning + warp summary + yield bound).

## Seen live after the tripwire landed (dev-server-9, 20:21:24, build fe1b7238)

3× `[Questown:Jobs/ERROR] No work found for job ID: JobID{rootId='hunter', jobId='seeking_work'}.
Falling back to any job in the same root ID.` — `ServerJobsRegistry.getWorkSupplier` via the needs
lookup (`ServerJobsRegistry` ~L756): `seeking_work` is a special job (`WorkSeekerJob`), not a
registered `Works` entry, so the needs path falls back with an ERROR. `getStatusText` already
special-cases `isSeekingWork`; the needs path does not. Would trip day1's `no-questown-errors`
whenever a hunter seeks work. Not fixed yet.

## Update (2026-10-17 evening)

- `Clobbered time` — fixed (9a7d57da): a partial work write started the next state's timer.
- seeking_work "No work found" — getWork no longer falls back for special jobs (be509a02); live
  trigger still unreproduced, the fallback ERROR now logs the caller's stack.
- New: the overnight absence made the reload real and exposed the quest-replay onboarding loss
  (c816c7c9) — villager 2 had been arriving before Raw Food after any real flag reload.
- Once in four runs: `Giving up on detecting sub block at (-19,65,-15) … wrong entity type: null`
  + `Job board registration … dropping stale entry` right after re-initialization. Not chased.
- day1 now 33/34; the only red is the job-board reseed (design question still open).
