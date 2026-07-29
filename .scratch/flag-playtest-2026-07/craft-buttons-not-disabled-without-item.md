---
title: Flag craft buttons are not disabled when the required item is missing
status: wontfix
created: 2026-07-14
resolved: 2026-07-29
priority: p2
---

## RESOLVED 2026-07-29 — shipped 2026-07-15, plus a wording fix

`a3e8f1d8` (2026-07-15, the day after the playtest) already implemented this and the file was never
closed. `FlagCraftingScreen.updateCraftButtonStates()` runs every frame off the live client
inventory, so each Craft button greys out at zero and re-enables the moment the item appears. The
nice-to-have tooltip is there too: a count line, red when zero.

**What was still wrong, and is now fixed:** the tooltip read *"%s in hand"*, but the count is
inventory-wide and `FlagCraftMessage.consumeItem` also takes the input from anywhere in the
inventory. A player with a stick in their backpack and an empty hand was told "1 in hand" — the
exact kind of statement this legibility pass exists to remove. The key is now
`menu.flag_crafting.in_inventory` = *"%s in your inventory"*, which is what the logic actually means.

The acceptance criterion's "in hand" wording was the reporter describing the symptom, not a spec:
gating on the hand alone would be wrong, since crafting succeeds from anywhere in the inventory.

## Triage notes

`wontfix` here means **done, kept as historical record** — see the note in
[[deed-not-consumed-on-move]]; the tracker has no "resolved" status.

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
