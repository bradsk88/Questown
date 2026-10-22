# Villager-arrival quest batch missing the bedroom quest

> status: done
> origin: 2026-10-15 playtest — user chat 04:01:36

"The batch of quests for the cook (job change, kitchen, food) should have also
included a bedroom quest since it represents the addition of another villager."

When a job change adds a new villager to the town (the cook, 03:32), the quest
batch issued for that arrival covered job change + base room (kitchen) + food,
but not the bedroom/sleeping arrangement. The arrival of a new villager should
include a bedroom quest (or the existing sleep/campfire path should surface one
in the batch).

Design gate — confirm with the user whether the bedroom quest is a NEW quest
type or the existing one, and which job-arrival flow issues the batch
(`changeJobRootNow` / `unlockAndChange` path in `SimpleVillagerHandle`).

## Resolved (audit 2026-10-22)

Dev onboarding rule (2026-10-18) 'a batch that brings a villager carries the bed that villager will sleep in' is in code: `TownQuests.addQuestsForJobChangeAndFood` (the cook batch) adds `MCQuest.upgrade(..., SpecialQuests.BEDROOM, BIG_BEDROOM)` (bedroom of 2 beds -> big bedroom of 4); the hunter batch carries `SpecialQuests.BEDROOM`.
