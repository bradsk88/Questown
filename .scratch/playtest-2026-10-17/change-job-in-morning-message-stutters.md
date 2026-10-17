---
title: '"Villager Villager 1 will change to a new job root in the morning" stutters'
status: ready-for-human
origin: 2026-10-17 playtest round 5 — user chat 18:00:45 (dev-server-8, build 3ed88de2)
---

"That message stutters :point-up: "Villagre Villager 1""

`message.questown.villager.change_job_in_morning` was "Villager %s will change to a new job root
in the morning." and `SimpleVillagerHandle` now passes the display name ("Villager 1"). Text is
now "%s will change to a new job in the morning." (also drops the "job root" jargon). The
no-display-name fallback passes a truncated UUID, which now reads without the "Villager" prefix.
