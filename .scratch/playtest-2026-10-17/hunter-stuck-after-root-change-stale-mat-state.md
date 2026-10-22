---
title: 'New hunter stuck in COLLECTING_SUPPLIES: the old gatherer job left the gate mat at state 3'
status: done
origin: 2026-10-17 playtest round 6 — user chat ~19:56 (dev-server-9, build fe1b7238); user hypothesis: "Maybe their old job (pre change) tainted the work spot state?"
---

Villager 1 changed gatherer → hunter:sword at the 19:55:39 morning (blessed BOP) and sat in
COLLECTING_SUPPLIES. WHY dump: gate mat room `[25, 46]` has a spot at **3** while the hunter
looks for workable spots at [0,1,2]; the supply loop reads the timer-less terminal state as fresh
(state 0, needs a sword) and the completed-product view reads it as the hunter's finished hunt
(answer: GOING_TO_JOB, rejected by the "not going to jobsite" gate) → collect supplies, forever
(until the 20000-tick `reseedStuckBlocks` self-heal).

Cause (user's hypothesis, confirmed): job-block states are per villager
(`TownFlagBlockEntity.jobHandles`, keyed by villager UUID) and survive a root change. The
gatherer's gather (3 work states → terminal 3) and hunter:sword (3 work states → terminal 3) share
the welcome mat, so the leftover terminal reads as the hunter's own.

## Fix

`SimpleVillagerHandle.changeJobForVillager` calls `Delegator.resetWorkStatus(villager)` on a ROOT
change; `TownFlagBlockEntity.resetWorkStatusFor` drops that villager's store (a fresh one is
created on demand). Same-root changes (gather → downtime → dining) keep it. Pinned by
`SimpleVillagerHandleTest.changeJob_toANewRoot_resetsTheVillagersWorkStatus` /
`changeJob_withinTheSameRoot_keepsTheVillagersWorkStatus`.

## Verify (needs restart)

Spend a BOP on a gatherer that has been gathering; next morning the hunter goes hunting instead
of parking in COLLECTING_SUPPLIES; log shows `Cleared work spot states for … after a job root
change`.

## Autotest (2026-10-17 evening)

`loot/gatherer_becomes_hunter` (c321107c) reproduces it: without the reset the hunter loops
taking/dropping the sword in COLLECTING_SUPPLIES (0/1); with it 1/1. Needs the villager outside
the mat's room and the supply chest away from the mat — otherwise the stale state is extracted
as a free hunt and the bug hides.

## Resolved (audit 2026-10-22)

Autotest `loot/gatherer_becomes_hunter` (c321107c, TestBlueprintRegistry.java:216) reproduces and pins the mat reset.
