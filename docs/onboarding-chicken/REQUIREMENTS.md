# Helper Chicken Onboarding Arc — Requirements

Captures the desired UX, design rules, and currently-known gaps for the
onboarding helper chicken (the bird that spawns in `empty_town` structures
and shepherds new players through their first town). This is a working
document — when behavior or strings change, update it.

---

## 1. Purpose

A new player who has never built a Questown town should be able to follow the
helper chicken's body language + hint text and — without reading external
docs — reach a fully working town with a registered room, a job board, a
chest, a welcome mat, a flag UI interaction, and the first delivery of
Worldly Seeds. The chicken is a tutorial substitute. It must teach by
*pointing* and *implying*; the player should never need to be told a
mechanic in plain prose unless they're stuck.

## 2. Beat State Machine

`ChickenBeatState` (closed enum, ordered) drives everything. Each beat is
satisfied by an observed condition; once satisfied the controller advances.

| Beat | Required action | Observed condition |
|---|---|---|
| `WAITING_FOR_STICK` | Right-click the flag with a stick | Player has a town wand in inventory |
| `WAITING_FOR_WAND_ON_CAMPFIRE` | Right-click the unlit campfire with the wand | Campfire is lit at `CAMPFIRE_OFFSET` |
| `SUNSET_AND_MAP` | Stay near the chicken at dusk; sleep via `wand on lit campfire` | `chickenObservedSleepSinceSunset` set on wake |
| `WAITING_FOR_WALL_BLOCK` | Place a cobblestone block in the wall gap | Solid block at `WALL_BLOCK_OFFSET` |
| `WAITING_FOR_DOOR` | Place an oak door in the doorway | Door block at `DOOR_OFFSET` |
| `WAITING_FOR_WAND_ON_DOOR` | Right-click the door with the wand | Room registered (door has a wand-marked door, OR a recipe-matched room exists) |
| `WAITING_FOR_SIGN` | Place a sign inside the room | Job-board block at `SIGN_OFFSET` (room recipe converts the sign automatically) |
| `WAITING_FOR_CHEST` | Place a chest inside the room | Chest block at `CHEST_OFFSET` |
| `WAITING_FOR_PRESSURE_PLATE` | Place a welcome mat between the gate fences | Welcome-mat block placed in flag-tracked welcome-mat list |
| `WAITING_FOR_VILLAGER_UI` | Right-click the villager | `chickenObservedVillagerUiOpen` set |
| `WAITING_FOR_FLAG_UI` | Right-click the flag | `chickenObservedFlagUiOpen` set |
| `AWAITING_WORLDLY_SEEDS_DELIVERY` | Hand Worldly Seeds to the chicken | Seed item consumed, statue transform fires |
| `COMPLETE` | — | Terminal state, chicken becomes statue |
| `FORFEIT` | — | Admin override / unrecoverable state |

`SIGN_OFFSET` and `CHEST_OFFSET` MUST be inside the room interior
(`x∈[3..5], z∈[3..5]`) so the room recipe scan sees them. The
`signAndChestOffsets_landInsideRoomInterior` test in
`ChickenScaffoldingLayoutTest` enforces this.

## 3. Bubble Behavior

`ChickenArcBubbles.forState(state, playerHasRequiredItem, sunsetChestSpawned, isNight)`:

- **No required item OR player isn't holding it** → single icon (the item the
  chicken wants).
- **Player holding the matching item** → alternates between the item and
  the destination block at 1Hz. Tells the player "now bring this to that".
- **Placement beats** (wall/door/sign/chest/plate) stay single — the
  destination is empty air, so there's no second icon.
- **`SUNSET_AND_MAP` is three-phase**:
  1. `!chestSpawned` → chest icon (chicken about to drop a chest with an axe + map).
  2. `chestSpawned && !isNight` → authored sunset texture (wait until dusk).
  3. `chestSpawned && isNight` → wand+campfire alternation (sleep-on-fire).
- **Through-walls mode** for `AWAITING_WORLDLY_SEEDS_DELIVERY` — the chicken
  is invisible behind blocks but the bubble still renders so the player can
  find their way back.

Icons render with `ItemTransforms.TransformType.GUI` and an explicit
translate to the bubble center, so 2D items and 3D blocks both center
inside the balloon (HEAD transform put blocks in the bottom-left corner).

