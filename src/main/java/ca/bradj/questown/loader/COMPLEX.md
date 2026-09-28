# loader/ — the loader-agnostic facade

This package separates the project code from the mod loader. Everything under
`loader/` that is **not** in the `neo` sub-package is loader-neutral: it names
only the interfaces the rest of the codebase programs against, and carries no
`net.neoforged` / `net.minecraftforge` import. The `neo` sub-package holds the
NeoForge 1.20.6 implementation that the facade delegates to at runtime. A second
loader (e.g. Forge for the legacy 1.19.2 line) would add `loader.forge` and a
different `LoaderApi` impl, with zero changes to `loader/` or to Questown core.

## The rule

- main code (everything outside `loader/` and `loader.neo/`) must not import
  `net.neoforged` or `net.minecraftforge`. It programs against the neutral
  surface (`holder.get()`, `ConfigValue.get()`, `Loader.itemFromId(...)`,
  `Loader.handlerOfBlockEntity(...)`, ...).
- per-loader code lives in `loader.neo/` (the `@Mod` entry, the `LoaderApi`
  impl, the loader event binders). That is the only place a loader import is
  allowed.

## Type map (neutral → NeoForge)

| neutral (`loader/`)         | NeoForge impl (`loader.neo/`) | wraps                          |
|-----------------------------|-------------------------------|--------------------------------|
| `Holder<T>`                 | `NeoHolder<T>`                | `DeferredHolder<T>` (or a lambda) |
| `HolderRegistry<T>`         | `NeoHolderRegistry<T>`        | `DeferredRegister<T>`           |
| `ConfigValue<T>` / `Spec`   | `NeoConfigValue` / `Spec`     | `ForgeConfigSpec.ConfigValue`   |
| `ConfigBuilder`             | `NeoConfigBuilder`            | `ForgeConfigSpec.Builder`       |
| `MenuFactory<T>`            | (a functional interface)      | `IContainerFactory<T>`          |
| `ItemHandler`               | `NeoItemHandler`              | `IItemHandler`                  |
| `Channel`                   | (a stub; Neo Payload migration pending) | — |
| `Loader` (static entry)     | `NeoLoader` (implements `LoaderApi`) | all of the above |

`Loader` is the single static entry point; it holds the active `LoaderApi`
(set once at mod init by the loader entry) and forwards each call.

## Bootstrap sequence

```
@Mod(QuestownMod)
  Loader.init(new NeoLoader())            # set the impl
  Questown.registerConfigs()              # Config.build() via Loader.configBuilder()
  Loader.defineRegistries()               # RegistryDefinitions.define() then bind()
    RegistryDefinitions.define()          # calls each HolderRegistry.define() → HolderRegistry.register() (creates the DeferredRegister + holds)
    NeoLoader.bind(IEventBus)             # for each registry, deferred.register(bus)
  Questown.onCommonSetup()                # network + job init (neutral statics)
QuestownMod.fmlCommonSetup                # Loader.fmlCommonSetup() (no-op in Neo)
QuestownMod.clientSetup (client)          # Questown.onClientSetup() (menus, renderers, ...)
```

## Status / pending slices

This is a **WIP checkpoint** (the tree does not yet compile). Done and
compiling to the facade: registries (`*Init` + `RegistryDefinitions`), config
(`Config` + `RecipeItemConfig`), menus (`MenuTypesInit`), `Compat` helpers, the
mod entry split (`Questown` bootstrap + `QuestownMod` + `ItemsInteract`), the
neutral facade types above, the facade's own 1.20.6 type references
(`Container` / `MenuType` / `SoundEvent`), a `biomeKeyOf` lookup, all the
mechanical registry/dist/straggler files (TownFlagBlock, TownRoomsMap,
HospitalBedBlock, TownQuests, TownKnownBiomes, MCTownItem, UIQuest), and the
client renderers (FishingHook/Bubble `@OnlyIn` markers dropped, hospital-bed
`Material` on 1.20.6), the **advancement triggers** (slice 1 — DONE, below), and the
**item-handler** slice (slice 2 — DONE, below: BOP handler + consumers neutral, menus
re-pointed to Neo `SlotItemHandler`/`InvWrapper`, the Forge `getCapability` override
removed), and **tool-actions** (slice 3 — DONE, below: Neo `ToolAction(s)` re-point +
`RecipeHolder`/`AbstractFurnaceBlockEntity.getFuel()`) (see the slice bullets).

