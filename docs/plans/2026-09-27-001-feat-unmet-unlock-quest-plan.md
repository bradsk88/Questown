# Plan: JOB_UNLOCK quest for stuck townies (unmet item past a grace period)

Date: 2026-09-27
Status: implemented this session (see test ledger for verification state)

## Problem

A held `village/self_sufficiency` watchtower run surfaced a silent freeze: a baker
assigned by `ChangeJobReward` needed an ingredient (bread's first ingredient) whose
producer job (farmer) was not unlocked by any townie. The townie sat in
`UNMET_ITEM` for 34,504+ ticks; no quest, hint, or broadcast named a next action.
Unmet needs are the mod's only source of drama (CONTEXT.md), yet the stuck state
surfaced only as a rotating item icon over the townie's head.

## Design

When a townie's root has no viable job (`UNMET_ITEM`, set by `TownPossibleWork`)
for longer than a grace period, the town creates a **JOB_UNLOCK quest**:

- **wantedId**: the root job of the producer root that can make the missing item
  (deterministic: first candidate root in sorted order).
- **owner**: the stuck townie.
- **flavor**: plain-English text naming the stuck townie's job, the missing item,
  and the producer root to unlock (mirrors the tutorial flavor-text style).
- **completes** when any townie unlocks that root (skill-tree unlock, a
  job-change reward, or any other path that reaches
  `TownVillagerLearningHandle.unlockJob`). Checked statelessly each flag tick
  from the town's unlocked-jobs map, the same shape as `processJobChanges`.
- **lost** ("no longer needed") when no stuck root maps to that producer root
  any more (e.g. the player stocked the missing item directly, making the stuck
  root viable again).

### Gating

- Grace period: `Config.UNMET_UNLOCK_QUEST_GRACE_TICKS` (default 12000 ticks =
  1 in-game day). Measured from the first tick a stuck townie of that root is
  observed; the observation window is `TownQuests.tick` (flag tick).
- One open JOB_UNLOCK quest per producer root (dedupe).
- No creation while onboarding drives the town: skip when any open quest is
  onboarding-flagged or an open JOB_CHANGE quest exists (the tutorial assigns
  jobs through JOB_CHANGE quests; an unlock quest then would be noise).
- No creation when the missing item has no known producer root, or when every
  producer root is already unlocked by some townie (unlocking would not help).

## Vocabulary to code

| Term | Code |
|---|---|
| JOB_UNLOCK quest | `Quest.QuestType.JOB_UNLOCK`; `MCQuest.jobUnlock(...)`; `Quest.QuestFactory.newJobUnlockQuest`; `QuestBatch.addJobUnlockQuest` |
| Stuck townie | `VisitorMobEntity.getNeed() == TownieNeed.UNMET_ITEM` + non-empty `getUnmetItem()` (set by `TownPossibleWork.registerUnmetNed`) |
| Grace-period bookkeeping | `TownQuests.unmetRootSince` (root -> first-stuck tick), pruned while the root stays stuck |
| Missing item -> producer roots | inversion over `Works.regularJobs()` + `ServerJobsRegistry.getResults(td, job)`; candidate roots filtered by `VillagerHolder.getUnlockedJobs()` (new interface method) |
| Quest creation | `TownQuests.tickUnmetUnlockQuests`, called from `TownQuests.tick`; batch built via `MCQuestBatch.Inputs` + `defaultQuestCompletionRewards`, broadcast via `town.getUnsafe().messages.broadcastMessage` |
| Quest completion | `TownQuests.processJobUnlocks(unlockedJobs)`, called from `TownFlagTicker.handleNewStoredData` alongside `processJobChanges` |
| Quest loss | `QuestBatch.markRecipeAsLostByKey(KEY)` (new; the existing lost path matches on room, which is null here) |
| UI | `UIQuest.getIngredients` (BOP icon), `UIQuest.getName` (`questown.menu.quests.job_unlock`), `QuestsScreen` tooltip + journal-entry switches, `TownMessages.questCompleted`/`questLost` name branch |

## Phases

1. Quest plumbing: `JOB_UNLOCK` enum value, factory methods, batch method,
   all exhaustive `QuestType` switch sites, lang keys.
2. State machine: grace bookkeeping, producer inversion, creation/loss/dedupe in
   `TownQuests`; `VillagerHolder.getUnlockedJobs()`; config value.
3. Completion: `processJobUnlocks` + ticker wiring.
4. Verification: unit tests for the pure decision logic; full build; held
   `village/self_sufficiency` run to watch the quest appear for the stuck baker.

## Test ledger

| Test | Status |
|---|---|
| `QuestTypesTest` values() round-trip covers the new type (name-based) | runs in suite |
| `TownQuestsJobUnlockTest`: producer-root selection (`chooseProducerRoot`, pure) | passing |
| `TownQuestsJobUnlockTest`: unlock detection (`rootNowUnlocked`, pure) | passing |
| `TownQuestsJobUnlockTest`: JOB_UNLOCK through the real `QuestBatch` plumbing (type/wanted root, completion, lost-by-key) | passing |
| `processJobUnlocks` wiring around the batch | not testable without a live level (completion listener spawns fireworks); verified via the held run |
| Held `village/self_sufficiency` run: JOB_UNLOCK quest appears in the log for the stuck baker | TODO next step |
| Producer inversion against live job results (`getResults`) | not testable without a Forge harness; verified via the held run |

## Open decisions

- Grace default (12000 ticks = 1 in-game day) is a guess; tune after watching a run.
- Reward reuses `defaultQuestCompletionRewards`; a JOB_UNLOCK-specific reward is a follow-up.
- The need bubble could later name the unlock ("They need wheat - unlock the Farmer");
  deferred, it needs a second synced field per entity.

## Resume (verification re-run)

Held run of `village/self_sufficiency` (06:08-07:11, `/tmp/selfsuff2-server.log`) did not
reproduce the stuck state: no `unmet_item` lock fired through the day-4 report (the
04:48 run did, from day 3). No quest posted, so the held-run ledger item stays open.
Re-run it with a short grace so any stuck root posts immediately:
`run/config/questown-server.toml` -> `UnmetUnlockQuestGraceTicks = 100L` (the file is
written at first boot with the 12000 default), then the standard held launch sequence
(`.pi/skills/questown-held-run`). Evidence: `Stuck root ... -> JOB_UNLOCK quest` line +
`New quests are available!` broadcast in the server log.
