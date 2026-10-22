---
title: 'change_job_in_morning broadcast shows a raw UUID instead of the villager name'
status: done
origin: 2026-10-15 playtest — user chat 03:31:36; log 03:31:26.896
---

# Villager name missing from the change-job-in-morning broadcast

User: "^ That should have used their villager name."

Log evidence: `Broadcasting message:
message.questown.villager.change_job_in_morning [69af...ced]` — the
argument is the raw UUID `69afd1df-796d-421d-b523-468d681dced8`, not a
display name. Resolve the UUID to the villager's name (the name
`messages.jobs.changed` correctly uses — "Villager 1" at 03:15:55) at the
broadcast site.

## Resolved (audit 2026-10-22)

bbbdf88f (Delegator.displayNameFor): `SimpleVillagerHandle.java:734` now broadcasts `change_job_in_morning` with `chatName(villagerUUID)` ("Villager N"); f76442cc then dropped the doubled "Villager" prefix.
