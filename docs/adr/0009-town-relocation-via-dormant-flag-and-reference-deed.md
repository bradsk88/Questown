# Town relocation via a dormant flag and a reference deed

status: proposed
date: 2026-06-15

## Context

Issue #199 ("Make it possible to move flags") asks for a way to relocate a
**town flag** — the placed block that marks a town's origin and owns its
persistent state (rooms, jobs, beats, registered fixtures). Today there is **no
survival way to move or remove a flag**: destruction is the creative-only
`qt flag destroy` dev command, and attacking the block just prints a "damaged"
message. The flag's `getBlockPos()` *is* the town origin — room scanning, tick
radius, helper-chicken beat geometry, biome scan, and the visitor spot all
derive from it.

Two facts about the existing data layer shape every option:

1. A **registered fixture** (door, fence gate, welcome mat, heal spot) is stored
   on a `TownPosition` whose **X/Z is absolute world coords but Y is
   flag-relative** (`scanLevel = fixtureY − flagY`; `getY(flagY) = flagY +
   scanLevel`). Rooms are *not* stored as fixtures — they reconstitute from
   registered doors via the room scan.
2. The entire town is one tile-data blob on the flag block entity
   (`TownFlagTileData`): quests, morning rewards, jobs, knowledge, villager
   roster, heal spots, BOP store, economics, and the chicken-arc state.

The issue author flagged the cheating risk explicitly ("it should probably be
slow and require the entire town to 'shut down' first") and sketched the
mechanic: villagers walk home and vanish, and when the last returns the flag
"turns into a different object that can be picked up."

## Decision

Relocation is a two-phase ritual built around a **dormant flag** and a
**reference deed** (terms defined in `CONTEXT.md`):

1. **Town shutdown.** Eligible only in realtime with the player present and the
   town not already shutting down (no chicken-arc gate). All townies path to the
   flag and vanish; leavers abort their trips. Completion requires **both** the
   recall finishing (`absorbed-count == roster`, with **force-absorb on
   timeout** as a deadlock guard) **and** a configurable minimum-duration floor
   (`TOWN_SHUTDOWN_TICKS`, default 200 = 10s). Cancelable until the last villager
   vanishes. Particle effects play over the flag throughout.

2. On completion the flag goes **dormant** — it stays in the world holding the
   town's authoritative data, inactive — and the player receives a **relocation
   deed**.

3. **The deed carries only a reference** (town UUID + original flag pos +
   dimension), never a snapshot. Placing it loads the dormant flag, copies its
   data to a new flag at the target, recomputes each fixture's flag-relative Y
   against the new flag, then **destroys the original**. Same-dimension only. If
   the original can't be reached, placement fails loudly rather than
   half-transferring.

4. **What travels:** all intangibles (identity, jobs, knowledge incl. known
   biomes, quests, roster, economics, chicken-arc state) and the registered
   fixtures as **absolute** positions. **Left behind:** the physical
   chest/bed/station blocks. **Re-derived:** rooms (rescanned from carried
   doors).

5. **Far-away fixtures.** If carried fixtures fall outside `TOWN_TICK_RADIUS` of
   the new flag, placement opens a **confirmation screen** whose copy says the
   town is "far away" (never "out of range"), defaulting to *leave behind* and
   requiring an explicit *bring-it-anyway* to retain.

6. **Lost-deed recovery.** Interacting with the dormant flag re-issues the deed
   or wakes the town in place — the town is never strandable because its data
   never leaves the world.

The flag stays unbreakable in survival, so the shutdown ritual is the only route
to a deed.

## Considered options

- **Flag-becomes-a-snapshot-item.** The flag itself converts into an item
  carrying the full town NBT. Rejected: losing the item (lava, despawn, death)
  destroys the entire town, and a copyable snapshot reopens a duplication hole.
  The dormant-flag model keeps the data in the world (loss-proof) and the
  reference deed is dupe-proof by construction (the source is read-and-destroyed
  atomically).
- **Flag-relative fixture frame.** Carry fixtures as offsets so the whole layout
  translates rigidly with the flag. Rejected: we deliberately leave the physical
  blocks behind, so relative offsets would point at empty air, and the
  "far-away" warning would have no measurable meaning. Absolute coords make
  "far away" a real distance check against `TOWN_TICK_RADIUS`.
- **Cross-dimension relocation.** Rejected (forbidden): fixtures are dimensionless
  absolute coords with no meaning in another world; the intangibles-only town
  that would result is not worth the edge-case surface.
- **Chicken-arc completion gate.** Considered blocking shutdown until the arc is
  terminal. Rejected: the arc controller re-derives its beats from observable
  flag-relative conditions every tick rather than latching, so the carried arc
  note is coherent in terminal states and simply re-derives at the new offsets
  mid-arc ("the tutorial follows you"). No gate needed; the note carries for free.
- **Material/resource cost for shutdown.** Rejected: the real cost is already
  steep (full downtime plus abandoning every physical structure); a resource tax
  on top is punitive for a rarely-used feature. The minimum-duration floor alone
  delivers the anti-cheat property (no reactive panic-button).

## Consequences

- A new dormant state and a deed item must persist a cross-reference (UUID + pos
  + dimension) that survives chunk unload; placement may force-load the original
  flag's chunk to destroy it.
- Relocating beside a new biome does **not** grant that biome's loot — knowledge
  carries, there is no re-scan. This is the intended anti-cheat behavior.
- Placement near an existing town can produce overlapping room/fixture claims;
  this is the same hazard as placing any flag near another town and is not solved
  here.
