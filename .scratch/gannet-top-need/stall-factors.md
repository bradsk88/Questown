# Gannet top-need — stall factors (for the follow-up session)

**Disposition (dev, 2026-10-09 end-of-mission):** the town in the
zero-coal `village/self_sufficiency` seed has hit a **genuine
self-sufficiency stall, not a harness bug**. The mission's *expected* beat
(coal → miner) never fires because coal is not the town's highest unmet
need — and the walk, had it re-evaluated, would have skipped every need in
the queue (see "walk outcome" below). The real stall is the chain of
supply gaps below. Working on these is a fresh session; no handoff doc —
this file is the record.

## The queue, as logged

Run 16d (last run), 07:23:24:
```
top needs: #questown:villager_food=5  minecraft:mutton=4  minecraft:stick=1
```
Run 16b, 06:49:16:
```
top needs: #questown:swords=5  minecraft:stick=5  #questown:villager_food=5  #minecraft:coals=3  minecraft:mutton=2
```

- `villager_food` is a satisfied need (LOOT food is COMPLETE; cook unlocked)
  — the walk correctly skips it (dev-confirmed: "should be getting skipped
  for the purposes of job-quest selection"). It still occupies top-queue
  position, which is a queue-hygiene observation, not a defect.
- **`stick` and `swords` are the unmet ones**, and both outrank coal
  (stick 5 > coals 3 in 16b; stick absent-coal in 16d). Coal is 4th or
  absent in every recorded snapshot.
- **Walk outcome** (unit-verified logic, never logged live — the chain
  stopped re-requesting before this): every need's only producer is either
  unlocked (food→cook, mutton→hunter) or produces nothing in the registry
  (`minecraft:sword` → *no job produces it* per `scripts/job-producers.py`);
  the single actionable need is **stick → crafter** (crafter root not in
  roster). So the town's honest next onboarding quest is a crafter unlock,
  not a miner unlock.

## Factor 1 — No space (gatherer capacity)

- 16d roster = hunter, cook, **7 gatherers** (roster=9). Two of them are
  *permanently* parked: `ae02705c` and `e186bbd9` in
  `gatherer/gather:CANT_REACH` (every `watch day` line, 07:0x–08:1x);
  `[WHY] job=gatherer:… → NO_SPACE` dumps show `job_blocks={}` /
  "no spots found" across all WorkStates for those instances.
  The other 5 cycle `seeking_work → gather → bop_depositing` normally and
  **are** extracting (`[LOOTTRACE] extracted '1 minecraft:birch_sapling'`
  at 07:07:19 — note: that extract then had "no open LOOT quests to give it
  to", which is itself a routing question).
  Mood tallies confirm the split: `cant_reach=2` (the parked two), rest
  `none`. So: the world has wood; the gatherer class works; a *subset*
  (2/7) can't find workable blocks. "No wood in the world" (my earlier
  phrasing) was wrong and is retracted here.
- Entry points: the `WHY` dump's
  "town has workable blocks for ANY WorkState{…no spots found…}" gate
  (`JobStatuses`/`RoomsWithWorkableStatefulBlocks`, per the
  `questown-status-why` skill); what a *working* gatherer instance's spots
  set looks like vs the parked ones' — the delta is the missing workable
  site. Roster size (6 gatherers for one town?) is also a content question.

## Factor 2 — Stick (cook's extraction input, no producer chain)

- Stick is the only *actionable* unmet need (producer = crafter; crafter
  not unlocked; no crafter in either 16b/16d roster).
- `crafter/stick` (data: `questown_jobs/crafter_stick.json`):
  `request=minecraft:stick`, `ingredients=[#minecraft:saplings x1]`,
  `room=questown:…` — i.e. the stick producer chain is
  **sapling → crafter → stick**, and the chain's own phase-4 (crafter
  job-change + craft room) is *after* the 2.75 top-need phase in
  `addTutorialBatches` (`TownQuests.java` L659-667: phase 2.75 < phase 3 <
  phase 4). So a stick-driven top-need beat would be asking the town to
  unlock a job the fixed tutorial phases unlock right afterwards — an
  ordering/content tension to decide (does 2.75 yield to the fixed phases'
  crafter beat, or does the crafter root count as "actionable" for the
  walk?).
