---
status: ready-for-agent
origin: 2026-10-12 playtest — user chat 17:56:35
---

# Villager status-icon tooltip lists ALL jobs, not the town-known ones

> The villager's status icon has "click to learn about jobs" which reveals ALL
> of the jobs. Should probably only reveal the ones that are "known" to the
> town.

The villager status icon's hover tooltip (the jobs screen / status icon
tooltip) lists the full job catalogue. On day 1 the town only knows a couple
of jobs; the list should be filtered to the jobs the town has unlocked/knows
(the same `isUnlocked` semantics used by the day-1 econ-tab fix
(econ-tab-shows-locked-job-items.md)). Find the tooltip builder (status-icon
tooltip path in the villager screen / `MultiStatusScreen` family) and filter
against the flag's unlocked-job set.
