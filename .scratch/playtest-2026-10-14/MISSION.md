---
title: 'Autonomous work queue — playtest-2026-10-14 closeout night'
status: ready-for-agent
created: 2026-10-14 (night)
---

# MISSION — autonomous queue (user asleep; do NOT stop to ask input)

User left for bed 2026-10-14 night. Instruction: "Continue autonomously... For
as long as possible, you should NOT stop to ask for my input." All 5 pending
rulings are ANSWERED and recorded in the tickets (see "Rulings" below).

## State

- **Server/client: DOWN** (cleanly torn down; world auto-saved). World = the
  playtest world (onboarding done; townies: gatherer cd8d3125, hunter
  62c1b324, cook 5de9ec1b). No wipe without user say-so; backup at
  `~/playtest-world-2026-10-14`.
- **Server-22 + Client-22: UP** (launched 2026-10-14 night ~05:44; server-22
  with `-Dquestown.tp.fifo` console — feed commands via `run/ffeed.sh feed
  '…'`; player "Dev" autojoined and was TP'd to the flag; `forceload` active
  on the village area). Same playtest world (NOT wiped). Logs:
  `/tmp/dev-server-22.log`, `/tmp/dev-client-22.log`. Tear down client→server
  at closeout (world autosaves).
  Key instruments learned: `_qtdev work_score <pos> <root>` dumps the
  per-job gate-failure reasons + board requests (DEV-DIAG command,
  `DevWorkScoreCommand`); the flag ticker stops when no player is near
  ("stopped ticking because: No pl…") and room detection does not run while
  stopped — a score dump with no player reads "No rooms match …" for
  everything. `forceload add -20 260 20 300` loads the village.
- **Warm logs**: `/tmp/dev-server-19.log` (04:01–04:34), `/tmp/dev-server-20.log`
  (04:35–04:53), `/tmp/dev-client-*.log`. These are the evidence base for the
  two soft-locks.
- Full unit suite was 1214/0/52-skip before tonight's work. Latest commits:
  `8c5d9e20` (tooltip fallback), `6d0c0166` (hunter guarantee), `23afa0c5`
  (BOP fix), `582d26c1` (quest cards).

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
   **ROOT-CAUSED, parked for ruling** (2026-10-14 night, live instrumented run
   on s22): the cook's selection is a near-uniform coin flip over ~7 tied
   ~1.0 jobs (extract/fuel `always_consider` no-ops tie with the real
   cook-food jobs); the named blocker flaps with the flip. All three facets
   are the same coin flip seen from the surfacing side. The fix is
   decision-layer (scoring/tracker), which Q3's "surfacing only" ruling
   excluded — needs the user's call. Evidence in the ticket's "Findings".
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
   **ROOT-CAUSED, no code defect** (2026-10-14 night): s19 log 04:26:47 proves the
   completion was a gatherer's crop-carrot extraction (postExtractHook ->
   processLootQuests), not the user's chest re-deposit (LOOT never scans
   containers); it WAS a LOOT quest - the perception came from the "Raw food"
   name (fixed by #5). Open ruling parked: card says "produce 8" but LOOT
   completes on first extraction - copy fix vs accumulate.
7. **Raw-food repeat eating** (`raw-food-repeat-eating.md`) — **ANSWERED:
   intended** (2026-10-14 night): raw meals apply HUNGER_FILL_HALF (0.5f)
   vs cooked HUNGER_FILL (1f); the satiety timer drains to 0 and re-triggers
   the hungry listener, so raw-only townies cycle ~2x as often. s19 live
   evidence: "Fullness changed from -1200 to 5000". No code change.
8. **Inventory tab in flag click-through menus** — DONE: management-only
   flag on `OpenVillagerMenuMessage` + payload (STATS landing, INVENTORY->STATS
   redirect, six tabbed menus filter via `VillagerTabs.hideInventory`).
   `gui/COMPLEX.md` "MultiStatusScreen: full-card click ... management-only
   mode".
9. **Villager display names** (`villager-display-names.md`) — ruling: name tags,
   menu headers, system chat messages; NO number reuse.
10. **Onboarding miner after cook** (`onboarding-miner-after-cook.md`) — full
    design ruling recorded in ticket.
11. **1.20 vanilla skins in the UUID pool** (`use-1.20-villager-skins.md`) —
    scope: add new 1.20.5+ vanilla skin variants to the existing UUID-hash pool
    (Steve/Alex/custom). No job mapping.
12. **load-time reward noise — plain-language explanation** (`.scratch/day1-playtest-2026-10-11/load-time-reward-reclaim-error-noise.md`) —
    user doesn't understand the problem; write a plain-language explanation
    into the ticket (what fires, why, silence-vs-fix options). HOLDING on decision.

## Rulings (2026-10-14 night, user, all recorded in tickets)

1. Names: name tags, menu headers, system chat messages etc.; no reuse.
2. Miner: quest-requested (room quest + N-of-initial-item quest), BOP morning
   reward, guaranteed-miner during onboarding (like hunter and cook should be).
3. Skins: no job mapping; extend the UUID-hash pool with the new vanilla skins.
4. village-is-thriving: HOLDING, discuss another day.
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
