# Strangler probe: 1.20.6-wip compiled against 1.19.2

Date: 2026-07-23
Probe branch: `probe/strangler-1.19.2` (throwaway; keep for re-runs)
Method: the wip source tree is unchanged. Only the build was flipped to the golden
1.19.2 setup (Forge 1.19.2-43.3.0, official mappings, `RoomRecipes:1.19.2-0.0.7-alpha.1`
from `~/.m2`, Gradle 7.2 on JDK 17, config taken from `/workspace/IdeaProjects/Questown-119`).
`javac` gave the first 100 errors (its per-run cap), then a source-wide marker sweep for
1.20+-only APIs completed the set. Every marker was checked by hand against the files that
carry it; false positives were dropped (e.g. questown's own `loader/Holder`,
`VillagerAdvancementsWidget`, the visitor-mob `BehaviorControl` package is ours, not JEI's).

## Verdict

The strangler seams are real and used. `mc/Compat`, `mc/Util`, `mc/JEI`, `gui/RenderUtil`,
`gui/RenderContext`, and the `loader/` + `loader/neo/` family already carry part of the
version-specific surface, and 627 of 772 main-source files (81%) carry no version marker
at all. But the "99% of changes live in the strangler files" bar is not met: **~120 files
outside the seams still touch version-specific APIs directly**, in six tight clusters.
The seams cover some call sites per cluster and miss the others, which is the shape of a
partially-applied strangler: each cluster needs its seam finished, not a new one invented.

## Clusters (the missed edges)

Counts are main-source files. "Seam" = the file already owns the version-specific call and
may change during a port; everything listed is a non-seam file that also does it.

### G1. Screen render pipeline (gui/, 29 files)

The 1.20 screen API passes `GuiGraphics` into `render`/`mouseClicked`/`renderWidget`;
1.19.2 passes `PoseStack`. 29 gui files take or call `GuiGraphics` directly. The homegrown
`gui/RenderContext` (a record wrapping GuiGraphics + PoseStack) already exists for the
`GuiGraphics`/`PoseStack` split but the screens bypass it; `mc/Compat` and `gui/RenderUtil`
wrap some helpers (e.g. `Compat.renderItemAndDecorations`) while nearby lines call
`gui.drawString`/`gui.fill` in file-local form. Same cluster: `Button.builder(...).build()`
construction (11 screens; 1.19.2 Button has a protected ctor), the
`setTooltipForNextRenderPass`/`renderTooltip` tooltip API (18 gui files),
`Component.getVisualOrderText()` (6 files), and raw `RenderSystem` calls outside
mc/Util/RenderUtil (10 gui files, incl. `VillagerTabs`, `FlagTabs`, `PagedCardScreen`).

Seam to finish: `gui/RenderContext` becomes the only holder of the render argument for
screens plus a small set of Compat wrappers (button build, tooltip set, component text).
Afterwards the 29 screens should name none of `GuiGraphics`, `Button.builder`,
`renderTooltip`, or `RenderSystem`.

### G2. Block and block-entity definitions (blocks/, _vanilla/, 25 files)

- `BlockBehaviour.Properties.of().mapColor(MapColor.X)` per block (19 blocks; 1.19.2 form
  is `Properties.of(Material.X, MaterialColor.X)`).
- Loot via `LootParams`/`LootPool.Builder` (1.20 loot API; 10 blocks in
  `blocks/buildLootTable` + `jobs/gatherer/Loots`).
- BlockEntity save/load round trips with `HolderLookup.Provider`,
  `handleUpdateTag(CompoundTag, ...)` (1.20 BE API): `TownFlagBlock`,
  `TownFlagBlockEntity`, `HospitalBedBlockEntity`, `FoodDisplayEntity`,
  `BlockAsRoomEntity`.
- `ItemInteractionResult useItemOn` / `InteractionResultHolder` (1.20 renamed enums):
  `TownFlagBlock`, items/StockRequestClipboardItem.

Seam to finish: a `Compat.blockProperties(mapColor)` (or equivalent) so block ctors name
one form, and `Compat`/existing `jobs/gatherer/Loots` owning the loot builder, plus a
`Compat` BE tag-round-trip pair (provider goes inside).

### G3. Items and data components (Component/NBT, 13 files)

`DataComponents`/`CustomData` (1.20 component stack) directly in:
`TownFlagBlock`, `BlockOfProgress`, `RelocationDeedItem`, `StockRequestItem`,
`items/QTNBT`, `items/GathererMap`, `MCTownItem`, `MCHeldItem`,
`jobs/declarative/RealtimeWorldInteraction`, `TimeWarpWorldInteraction`,
`QuestRemoveConfirmScreen`, `core/network/CreateStockRequestFromUIMessage`,
`commands/test/TestBlueprintRegistry`. `mc/Compat` already wraps component access for some
call sites and is the right seam; the 13 files above skip it.

### G4. Registries, holders, recipes (18 files)

- `net.minecraft.core.registries.BuiltInRegistries` (1.19.2: `Registry`): `MCTownItem`,
  `MCHeldItem`, `Ingredients`, `HelperChickenSunsetChestGoal`, `core/RecipeItemConfig`,
  `TownieNames`, `WallDetection`, `WorkRequest`, `WorkRequestSerializer`, `Loots`,
  `ScheduleInit`, `TilesInit`, `CommandsInit`, `AdvancementsInit`,
  `commands/test/TestBlueprintRegistry`, `_vanilla/TreeFeatureResolver`,
  `jobs/ResourceJobLoader`.
- `net.minecraft.core.Holder`/`RegistryAccess`: `TownFlagBlock`, `FoodDisplayEntity`,
  `VisitorMobEntity`, `TownKnownBiomes`, `TownFlagBlockEntity`, `_vanilla/VoidLevel`,
  `TownFlagMenus`, `logic/RoomRecipes`, `TownQuests`, `jobs/ResourceJobLoader`,
  `_vanilla/TreeFeatureResolver`.
- Recipe `assemble(...)` on `RecipeHolder` (1.20 recipe API):
  `jobs/ResourceJobLoader`, `logic/RoomRecipes`, `town/TownFlagMenus`, `TownQuests`.
- `CreativeModeTab.Builder` (1.19.2: icon/String ctor form): `core/init/ModItemGroup`.
- Advancements 1.20 critereon (`ContextAwarePredicate`, `SimpleInstance`):
  `core/advancements/TutorialTrigger` (+ the `core/advancements` and
  `gui/villager/advancements` files that carry trigger predicates).

Seam to finish: registry/holder access already has near-homes in `mc/Compat` and
`logic/RoomRecipes`; a registry-access facade (one lookup entry point) and a recipe-assemble
wrapper in Compat would close this cluster.

### G5. Renderers (blocks/entity/renderer/, render/, 8 files)

`org.joml` imports (1.19.2: `com.mojang.math`) in: the 4 block-entity renderers,
`SpinningCube`, `render/BubbleRenderer`, `render/FishingHookRenderer`,
`TownFlagTicker`. `ItemDisplayContext` (1.19.2: `DisplayType`) in the same 4 renderers
+ `render/BubbleRenderer` + `SpinningCube`.
Per the mission rule, joml import moves are mechanical and may stay inline; the
DisplayType/ItemDisplayContext split is a real API edge and belongs in Compat.

### G6. Loader and event tier (50 files, ~31 outside the loader seam)

`net.neoforged.*` (events, `IItemHandler`/`InvWrapper`, `CommandRegisterEvent`,
items API) is imported outside `loader/` in: `commands/` (TestCommand, TestAllCommand,
CommandInit, test/AutoTestRunner, test/TestBlueprintRegistry), `items/`
(CampfireSleepForgeEvents, CampfireSleepClientEvents), `integration/`
(IntegrationForgeEvents, QuestownSpecialRules), `core/` (AgentMessageBridge,
JobFileEvents, ServerTickProfiler, advancements/AdvancementEvents, init/AdvancementsInit),
`town/` (TownEvents, TownFlags, rooms/DeadDoorBubbles, rewards/RegistryEvents,
entity/TownFlagBlockEntity), `gui/` (LockableInventoryWrapper, QuestownHudEvents,
AbstractVillagerMenu, GuiLayoutCheckClientEvents), `mobs/helperchicken/` (5 files),
`mobs/visitor/` (6 files, incl. VisitorMobEntity which also carries the raw-vanilla-packet
and DamageTypes edges), `world/` (MinecraftWorldAccess, WarpWorldAccess).
In the 1.19.2 golden tree these were bare `net.minecraftforge.*` imports inline; the
`loader/` seam family is a wip-era invention. Either the loader seam extends to event
registration and item-handler slots (most of these call sites), or the port rule explicitly
exempts "loader package move" imports as mechanical (like joml). Decide one way and stop
mixing the two.

### G7. Vanilla payload/packet plumbing (4 files outside the network seam)

`net.minecraft.network.protocol` packet construction in `HospitalBedBlockEntity`,
`_vanilla/entities/FishingHook`, `VisitorMobEntity`, `TownFlagBlockEntity`.
The channel side is seam-finished (`loader/Channel`, `loader/NeoEnvelope`,
`core/network/QuestownNetwork`, `loader/Loader*`); these four build vanilla packets by
hand and would each need per-port edits.

### Tests (4 files)

`TestWorldInteraction`, `TestLoader`, `NeoChannelWireTest`, `TownQuestsLootTest`
carry the same markers; they track whichever seams above are chosen.

## What is already seam-finished (do not re-do)

`mc/Compat`, `mc/Util`, `mc/JEI`, `mc/Journal`, `gui/RenderUtil`, `gui/RenderContext`,
`loader/*`, `loader/neo/*`, `core/network/QuestownNetwork` (21 files). These are the
expected port-diff surface and the probe confirms the rest of the code routes some calls
through them.

## Suggested fix order (on the 1.20 side)

1. G1 RenderContext expansion: biggest file count (29), one seam, mechanical per-screen
   change.
2. G2 block/BE wrappers: one call site each in 25 files, high repeat per port.
3. G3 + G4: route existing scattered calls into Compat; fewest new seams, closes
   `TownFlagBlock`/`TownFlagBlockEntity`/items.
4. G6 decision (seam vs mechanical-exempt) before G5/G7 so the port rule stays one story.
5. Re-run the probe (`probe/strangler-1.19.2` still has the 1.19.2 build config;
   re-point at the fixed tree, recompile, recount) until only the seam family + an agreed
   mechanical list carry version markers.

## Caveats

- Pass-1 javac hit its 100-error cap; the marker sweep fills the rest but was built from
  known 1.19.2→1.20 API deltas. A final re-probe after the fixes is the authoritative pass.
- RoomRecipes (two-repo scope) was probed only via its 1.19.2 jar on the classpath;
  `logic/RoomRecipes.java` + `jobs/ResourceJobLoader` show the questown-side recipe
  surface; RoomRecipes' own port has not been strangler-checked this session.
- The probe build does not run the forge runtime (setup task only); a full 1.19.2 boot of
  the wip tree is a separate follow-up.
- While probing, the dev `runClient` was killed at request; the held 1.20.6 dev
  `runServer` (forgeserveruserdev) was left running.
