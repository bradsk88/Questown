# Need bubbles — from "gave up walking" to an icon over one townie's head

One unmet need (ADR-0011) travels from a brain behaviour on the server to a
world-space bubble on the client. The server decides *that* a townie is stuck; the
client decides *which* stuck townie the player is currently asking about. Neither
half knows the other's rule.

See `CONTEXT.md` → **Need bubble** for the vocabulary, and
`docs/adr/0011-watching-first-and-need-bubbles.md` for why silence-means-working.

## Happy path

```mermaid
flowchart TD
    Sink["MoveToTownTargetSink.tick<br/>(brain activity)"]
    Sink -->|"CANT_REACH_WALK_TARGET_SINCE older than 200 ticks<br/>OR 3 failed unstick shoves"| GiveUp["hasGivenUpReaching"]
    GiveUp --> Set["vme.setNeed(CANT_REACH)<br/>+ erase WALK_TARGET / PATH"]
    Set --> Data["SynchedEntityData 'need' (String)"]

    Data -.auto-syncs.-> Focus["NeedBubbleFocus.tick<br/>(client tick, 16-block box)"]
    Focus -->|"best crosshairAlignment >= 0.9,<br/>one winner"| Hold["current = that townie's UUID"]

    Hold --> Render["VisitorMobRenderer.render<br/>→ renderNeedBubble"]
    Render --> Bubble["BubbleRenderer.renderIconBubbleFor<br/>(TownieNeed.icon() = barrier)"]

    Hold -->|"within 8 blocks AND alignment >= 0.985"| Hint["ClientAccess.showHint(need.hintKey())<br/>every 40 ticks"]
```

- **The give-up is keyed on failure, not elapsed travel.** `Config.WANDER_GIVEUP_TICKS`
  is this behaviour's *maximum duration*, so reusing it would cap healthy long walks
  and abandon townies mid-journey in a large town.
- **The bubble is drawn from `render`, not as a `RenderLayer`** — inside a layer the
  pose is still under body yaw and the icon would spin as the townie turns. This is
  the same reason `HelperChickenRenderer` calls the bubble layer statically.
