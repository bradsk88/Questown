---
title: 'Campfire quest copy: two lines are wrong (wand, not right-click; wand sleep, not "sleep here")'
status: done
origin: 2026-10-15 playtest — user chat 02:59:49 + 03:00:33
---

# Campfire copy: two user-ruled replacements

1. 02:59:49 — `"Right-click it at night" should be "Use wand on campfire"`
   - `en_us.json` `tutorial.campfire_ready.description` = "Right-click it at
     night" → **"Use wand on campfire"** (user's exact words).
2. 03:00:33 — `"Sleep here at nightfall" should instead say "Your Town Wand
   enables you to sleep by the campfire"`
   - `en_us.json` `message.wand.campfire.day_hint` = "Sleep here at nightfall"
     → **"Your Town Wand enables you to sleep by the campfire"** (user's
     exact words).

Both are lang-only; check any other reference to the old wording before
committing (docs, COMPLEX.md flavor sections).

## Resolved (audit 2026-10-22)

Both user-worded lines are live in `en_us.json`: `tutorial.campfire_ready.description` = "Use wand on campfire" (line 554), `message.wand.campfire.day_hint` = "Your Town Wand enables you to sleep by the campfire" (line 558). Landed in bbbdf88f.
