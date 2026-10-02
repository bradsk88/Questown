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

Shipped: the SAFE-tier confirm screens (Work, AddWork, FlagCrafting, JobChangeConfirm, the
Block-of-Progress pair, CreateStockRequest, QuestRemoveConfirm) are on `CompatScreen` /
`CompatContainerScreen` hooks, and the render-argument helpers `IngredientRenderer` /
`StatusGraphic` / `Tabs` now take `RenderContext` directly (verbatim draw calls; callers
pass `ctx`). Grandfather 93 → 87, gate green; the gate also ignores `.md` files (prose
names markers on purpose), dropping `gui/COMPLEX.md` from the list. `VillagerStatsScreen` and `InventoryAndStatusScreen` followed onto
`CompatContainerScreen` (the last absorbs the blurred-background override; InventoryAndStatus
also routes `Button.builder` through `Compat.button` and the status-tooltip
`getVisualOrderText` map through `Compat.componentText` + `showTooltip`). `ItemJobsScreen` and `PagedCardScreen` then landed
off the render argument together (the delegate's public surface + `CardRenderer` onto
`RenderContext`, the leaf onto `CompatScreen` - which also fixed the 1.20.6
panel-under-second-background draw-order regression; see `gui/COMPLEX.md`); grandfather
93 → 85, gate green. `Ingredients` then landed (`render` onto `RenderContext`, the
direct callers pass `ctx`/`rc`, and the three `Registries.ITEM` tag constructions route
through the new `Compat.itemTag(ResourceLocation)` seam - see `gui/COMPLEX.md`);
grandfather 85 → 84, gate green. The `villager/advancements` trio then landed (the
screen onto `CompatScreen` with the blurred background in `renderScreenSurface` and
content in `renderScreenContent`; the content + widget files onto `RenderContext`;
see `gui/COMPLEX.md`); grandfather 84 → 81, gate green. The `gui/` client event
subscribers (`QuestownHudEvents` HUD-over-blur redraw, `GuiLayoutCheckClientEvents`
dev gui-lint) moved into the `loader/neo` seam (package-independent bus, both
verified live) — grandfather 81 → 79, gate green. The last two `gui/` neoforged
files: `AbstractVillagerMenu`'s item-handler surface (`IItemHandler`/
`SlotItemHandler`/`InvWrapper`) was replaced by vanilla `Slot(container,
index, x, y)` over the original `Container`/`Inventory` (its custom
quickMoveStack only uses `Slot.set/get/mayPlace`, never the handler's
`insertItem`/`extractItem`), and the zero-reference `LockableInventoryWrapper`
was deleted — grandfather 79 → 77, gate green, `gui/` now leak-free. Frontier
is the G2–G7 clusters (MapColor blocks, LootParams, data-components,
registries, renderers, the remaining `net.neoforged` surfaces).

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

MapColor part shipped: `mc/CompatBlockColor` (a version-neutral enum) + `Compat.blockProps(CompatBlockColor)`.
The enum is named *Block*Color, not `MapColor`, because the gate's bare `MapColor` marker would substring-match a
`CompatMapColor` type in a leaf (and keep it flagged). 7 constant block leaves de-grandfathered (SaplingTester,
AxeRack, FalseDoor, FalseWall, FishingStation, FoodDisplay, Mineshaft); grandfather 107 -> 100, gate green.
Remaining G2 mapColor: HospitalBedBlock (a *dynamic* `state -> MapColor` lambda, a different shape) + the 10
`LootParams` blocks (BlacksmithsTable, BowlRack, BreadOven, JobBoard, OreProcessing, Plate, SeedBin, SmallSoupPot,
SoupPot, WelcomeMat) which also carry `LootParams`, plus BlockOfProgress (data-components) and TownFlagBlock
(HolderLookup/ItemInteractionResult/data-components) — the multi-marker files that need their other seams first.

### G3. Items and data components (Component/NBT, 12 files)

`DataComponents`/`CustomData` (1.20 component stack) directly in:
`TownFlagBlock`, `BlockOfProgress`, `RelocationDeedItem`, `StockRequestItem`,
`items/QTNBT`, `items/GathererMap`, `MCTownItem`, `MCHeldItem`,
`jobs/declarative/RealtimeWorldInteraction`, `TimeWarpWorldInteraction`,
`core/network/CreateStockRequestFromUIMessage`,
`commands/test/TestBlueprintRegistry`. `mc/Compat` already wraps component access for some
call sites and is the right seam; the files above skip it. (`QuestRemoveConfirmScreen` is resolved: its
`ResolvableProfile`/`DataComponents.PROFILE` player head moved to `Compat.resolvablePlayerHead`
in `mc/Compat`; no longer a marker, off the grandfather list, gate green.)

