---
status: fixed-2026-10-14
---
# Entity load: TownVillagerSleepModule.claimBed dies with "Town has not been initialized"

Observed (2026-10-14, playtest world): on every world load, one entity fails
to deserialize:

```
WARN [minecraft/EntityType]: Exception loading entity:
java.lang.IllegalStateException: Town has not been initialized on
class ca.bradj.questown.town.entity.TownVillagerSleepModule
  at ca.bradj.questown.town.UnsafeTown.getUnsafe(UnsafeTown.java:31)
  at ca.bradj.questown.town.entity.TownVillagerSleepModule.claimBed(TownVillagerSleepModule.java:44)
  at ca.bradj.questown.town.entity.TownVillagerSleepModule.claimBed(TownVillagerSleepModule.java:16)
```

Present on both the 00:18 (server-12) and 00:35 (server-13) loads of this
world → pre-existing, not a regression.

## Why it matters

- The entity carrying the sleep module **fails to load** (vanilla logs the
  exception and drops the entity). If that entity is a townie, the town loses
  it silently on every restart — no "villager vanished" notice reaches the
  player. (Not yet confirmed: the playtest roster has so far looked intact,
  so the affected entity may be a leaver/visitor or the failure may be
  non-fatal to the entity's other modules.)
- Root cause shape: vanilla deserializes entities **before** (or while) the
  flag's block entities finish their `Running initializer (N left)`
  sequence, so a villager entity whose NBT carries a claimed bed resolves
  `getUnsafe()` into a not-yet-initialized town.

## Open questions

- Which entity id / species hits this? (need the full WARN with the entity
  context — vanilla sometimes logs it a few lines after; or instrument the
  load and dump the entity's UUID/type.)
- Is the entity actually dropped, or does vanilla retry/partial-load?
- FIX direction: claimBed at load must not touch `getUnsafe()` before the
  town is initialized — defer the claim to first tick (the flag's initializer
  runs "N left" steps; the town handle is live by step k) or guard with an
  initialized-check.

## Log lines

`/tmp/dev-server-13.log` at 00:35:03 (and server-12 equivalent): the stack
above, once per load.


## Root cause (2026-10-14, static trace)

Full chain: `VisitorMobEntity.load` (entity deserializer) →
`TownVillagerMobs.assumeStateFromTown` → `TownVillagerHandle.register` →
`SimpleVillagerHandle.register` → `this.add(vEntity)` →
`delegator.getSleepModule().claimBed(vEntity)` →
`TownVillagerSleepModule.claimBed` → `town.getUnsafe()` → **throw** — the flag's
initializer (`TownFlagTileData` → `villagerHandle.associate` → `sleep.associate`)
had not run yet for the entity being deserialized. Vanilla's reaction to an
exception during entity load: log WARN and **drop the entity**.

Impact: one townie/visitor entity is silently lost on every restart (its personal
state - position, inventory, bed claim - dies with it; the town itself survives,
its state lives in the flag NBT). 2026-10-14 00:46 restart (server-14): the
gatherer's UUID `bdaf231f` vanished from the log entirely; a gatherer reappeared
at the next morning tick under UUID `2f33acf3`.

## Fix (commit follows)

`TownVillagerSleepModule` now gates on `UnsafeTown.isInitialized()` (already
present): an early `claimBed` appends to `pendingClaims` instead of throwing;
`associate(t)` initializes the town then drains the queue. The entity loads
normally and its claim lands when the flag initializes.

- Test: `TownVillagerSleepModuleTest` - `claimBed` before initialization must not
  throw (pre-fix this was the exact ISE above; the null entity stands in because
  the deferred path never touches entity state). The drain path needs a live
  `TownFlagBlockEntity` (registry-bound) → held-run covered.

## Second landmine (2026-10-14 morning)

The claimBed fix exposed a second unguarded `getUnsafe()` one line further
down the same path: `SimpleVillagerHandle.register`'s tail
`delegator.setChanged()` -> `TownVillagerHandle$1.setChanged()` ->
`town.getUnsafe().setChanged()`. Same boot, same "Exception loading entity"
drop (02:40:13, class TownVillagerHandle). Fixed with the same
`isInitialized()` gate (deliberate no-op: the town state was just read from
the block entity - nothing new to persist). The first throw masked the
second. Live-verify: zero "Exception loading entity" on the next restart
(world has a townie).
