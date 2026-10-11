---
title: Stats page should show when a villager is suffering from "didn't eat" or "didn't sleep"
status: ready-for-agent
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 23:17:48 (evening from-zero run, `/tmp/dev-server.log`):

> The "stats" page should have some indication when the villager is suffering
> from "didn't eat" or "didn't sleep"

The villager "stats" page (the villager menu's stats tab) has no indication of
the negative mood/status effects a villager is currently suffering — in
particular the **"didn't eat"** (slept/hungry) and **"didn't sleep"**
(sleepless) moods. The player can't see *why* a villager is unhappy or working
poorly.

## Acceptance criteria

- The stats page shows a clear indicator for each active negative effect, at
  minimum: "didn't eat" and "didn't sleep" (sleepless).
- The indicator is legible (icon + text / tooltip), consistent with the existing
  status-icon set.

## Notes

The mood effects already exist server-side (`TownVillagerMoods` /
`MoodEffects`: SLEEPLESS=-15, ATE_RAW_FOOD=-10, UNCOMFORTABLE_EATING=-5,
COMFORTABLE_EATING=+5; `NEUTRAL_MOOD`=75, `MOOD_TICK_INTERVAL`=10). The data
is likely already in the villager's UI payload (`TownVillagerUIs` / the villager
stats screen) — the stats tab just doesn't render the negative moods. Check
what the villager-stats screen receives and add an indicator for each active
negative mood. Ties into `villager-menu-status-icons-no-tooltips.md` (status
icons had no tooltips — the same UI surface) and the "didn't sleep" detection
(`villager-dining-with-zero-food-stuck.md` is the related soft-lock).