## 4. Hint Text Behavior

When the player clicks (left or right) the chicken,
`ChickenArcController.onPlayerClickedChicken` emits an on-screen text hint.

**Voice:** internal monologue from the player's POV. "It seems to want a
stick…", "It looks at your wand, then to the door…". Never imperative,
never names mechanics directly. ≤80 chars per line, target ~50.

**Item-aware variants:** when the bubble alternates (player holding the
matching item), the hint shifts to "It looks at your X, then to Y…" form.
Today this applies to:

- `WAITING_FOR_STICK` → `…hint.stick` / `…hint.stick.use_on_flag`
- `WAITING_FOR_WAND_ON_CAMPFIRE` → `…hint.wand_on_campfire` / `…hint.wand_on_campfire.use`
- `WAITING_FOR_WAND_ON_DOOR` → `…hint.wand_on_door` / `…hint.wand_on_door.use`
- `WAITING_FOR_DOOR` → `…hint.door` / `…hint.door.with_item`

Hints MUST NOT telegraph specifics the chicken hasn't yet shown the player.
For example, the no-item door hint says "It seems to want a door…", not
"…in the open gap…" — the player hasn't been led to the gap yet, so
naming it spoils the chicken's pecking demonstration.

**Plain-text fourth-wall fallback.** Every 3rd click on the same beat
(counter resets on beat advance) the hint switches to a bracketed
plain-text imperative — `[Right-click the town flag with a stick]`. The
square brackets emphasize that the game is breaking character. The cycle
then restarts: clicks 4 and 5 are monologue again, click 6 is plain, etc.

The click counter is per-player + per-beat. State change clears all
counters. Off-hand interactAt fires are filtered out so a single user
click increments by exactly one.

## 5. Chicken Behavior (AI Goals)

Goal priorities (lower = higher priority):

| Pri | Goal | Purpose |
|---|---|---|
| 0 | `FloatGoal` | Don't drown |
| 1 | `OpenDoorGoal(this, true)` | Open closed doors when path crosses one, close after |
| 2 | `HelperChickenSunsetChestGoal` | One-shot during `SUNSET_AND_MAP` phase 1 (drops chest) |
| 2 | `HelperChickenBeatPeckGoal` | Walk to the current beat's peck-stand position, look at focal block, peck |
| 3 | `HelperChickenFollowNearFlagGoal` | Trail the player while peck stands down (player not yet holding the matching item) |
| 3 | `HelperChickenWanderNearFlagGoal` | Wander randomly during `SUNSET_AND_MAP` phase 2 (waiting for dusk) |
| 5 | `LookAtPlayerGoal` | Idle look |
| 6 | `RandomLookAroundGoal` | Idle look |

**Key invariants:**

- The chicken is invulnerable (`hurt` always returns `false`); attacks are
  routed to the click-handler hint.
- `GroundPathNavigation.setCanOpenDoors(true)` + `setCanPassDoors(true)`
  lets paths route through the doorway. Without this the chicken orbits
  the room because chicken pathfinding treats door blocks as obstacles.
- **Peck stand position** for campfire-focused beats (`WAND_ON_CAMPFIRE`,
  `VILLAGER_UI`, `FLAG_UI`) is the cell south of the campfire — the
  chicken stands there and looks at the campfire instead of standing
  *in* the fire. Other beats: stand pos = focal pos.
- **Follow goal** activates whenever the arc is active, the player is
  within 16 blocks of the flag, and `SUNSET_AND_MAP` isn't in
  `chestSpawned` mode (where wander takes over). Combined with the
  peck gate, this gives the desired UX: chicken trails the player while
  they fetch the matching item, then walks to the target once they have
  it. Range is ~16 blocks of the flag.

## 6. Empty Town Structure

Defined under `src/main/resources/data/questown/`:

