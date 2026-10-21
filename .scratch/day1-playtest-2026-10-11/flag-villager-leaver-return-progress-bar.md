---
title: 'Flag villagers menu: show a progress bar for when leavers return'
status: ready-for-agent
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 22:44:57:

> It would be nice to show a progress bar on the flag villagers menu so we can
> tell when the leavers will be back.

When a leaver has left the town (on a gathering/leaving job), the flag's
villagers menu gives no indication of when they'll be back. Add a progress bar
for the leaver's return.

## Acceptance criteria

- The flag's villagers menu shows a progress indicator for each leaver that has
  left, reflecting time-until-return.
- The bar completes / the leaver reappears when they return to the flag.

## Notes

Find the leaver's away/return timer (the leaver job's departure + return
schedule, likely in the leaver executor / `TownFlagTicker`). Expose a
progress fraction (elapsed / total away-time) to the villager-menu payload
(`MultiStatusMenu` / the status packets) and render a bar on the leaver's row.
Tie into the existing status-art area (`MultiStatusScreen`) rather than a new
screen.

## Decision (2026-10-13, walkthrough G3)

As scoped (leaver away/return fraction on the leaver's row in the
villagers menu status-art area), **plus the bar must tick down live in the
menu while the player watches**. Implementation: the payload carries the
absolute **return tick** (server time), and the client derives
`elapsed/total` every frame from its own clock — no push channel needed;
the bar ticks down smoothly and stays in sync with the server on LAN.

## Shipped (2026-10-12)

Implemented per the G3 decision: `StatusPacket` carries
`leaverReturnTick` (absolute server tick) + `leaverReturnTotal`;
`DeclarativeJob.getLeaverReturn` reads the gate-mat timer (remaining + the
timed state's declared duration); `MultiStatusScreen` draws a 30×3 bar
under the 32×32 status art, re-derived every frame from the client clock.
Full unit suite green (1164/0/50). **In-game verification rides the user's
fresh playtest** (first villagers-tab open on a leaver mid-timed-trip,
e.g. the gatherer's 2000-tick roam tail); gui-lint check is part of that
first menu open.

## Bug: bar stuck at 100 (2026-10-21 playtest)

On the flag villagers page the bar can stay at 100. **The dev confirmed that switching tabs and
back updates it**, so the screen keeps a stale value. The server-side listener is fine
(`ui/hunter_status_resync`). Look at how `MultiStatusScreen` caches the per-villager status and
leaver-return data between status pushes, not at what the server sends.

### Reproduced live (2026-10-21 agent playtest, 19:46-19:49)
Hunter on `hunter/sword`, flag villagers tab left open, bar pixels sampled under the status art:
- During the trip the bar fills live (1 px -> 4 px of 30 in 10 s): the client clock drives it.
- The hunter returned at 19:48:42 (`+200 XP`), dropped loot and left again (`GOING_TO_JOB`
  19:48:50). Three shots over ~20 s after the return: bar stays at 30/30.
- Toggle to another tab and back: bar shows 5/30, the new trip.
So the screen keeps the old absolute `leaverReturnTick` and nothing pushes the next trip's tick
to an open screen; reopening resyncs it. Fix direction: send the updated leaver-return data to
an open `MultiStatusScreen` when a villager's timed state starts (or on status change).
