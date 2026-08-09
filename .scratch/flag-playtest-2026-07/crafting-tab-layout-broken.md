---
title: Flag crafting-tab text layout is broken
status: wontfix
created: 2026-07-14
resolved: 2026-08-09
priority: p2
---

## RESOLVED 2026-08-09 — already fixed by the flow-layout rewrite; verified via gui-lint

`3ebeb496 feat(gui): layout-validation linter + flow-layout crafting screen` rewrote
`FlagCraftingScreen` to compute every block's Y from the real wrapped height of the block above
(`computeLayout()`), so text can no longer overlap regardless of string length. The screen is now
the reference example in `docs/solutions/conventions/gui-layout-flow-and-linter.md`, which cites
this 2026-07-14 overlap as a resolved bug.

Verified 2026-08-09 in a dev client (633x1035 window):
`[gui-lint] FlagCraftingScreen — OK, no layout violations`.

Fourth case of the pattern noted in the legibility handoff: playtest issues that were fixed
shortly after the playtest but never closed. (`wontfix` + `resolved:` date is the standing
convention for "closed as historical record" until the tracker gains a real resolved status.)

## Context

Found during the 2026-07-14 relocation playtest. On the flag menu's **crafting tab**
(`FlagCraftingScreen`), the text layout is "totally broken" per the tester —
labels/rows do not lay out correctly. Likely regressed or was never adjusted after the
"Begin moving" button was added full-width below the craft rows (#199, commit
`9bf33bcb`), which may have shifted/overlapped the existing craft-row text.

## Acceptance criteria

- Crafting-tab labels and buttons render in aligned, readable positions at the standard
  GUI scale.
- The "Begin moving" button does not overlap or push the craft rows out of place.

## Notes

- Screen: `gui/FlagCraftingScreen`. Check widget Y-offsets after the relocation button
  insertion; verify against the `menu.flag_crafting.*` lang keys (backfilled in #199).
- GUI is a server-autotest blind spot — needs visual/manual verification.
