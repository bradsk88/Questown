---
title: Flag crafting-tab text layout is broken
status: needs-triage
created: 2026-07-14
priority: p2
---

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
