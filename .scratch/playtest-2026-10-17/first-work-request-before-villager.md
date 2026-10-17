---
title: '"Post a request" quest posted before any villager has arrived'
status: fixed
origin: 2026-10-17 playtest — user chat 04:29:48 (repeat of 2026-10-15 first-work-request-unlock-on-villager)
---

"'Post a request' quest should not get added until the villager has arrived.
Which will be tomorrow."

Evidence: 04:29:24 `First-work-request batch posted` on basics completion; the
first villager registered at 04:31:26.

Fix: `PostFirstWorkRequestQuestReward` defers itself (a fresh instance — `claim()`
ignores an applied one) into the persisted morning rewards while the town has no
villager; the morning release claims arrivals first, so it posts the morning the
first villager arrives. Docs: `town/entity/COMPLEX.md` "First-work-request quest
waits for a villager".
