---
title: Job board briefly offered no cooked-food requests, then all requests became available
status: needs-info
created: 2026-10-12
---

## Context

Playtest chat, 2026-10-12 (`/tmp/dev-server-7.log`):

- 19:17:49: > I think it might be because I'm unable to request any cooked
  food that we have raw versions of...
- 19:18:18: > Something changed. I can now request all cooked foods.

The board's request filter excluded every cooked food with a raw variant for
~30-60s, then (something the player did or a rescan) opened them all up.

## Notes

ADR-0007 ("job board offers only fulfillable products") is the governing rule
— the filter is intentional, so this is about WHEN it flips, not removing it.
Need: a live catch of the 19:17-19:18 window (or the log lines around it) to
see what invalidated/re-validated the fulfillability check — room rescan,
furnace fueled/unfueled, top-need batch re-post (19:14:12 posted the baker
batch), or the cook unlocking works (19:19:10 "Now that I've unlocked one,
they're cooking").

## Audit (2026-10-22)

Still unverified: no repro since 10-12, and no commit names this window. ADR-0007's filter is intended; the open question is only when it flipped. Close if a fresh playtest never shows cooked-food requests missing while a raw variant is in stock.
