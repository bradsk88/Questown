---
title: 'Autonomous work queue — playtest-2026-10-14 closeout night'
status: ready-for-agent
created: 2026-10-14 (night)
---

# MISSION — autonomous queue (user asleep; do NOT stop to ask input)

User left for bed 2026-10-14 night. Instruction: "Continue autonomously... For
as long as possible, you should NOT stop to ask for my input." All 5 pending
rulings are ANSWERED and recorded in the tickets (see "Rulings" below).

**QUEUE COMPLETE** (2026-10-14/15 morning): all 12 items done — #10 miner
beat `11c7952f`, #11 skins `2bbedf3e`, #12 load-time-noise explanation
`848fb72e`. Full unit suite 1238/0/52.

## State

- **Server-22 + Client-22: TORN DOWN** (clean shutdown 2026-10-15 06:47 BST;
  world auto-saved on stop). Final playtest world backed up at
  `~/playtest-world-2026-10-14` (29M, level.dat 06:47:21).
- **Full autotest suite: COMPLETE** (ran 06:48–07:50, log
  `/tmp/autotest-full-20261013-r2.log`; first attempt died at test 16 in the
  arborist warp — a managed chunk-load deadlock in `WarpWorldAccess`'s
  level fallback, fixed in `497b86da` + `WarpWorldAccessChunkBoundaryTest`).
  58 scenarios: 50 PASS, 8 FAIL. 7 of the 8 are in the known-fail baseline
  (arborist×2, eating/dine_at_time, farmer×2, nav/roof_gatherer,
  chicken-arc:skip_chicken_command); **new**: `flag/floating_furniture_over_half`
  (the over-halved room was NOT rejected; deadDoors empty; not in the 2026-
  10-10 baseline). **RESOLVED 2026-10-15 afternoon** (ticket
  `.scratch/floaty-room-2026-10-15/floating-furniture-over-half.md`): the room
  was built in postSpawn, OUTSIDE the arena-flatten region derived from
  `blueprint.blocks()`, so its interior foot level was un-flattened
  random-seed terrain — solid terrain gave `floorCells=0` and the day-1
  storeroom escape hatch accepted the room (seed-dependent flake; the morning's
  "instrumented vs un-instrumented" split was two different world seeds).
  Fixture fix: both rejection-asserting builders
  (`floating_furniture_over_half`, `too_short_room_dead`) clear their
  footprint to air before building. WALKPROBE instrumentation reverted;
  `level-seed` restored to empty. Verified 3 random-seed runs (6/6, 2/2,
  full-flag sweep).
- **Warm logs**: `/tmp/dev-server-22.log` (05:46–06:47; the hunter/coin-flip
  oscillation continued — parked Q3 item, not a regression),
  `/tmp/dev-server-19.log`, `/tmp/dev-server-20.log`.

## Queue (in priority order)

1. **Hunter soft-lock** (`.scratch/playtest-2026-10-14/hunter-stuck-sword-need-10-14.md`) —
   **DONE** (2026-10-14 night): root-caused as a `TownHasSupplies` supply-gating
   defect (the sword requirement was excluded from the "town has supplies"
   check whenever no state had a workable block — i.e. exactly when the mat
   sat at its terminal state); fixed with a full-requirement fallback when no
   state is workable; pinned by `TownHasSuppliesNoWorkableStateTest` (red on
   pre-fix code with the live dump's exact failure signature, then green);
   full suite 1217/0/52-skip. See the ticket's "Root cause" section; the
   econ-need and job-change sub-items were consequences of the same root.
2. **Cook blocker surfacing** (`cook-status-blocker-surfacing-10-14.md`) —
   **TDD'd per the 2026-10-15 ruling** (union of live needs — no priority
   ordering; the **job independence principle** is now in CONTEXT.md +
   design-principles.md): `UnmetNeedTracker` enter/applyPass/exitKey/
   primaryKey (14 tests, 4 red-verified on the pre-fix replace semantics),
   `TownPossibleWork.refreshUnmetNeeds` runs every recompute pass and diffs
   the union of named keys across all unlocked candidate jobs; bubble shows
   the townie's oldest live key. Facets 1+2 (hint side) closed; the
   behaviour-side coin flip (idle no-op jobs tie with real work, facet 3's
   status text) remains a separate open decision-layer item.
