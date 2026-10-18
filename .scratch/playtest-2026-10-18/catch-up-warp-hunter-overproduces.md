---
title: 'Catch-up warp: a brand-new hunter banked 19 items in one missed day'
status: resolved
origin: 2026-10-18 playtest (dev-server-11, build ab4203ee) — user, in Claude chat: "I was only gone one night, but the chest is FULL of raw meat. Seems like we're still producing too much."
related: .scratch/playtest-2026-10-17/warp-gatherer-yield-while-away.md (29 gatherer items, same class), .scratch/playtest-2026-10-15/hunter-food-volume-and-invisible-loot.md
---

## Facts (/tmp/dev-server-11.log)

- Flag stopped ticking 20:35:23 (player >100 blocks away); woke 20:45:06 and stopped again the
  same second (player still at the edge).
- 20:45:06, in order: blessed-BOP root change gatherer -> `hunter/sword` (Villager 1,
  285a36c1), then **19** `[LOOTTRACE] extract` lines in the same tick (mutton ×13, chicken ×3,
  beef, porkchop, string, leather ×2; first one closes the Raw Food LOOT quest), then
  `Job changed to hunter:sword for 8af515b3… [from gatherer:gather]` — 8af515b3 is not a
  registered villager (only 285a36c1 is); likely the warp's state copy, unverified.
- No "warp"/"While you were away" line was logged for this catch-up.

## Expected bound (job data)

`jobs/hunter_sword/default` loot table: 1 pool, rolls 1, every entry count 1 -> 1 item per
attempt. `hunter/sword` 1 attempt / 2000 t; `sword_half_day` 2 / 4000; `sword_full_day` 4 / 6000.
One 12000-tick day realtime: at most 6 (base) to 8 (full-day tier) items. 19 is 2.4–3× the
most generous tier.

## Next step

Autotest the real catch-up path (`MCAdvanceTime` -> `PostDowntimeWarper.runToCompletion`, up to
64 warp steps per call, ticks from `ImportantTicks.forVillager`) for one hunter over one
simulated day and count extracted items against the realtime bound. Suspects to check with
it, not yet verified: per-call cycle count vs daytime ticks; preselected tiers cycling
(`getPreselectedJobs` shuffles all hunter tiers) each banking their own attempts.

## Resolved (2026-10-18)

Root cause (verified with a per-step diagnostic in the autotest): each scheduled warp sub-step ran
`PostDowntimeWarper.runToCompletion` to a whole cycle, and each inner warp shortened the job's
timer by the step's delta, so a 400-tick step could finish a 2000-tick hunt. Fix: a per-villager
`WarpCycleBudget` (see `town/COMPLEX.md` "Warp cycle budget").

New autotest `hunter/sword [one_day_yield]` (`*` delta 1..8 over a 24000-tick warp): 21 on the
pre-fix code, 3 after. Full suite 71/75 (known reds only). Residual: mild under-production
(the warp holds a full-day tier it can no longer afford instead of dropping to a cheaper one).
The gatherer's 29-item morning (warp-gatherer-yield-while-away.md) runs the same path and is
likely fixed too; not separately autotested. The unregistered 8af515b3 villager line is not
explained yet.
