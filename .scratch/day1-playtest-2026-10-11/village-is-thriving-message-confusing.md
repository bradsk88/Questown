---
title: 'The village-is-thriving message does not say what it means'
status: done (2026-10-15, option 3 — line dropped)
---

# "village is thriving" message is meaningless to the player

Captured 2026-10-13 (playtest, in-game chat):

> "Not sure what the 'village is thriving' message is supposed to be telling
> me..."

## What it actually is

`messages.tutorial.complete` — "Your village is thriving! New quests will
appear as your village grows." — broadcast the moment the **first
procedural (onboarding) batch** completes
(`TownQuests.fireChapterMilestonesIfNeeded`, batch #1 of the 1/5/10/20
chapter-milestone ladder). So the intended meaning is "onboarding is done —
the game proper starts now." Nothing in the copy says that; it reads as
ambient flavor.

## Options (user call)

1. **Re-word as the milestone it is** — e.g. "Onboarding complete! New quests
   will appear as your village grows." (keeps the second half, which is true
   and forward-looking).
2. **Name the reward side** — say what the player gets: the basics batch
   (job board + store room) posting is the real news.
3. **Drop the line** — the quest-completion broadcast already told them the
   batch is done; this adds nothing.

Not implemented — copy the user wants to rule on.

## Status (2026-10-14, user)

**HOLDING** — user will discuss another day. Do not implement meanwhile.

## Ruling + fix (2026-10-15, user, option 3 — drop the line)

Implemented: `TownQuests.fireChapterMilestonesIfNeeded` no longer broadcasts
`messages.tutorial.complete` at the first procedural batch (branch deleted;
the 5/10/20 chapter advancement triggers and the `completedProceduralBatches`
counter are untouched — the counter also feeds `QuestBatchSeed`'s below-3
gating). The `messages.tutorial.complete` lang entry is removed. The
premise was verified before removing: the basics batch's own
`getCompletionMessage()` is null, but `TownMessages.questCompleted`
broadcasts "Quest completed: …" for each quest, so the player already hears
the batch land. `TutorialPhaseGateTest.campfireBatch_isNotProceduralBatch`
kept (it pins `isProceduralBatch` for the chapter triggers; comment
updated). Docs: `town/quests/COMPLEX.md` "Procedural-batch milestone ladder".
Verification: full `:test` 159 suites / 1256 tests / 0 failures, strangler
clean. Red-verify note: the removed branch was the sole call site of the key
(grep-verified single site + lang entry + one test comment); the batch-1
broadcast path is `town.getUnsafe()`-bound and not unit-testable in a plain
JVM (IllegalStateException by construction), so the removal's certainty is by
construction, not by a red run.