3. **Econ tab "switch job" also selected** (`econ-tab-switch-job-also-selected.md`) —
   **DONE** (`7b6ac864`): `VillagerEconomicsMenu`'s hand-rolled enabled-tab
   list omitted `CHANGE_ROOT`, so the switch-job tab's open-fn was null and it
   rendered selected; now `VillagerTabs.except(ECONOMICS)` like every sibling,
   pinned by `VillagerEconomicsMenuEnabledTabsTest` (red-verified by stash).
4. **"Create N items" shows no (3/8) progress** (`item-quest-deposit-progress.md`) —
   **DONE** (2026-10-14 night): `UIQuest.deposited` (wire -1 == null) + "(n/target)"
   card rendering capped at target; `TownQuests.openItemQuestProgress()` uses the
   same ingredient matching as the completion path; wired via `QuestsHolder` into
   the flag QUESTS tab + `showQuestsUI`. Pinned by `UIQuestItemProgressTest`
   (3 tests; red by construction — the field did not exist pre-fix).
5. **Raw-food quest naming** (`raw-food-quest-naming-mismatch.md`) —
   **DONE** (2026-10-14 night): per the user's lean, LOOT card name is now
   "Food" — lang key `menu.quests.raw_food` → `menu.quests.food` (+3 call
   sites); pinned by `UIQuestLootCardNameTest`.
