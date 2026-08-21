---
title: "Begin moving" confirmation text is too wide and vanishes too fast
status: ready-for-human
created: 2026-07-14
resolved: 2026-08-21
priority: p2
---

## Triage 2026-08-21: already fixed

`BeginTownRelocationMessage.handle` now sends the acknowledgement via
`sender.sendSystemMessage(...)` (persistent chat) instead of the action bar, with a comment
describing exactly this bug ("the message is long, so the overlay truncated it off-screen and
faded before it could be read"). Both acceptance criteria are met by that change (chat persists
and wraps). Fixed somewhere between #199 (`9bf33bcb`) and `42a6cc95`. Marking resolved — close
after a quick in-game glance if you want confirmation.

## Context

Found during the 2026-07-14 relocation playtest. After clicking "Begin moving" (the
flag-menu relocation entry, #199), the on-screen acknowledgement text (action-bar /
overlay message) is **too wide to fit on screen** and **disappears too quickly** to
read.

## Acceptance criteria

- The confirmation message fits within the screen width (wrap or shorten the string).
- It stays on screen long enough to read (or move it to chat, which persists).

## Notes

- Likely the `shutdown_started` / relocation action-bar ack sent from
  `BeginTownRelocationMessage` handling. Action-bar text is single-line + short-lived by
  design — consider sending it as a chat/system message instead, or shortening the lang
  string (`menu.flag_crafting.*` / relocation-msg keys added in #199).
