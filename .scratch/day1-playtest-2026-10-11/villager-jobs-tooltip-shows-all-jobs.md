---
status: done
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

## Shipped (2026-10-13)

`TownVillagerUIs.showJobsWithSameRootUI` now filters the same-root job list
against the town-wide unlocked set (`VillagerHolder.isUnlocked(JobID)` - the
same "can anyone in town do this" gate the request board uses, and the same
semantics as the day-1 econ-tab fix). The clicked townie's own job is always
in the list (it is unlocked by that townie), so the screen is never empty.
In-game confirmation pending.

## Resolved (audit 2026-10-22)

`TownVillagerUIs.java:220` filters the jobs list by `getVillagerHandle()::isUnlocked` (town-wide unlocked set), as shipped 2026-10-13.
