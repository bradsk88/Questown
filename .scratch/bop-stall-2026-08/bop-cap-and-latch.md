---
title: BOP stall is a per-townie latch + a 64-slot flag cap; a full flag silently loses BOPs
status: ready-for-human
created: 2026-08-25
closed: 2026-08-24
priority: p2
---

# BOP "stall" — what actually happens at the flag cap

Investigation + autotest (2026-08-25) of the Block of Progress stall that ADR-0011 and
CONTEXT.md describe as the counterweight to warp. The old phrasing ("the townie accrues no
further experience **until the player collects it**") is imprecise at the latch level.

## Two states, not one — "uncollected" was a conflation

- **The XP latch** — `SimpleVillagerHandle.hasBlockOfProgress`, one `Boolean` per townie.
  Set on level-up; the value `addExperience` early-returns on. Cleared **at deposit** by
  `ClearBOPSpecialRule`, which fires whenever a BOP is inserted — *regardless of whether the
  insert succeeded*. So a townie holds at most one uncollected BOP and is only stalled for the
  brief level-up→deposit window.
- **The flag's deposited count** — `TownFlagBlockEntity.bopCount`, up to **64**
  (`TownFlagBOPItemHandler.getSlots()`). Bumped on deposit, decremented when the **player**
  collects (`ejectBlockOfProgress`).

## Autotest findings (`flag/bop_boundary_63`, `flag/bop_full_64`, 2/2 passed)

Drive one real `BOPDepositorWork` deposit after pre-filling the flag:

- **63→64 (one slot free):** the deposit is **accepted** (`messages.bop.earned`,
  `bopCount 63→64`); the latch clears; the townie returns to downtime.
- **64 (full):** the deposit is **rejected** (`"Item lost due to not enough space in target
  container … 1 block_of_progress"`), `bopCount` stays 64 — **but the latch still clears** and
  the townie returns to work. **A full flag does NOT re-stall the townie.**

So a left-alone town does not freeze: townies keep working/leveling, BOPs pile to 64, then
further BOPs are **silently dropped** (error-logged server-side, invisible to the player).
"Stops progressing" = the skill tree does not advance (player never collects/spends), **not**
townies freezing.

## Open question for the human

When the flag is full (64) and a townie levels up, the BOP is **lost with no player-facing
notice**. This is consistent with the "BOPs don't accumulate while away" vision, but the loss is
silent and permanent. Is that acceptable, or should a full-flag deposit (a) surface a notice
(bubble/chat) and/or (b) be capped so the townie doesn't churn toward a level-up it can't
deposit? The invisible-stall `_Open_` in CONTEXT's BOP entry partly covers this.

## Closure (2026-08-24)

Answer (Brad): **(a) surface a notice.** Implemented in `9ef06f7c` — a `BOP_FULL`
blockstate, a red BOP rendered above the flag when full, and opening the flag full jumps
straight to the BOP menu with a red warning line. Verified by 873 unit tests and the
`flag/bop_full_64` autotest. **(b) capping churn was not pursued** — BOPs are still
dropped at 64, but no longer silently (the flag turns red).

## Notes

- Test hook added: `TownFlagBlockEntity.grantBlockOfProgressForTest(int)` (mirrors the
  tutorial `grantBop`; `...ForTest` convention, like `placeTempBedForTest`).
- Scenarios are **report-only** (empty `TestExpectation`, always pass) — they print the state,
  like the `perf/*` measurements, rather than gate on it.
- See CONTEXT.md "Block of Progress (BOP)" entry for the corrected mechanism.
