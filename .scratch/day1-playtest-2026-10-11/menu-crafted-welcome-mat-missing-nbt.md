---
title: Menu-crafted welcome mat is missing the NBT it needs to work
status: needs-triage
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:13:50 (`run/client2.log`):

> The menu-crafted welcome mat is missing the NBT it needs to work.

## Notes

Bug report, day-1 flow: the mat crafted from the flag menu does not
carry the registration NBT the placed block needs, so the crafted-then-
placed mat fails where the quest expected a working town gate. Candidate
same root cause as `welcome-mat-craft-chat-says-created-air.md` ("Created
Air" chat line on the same action) — the craft likely resolves to Air
before the NBT/registration step. The 17:14:01 "Town Gate created at
location [-108, 31]" in the client log came after the player fell back to
a differently-obtained mat — confirm which mat actually registered.
