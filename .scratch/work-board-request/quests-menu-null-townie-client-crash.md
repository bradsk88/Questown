---
title: Opening the villager quests menu crashes the client (null townie UUID)
status: needs-triage
created: 2026-10-01
closed: 2026-10-02
---

## Context

Client log (1.20.6 wip, 2026-10-01 03:44:57):

```
[Render thread/ERROR] [Questown:GUI/]: Failed to open villager quests
container: Cannot invoke
"ca.bradj.questown.mobs.visitor.VisitorMobEntity.getUUID()" because "e" is null
```

The client then disconnects (`Client disconnected with reason: Failed to
open a screen`) - the whole session is lost mid-capture.

## State when it fires

Same world/fixture where the quests menu opened fine ~20 minutes earlier:
fresh-world fixture, one townie (`NO_JOBSITE`, "needs item: Food"), one
posted work request (wheat_seeds). The menu's container build walks the
quest/villager state and hits a null `VisitorMobEntity` reference - a quest
that references a townie UUID the entity lookup no longer resolves (townie
despawned? reloaded after the unstick reset? quest record pointing at a
stale entity).

## Why it matters

It turns an open-menu drive into a client death mid-capture (bank
corruption: the shot then lands a "Connection Lost" or gameplay screen).
The shot-stage health/list guards do not catch a client that is crashing,
because the player is *online* while the client tears itself down. A guard
that vision-checks the first frame after an open-menu command (or checks the
client log for "Failed to open") would have caught it.

## Resolved 2026-10-02

The client no longer resolves the townie from a raw entity id: the wire
payload (`VillagerMenusPayload`) carries the UUID + slot locks, and a
best-effort entity lookup only feeds `VillagerStatsMenu.attach()` live
stats updates. A despawned local entity now degrades the menu to the
wire's static data instead of NPE-ing the screen open. Round-trip pinned
by `VillagerMenusPayloadTest`; flow documented in
`gui/COMPLEX.md` ("Villager menu wire").
