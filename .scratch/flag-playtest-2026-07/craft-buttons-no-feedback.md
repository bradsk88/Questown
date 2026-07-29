---
title: Flag craft buttons give no feedback on success
status: wontfix
created: 2026-07-14
resolved: 2026-07-29
priority: p2
---

## RESOLVED 2026-07-29 — shipped 2026-07-15, plus a case where the confirmation could lie

`a3e8f1d8` (2026-07-15) already implemented both acceptance criteria and the file was never closed:
the craft button calls `onClose()`, and `FlagCraftMessage.giveAndNotify` sends
`message.flag_crafting.created` ("Created %s", naming the crafted item).

**What was still wrong, and is now fixed:** `giveAndNotify` ignored the return value of
`getInventory().add(...)`. With a full inventory the input was consumed, the item was silently
dropped on the floor of the code — and the player was still told "Created X". A confirmation that
can be false is worse than no confirmation, which is the whole point of this issue. It now falls
back to `player.drop(crafted, false)`, matching the existing flag idiom (`TownFlagBOPItemHandler`,
`collectDeed`) of "inventory if it fits, at your feet otherwise".

Also switched the message to `Compat.translatable` per the repo convention — the commit that added
it claimed the Compat wrappers but used raw `Component.translatable` here.

**Left alone:** the key is `message.flag_crafting.created` while every neighbouring message key is
`message.questown.*`. It resolves fine; renaming is churn, but the inconsistency is real if someone
is auditing key namespaces.

## Triage notes

`wontfix` here means **done, kept as historical record** — see the note in
[[deed-not-consumed-on-move]]; the tracker has no "resolved" status.

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
