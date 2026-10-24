---
title: 'Post-onboarding batches alternate a job change with a batch for the job it became'
status: superseded by 2026-10-24-002-feat-curated-quest-paths-plan.md (dev, 2026-10-24: curated paths instead of a random alternation). The RootRoll change below shipped and stays.
origin: r16 agent playtest #14; dev ruling 2026-10-24 (design chat, handoff `jetty`)
---

# Post-onboarding job-change alternation (r16 #14)

## The ruling (dev, 2026-10-24, verbatim intent)

- "Invert it. Alternate between a 'change a villager's job' quest and a batch of quests that are
  FOR that job once it changes. Plus have the random job selection prefer jobs that are not
  already taken."
- "Once we're past onboarding we switch back to fully random. So the quest is just 'change a
  villager's job'." (No top-need producer steering.)
- The job-change quest completes on **any** Block of Progress job change (dev picked this over a
  named villager).

## Why (r16)

The top-need batch (`TownQuests.tryPhase2_75_topNeedUnlock`) posts a room-only quest for the top
need's producer (dev ruling 2026-10-22: the old "Unlock job" BOP spend rolled a random root). The
room got built and nothing staffed it: the only path to the producer was the same random roll
(r16: crafter room built, the two rolls gave a baker and a second hunter).

## Done already (commit with this plan)

- **Random rolls prefer unheld roots.** `RootRoll.candidates(allRoots, current, heldRoots)`
  (`RootRollTest`): roots no townie holds; every other root when all are held. Wired into both
  random rolls in `SimpleVillagerHandle` (`rollPendingRoot`, the BOP-spend pre-roll, and the dawn
  fallback in `handleMorning`). A gold (blessed) BOP still forces its root (ADR-0015).

## Proposed shape (not implemented)

1. **Job-change batch.** Replace `tryPhase2_75_topNeedUnlock` with a batch holding one
   "Change a villager's job" quest. New quest kind (see decision 1), completed in
   `TownQuests.processJobChanges` by any root change after the quest was posted (today a
   JOB_CHANGE quest names a job and completes when a townie holds it, owner first, then any
   townie of the same root).
2. **Batch for the job it became.** When that quest completes, post a batch for the new root:
   its base room (`view.getRoomForJob`), its held tools as ITEM (deposit) quests, and its first
   product as a LOOT quest (the r16 #7 rule). `tryPhase3_newJobRoom` already posts "the room for
   a JOB_CHANGE-completed job whose room has no quest" (`getJobToCreateRoomFor`); it is the
   natural place to extend from room-only to room + tools + product.
3. **Loop.** When the job's batch completes, post the next job-change batch.
4. Arrival provisioning (`TownQuests.provisionArrival`, r16 #11/#17) applies to both batch kinds
   if their reward brings a villager.

## Vocabulary -> code

| Term | Code |
|---|---|
| job-change quest (today) | `Quest.QuestType.JOB_CHANGE`, `MCQuest.jobChange(...)`; completion `TownQuests.processJobChanges` |
| top-need batch | `TownQuests.tryPhase2_75_topNeedUnlock`, `topNeedBeatOpen`, `chooseTopNeed` |
| job room batch | `TownQuests.tryPhase3_newJobRoom` -> `getJobToCreateRoomFor` -> `addQuestForNewJobRoom` |
| random root roll | `SimpleVillagerHandle.rollPendingRoot`, `handleMorning` (dawn), now via `RootRoll` |
| scripted post-miner beats | `tryPhase4_crafter` (crafter + crafting room, "TutorialComplete" trigger), `tryPhase5_supplyChain` (concurrent jobs + small kitchen) |

## Test ledger

| Test | State |
|---|---|
| `RootRollTest` (3) | green |
| job-change "any root change" completion | to write (pure: `processJobChanges` with a before/after job map) |
| job batch contents (room + tools + LOOT product) | to write (`TestTutorialTownView`) |
| alternation (job batch only after the change completes; next change only after the job batch) | to write |
| village/growth reaches past the miner beat | live; note the open inn-upgrade stall ticket |

## Open decisions (for the dev)

1. **Quest kind:** a new `ANY_JOB_CHANGE` type, or JOB_CHANGE with a wildcard wanted id? A new
   type is clearer for the quest card text and `processJobChanges`; it touches the switch-covered
   `QuestType` (AGENTS: grep all switches first).
2. **Phases 4 and 5:** do the scripted crafter (phase 4, which also fires the TutorialComplete
   trigger) and supply-chain (phase 5) beats stay before the alternation starts, get folded into
   it, or go?
3. **Where "past onboarding" starts:** right after the miner beat (where top-need runs today), or
   after phase 5 (where the procedural quest garden takes over)?
4. **Does the procedural garden keep running** alongside the alternation, or does the alternation
   replace it?
5. **Tools for the job batch:** which tools count (`job JSON tools`), and how many (one per
   townie of that root, or a fixed count like the onboarding swords/pickaxes = 4)?
6. **Rewards:** what does each batch give (`defaultQuestCompletionRewards`, i.e. a villager or an
   upgrade quest, or a BOP so the player can make the next job change)?
