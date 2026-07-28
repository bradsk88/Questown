---
title: Flag craft buttons are not disabled when the required item is missing
status: needs-triage
created: 2026-07-14
priority: p2
---

## Context

Found during the 2026-07-14 playtest. The flag crafting-tab buttons remain clickable
even when the player does not hold the required input item, so clicking does nothing
with no explanation. Expected: buttons are disabled (greyed out) unless the player has
the needed item in hand.

## Acceptance criteria

- A craft button is disabled/greyed when the player lacks the required input item.
- It enables once the required item is in hand.
- (Nice-to-have) a tooltip explains what's missing.

## Notes

- Screen/handler: `FlagCraftingScreen` + `FlagCraftMessage`. Eligibility is currently
  decided server-side; the client may need the needed-item info (or a can-craft flag) to
  gate the button. Pairs with [[craft-buttons-no-feedback]].
