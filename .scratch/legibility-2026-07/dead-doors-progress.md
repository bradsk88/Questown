---
title: Item 5 (dead doors) — server-side scenario done; visual check remains
status: ready-for-agent
created: 2026-08-20
priority: p1
---

# Dead doors (legibility item 5) — progress

**Done:** `flag/dead_door_bubbled` autotest scenario (commit af7a4219). Places a lone oak
door two blocks west of the flag, registers it via the real wand path
(`TownWand.onRightClicked` with a FakePlayer holding a flag-bound wand), and asserts
`flag.getDeadDoors()` maps the door to `NOT_ENCLOSED` with no false positives from the
farm's fence gate. Passes 1/1 in the suite.

**Remaining:** visual verification with the hold-mode + grim flow from
HANDOFF-2026-08-19 — relaunch with `-Dquestown.autotest.hold=true
-Dquestown.autotest.only=flag/dead_door_bubbled`, join with `-Pautojoin`, capture with
`grim`, confirm:
- the door-icon bubble renders at the door pos (`NeedBubbleClientEvents.onRenderLevel`
  draws `NeedBubbleFocus.currentDoor()`), and
- a close-up shows `door_need.not_enclosed` on the action bar.

**Caveat:** hold mode's aim tracker points at the nearest needy *townie*, and this
scenario has no needy townies — the camera may not face the door. May need the tracker
extended to dead doors, or a spectator teleport angled at `flagPos.offset(-2, 0, 0)`.
