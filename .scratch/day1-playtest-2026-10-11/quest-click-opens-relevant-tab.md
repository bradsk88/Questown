---
title: Clicking the welcome-mat quest should open the flag's crafting tab
status: implemented
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:12:25 (`run/client2.log`):

> Clicking on the "welcome mat" quest should take me to the flag's
> crafting tab. We can hardcode it for now. Will probably add better
> "tell me more about this quest" logic in the future.

## Notes

Explicit scope note from the player: hardcode the mapping for now;
general quest→"more about this" logic is a future concern.

## Triage notes

Implemented in 530c8923: Tabs.openTab + QuestsScreen TOWN_GATE check deep-links the quest click to the crafting tab.
