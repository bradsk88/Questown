# Villager loops depositing BOPs into a chest, cannot be stopped (unreproducible)

**Status:** Reported from a live world; not reproduced. Held for a fresh as-it-happens log.

## Symptom (log10.txt, 2026-09-05)

A villager stood beside a **chest** (not the flag) and repeatedly generated and
deposited Block of Progress (BOP) items into the chest, over and over. Changing the
villager's job did not stop it.

From the log (one live-world session, 14:16–14:23):

- 14:16:24 — the villager `ee7d2394-…` levels up:
  `[Experience] +400 from hunter:sword_half_day`, then
  `message.villager.leveled_up [ee7d...cc3, 3]`, then
  `Job changed … [from hunter:sword_half_day]` → `hunter:bop_depositing`.
- 14:16:34 onward — the villager cycles every ~10s:
  `seeking_work → bop_depositing → seeking_work → bop_depositing …`, until
  14:19:41, when the player force-set it to `hunter:sword` to break it.
- No further `[Experience]` line for this villager after 14:16:24.
- The ~10s cadence matches `Config.WORK_SEEKER_COOLDOWN` (default 200 ticks), which is set
  only when the job board finds zero work.

## What is known about the mechanism (verified in code, not from the log)

- Level-up sets the per-townie latch `SimpleVillagerHandle.hasBlockOfProgress = true`;
  `addExperience` early-returns while it is set, so no further exp accrues until it clears.
- `TownFlagBlockEntity.changeJobForVisitorFromBoard` (line 757) routes **every** board visit
  with the latch set into `bop_depositing`, *above* root-job selection — so changing the root
  job cannot escape it. This is why "changing the job" did not stop it.
- The latch is cleared only via `ClearBOPSpecialRule → clearBlockOfProgress`, reached on the
  flag-deposit path (`RealtimeWorldInteraction.postInsertHook`).

## What could not be confirmed — why it is unreproducible

- **The chest path.** The live report shows BOPs going into a **chest**, but the code path
  traced deposits into the flag's `bopStacks` via `TownFlagBOPItemHandler` (the in-game menu
  text `menu.block_of_progress.will_deposit` says BOPs are stored on the flag's base block).
  No code path was found that drops a BOP into a chest, and `log10.txt` has no drop-into-chest
  line. The chest behavior is therefore **unconfirmed**.
- **Why the latch stuck.** No "no space / full" line, no flag `count` increment, and no
  `Item lost` error were found in the log. The exact condition that left the latch set could
  not be reproduced.
- **This contradicts the sibling issue** `.scratch/bop-stall-2026-08/bop-cap-and-latch.md`
  (2026-08-25), whose autotest concluded a full flag does **not** re-stall the townie (the
  latch clears even when the deposit is rejected). The live report here shows the opposite:
  the townie did stay stuck. The two cannot both be true, but neither can be reconciled from
  what is on hand.

## Why it is not being fixed yet

Without an as-it-happens log (the villager's job-state, the target container's position and
contents, and whether the deposit succeeded or was rejected), the investigation would chase
inferences in both directions. The fix direction depends on a fact the log does not contain:
whether the deposit went to the flag or a chest, and whether it succeeded or was rejected.

## To reproduce / resolve, capture

A held-server run (see `src/main/java/ca/bradj/questown/commands/test/COMPLEX.md`) that
levels up a townie and then dumps, per tick: the townie's current job,
`hasBlockOfProgress`, the deposit job's target block position, that block's container
contents, and whether the insert returned true or false. That distinguishes "full flag" from
"full chest" from "latch never cleared on a failed chest drop".

## Open question for the human

The user's stated preference for BOPs: **BOPs should not be deposited into chests**; at worst,
a BOP placed in a chest should be instantly pulled out and counted on the flag. That
preference is recorded here for when a repro is in hand.
