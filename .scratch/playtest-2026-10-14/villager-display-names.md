---
title: 'Villagers named "Villager 1", "Villager 2" instead of showing UUIDs'
status: ready-for-agent
---

# Villager names: "Villager 1", "Villager 2", not UUIDs

Captured 2026-10-14 (server-19, in-game chat 04:16:50):

> "We should do a super simple pass at villager's names. They should get
> named 'Villager 1' 'Villager 2' etc instead of showing their UUID all
> over the place."

The UUID currently appears wherever a townie is identified (status lines,
quest text, log-derived UI). Scope: a stable per-townie display name,
assigned at arrival (Villager 1, 2, 3 ...) and used in the user-facing
strings that currently interpolate the UUID.

## Decision (2026-10-14, user)

Surfaces: **name tags, menu headers, system-generated chat messages, etc.**
Numbering: **no reuse** — when a villager dies/leaves its number is retired,
never re-issued to a new townie.

## 2026-10-14 implemented

"Villager N" = monotonic arrival number (pure core: `VillagerDisplayNumbers`,
pinned in `VillagerDisplayNumbersTest`). Assigned in
`TownVillagerHandle.register` (the single spawn+reload funnel), persisted in
the handle NBT (`display_numbers` + `display_numbers_highest`). Surfaces
done: the name tag (custom name + visibility on register), system chat
(`TownMessages.jobChanged` label overload), and the BOP screen text (via
`VillagerMenusPayload.villagerDisplayName`). Lang: `menu.villager.name` =
"Villager %1$s". Deferred: the flag quest-card tooltip (`quests.job_owner`
still shows the truncated UUID - needs a UUID->name map on the flag-menu
sync). `town/entity/COMPLEX.md` "Townie display numbers".
