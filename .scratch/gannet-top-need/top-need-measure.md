---
title: Measure the town's top economic need (gannet experiment runs 1-2)
status: needs-triage
created: 2026-10-03
---

# Measuring the town's top economic need (gannet mission)

Question (from the `gannet` handoff): after cook, would
"unlock the job that produces the town's most-needed resource + build its base
room" organically surface **coal/miner**? The mechanism to measure against
(re-pinned in this session, 2026-10-03):

- Need ranking: `NoMCEconomics` (unmet-need queue, `timesNeeded` aggregate);
  the self_suff daily report prints the live ranking as `top needs:` lines.
- The need-driven batch already exists: `TownQuests.tryPhase2_75_topNeedUnlock`
  (**uncommitted** working-tree code) — gated on `jobChangesComplete() >= 2`,
  picks the top `ItemEconomicsData` need, maps it to producer roots
  (`producerRootsForItem`), filters out already-unlocked roots
  (`chooseProducerRoot`), posts `JOB_UNLOCK(producer)` + the producer's base
  room (`view.getRoomForJob` — for coal/miner that is the `questown:block_room/block.questown.mineshaft`
  room per `miner_short.json`).
- **`CoreProgression.getFirstJobChange` is dead in production** — only
  `CoreProgressionTest` exercises it. It is not the live ranking path for quest
  generation (the live path is the tutorial phases above).

## Run 1 — `run/server6.log` (intended coal=16, days=3; ran under defaults — see forwarding trap)

**Forwarding trap found while running:** `build.gradle`'s `server` block forwards
only a *whitelist* of `-D` properties to the forked server JVM; the
selfsuff knobs were not on it, so `days=3` and `coal=0` never landed. Run 1 ran
on `DEFAULT_DAYS=5` (I truncated it between day 4 and 5) and with the seed
default (16 coal — its log line says so). Fixed by adding `selfsuff.days` /
`selfsuff.coal` to the whitelist (uncommitted build.gradle edit); run 2 was
relaunched after the fix.

- Coal seeded: `[seed] seeded 144 opening food + 16 coal (player-input stand-in)`.
- Cook came up (`cookUnlocked=true`, roster 5: cook/gatherer×2/hunter×2),
  kitchen_small built, 8× cooked food completed.
- **Top need, every report (days 1–4): `#questown:villager_food=70`, then
  `#questown:swords` (16 day 2 → 26 day 4, rising).** Coal never appeared in
  the ranking and never ran short (no `NO_SUPPLIES` lock, no coal unmet).
- **The batch after cook never posted.** Gate `jobChangesComplete() >= 2` never
  passed: `JOB_CHANGE/ACTIVE: cook_qt_simple_furnace_food` stayed unclosed for
  the whole run. The cook townie cycled `cook/extract ↔ cook/fuel` every ~5s
  (the re-select/supply-starvation pattern from `cook-storm`); nobody ever
  held `cook/simple_furnace_food`, which is the only state that closes the
  quest. That job's parent is `cook/extract` and its input side was starved
  (raw food depleted; `LOOTTRACE extract hook: null=true empty=true`), so the
  work selector never picked it.
- Adoption is **exact-job match**: `TownQuests.processJobChanges` line
  `filter(v -> v.getValue().equals(wantedJobId))` — code-verified, not inferred.
- Read: with coal stock present, the town's top need is *cooked food* (and it
  wants *a cook who can actually cook*), not coal. The suspected
  "coal → miner" emergent beat requires coal to be in fact scarce *and* the
  cook chain settled.

## Run 2 (control) — coal=0 — DONE, `run/server7.log`

`[seed] seeded 144 opening food + 0 coal` — the coal=0 control held (the
build.gradle forwarding fix is verified by this line). 3-day window (7 days of
slept nights inside it), ended `07:56:47 WATCH complete`.

