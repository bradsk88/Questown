---
title: 'Self-satisfied quest batches cascade: 8 villagers arrived in 3 seconds one morning'
status: closed
closed: 2026-10-22
origin: 2026-10-22 agent playtest (worktree build/wt, flag 15 71 161)
world: ~/questown-worlds/villager-cascade-2026-10-22.tgz (saved right after, 11 villagers)
---

# What happened

Town of 3 (cook, hunter, miner) with a placed Mineshaft, 4+ wooden pickaxes, 8+ coal and stone
swords in the storeroom. The player slept at the campfire (03:12:39). At sunrise, villagers 4-11
arrived ~250 ms (10 ticks at 40 t/s) apart, 03:12:41.674 -> 03:12:44.680. Each arrival:

```
03:12:41.674 Registered entity with town a94665f4-...: VisitorMobEntity[...]
03:12:41.705 Dropped duplicate queued broadcast: New quests are available! ...
03:12:41.724 Dropped duplicate queued broadcast: messages.town_flag.quest_completed [block.questown.mineshaft]
03:12:41.725 Dropped duplicate queued broadcast: messages.town_flag.quest_completed [quantity item...]
03:12:41.725 Dropped duplicate queued broadcast: messages.town_flag.quest_completed [quantity item...]
03:12:41.754 Dropped duplicate queued broadcast: messages.town_flag.quest_completed [job_change jobs.miner]
03:12:41.925 Registered entity with town 917e5944-...   (next villager)
```

So: a visitor arrives with a new batch (Mineshaft, Miner job, Wooden Pickaxe x4, Coal x8 / Stone
Sword x4), every quest in it is already satisfied, per-tick backfill closes the batch at once, and
the batch's reward spawns the next visitor with the next batch. It stopped after 8 arrivals; the
log then shows `Skipping generation. The flag entity will generate a random batch instead.` x5.
The town now has 11 villagers; beds: at least 3 (the last nightfall logged "0 of 3 townies have no bed"), count not checked.

Before the sleep, the same shape ran during the day (no arrivals yet, rewards queued for
morning): `TownFlagTicker.addQuests` posted a batch whenever no quest was open (03:07:42,
03:09:50, 03:10:45), and each re-closed Mineshaft + Miner job + pickaxes immediately.

# Why (code read, not instrumented)

- `TownQuests.defaultQuestCompletionRewards`: with more than one villager, a completed batch's
  reward is `SpawnVisitorReward` + `AddBatchOfQuestsForVisitorReward` (or, on a coin flip, an
  upgrade quest for an existing villager, which is what ends a run).
- `backfillSatisfiedQuests` (per tick) completes quests whose state already exists, through the
  normal completion path, reward included (`town/entity/COMPLEX.md` "Quest backfill").
- The batch for each new visitor asks for things the town already has, so it completes the tick
  it is posted, and its reward is another visitor.

Not settled: why every visitor batch is the miner batch, and why the morning applies the chained
rewards immediately instead of queueing them for the next morning.

# For the dev (judge)

Candidate fixes, each a design call: don't post a quest that is already satisfied; a batch
complete when posted earns no reward (or no visitor); or cap arrivals per morning. CONTEXT.md
says onboarding brings at most one villager per morning, and this is past onboarding.

# Resolved (2026-10-22)

The dev spotted it: "Seems like its replaying the last batch of onboarding quests." Confirmed:
`TownQuests.tryPhase2_7_miner` had no posted check, so every batch request re-posted the miner
beat. It now hands off once a miner JOB_CHANGE quest exists (`minerBeatPosted`). Red/green:
`TutorialPhaseGateTest.whenMinerBeatDone_itIsNotPostedAgain` (2 miner quests before, 1 after);
full `:test` 1402 tests, 0 failures. Live on the cascade world: the next request posted the
phase 2.75 top-need batch (baker + breadmaker) and no miner batch. A second cascade had already
run the following morning before the fix (03:22:52-55, 11 -> 18); 2 more arrived just after the
restart, from rewards queued earlier. The world now has 20 villagers.