6. **"Food" quest type + re-deposit completion** (`food-quest-wrong-type-and-redeposit-completion.md`) —
   **DONE (2026-10-15)**: root-cause stood (it WAS a LOOT quest; the s19
   completion was a gatherer's crop-carrot extraction), then the parked
   ruling was settled on **option (b) accumulate**: LOOT quests credit every
   matching extraction to a persisted extracted total and complete at the
   count (count-1 keeps close-on-first-extraction, so the 2.75 gate is
   untouched); the card shows "(n/8)" via the #4 progress mechanism
   (`openQuestProgress` now covers ITEM + LOOT). Pinned by
   `TownQuestsLootTest` (red-verified), `MCQuestLootProgressTest`,
   `UIQuestLootProgressTest`, and the autotest oracle
   `loot/loot_on_extract_accumulate` (3/3 loot scenarios PASS at the
   auto-applied 200 t/s).
7. **Raw-food repeat eating** (`raw-food-repeat-eating.md`) — **ANSWERED
   2026-10-14 night, REVERSED by user ruling 2026-10-15**: raw meals apply
   HUNGER_FILL_HALF (0.5f) vs cooked HUNGER_FILL (1f); the satiety timer
   drains to 0 and re-triggers the hungry listener, so raw-only townies
   cycle ~2x as often. The "intended fullness-chasing, by design" framing
   was NOT a user ruling and is reversed: intent is **one meal per hunger
   cycle** — the townie goes back to work after eating once (at ~50%
   fullness), and the smaller meal just means the next meal comes sooner.
   No code change either way (the code already did this). See the ticket's
   "Ruling reversal" section.
8. **Inventory tab in flag click-through menus** — DONE: management-only
   flag on `OpenVillagerMenuMessage` + payload (STATS landing, INVENTORY->STATS
   redirect, six tabbed menus filter via `VillagerTabs.hideInventory`).
   `gui/COMPLEX.md` "MultiStatusScreen: full-card click ... management-only
   mode".
9. **Villager display names** — DONE: "Villager N" monotonic arrival number
   (no reuse), name tag + system chat + BOP screen; payload carries the name.
   Deferred: flag quest-card tooltip (`quests.job_owner`).
10. **Onboarding miner after cook** — DONE: fixed post-cook beat (phase 2.7),
    JOB_CHANGE(miner/coal) + pickaxes + coal + mineshaft room; onboarding-flagged
    guarantee; PHASE_MINER reward.
11. **1.20 vanilla skins in the UUID pool** (`use-1.20-villager-skins.md`) — DONE:
    9 vanilla slim skins (1.20.6 jar-verified) + 6 custom, floor-mod UUID pick,
    pure `TownieSkins` + `TownieSkinsTest`.
12. **load-time reward noise — plain-language explanation** (`.scratch/day1-playtest-2026-10-11/load-time-reward-reclaim-error-noise.md`) — DONE:
    explanation written (two different log lines: stale WARN comment +
    deserializer replay re-claiming rewards; corrected the ticket's
    initializer hypothesis). Decision HOLDING — recommendation B
    (replay-without-claim) + WARN downgrade.

## Rulings (2026-10-14 night, user, all recorded in tickets)

1. Names: name tags, menu headers, system chat messages etc.; no reuse.
2. Miner: quest-requested (room quest + N-of-initial-item quest), BOP morning
   reward, guaranteed-miner during onboarding (like hunter and cook should be).
3. Skins: no job mapping; extend the UUID-hash pool with the new vanilla skins.
4. village-is-thriving: **RULED + DONE 2026-10-15 (option 3, drop the line)** —
   `messages.tutorial.complete` no longer broadcasts at the first procedural
   batch; the per-quest "Quest completed: …" lines already announce it. See
   the ticket's "Ruling + fix" section and `town/quests/COMPLEX.md`
   "Procedural-batch milestone ladder".
5. load-time reward: HOLDING; needs the plain-language explanation (queue #12).

## Hunter evidence (collected so far)

- Hunter 62c1b324 worked on server-19 (EXTRACTING_PRODUCT at 04:10:58,
  04:13:22, 04:16:18). On server-20 it **never worked**: first NO_JOBSITE at
  04:36:29, 4 min after boot; oscillates sword↔seeking_work↔downtime, always
  NO_JOBSITE. Server-20 = same world, plain restart (no wipe).
- The hunter's job block is `questown:welcome_mat_block` in room
  `questown:special_quest.town_gate`. The hunter's actual job was the **short**
  variant `hunter/sword` (`hunter_unmapped_sword_short.json`): work states
  [tools #questown:swords, work 1, time 2000] → maxState=3, state 3 = terminal
  (extractable). (The earlier note here said `hunter_unmapped_sword_full.json`
  with 4 states — corrected 2026-10-14 night against the log's
  `JobID{rootId='hunter', jobId='sword'}`.)
- [WHY] dump (server-20 04:44): `town has workable blocks for ANY WorkState`
  = false; "No rooms found with workable blocks at states [0,1,2]";
  candidate spots exist (`spots={...=0}`) in room `[-3, 280]` whose block
  histogram shows a `3` at index 32, but `job_blocks={...=false}` for every
  candidate.
- At server-20 boot, many early dumps show `gate[rooms={}]` (EMPTY room set);
  on server-19 boot the same dumps already show room `[-3, 280]` (all-zero
  histogram). → suspect the gate/special room (town_gate) registration
  differs across the restart: not registered, registered with a different
  type, or the mat spots no longer inside it.
- NEXT: (a) grep both logs for explicit gate-room registration lines
  (`town_gate`, `special_quest`, "All rooms registered", room detection
  lines); (b) read the workable-block gate code
  (`RoomsWithWorkableStatefulBlocks`, `RoomsStatusLogic`, `DeclarativeJobs.rooms`/
  `findJobSite`) to decode `job_blocks`/histogram semantics; (c) determine
  why spot (e.g. -2,68,279) is not a job block on server-20 but was on 19.

## Discipline

- TDD per `questown-unit-tdd-fix` (red-verify; surgical revert, not whole-file
  stash; rm target XML before reading).
- Commit prep per AGENTS.md (token + `git commit -F`; no apostrophe-in-`-m`
  trap; stage paths explicitly; do NOT commit AGENTS.md / CONTEXT.md /
  docs/design-principles.md).
- Strangler check after any main-code change: `sh tools/strangler-check`.
- Update tickets (root cause + evidence lines) as they get settled; add
  `FIXED <commit>` notes when committed.
- When the queue is exhausted: run the full unit suite, update MISSION status,
  update handoff `~/.pi/agent/handoffs/magpie.md`, and `beep` + `summon`.
