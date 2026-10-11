---
title: Crafting a welcome mat posts "Created Air" to chat
status: needs-triage
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:13:09 (`run/client2.log`):

> Crafting a welcome mat causes a message to be added to chat that says
> "Created Air" - but I DID get a welcome mat in my hand.

## Notes

Bug report: the craft succeeds (item in hand) but the chat feedback
announces Air. Likely the same root area as
`menu-crafted-welcome-mat-missing-nbt.md` (the menu-craft path) — verify
together.