- `structures/empty_town.nbt` — the structure blocks themselves.
- `worldgen/structure/empty_town.json` — placement config, biome tag,
  start_height (`absolute: -4` after the terrain shaper's Y shift).
- `worldgen/template_pool/empty_town/start_pool.json` — projection (rigid),
  no processors.
- `tags/worldgen/biome/has_structure/empty_town_biomes.json` — allowed
  biomes (currently plains, savanna, desert).

### NBT layers (devtool-baked)

Three one-shot devtools live under
`src/main/java/ca/bradj/questown/devtools/` with matching disabled-by-
default JUnit `applyToRealStructure` entry points. Run order matters when
re-baking from scratch:

1. `ChickenScaffoldingNbtEditor` — places campfire, perimeter walls (with
   wall-block + door gaps), and gate fence columns at flag-relative
   offsets from `ChickenScaffoldingLayout`.
2. `EmptyTownGrassThinner` — replaces tall_grass with 1-block grass and
   removes ~50% of remaining grass blocks (deterministic seed).
3. `EmptyTownTerrainShaper` — adds 16-layer air ceiling above the plane,
   3-layer dirt floor below, and a 3-block grass-topped cascade ring
   around the original footprint. Shifts existing payload up by
   `FLOOR_DEPTH=3` to make room for the floor; the structure JSON's
   `start_height.absolute` is set to `-4` to compensate.

### Biome tag

Currently allows `#forge:is_plains`, `#minecraft:is_savanna`, and
`minecraft:desert`. Removed jungle and taiga (tree/hill heavy →
unworkable for a flat plane). Coverage ≈ 20% of land surface.

## 7. Devtools

| Tool | File | Run via |
|---|---|---|
| Scaffolding NBT editor | `devtools/ChickenScaffoldingNbtEditor.java` | `ChickenScaffoldingNbtEditorTest.applyToRealStructure` (un-`@Disabled`, run, restore) |
| Grass thinner | `devtools/EmptyTownGrassThinner.java` | `EmptyTownGrassThinnerTest.applyToRealStructure` |
| Terrain shaper | `devtools/EmptyTownTerrainShaper.java` | `EmptyTownTerrainShaperTest.applyToRealStructure` |

All three write a sibling `.bak` / `.grassbak` / `.terrainbak` file before
mutating, so revert is one `cp`.

## 8. Test Coverage

- `EmptyTownStructureIntegrityTest` — runs against the committed NBT on
  every `./gradlew test`. Asserts: campfire at `flag + CAMPFIRE_OFFSET`,
  terrain layers (dirt > 1000, air > 5000, grass_block > 100 entries).
- Per-tool unit tests on synthetic NBT fixtures.
- `ChickenArcConditionsTest`, `ChickenArcTransitionsTest`,
  `ChickenArcBubblesTest` — pure-function state machine tests.
- `ChickenScaffoldingLayoutTest` — perimeter geometry, rotation
  invariance, sign/chest interior assertion.
- `ChickenArcBlueprintRegistry` (in-game `/_qtdev` test harness) —
  scripted scenarios that drive the arc via real player actions in a
  spawned arena and assert observed-condition firing.

## 9. Open Issues

### 9.1 Floating shelves on hilly biomes

**Symptom:** Even with the terrain shaper baked in and biomes restricted
to plains/savanna/desert, the structure on hilly savanna terrain looks
like a flat shelf cut into the side of the hill, with leftover terrain
blocks floating at the structure's plane elevation, then a square
"carved out" cube above.

**Root cause:** `projection: rigid` + 3-block cascade can't blend with
terrain whose height varies by more than ~3 blocks across the structure
footprint. The structure forcibly places its blocks at fixed Y; world
blocks outside the footprint stay at their natural Y. On a hill, the
structure punches through, leaving:
- Mountain side rising next to the plane (cascade is too short).
- Air carved into the hill *above* the plane (the 16-block ceiling).
- Cave below the plane on the downhill side (no in-NBT fill below the
  cascade ring).

**Options:**

1. **Custom Java `StructureProcessor`** that walks outward from the
   bbox in the world during placement, carving high terrain above the
   plane and filling caves below over a configurable radius. Most
   powerful, requires registering a new processor type.
2. **Bigger cascade + fill-below-cascade in NBT.** Extend cascade to
   16+ cells, add deep dirt columns under each cascade cell. Wasteful
   NBT but no Java code.
3. **Tighter biome restriction.** Drop savanna_plateau, windswept_savanna
   from the tag — keep only the truly flat plains family + flat desert.
   Reduces "hilly biome" hit rate at the cost of structure rarity.

Recommended next step: option 1 (Java processor). The flat-biomes-only
approach has already failed in playtest because savanna and even plains
have minor undulation that the 3-block cascade can't span.

### 9.2 Door beat hint mentions the gap before player has a door

**FIXED 2026-04-26.** The no-item `WAITING_FOR_DOOR` hint now reads "It
seems to want a door…" (no mention of the gap). The with-item variant
"It looks at your door, then to a gap in the wall…" only fires once
the player is carrying a door, by which time the chicken is pecking at
the gap and the player can correlate.

### 9.3 Sleep-via-campfire-wand sometimes doesn't progress to morning

**Symptom:** Player uses wand on lit campfire → screen dims (sleep
starts) → time never advances → player can't wake. `[campfire-sleep]`
diagnostic logs added in `CampfireSleepHandler.beginCampfireSleep` and
`onWake` to capture which branch fires (RIGHT vs LEFT, problem code,
`isDay`/`dayTime` at start and wake). Not yet root-caused.

Vanilla MP single-player should fast-skip after 100 ticks of deep
sleep; for shared-MP this depends on `playerSleepingPercentage`. The
fix must NOT force `setDayTime` server-side because the mod runs in
multiplayer too.

### 9.4 Returning to town with wand-in-hand sometimes shows "wants a stick"

**Symptom:** Player completed stick→flag earlier in the same world,
walked away (chunks unloaded), came back ~10s later in the evening.
Click on chicken: caption says "wants a stick" (i.e., beat is
`WAITING_FOR_STICK`). Wand is still in inventory; `chickenBeatState`
should have been persisted as `WAITING_FOR_WAND_ON_CAMPFIRE` or later.

**Diagnostic in place** (2026-04-26):
- `[chicken-arc] beat advanced X -> Y (...)` logs every transition with
  the observation snapshot.
- `[chicken-arc] click handler: state=… hasMatchingItem=… hasWandInInv=… clickCount=…`
  logs every click.

Awaiting next playtest log dump to determine whether (a) the controller
never sees the wand because `findNearestPlayer` returns null at the
moment the user clicked, (b) the `chickenBeatState` NBT round-trip
loses data, or (c) some other code resets the state. Search for
`setChickenBeatState` showed only forward-only writes
(`COMPLETE`, advance) — no obvious regression site.

### 9.5 Bubble icon centering depends on item type

**FIXED.** Bubble previously rendered icons via `TransformType.HEAD`,
which translates 2D items by `+13/16 Y` but blocks by 0 — block icons
landed in the bottom-left corner of the bubble. Switched to
`TransformType.GUI` with explicit translate to bubble center; both
items and blocks now center.

### 9.6 Door corner placement (historical)

**FIXED.** `DOOR_OFFSET` was at `(6,0,2)` — a corner of the 5×5
perimeter, where doors don't legally go. Moved to `(6,0,4)` (middle
of +x wall). `ChickenScaffoldingLayout.gapOffsets()` now exposes the
intended-empty cells so the NBT editor evicts stale cobble at old gap
positions on re-bake.

