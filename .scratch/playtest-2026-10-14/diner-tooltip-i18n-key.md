---
title: 'Diner tooltip shows raw i18n key'
status: ready-for-agent
---

2026-10-14: FIXED `8c5d9e20` (`Compat.translatableWithFallback` + `JobTooltips` fallback lines; `JobTooltipsFallbackTest`).

# Diner tooltip shows raw i18n key

Captured 2026-10-14 (server-19, in-game chat 04:02:30): "Diner tooltip is
showing i18n key".

## Root cause

`JobTooltips.buildStandardTooltipKeys` builds
`tooltips.villagers.job.<cat>.status_N.<STATUS>` keys for the status
tooltip's two lines. en_us.json only authors **some** category × status
pairs: the diner category has just NO_SUPPLIES and NO_JOBSITE, and the
fisher category has none at all (the user had a fisher that morning). Any
other status a diner/fisher reports (COLLECTING_SUPPLIES, GOING_TO_JOB,
DROPPING_LOOT, IDLE, the numeric built-in states) missed the lang lookup
and rendered the raw key to the player.

## Fix (implemented, full suite 1214/0)

- `Compat.translatableWithFallback(key, fallback, args)` — new seam over the
  vanilla `Component.translatableWithFallback`.
- `JobTooltips` builds both lines through it, with last-resort lines
  `FALLBACK_LINE_1` ("%s is busy with their work.") / `FALLBACK_LINE_2`
  (empty). The specific key still wins when authored; the fallback renders
  client-side at display time, so no `I18n.has` probing (and no
  server/client I18n asymmetry).
- Pinned by `JobTooltipsFallbackTest` (red-verified: pre-fix the components
  carry no fallback and the contents-assertion fails).

New jobs/statuses get proper copy by adding lang keys; the fallback is only
the last resort. The pre-existing `questown.`-prefixed `job_common`
DOWNTIME keys in en_us.json are a separate (working) convention and were
left alone.
