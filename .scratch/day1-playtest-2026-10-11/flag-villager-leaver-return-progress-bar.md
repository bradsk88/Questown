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
