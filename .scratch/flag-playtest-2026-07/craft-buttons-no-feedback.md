---
title: Flag craft buttons give no feedback on success
status: needs-triage
created: 2026-07-14
priority: p2
---

## Context

Found during the 2026-07-14 playtest. Clicking a craft button on the flag crafting tab
produces no visible confirmation — the tester "can't really tell that anything
happened." Expected: on a successful craft, close the UI and show a "Created XYZ"
message so the outcome is obvious.

## Acceptance criteria

- A successful craft shows a clear confirmation naming the crafted item ("Created XYZ").
- The UI closes (or otherwise clearly signals success) after crafting.

## Notes

- Screen/handler: `FlagCraftingScreen` + `FlagCraftMessage`. Add a success ack (chat or
  overlay) + `onClose()` on success. Coordinate with the "disable when item missing"
  issue ([[craft-buttons-not-disabled-without-item]]).