- **Icon then words, in two gestures**: sweeping the crosshair earns the icon ("who
  needs me"); walking up and looking squarely earns the action-bar line ("what for").
- The bubble renderer was moved to `render/` (from `mobs/helperchicken`, where it was
  first built) once the third consumer (dead doors) landed, since it is now a neutral
  world-space bubble shared by the chicken, townie needs, and dead doors. It is
  `render/BubbleRenderer` — the chicken-specific `renderBubbleFor` path is the only
  method that still takes a `HelperChickenEntity`.

## The second consumer: dead doors

A registered door with no room bubbles too (ADR-0011, grilling 2026-08-09). Doors are
not entities, so the pipeline differs from townies at both ends and meets in the middle:

```mermaid
flowchart TD
    Scan["MultiLevelRoomDetector scan callback<br/>+ recipe listeners + register/deRegister"]
    Scan --> Recompute["TownRoomsMap.recomputeDeadDoors"]
    Recompute --> Classify{"rooms.get(doorPos)?"}
    Classify -->|"no room"| NE["NOT_ENCLOSED"]
    Classify -->|"room, no recipe match"| NR["NO_RECIPE"]
    NE --> Push["flag.setDeadDoors<br/>+ sendBlockUpdated on change"]
    NR --> Push
    Push --> Tag["flag BE update tag: 'deadDoors' list"]
    Tag -.chunk sync.-> Reg["DeadDoorBubbles.register(flag)<br/>client onLoad / setRemoved"]
    Reg --> Focus["NeedBubbleFocus.tick:<br/>doors (BlockPos keys) compete in the SAME<br/>contest as townies (UUID keys)"]
    Focus -->|"currentDoor()"| Render["NeedBubbleClientEvents.onRenderLevel<br/>AFTER_TRANSLUCENT_BLOCKS<br/>→ renderIconBubbleAtBlock"]
```

- **Zombie doors are not bubbles.** If the door *block* is gone, the registration is
  silently discarded on the next tick's block check (ADR-0013) — there is nothing to
  point a bubble at, and a never-completed room cost the player nothing, so there is no
  broadcast either.
- **Both troubles share one door icon** (`DoorTrouble.sharedIcon`); the distinction
  lives only in the close-up words, because the fix differs (walls/roof vs. contents).
- **The sync rides the flag BE update tag**, not a packet. Accepted cost: a player in
  bubble range of a door whose *flag* chunk is not loaded sees no bubble.
- **A door bubbles from the moment it is registered** — the wand-then-fetch-materials
  build flow is *expected* to show a bubble. It diagnoses; it never deregisters.
- The focus holder key is now `Object` (UUID for townies, BlockPos for doors) — the
  hysteresis rule (`keepsBubble`) is type-agnostic and its tests did not change.

## The third consumer: unmet item

When `TownPossibleWork.applyScores` finds **no viable job** for a root, the villagers of
that root are waiting on supplies the town does not have. That path already computed the
exact missing ingredient for the economics record; it now also raises the bubble, and the
bubble shows that item, not a generic stand-in.

- **Set** in `registerUnmetNed` (per-villager, same pass as `econ.registerUnmetNeed`):
  `setNeed(UNMET_ITEM)` and `setUnmetItem` with the missing ingredient's
  `Ingredients.toString` form (a tag like `#questown:villager_food` or a single item).
- **Cleared** in the `applyScores` else-branch (`clearUnmetItemNeeds`): any villager of
  the root whose need is `UNMET_ITEM` goes back to `NONE` the moment a viable job exists
  again, and `setNeed` clears `unmetItem` when the need is not `UNMET_ITEM` (so the item
  cannot linger onto another need). Only `UNMET_ITEM` is cleared here. A simultaneous
  `CANT_REACH` is left alone, and `MoveToTownTargetSink.clearCantReachNeed` returns the
  courtesy (each consumer clears only its own need).
- **Icon is the missing item itself**, shown like the Inventory screen's "needs" row:
  the item ID is synced per entity (`unmetItem`, alongside `need`), the client rebuilds
  the ingredient, and the bubble renders a rotating representative via
  `Ingredients.representativeItemStack`. It falls back to `TownieNeed.icon()` (the bundle)
  when the item cannot be resolved on the client.
- **The words name the item** ("They need %s to work", `%s` from
  `Ingredients.getName`, so a tag renders as its name, e.g. "Food"). The generic
  `unmet_item` key is the fallback when no item resolves. This is the ADR's deferred
  "townie under the actual missing item" follow-up: the item ID is now synced per entity,
  so the bubble no longer needs the developer-facing `WithReason` prose to be rephrased.
- **Autotest**: `farmer/harvest_wheat [no_supplies]` asserts every townie ends the run
  at `UNMET_ITEM`; the base `farmer/harvest_wheat` asserts the supplied counterpart ends
  at `NONE`. Both ride the existing `CustomAssertion` seam (realtime phase). The item the
  bubble shows is client-side, so it is verified in a dev client, not by the autotest.

## Exceptional: the need clears itself

```mermaid
flowchart LR
    TCP["tryComputePath"] -->|reachedTarget| Clear["clearCantReachNeed"]
    TCP -->|"path.canReach()"| Clear
    Clear --> Reset["failedUnsticks = 0<br/>setNeed(NONE) if CANT_REACH"]
    Reset --> Sync["synched need = NONE"]
    Sync -.-> Forget["NeedBubbleFocus.forgetIfResolved<br/>(called from the renderer)"]
```

- **There is no backoff and no silent re-targeting.** A townie that gave up retries,
  fails, and re-bubbles until the player clears the way — silent self-correction is
  the failure mode the mechanism exists to kill (CONTEXT.md, 2026-07-28).
- Only the server writes `need`; `setNeed` no-ops client-side and on an unchanged
  value, so the synched field is quiet unless something actually changed.
- `failedUnsticks` also resets in `start`, i.e. whenever a fresh path is taken up.
  Without that, a shove count earned against one target would carry to the next and
  make the townie give up on it instantly, before trying.

## Exceptional: two needy townies standing together

```mermaid
flowchart TD
    Tick["tick: score every needy townie in 16 blocks"] --> Best["best = highest crosshairAlignment"]
    Best --> Keep{"keepsBubble(holder, holderScore,<br/>challenger, challengerScore)"}
    Keep -->|"challenger < holder + 0.02"| Stay["holder keeps it"]
    Keep -->|"clearly better, or holder no longer qualifies"| Swap["adopt(challenger)"]
```

- `keepsBubble` is deliberately pure and package-visible so the flicker rule is unit
  tested (`NeedBubbleFocusTest`) without a client level; the cone threshold itself is
  only verifiable in-game.
- Focus state is **static** — there is exactly one local player, and exactly one
  bubble is shown town-wide by design.

# Third holder type: attention targets (G1+G2 "!" bubbles)

The one-bubble contest now has three candidate classes: townies (UUID holders),
dead doors (raw `BlockPos` holders) and **attention targets**
(`AttentionBubbles.Target` record holders — a flag with incomplete quests, or a
job board with zero work requests). The same cone/hysteresis/`keepsBubble`
rules apply; the holder type is carried through `current` as `Object`, so
`currentDoor()` still means "holder is a raw BlockPos" and
`currentAttention()` is the new accessor. When an attention target wins,
`NeedBubbleClientEvents` draws the "!" texture bubble
(`BubbleRenderer.renderTextureBubbleAtBlock`, authored
`textures/bubble/attention.png`) instead of the door's item bubble. The hint
(action-bar text on close examination) comes from `Kind.hintKey` —
`message.questown.attention.*` in en_us.json.

## Townie skin pool (`TownieSkins`, 2026-10-14)

Townie skins are a deterministic per-UUID pick from a pool: the 9 vanilla
1.20.6 character skins (slim variants, `minecraft:textures/entity/player/
slim/{alex,ari,efe,kai,makena,noor,steve,sunny,zuri}.png` — added alongside
Steve/Alex in 1.20.5) plus the mod's 6 custom `textures/entity/1-6.png`.
`VisitorMobRenderer` bakes `PLAYER_SLIM`, so the slim textures are the
match. `TownieSkins` is pure (pool + floor-mod pick) so it is
unit-testable without the client; the renderer delegates to it. Use
floor-mod, not `Math.abs`, for the hash (MIN_VALUE edge).

## Held-item NBT load: empty-slot guard (2026-10-15 playtest)

`readAdditionalSaveData` mapped every saved held-item tag through
`ItemStack.parse(...)`. `addAdditionalSaveData` saves an **empty compound**
for empty slots, and vanilla's parser logs `Tried to load invalid item: No
key id in MapLike[{}]` for it - 12 lines per entity load (every save/reload
of every townie). The guard: `tag.isEmpty() ? ItemStack.EMPTY : parse(...)`
before the `orElse` fallback. No behaviour change for non-empty slots.

## Doorway pathfinding preference — reverted (2026-10-15 → 2026-10-17)

The 2026-10-15 fix ("townies prefer jumping over walls to get inside the room") gave
`VisitorMobEntity.getPathfindingMalus` a 0.5 discount on `DOOR_WOOD_CLOSED` /
`DOOR_IRON_CLOSED` / `DOOR_OPEN`. **That made open doors impassable**: vanilla's
`WalkNodeEvaluator` treats any malus `< 0.0F` as unwalkable (its `>= 0.0F` gates), and
`DOOR_OPEN`'s default is `0.0F` → `-0.5`. A closed wooden door was unaffected because a
door-opening mob's evaluator re-types it to `WALKABLE_DOOR` (not discounted). Live
symptoms 2026-10-17: villagers could not enter rooms through open doors ("if I close the
door, they open it and walk through"), routed over walls / onto a bed, and stood on top
of a supply chest unable to path off. The override is removed (vanilla costs).

**Rule:** a malus is never negative — to make one route cheaper, raise the cost of the
alternative instead. The original wall-climb complaint is open again
(`.scratch/playtest-2026-10-17/villager-stuck-on-bed-pathing-over-wall.md`).
The `VillagerPathNavigation` seam (re-classifying campfire, water, fence cells) is the
place for a wall-top penalty if one is needed.

## NO_STORAGE need: full storage bubbles at once (2026-10-21 dev request)

Fourth need consumer. `VisitorMobEntity.syncStorageNeed` (each server tick, beside the status
sync): a townie whose job status is NO_SPACE — holding work output with no free town storage slot
(`JobStatuses` "stop when holding output and the town has no space") — gets `NO_STORAGE` (drawn
from `TownieNeed.iconTexture()` — the questown "no space" status art, face-on via
`BubbleRenderer.renderTextureBubbleFor`; an item-model chest rendered at an angle, front away, hint "Every chest is full: they have nowhere to put what they made."). It replaces NONE
or UNMET_ITEM: full storage is the blocker right now, and `TownPossibleWork.refreshUnmetNeeds`
no longer overwrites NO_STORAGE with UNMET_ITEM (without both halves the bubble flapped between
them every recompute pass). It clears only its own need; UNMET_ITEM comes back on the next pass
if it still applies. The flag-level counterpart (after a tick build-up) is in
`town/rooms/COMPLEX.md`. Live pin: `cook/fuel [storage_full]`.

## Waking: stand up beside the bed, else in the doorway (2026-10-23, r12 #6)

`VisitorMobEntity.stopSleeping` overrides vanilla's whole wake path. Its copy of vanilla's
stand-up search checked only `HospitalBedBlock`, so a townie waking in a **vanilla** bed was set
STANDING at its sleeping position: on top of the bed (r12: y 76.5625). In a room 2 blocks tall
inside, a ~1.95-tall townie there is wedged under the ceiling and never moves again (r12 day 4:
both townies pinned until the roof was raised). `standUpBesideBed` now runs for both bed kinds:
- the unoccupied flag is cleared;
- `BedBlock.findStandUpPosition` is called with the bed's own `FACING` for a vanilla bed (as
  vanilla does), or the entity's direction for a hospital bed (as before);
- with no free floor beside the bed, the townie stands in the doorway of the room holding the
  bed. That is the dev's fallback, looked up through `RoomsHolder.findDoorOfRoomContaining` ->
  `RoomDoors`; above the bed is the last resort, when no room is found.

`RoomDoors` is pinned by `RoomDoorsTest`. The entity half is registry-bound (`Level`,
`BedBlock`), so it is checked live (a 2-high bedroom, a night's sleep, the townie walks off).

## A puff when a villager leaves town or comes back (r17 #4, dev 2026-10-25)

A leaver vanishes when its job's `remove_from_world` rule applies (`VisitorMobEntity` sets the
synced `visible` flag from `!job.shouldDisappear(...)` every tick), with nothing to mark it. When
the flag flips (either way) the server now sends 12 `POOF` particles around the body. A villager
that loads already away puffs once on its first tick (the synced value starts visible).

## Stranded outside the town: poof back to the gate (r20, dev 2026-10-25)

A villager that fell off a cliff into water floated 8 blocks below the town for minutes. Dev call:
rescue it, but only **outside** the town, and visibly. Inside the town a blocked path is usually
something the player built, so it stays a `CANT_REACH` bubble (ADR-0011).

- **Outside the town** (`StrandedRescue.isOutsideTown`): outside the box around
  `TownInterface.getTownAnchors()` (the flag, the welcome mats, every registered door; a door's
  `scanLevel` is a Y offset from the flag), widened 8 blocks sideways and 4 below the lowest
  anchor. No ceiling. The vertical margin is load-bearing: the r20 villager was only 7 blocks from
  the flag sideways, but 8 below it.
- **Triggers** (`MoveToTownTargetSink.tick`, after the `shouldBeMoving` gate): (1) the existing
  give-up (`hasGivenUpReaching`) fires while outside, and the rescue replaces the bubble; (2) in or
  directly over water for 200 ticks without getting a block closer to the walk target
  (`StrandedRescue.observeWater`). The water case exists because the unstick ladder keys on
  "position not changing", and a drifting swimmer keeps changing position.
- **The poof** (`VisitorMobEntity.rescueToGate`): `POOF` particles plus the chorus-fruit teleport
  sound at both ends, a teleport to `getVisitorJoinPos()` (beside the gate, clear of the campfire's
  burn radius), the need cleared, and an INFO `[rescue]` line on `Questown:Villagers`. A villager
  hidden on a trip (`visible` false) is never "stranded".
- Pinned by `StrandedRescueTest` (footprint + water window). The sink and the teleport are
  registry-bound and not unit-tested; they still need a live catch (a `[rescue]` line).

## `_qtdev nav <villager>`: read the walk target, do not infer it (r20 #4)

Read-only probe over `VisitorMobEntity.describeNav()`: position, status, the brain's WALK_TARGET
(and its close-enough distance), the navigation path's target, end node, `canReach` and progress,
the CANT_REACH_WALK_TARGET_SINCE timestamp, and the need. Added when two villagers stood in the
bedroom facing the wall shared with the store room, and three code readings of the target pickers
(`InteractionSpots.find`, the no-room chest-front branch, standable-on-the-wall) all pointed at
the store room's doorway instead. Output: `[_qtdev nav]` on `Questown:Villagers`.