- **The cook never came up** (`cookUnlocked=false`) — the raw-food loot quest
  (the hunter's realtime hunt) never closed in this world's draw, so batch 2.5
  never even posted. This is a stochastic pre-cook bottleneck, independent of
  the coal setting (run 1's identical seed closed it in ~8 min).
- **Top needs over the run:** `villager_food` led early (70→77→81→73→58),
  then **swords flipped to #1** (16→16→19→27→42→61) as the hunters' weapon
  path kept registering needs. Coal: **absent from every report** — with no
  cook, no fuel work ever registered a coal need.
- `[lock] NO_FORWARD_PROGRESS` fired at 2 days (roster 5, 1 quest open, stock
  130 flat).

## Combined read (the measurement)

1. In both worlds the town's top economic need is **cooked food
   (`#questown:villager_food`)** in the early days, with **swords** (#2 rising;
   #1 by the end of the coal=0 world). **Coal never surfaces as a top need in
   either world.**
2. Why coal can't be a top need yet: it only enters the unmet-need queue via
   the cook's fuel job. Run 1 stocked it (16 covered the whole window); run 2
   never reached the cook at all. So the "coal runs out → miner is the answer"
   beat is not yet *reachable* in play; it is gated behind (a) a settled cook
   chain and (b) fuel actually being demanded.
3. The suspected batch "after cook → unlock top-need producer → build its room"
   exists (`tryPhase2_75_topNeedUnlock`) but was **never triggered in either
   run**: its gate is `jobChangesComplete() >= 2`, and the cook's JOB_CHANGE
   quest (exact-job `cook/simple_furnace_food`) never closed (run 1) or never
   posted (run 2).
4. The real gating constraint is the **cook-chain settle** (raw-food supply →
   cook holds a recipe job → batch closes), which in turn is limited by the
   hunter's raw-food hunt pace and the 2-job starter unlock leaving the cook on
   extract/fuel. That is where design attention belongs before the emergent
   top-need batch can be exercised end-to-end.

## Open findings for the next session

1. **Cook quest adoption gap** (design decision). Batch 2.5 closes only on the
   exact job `cook/simple_furnace_food` (`processJobChanges`, exact-match
   `filter(v -> v.getValue().equals(wantedJobId))`, code-verified), but under
   the 2-job starter set the work selector can leave the cook on extract/fuel
   indefinitely → batch 2.5 cannot complete → the emergent top-need batch never
   evaluates. Options: (a) let a cook-*root* townie satisfy the batch's
   JOB_CHANGE; (b) make simple_furnace_food selectable once its input (raw
   food) is present; (c) gate the top-need batch on the roster having a cook
   rather than on the cook quest closing.
2. **Coal demand is only measurable once a cook is live and fuel is demanded.**
   To actually test "coal emerges as a top need", a world needs: enough raw
   food to settle the cook + a cook holding the fuel job + a low/no coal stock.
   Run 2 removed coal *and* the cook's chance to register it; run 1 kept coal
   in stock. A third run (cook-settled world, coal=0 after cook is live) is the
   missing cell.
3. The daily report's `top needs:` line is the working measurement channel
   (it read coal-absence/presence correctly in both runs).
4. **build.gradle forwarding** (this session, verified by run 2's `+ 0 coal`
   seed line): the `server` block only forwards a whitelist of `-D` properties
   to the forked server JVM; `questown.village.selfsuff.days` / `.coal` were
   missing, so they silently fell back to defaults (run 1 ran on
   `DEFAULT_DAYS=5`, truncated by hand, and coal default 16). Added them to the
   whitelist. **Not yet committed** (see working-tree note).
5. DevLootFire/DevWorkScore (`_qtdev loot_fire`, `_qtdev workscore`, DEV-DIAG)
   cannot be injected in hold mode (the autotest server does not poll the
   console/FIFO). Run controls for held self_suff must be scenario code or
   system properties (now forwarded).

## Working-tree state (for the committer)

- `build.gradle` — selfsuff `.days`/`.coal` whitelist forwarding (verified).
- `TownQuests.java` — uncommitted: 2-job starter unlock + `tryPhase2_75_topNeedUnlock`
  (the emergent top-need batch) + the stuck-root `JOB_UNLOCK` path helpers.
- `VillageSelfSufficiencyExecutor.java` — uncommitted: `coalSeed()` knob +
  `COAL_SEED_DEFAULT` + the `questown.village.selfsuff.coal` read.
- `DevLootFireCommand.java` / `DevWorkScoreCommand.java` — untracked DEV-DIAG
  (raw_food hunt investigation, 2026-10-02); keep/remove per handoff.
- None of the above committed this session. Re-pin before committing.
