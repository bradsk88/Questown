---
title: The "highest demand item" quest keeps getting replaced instead of staying added
status: implemented
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:36:37–17:38:59 (`run/client2.log`, one
thought across four messages):

> My current batch of quests is a bit confusing.
> I see: raw food, four swords, and unlock hunter - those all make
> sense. Bedroom too.
> But I also have an ADDITIONAL quests for stone sword x1, and a
> job-change quest that seems to change every time I open the menu...
> If that's the "open a quest for the highest demand item" - it should
> NOT be constantly getting replaced, it should eget added and STAY
> added.

## Notes

The demand-driven extra quest (a JOB_UNLOCK-style quest for the stuck
root) visibly changes target every time the menu is opened. Note: the
playtest ran WITH the bug-1 fix (`8f670d10`, per-stuck-root dedupe +
in-place retarget) — the fix stops the 214-phantom accumulation but the
*retarget-in-place* behavior is exactly what the player experienced as
"changes every time I open the menu". Direction: pick the demand target
once and keep it until it completes (or the demand actually changes),
not re-roll per menu open / per need cycle.

## Triage notes

Design item: the demand quest is constantly replaced, churning the UI. Needs a product decision on replacement stability.

## Decision (2026-10-11, design walkthrough)

**(a) Lock until complete.** Once a top-need JOB_UNLOCK quest is posted, it stays
until the player completes it — no per-menu / per-tick re-roll, and no retarget
if a different item becomes the top need mid-flight. The demand re-evaluates
*only when the in-flight top-need quest is done*; a new top need then gets posted
after the current one completes.

## Mechanism (grounded, 2026-10-11)

The top-need is a tutorial phase `TownQuests.tryPhase2_75_topNeedUnlock` (runs
after hunter+cook: `jobChangesComplete() >= 2`). On every evaluation it calls
`chooseTopNeed(needs, producerRootsFor, unlockedRoots)` — which walks the ranked
unmet-need queue to the first need with an unlockable producer — and posts a
JOB_UNLOCK + room quest for it (or retargets the in-flight one via the `8f670d10`
in-place retarget). Because the ranked queue shifts tick-to-tick, the chosen top
need moves, so the quest visibly changes on each menu open. `processJobUnlocks`
completes the open JOB_UNLOCK once the root is unlocked.

## Fix

Latch the choice: while an in-flight top-need JOB_UNLOCK quest is open (incomplete),
do not re-run `chooseTopNeed` / retarget — keep the existing quest. Re-evaluate the
top need only after that quest completes (or is withdrawn). This removes the churn
while keeping the "quest for the highest demand item" behaviour. Verify: a top-need
quest stays on the same target across repeated menu opens and economy shifts until
it's completed; only then does the next top need get posted.

## Implementation (2026-10-12)

Lock-until-complete, two parts in `TownQuests`:
1. `reconcileUnlockQuests` no longer withdraws/retargets town-wide quests — they are
   pinned until `processJobUnlocks` completes them (the `townWideOpenQuests` arg is now
   intentionally unused; only rooted quests are reconciled).
2. `tryPhase2_75_topNeedUnlock` returns `SKIPPED` early when
   `openTownWideJobUnlockQuestExists()` (an open incomplete JOB_UNLOCK with an empty
   stuck root), so it never re-rolls the top need or posts a second town-wide quest while
   one is in flight.

Verified: `TownQuestsJobUnlockTest` — `reconcileTownWideQuestPinnedEvenWhenNoRootMapsToProducer`
(pinning) + 3 `openTownWideJobUnlockQuest*` tests (the guard predicate). The phase wiring
around the guard needs a town/econ harness; verified by held run when available. Documented
in `town/entity/COMPLEX.md` "Top-need JOB_UNLOCK quest — lock-until-complete".
