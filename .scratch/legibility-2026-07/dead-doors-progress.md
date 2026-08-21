---
title: Item 5 (dead doors) — VERIFIED in a dev client
status: ready-for-human
created: 2026-08-20
closed: 2026-08-21
priority: p1
---

# Dead doors (legibility item 5) — done, verified

**Verified in a dev client** (grim capture, 2026-08-20 ~19:05):
- First gesture: door-icon thought bubble above the lone wand-registered oak door.
- Second gesture: action bar "This door has no room — finish the walls and roof."
  (`door_need.not_enclosed`), tripped by the tracker standing ~2.5 blocks out,
  square on the door.

**How**: `flag/dead_door_bubbled` posts its door offset to the System property
`questown.autotest.aim.door` in its postSpawnAction; the held server's
`HoldModeLoginListener` reads it on login and prefers it over needy townies.
Without it, post-scenario needy townies won the aim and the door's bubble never
made it on screen (one bubble, one winner — the contest doesn't favor doors).
Also: the generic fallback now scans arena chunks for flags with dead doors
(1.19.2 ServerChunkCache has no getLoadedChunks()).

Commits: af7a4219 (scenario), 568f15ca (hold-mode aim). Item 5 closes the
bubbles 1.0 list except the HelperChickenBubbleLayer package move.
