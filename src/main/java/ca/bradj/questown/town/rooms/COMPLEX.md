# Room detection — door → wall-walk → walkable room

A registered door becomes a town room through a per-level 2D wall-walk, a
walkability filter, and a recipe match. The scan is spread across ticks:
`TownRoomsMap.tick` builds one `MultiLevelRoomDetector` and steps it with
`pendingRooms.proceed(...)` (one level-detector step per tick) rather than
scanning a whole town in one burst.

## Happy path

```mermaid
flowchart TD
    Door["wand click on a door<br/>or _qtdev registerroom<br/>→ TownRoomsHandle.registerDoor"] --> Map["TownRoomsMap.tick<br/>groups registered doors by scanLevel"]
    Map --> ML["MultiLevelRoomDetector<br/>one LevelRoomDetector per Y level"]
    ML --> Walk["LevelRoomDetector.proceed<br/>→ WallWalkingRoomDetection.tryFind<br/>(2D wall-walk on X/Z at that level)"]
    Walk --> Foot["enclosed 2D footprint<br/>(InclusiveSpace)"]
    Foot --> Filt{"hasWalkableInterior?<br/>(walkability filter)"}
    Filt -- "too few standable cells" --> Dead["Optional.empty()<br/>→ door is dead (NOT_ENCLOSED)"]
    Filt -- "enough standable cells" --> Recipe["RecipeDetection.getActiveRecipes<br/>(match room contents)"]
    Recipe --> Room["valid room,<br/>or NO_RECIPE if contents match nothing"]
```

- **A wall is two solid blocks; interior is anything that is not a wall.**
  `WallDetection.IsWall(dp, y)` reads the block at `y` and at `y+1`: both solid
  (or a door) makes a wall. Anything else — including a cell empty at `y` but
  solid at `y+1` — reads as interior. That last case is the gap the walkability
  filter closes.
- The scan is resumable: `proceed()` returns null until a level finishes, and
  `TownRoomsMap` re-queues the detector, so a large town does not stall the
  flag tick.
- A door with no room (`NOT_ENCLOSED`) or a room that matches no recipe
  (`NO_RECIPE`) surfaces as a world-space bubble over the door (ADR-0011); the
  scan never deregisters it on those grounds.

## The walkability filter (rejects villager-unreachable rooms)

```mermaid
flowchart LR
    Foot["detected footprint"] --> Cells["InclusiveSpaces.getPositions<br/>(INTERIOR_ONLY)"]
    Cells --> Floor{"IsFloorOpen(dp, y)?<br/>passable at the feet (y)?"}
    Floor -- "no (a block on the floor)" --> Content["room contents —<br/>excluded from the count"]
    Floor -- yes --> Check{"IsWalkable(dp, y)?<br/>passable at feet (y) AND head (y+1)?"}
    Check --> Count["walkable / open-floor cells"]
    Count --> Verdict{"walkable >= ceil(open-floor ×<br/>MIN_ROOM_WALKABLE_FRACTION)?<br/>(0 open-floor cells → accept)"}
    Verdict -- no --> Rej["reject → NOT_ENCLOSED"]
    Verdict -- yes --> Keep["keep → recipe match"]
```

- **Why it exists.** The wall-walk is 2D: a room roofed one block above its
  floor has interior cells that are empty at the feet level (so "not a wall")
  but solid at the head level — enclosed, yet a two-block-tall villager can
  never step inside. Before this filter such a room detected, and could even
  match a recipe, so the town assigned villagers a room they could not enter.
- **Some minimum, not every cell.** `Config.MIN_ROOM_WALKABLE_FRACTION`
  (default 0.5) is the fraction of interior floor cells that must be standable,
  so a room with a low overhang over only part of its floor still counts while
  a fully low-roofed room (0 standable) is rejected. `0.0` disables the check.
- **A block on the floor is contents, not a low roof.** Only cells open at the
  feet level (`WallDetection.IsFloorOpen`) count toward the standable
  denominator. A cell filled at the feet by a block — the chest in a 3x3
  storeroom's single interior cell — is the room's own work block, so it is
  excluded rather than counted against the room. The low-roof case is air at
  the feet (solid at the head), so it still counts and still fails; a room
  whose interior is entirely filled (0 open-floor cells) is accepted. This is
  what stopped the 3x3 storeroom from being destroyed the moment its chest
  matched the recipe (day-1 playtest 2026-10-11,
  `room-3x3-chest-instantly-destroyed`).
- The filter runs in `MultiLevelRoomDetector.handleNewRooms`, so a rejected
  room never reaches recipe matching. It surfaces as the existing
  `DoorTrouble.NOT_ENCLOSED` (no new trouble type), pointing the player at the
  door to rebuild taller. Regression guard: the `flag/too_short_room_dead`
  autotest builds the exact 1-block-tall structure and asserts the rejection.
  The 0.5 fraction is bracketed from both sides by two more tests: `flag/floating_chest_room`
  (a 5x5 room with one chest floating at head height is accepted) and
  `flag/floating_furniture_over_half` (a 4x4 room with three of four interior
  cells floating is rejected). `flag/filled_storeroom_alive` pins the contents
  rule: a 3x3 room whose single interior cell is the recipe's chest is accepted
  (door stays live, a room is detected).

# AttentionBubbles — flag/board attention candidates for the need bubble

Client-side registry mirroring `DeadDoorBubbles`: every client-loaded
`TownFlagBlockEntity` registers in `onLoad` and unregisters in `setRemoved`.
`near(player)` returns the bubble candidates within 16 blocks: each flag whose
synced `attentionIncompleteQuests` is true (G1, above the flag) and each of its
job boards when the synced `attentionNoWorkRequests` is true (G2, above the
board). The state itself is server-decided and synced on the flag's update tag
(see `town/entity/COMPLEX.md` "Attention ("!") bubble state"); this class only
collects the client-loaded copies. `AttentionBubbles.Target` (a `BlockPos` +
`Kind` record) is the third holder type in the `NeedBubbleFocus` contest —
distinct from the door's raw `BlockPos` so the two never collide.

## Storage-full flag bubble (2026-10-21 dev request)

`AttentionBubbles.Kind.STORAGE_FULL` (chest bubble, `textures/bubble/chest.png`; each Kind now
carries its texture, rendered by `NeedBubbleClientEvents`) over the flag when full storage has
become a blocker: `TownFlagBlockEntity.syncAttentionState` (every 20 ticks) feeds
`StorageBlockTally` the number of townies in NO_SPACE and `Jobs.townHasSpace`; it trips at 1200
villager-ticks (one townie for a minute, three for 20 s — dev: "let's try it") and resets on any
free slot or when nobody is blocked. Synced like the other attention booleans
(`attentionStorageFull` in the update tag). The townies themselves bubble immediately
(`TownieNeed.NO_STORAGE`, `mobs/visitor/COMPLEX.md`). Unit: `StorageBlockTallyTest`; live:
`cook/fuel [storage_full]` (3000 realtime ticks, every container filled with dirt).