## 10. Style / Voice

- **Voice for the chicken's hint text** is internal monologue from the
  player's POV. "It seems to want…", "It looks at…", "It pecks at…".
  Never imperative ("Place a sign here"). Never mechanical jargon.
- **Bracketed plain-text fourth-wall hints** are the explicit fallback
  when the monologue isn't landing — "[Right-click the town flag with
  a stick]". Brackets signal the break.
- **Hint length cap ≤80 chars**, target ~50 for legibility on the
  on-screen text overlay (which wraps narrowly).
- The chicken **does not "nudge"** anything (it's a chicken — it pecks,
  walks, looks).
- Keep hints from telegraphing mechanics the chicken hasn't visually
  demonstrated yet (e.g. don't mention "the gap" before the chicken
  has pecked at it).

## 11. Things Not To Reset Across Sessions

`chickenBeatState`, `chickenSunsetChestSpawned`,
`chickenFirstGatherWorldlySeedsFired`, `chickenStructureRotation`,
`chickenRotationDetected`, `chickenArcForfeit`,
`chickenEverSpawned` — all persisted via `TownFlagTileData` →
`writeTownData` → block-stored NBT.

Transient (intentional reset-on-load): `chickenObservedSleepSinceSunset`,
`chickenObservedVillagerUiOpen`, `chickenObservedFlagUiOpen`,
`chickenObservedSeedsGiven` — single-session observation flags. Reset
on chunk reload is acceptable because the trigger (sleep, open UI,
deliver seeds) is reproducible.