Remaining slices (each a focused pass; file lists are the compiler's surface):

- **advancement triggers** (4 + init) — **DONE, cleared.** `TutorialTrigger`,
  `ApproachTownTrigger`, `VisitorTrigger`, `RoomTrigger` + `AdvancementsInit` now use the
  1.20.4+ shape: a `Triggers.CODEC` (`Codec.STRING.xmap`), a nested
  `record Instance(Optional<ContextAwarePredicate> player, Triggers …) implements
  SimpleCriterionTrigger.SimpleInstance` with a `RecordCodecBuilder` CODEC (player via
  `EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player")`), a `codec()` override,
  and `CriteriaTriggers.register(id, trigger)` (2-arg). `Triggers.fromJSON`/`getID()` kept.
- **item-handler capability** (~7 files) — **DONE for compile.** TownFlagBOPItemHandler implements the neutral ItemHandler; TownFlagState + MinecraftWorldAccess consume via Loader.handlerOf(BlockEntity) (was getCapability + LazyOptional); AbstractVillagerMenu + LockableInventoryWrapper re-point to Neo SlotItemHandler/InvWrapper(Container); TownFlagBlockEntity dropped the LazyOptional<IItemHandler> field + getCapability override (removed in 1.20.6) and exposes a neutral bopItemHandler().
  **Follow-up (strangler end-state):** the runtime provider/consumer path is not wired — Neo 1.20.6 removed BlockEntity.getCapability, so a working BOP handler needs (a) a loader.neo binder registering the flag's bopItemHandler() via RegisterCapabilitiesEvent.registerBlockEntity(Capabilities.ItemHandler.BLOCK, FLAG type, ...) wrapped in a loader IItemHandler, and (b) the neutral facade gaining a handlerAt(Level, BlockPos) (level-based retrieval via level.getCapability(...)), since the BE-only handlerOf(BlockEntity) can't find a registered BLOCK capability. At this checkpoint the consumers return Optional.empty and degrade gracefully.
- **tool-actions** (~2 files) — **DONE, cleared.** MinecraftWorldAccess + WarpWorldAccess both drove hoe/axe/shovel via `BlockState.getToolModifiedState(UseOnContext, ToolAction, boolean)` with `net.minecraftforge.common.ToolAction(s)`. That `getToolModifiedState` is a **NeoForge patch onto vanilla `BlockState`** in 1.20.6 (vanilla `HoeItem` calls it with `net.neoforged.neoforge.common.ToolActions`), so the fix was an import re-point — `forge` → `neoforged.neoforge.common.ToolAction(s)` — not a rework. WarpWorldAccess also: `getRecipeFor` now returns `RecipeHolder<T>` (resolver uses `r.recipe()`), and burn time moved from the removed `ForgeHooks.getBurnTime` to the vanilla `AbstractFurnaceBlockEntity.getFuel()` table (Map<Item,Integer>).
- **network** (~10 files) — `TownMessages`, `TownFlagMenus`, `mc/Util`,
  `StockRequestClipboardItem`, `RelocationDeedItem`, `PlateBlockEntity`,
  `FoodDisplayEntity`, `AxeRackBlock`, and the `core/network/*.java` message
  classes (still `PacketDistributor` + `Dist.CLIENT`). Onto Neo
  `Payload`/`StreamCodec`; the send paths are currently log-and-skip no-ops at
  `QuestownNetwork.init()`.
- **events** (~11 files) — `AutoTestRunner`, `HelperChickenEntityEvents`, `QuestownSpecialRules`,
  `IntegrationForgeEvents`, `CampfireSleepForgeEvents`, `CampfireSleepClientEvents`, `CommandInit`,
  `TestCommand`, `TestAllCommand`, `GuiLayoutCheckClientEvents`, `TownFlagBlockEntity`. Mapping confirmed
  against the Neo jars: `SubscribeEvent` → `net.neoforged.bus.api.SubscribeEvent`; `@Mod.EventBusSubscriber`
  → standalone `@net.neoforged.fml.common.EventBusSubscriber` (`Bus.FORGE`/`MOD` unchanged); the `Phase` enum
  is GONE — tick handlers now target abstract base + nested `Pre`/`Post` (`ServerTickEvent.Post`,
  `ClientTickEvent.Post`); `FMLCommonSetupEvent` → `net.neoforged.fml.event.lifecycle`; `MinecraftForge` →
  `NeoForge`; `ServerAboutToStartEvent`/`PlayerWakeUpEvent`/`ServerLifecycleHooks`/`ScreenEvent`/`EntityEvent`
  re-point to `net.neoforged.neoforge.*`. (End-state: neutral core + `loader.neo` bus binder.)
- **journal (Patchouli) drop** — `ServerJobsRegistry` + `PagedCardScreen` **DONE** (TriPredicate →`neutral
  loader.TriPredicate`). Remaining: `QuestsScreen` line ~497 `PatchouliAPI.get().openBookEntry(...)` — the
  journal-open action; with Patchouli out of the classpath it becomes a no-op/log (journal dropped per user).
  See `.scratch/bring-back-journal`.
- **loot API** (2 files) — `Loots` + `ResourceJobLoader`: `LootDataManager` is gone in 1.20.6, map onto the
  vanilla 1.20.6 loot API (`LootTable`/`LootPool`/`LootContext`).
- **gui FrameType** (2 files) — `VillagerAdvancementsWidget` + `VillagerAdvancementsScreen` import
  `net.minecraft.advancements.FrameType` (`TASK`/`GOAL`/`CHALLENGE`, `.getTexture()`); it moved in 1.20.x —
  not yet located, needs the 1.20.6 name before mapping.

The neutral contract (what `Loader` must offer) is enumerated in
`LoaderApi`. The strangler goal: 1 codebase, any loader / MC.
