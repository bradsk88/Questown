---
title: 'Onboarding should include a miner step after the cook (coal is crucial for self-sustaining)'
status: done
---

# Onboarding: add a miner after the cook

Captured 2026-10-14 (server-20, in-game chat 04:51:14):

> "Starting to thing onboarding quests should include a step for adding
> miner after cook. Because coal is quite crucial for self-sustaining..."

Design feedback: the onboarding arc ends at cook (kitchen) but a town
without a miner has to hand-feed coal forever. Propose a post-cook
onboarding beat: "unlock a miner" (+ optionally "build the miner's base
room"), placed where coal first becomes the town's top need. Fits the
existing tutorial-phase machine (`TownQuests` phases 2.5+ already post
need-driven JOB_UNLOCK/JOB_CHANGE beats — e.g. `tryPhase2_75_topNeedUnlock`)
or could be a fixed phase. Decide fixed-vs-need-driven.

Related: `econ-tab-top-blocker-missing-coal.md`,
`cook-status-blocker-surfacing-10-14.md` (the coal pain this is a response
to).

## Decision (2026-10-14, user) — full design

Same pattern as hunter and cook:
- **Requested via quest**: one quest for the room the miner needs + one quest
  for N of the initial item the miner produces.
- **Unlocked via BOP as a morning reward.**
- **Guaranteed to be miner during the onboarding phase** — the onboarding
  guarantee slot fills with miner, exactly as hunter and cook *should*
  currently be guaranteed (the guarantee mechanism should cover all three).

## 2026-10-14 implemented

Fixed post-cook beat, phase 2.7 (`TownQuests.tryPhase2_7_miner` + `addMiner`),
posted when two job changes are complete + 3 townies + no open JOB_CHANGE:
JOB_CHANGE(miner/coal) + 4 wooden pickaxes + 8 coal + the mineshaft room
quest; markOnboarding-flagged so the open quest fills the BOP-spend
onboarding guarantee (covering all three beats); reward = PHASE_MINER ->
defaultQuestCompletionRewards (BOP + next villager). Need-driven top-need
batch (2.75) now picks up whichever need is NOT coal. Pinned:
`TownQuestsMinerOnboardingTest` (flag + guarantee + beat content),
`TutorialPhaseGateTest` (the ladder walks through the beat).
`town/quests/COMPLEX.md` "Post-cook miner beat".

## Resolved (audit 2026-10-22)

Phase 2.7 miner beat (`TownQuests.tryPhase2_7_miner`), onboarding-flagged; `TownQuestsMinerOnboardingTest`; live: the miner beat posted once in r3/r4/r5/r7 (2026-10-22 whisk handoff).