Data-components part shipped: `Compat.itemDataTag(ItemStack)` + `Compat.setItemDataTag(ItemStack, CompoundTag)`
own the 1.19.2 "arbitrary item NBT" surface (the `DataComponents.CUSTOM_DATA` read/write; 1.19.2 held that NBT
in the stack's own `CompoundTag`). Named `*DataTag`, not *CustomData*, for the bare-name-marker substring
collision above. 7 clean leaves de-grandfathered (RelocationDeedItem, StockRequestItem, QTNBT, GathererMap,
MCHeldItem, RealtimeWorldInteraction, TimeWarpWorldInteraction); grandfather 100 -> 93, gate green. Remaining G3:
the multi-marker files (MCTownItem + TestBlueprintRegistry also carry `net.minecraft.core.registries`;
TownFlagBlock + BlockOfProgress also carry `MapColor`/`ItemInteractionResult`/`core.Holder`) and
`core/network/CreateStockRequestFromUIMessage`, which is already in the `core/network/` seam family so was
never a gate hit.

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

G5 shipped (2026-10-13): `Compat.renderItemStaticGui(...)` owns the GUI display-context
render (the helper passes identical `renderStatic` arguments to the old call site, so the
four leaves are behavior-identical), and the gate's `org.joml` marker was removed as
mechanical (precedent comment in `tools/strangler-check` beside the renderTooltip removal).
Eight lines de-grandfathered: AxeRack / FoodDisplay / HospitalBed / Plate renderers,
SpinningCube, BubbleRenderer, FishingHookRenderer, TownFlagTicker - 77 -> 69, gate green,
`compileJava`+`compileTestJava` executed. The two G5 test files (TestWorldInteraction,
TownQuestsLootTest) still carry G4 registry markers and stay grandfathered for the
registry facade.

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

G6 decision (2026-10-13, per `loader/COMPLEX.md` "The rule"): the loader seam owns all
loader imports - main code programs the neutral `loader/` surface, binders move to
`loader/neo` (the QuestownHudEvents / GuiLayoutCheckClientEvents precedent). Two passes
landed: (1) the one mechanical-exception class - `net.neoforged.api.distmarker`
(@OnlyIn/@Dist) survives in the 1.19.2 golden in the same leaves with only the package
renamed, so the marker narrows to `net.neoforged.(bus|fml|neoforge)` and the seven
distmarker-only leaves de-grandfather (69 -> 62); (2) the nine pure event binders
(TownEvents, IntegrationForgeEvents, VisitorMobEntityEvents, RegistryEvents, JobFileEvents,
HelperChickenEntityEvents, ServerTickProfiler, CampfireSleepClient/ForgeEvents) move
whole into `loader/neo` (git-mv + package line + same-package sibling imports restored),
and TownFlags' `OnWake` handler splits into the new `NeoTownWakeEvents` binder delegating
to the owner's new public `TownFlags.onSleepFinished(long)` - the wiring is registration-
identical (@EventBusSubscriber is package-independent), so behavior is unchanged;
FoundationRegistrationTest's `Class.forName` FQN updated for the moved
HelperChickenEntityEvents. 62 -> 52, gate green, `compileJava`+`compileTestJava` green;
then round 2: QuestownSpecialRules' rule list becomes the neutral public
`registerRules()` + new `NeoSpecialRulesInit` binder (class-level
@EventBusSubscriber dropped), and NeedBubbleClientEvents / AgentMessageBridge move whole
(git-mv + NeedBubbleFocus sibling import) - 52 -> 49, same verification; round 3:
the two world-access implementations' Forge tool surface collapses into
`Compat.toolModifiedState(state, useOnContext, QTToolAction)` (the private
`forgeToolAction` switch moved into it - the call is arg-identical; the vanilla,
marker-free `toolForAction`/`toolContext` stay local) - 49 -> 47, same verification.

Note (pre-existing, not changed by this pass): `ServerTickProfiler`'s instance
`@SubscribeEvent` handlers have no bus attachment in either era (no annotation, no
addListener; the NeoLoader comment says "join the same pattern in the events slice" -
never done). The move preserves that exact wiring, so the gap is carried forward, not
created.

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

Live as of the fix pass that this report drives: the strangler gate (tools/strangler-check, gradle stranglerCheck, wired to check) enforces the rule - the current 122 main + 2 test leak files are grandfathered in tools/strangler-grandfather.txt and must shrink to zero as the clusters below are fixed. A new file outside the seams that carries a 1.20+-only API fails the build.

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
