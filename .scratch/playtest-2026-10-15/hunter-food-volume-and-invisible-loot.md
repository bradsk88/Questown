---
title: 'Hunter produced WAY too much food in one day; "Dropping loot" animals not visible in town'
status: ready-for-agent
origin: 2026-10-15 playtest — user chat 03:26:56 + 03:27:27 + 03:27:44
---

# Hunt loot volume + invisible "Dropping loot" animals

Three linked reports:
1. 03:26:56 — "My hunter produced WAY too much food for one day."
2. 03:27:27 — "Also, I can't see them anywhere in town, but they're
   'Dropping loot'."
3. 03:27:44 — "Oh, tjey're in bed." (self-answered part: the townies
   themselves were in bed — sleep module)

Log evidence (03:22:32.529, single tick): a batch extraction of 10+ items
landed at once — 7+ × mutton, plus beef, chicken, leather, feather, all
logged as "extracted '1 minecraft:X' but no open LOOT quests exist". So the
hunter(s) banked a large multi-animal haul in one go with no quest to
absorb it.

Open questions:
- Is the volume one hunter's legitimate loop rate, or a burst (e.g. several
  animals killed in one hunt cycle with no pace gate)? Census the hunter's
  kill/extract log for the full morning.
- "Dropping loot" state shows but the dropped animals/items are not
  visible to the player — where do they drop (off-screen, in the bed
  room)? If the loot is invisible until extraction, that is the
  player-facing oddity.

## Audit (2026-10-22)

Part 3 self-answered (they were in bed). Parts 1-2 are taste/design (is one multi-animal haul per hunt the intended yield? should 'Dropping loot' show where the loot goes?). Queued in .scratch/feedback-audit/README.md.

## Warp yield check (2026-10-22, dev: "I think that was a warp bug. The JSON definitions determine how many items come back")

- The JSON: `hunter/sword` rolls its table once per trip (`attempts: 1`; every hunter table has one
  pool with `rolls: 1`), so one item per trip.
- Current warp: `hunter/sword [one_day_yield]` warps 24000 ticks for one hunter and yields 5 items
  (mutton 2, porkchop 2, beef 1); the plain `hunter/sword` warp 2, `[warp_then_realtime]` 9.
  All 5 hunter scenarios PASS. So today's warp stays in the JSON's range for one day.
- The 10-15 burst (`run/logs/2026-10-16-2.log.gz` 03:22:32.529): 26 extractions settled in one tick,
  38 s after the town's entities re-loaded (03:21:54, "Registered entity with town"), with one hunter
  and one gatherer. 26 is about five days of one hunter's trips. The log has no warp lines (the
  TIME_WARP debug channel was off), so the warped duration is unknown: a long unloaded absence would
  explain it without a bug.
- Not reproduced. Next step if it recurs: enable the warp log before leaving the town (`/_qtdev debug toggle_log <flag pos> time_warp`, `DevFlagDebugToggleCommand`), and
  compare the logged warp length with the extraction count.