- Why the stall: phase 4 never applied in either run (no crafter quest in
  any 16b/16d quest list) — the chain stalled before it (see Factor 3 /
  "why the chain froze"), so stick was never supplied, so the cook's
  extraction-need (if that's the source of `stick=1..5`) is never cleared.
  (Provenance of the stick need entry — which job registered it — is still
  open; check `JobLogic.registerUnmetNeeds` / `NeedsRegistrations` with the
  run's `[WHY]` context.)

## Factor 3 — Sword (hunter's tool, no in-world producer)

- 16b: `#questown:swords=5` — top of the queue, tied. The hunter's
  `hunter_unmapped_sword_*` jobs carry the sword and loot
  mutton/porkchop/beef; `job-producers.py minecraft:sword` → **no job
  PRODUCES swords**. Whatever posts the `swords` need has no producible
  producer in the registry → the walk skips it forever → it sits at the
  top of every queue, permanently unmet, permanently non-actionable.
- Decision needed: either (a) a sword-producer job exists conceptually
  (blacksmith?) and is missing from data — a data gap; (b) the need
  registration double-posts swords as a *supply* when they're a *held
  tool* (same class of bug as the run-14 oak_wood mislabel the leak fix
  addressed — a second instance of "product/tool mislabelled as supply
  item"); or (c) it is correctly unmet and the queue should rank/drop
  producer-less needs explicitly (the walk skips them silently today).

## Why the chain froze (the re-eval gap) — verified, not a bug

The top-need phase re-evaluates **only on a quest-batch request**, which is
posted by the *previous batch's close reward*
(`defaultQuestCompletionRewards` → `MCDelayedReward` → morning →
`addBatchOfQuestsForVisitor` → `questRequests` → `tick` walks the phases).
In 16b/16d the last random batch (several `kitchen_stocked` room quests)
kept ACTIVE instances the builder then refused (observed 16d:
`skipping questown:kitchen_stocked (3 built for 3 open)` — see "harness
note"), so **no further batch request was ever posted and phase 2.75
stopped re-evaluating after the last logged `falling through`** (16d:
07:09:37; needs were empty then, and became populated only after, at
~07:16+). The town is therefore holding a correctly-composed chain at a
correctly-skipped phase; what's missing is the *time*, which only a
closing batch would buy. (Whether the batch *ever* would have closed is
unverified either way — that's the harness note below.)

## Harness note (known, unfixed — flag if a re-drive is ever planned)

`VillageSelfSufficiencyExecutor.builtRoomsForRecipe` (`5420c202`) caps
**lifetime** rooms per recipe id and never decrements when those quests
close, so any *later* batch containing the same recipe (e.g. `ks` in batch 2
and batch 4 both) is refused (`skipping … (3 built for 3 open)`). Same
family as the recipe-id latch it replaced — **monotonic counters latch
across generations**. If this is ever re-driven, build a room per *active
quest instance*, not per recipe id, or let the counter retire with its
quest. (16d's batch-2 rooms all built+closed fine; it's the batch-4
refusal that froze the chain.)

## What IS verified (don't re-prove)

- Walk + resolver logic: `TownQuestsTopNeedWalkTest` 6/6,
  `StaticProducerRootsTest` (5 green + 2 tag skip).
- NPE fix live-safe through the upgrade path (16d, zero
  `getUpgradeRecipe` exceptions).
- Room build + detect + quest-close cycles work for `ks` × several and beds
  (16d).
- Chain reaches 2.75 and correctly falls through when needs are empty
  (16d 07:08:08 / 07:09:37 log lines).

## Pointers

- Runs: `/tmp/run16b-server.log`, `/tmp/run16d-server.log` (volatile —
  copy before citing).
- Mission record + Run 16 section: `.scratch/gannet-top-need/top-need-measure.md`.
- Retro #3 (process): `.scratch/gannet-top-need/retro-2026-10-09-run16.md`.
- Walk/resolver: `TownQuests.chooseTopNeed` / `tryPhase2_75_topNeedUnlock`,
  `ServerJobsRegistry.staticProducerRootsForItem`.
- Job data (oracle): `src/main/resources/data/questown/questown_jobs/`,
  `scripts/job-producers.py <item>`.
