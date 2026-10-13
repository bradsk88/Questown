---
title: 'First-hunter guarantee regression: pre-roll fisher beat the intended hunter'
status: ready-for-agent
---

# First-hunter guarantee regression

Captured 2026-10-14 (server-19, in-game chat):

- 04:02:52: "Why do we have a fisher?"
- 04:03:03: "First job change should have been hunter..."
- 04:09:29: "Had to force hunter job change. Something caused the guaranteed
  hunter job change to not work. That's a recent regression."

## Root cause

`TownQuests.onboardingJobChangeGuarantee` (ruling D, 2026-10-13, commit
99957a38) fills the BOP-spend guarantee slot from the oldest open
**onboarding** JOB_CHANGE quest. The tutorial's first-hunter batch
(`addHunterGatherer`, "tutorial batch #1.5": raw food + 4 stone swords +
hunter job change + bedroom) was the one tutorial batch that never called
`markOnboarding(qq)` — the cook batch (`addQuestsForJobChangeAndFood`) and
every other phase batch do. So:

1. Tutorial grants 3 **plain** BOPs (`TownFlagTutorialAdapter.grantBop` —
   `new ItemStack`, no blessing).
2. Player spends a BOP on a townie → `requestJobRootChange` with an empty
   blessed root → `onboardingJobChangeGuarantee` finds no open onboarding
   JOB_CHANGE quest (the hunter quest is unflagged) → no guarantee.
3. `scheduleJobRootChange` pre-rolls arbitrarily → fisher.

It reads as a "recent regression" because 99957a38 is the commit that made
the onboarding quest the guarantee source; before it, the day-1 world's first
hunter was guaranteed by a BOP that happened to be hunter-blessed. The
guarantee's own javadoc claimed the first BOP was "hunter-blessed" — it is
not (the tutorial grant is plain); that parenthical is now corrected.

## Fix (implemented, full suite 1214/0)

- `addHunterGatherer` now calls `markOnboarding(qq)` like every other
  tutorial-phase batch, so the open hunter quest fills the guarantee slot
  and a BOP spend while it is open decides hunter deterministically
  (guaranteedRoots beat preRolls at morning apply).
- `addHunterGatherer` is package-private (test seam).
- Pinned by `TownQuestsHunterGathererOnboardingTest` (red-verified: the
  onboarding assertion fails on the pre-fix method form).

Note: only affects towns whose hunter-gatherer batch is still open. The
playtest world's batch is long complete (the player force-changed the
hunter), so no world repair is needed.
