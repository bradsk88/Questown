---
title: Two villagers present after the first sleep (expected one)
status: needs-info
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 22:16:48:

> I ahve two villager after my first sleep? Thta seems worng...

After the player's first campfire sleep (which should trigger the first morning
arrival), the town has **two** villagers. The expectation is one (a single first
arrival "in the morning").

## Acceptance criteria

- The first sleep yields exactly one new villager (the first morning arrival),
  not two.
- If the design is actually "more than one arrives," document that and confirm
  the count is intentional.

## Notes

Investigate the arrival/roster mechanism: where a morning arrival is spawned and
whether a first-sleep path can spawn two (e.g. an arrival + a separate
onboarding grant, or a double-fire of the arrival trigger). Check
`TownFlagTicker` / the arrival executor and the roster count after the first
sleep. Determine whether it's a real double-spawn or an off-by-one in the
roster count the player is reading.
