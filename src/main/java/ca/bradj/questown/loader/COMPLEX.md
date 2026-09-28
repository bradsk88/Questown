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
`Material` on 1.20.6).

Remaining slices (each a focused pass; file lists are the compiler's surface):

- **advancement triggers** (4 + init) — `TutorialTrigger`,
  `ApproachTownTrigger`, `VisitorTrigger`, `RoomTrigger` +
  `AdvancementsInit`. The 1.20.4+ trigger rework replaces
  `getId()`+`createInstance(JsonObject, ContextAwarePredicate,
  DeserializationContext)`+`AbstractCriterionTriggerInstance` with
  `SimpleCriterionTrigger.SimpleInstance` (nested interface, `player()` →
  `Optional<ContextAwarePredicate>`, custom `matches(...)`), a `codec()` that
  returns an instance `Codec` (RecordCodecBuilder, player via
  `EntityPredicate.ADVANCEMENT_CODEC`), and registration via
  `CriteriaTriggers.register("questown:<id>", new XxxTrigger())` (2-arg id).
- **item-handler capability** (~7 files) — `MinecraftWorldAccess`,
  `WarpWorldAccess`, `TownFlagState`, `AbstractVillagerMenu`,
  `TownFlagBlockEntity`, `TownFlagBOPItemHandler`, `LockableInventoryWrapper`
  — replace `IItemHandler` / `getCapability(ForgeCapabilities.ITEM_HANDLER)` /
  `InvWrapper` / `LazyOptional` with the neutral `ItemHandler` +
  `Loader.handlerForInventory/handlerOfBlockEntity`; the Neo
  `RegisterCapabilities` event stays a `loader.neo` binder, consumed via the
  facade.
- **network** (~10 files) — `TownMessages`, `TownFlagMenus`, `mc/Util`,
  `StockRequestClipboardItem`, `RelocationDeedItem`, `PlateBlockEntity`,
  `FoodDisplayEntity`, `AxeRackBlock`, and the `core/network/*.java` message
  classes (still `PacketDistributor` + `Dist.CLIENT`). Onto Neo
  `Payload`/`StreamCodec`; the send paths are currently log-and-skip no-ops at
  `QuestownNetwork.init()`.
- **events** (~8 files) — `AutoTestRunner`, `QuestownSpecialRules`,
  `IntegrationForgeEvents`, `CampfireSleepForgeEvents`,
  `CampfireSleepClientEvents`, `CommandInit`, `TestCommand`, `TestAllCommand`,
  and the `TownFlagBlockEntity` event. Replace `@SubscribeEvent` /
  `Mod.EventBusSubscriber` / `TickEvent` / `FMLCommonSetupEvent` /
  `ServerAboutToStartEvent` with a neutral core + a `loader.neo` bus binder
  (Neo `RegisterEventListener` + its event types).
- **journal (Patchouli) drop** (1 file) — `ServerJobsRegistry` imports
  `vazkii.patchouli.api.TriPredicate`; replace with a neutral
  `TriPredicate<T,U,V>` functional interface (or three `Predicate`s). See the
  `.scratch/bring-back-journal` TODO.
- **loot API** (2 files) — `Loots` and `ResourceJobLoader` use the pre-1.20.4
  `LootTable` / `LootDataManager` / `LootDataType` shapes; `LootDataManager` is
  gone in 1.20.6, so map onto the vanilla 1.20.6 loot tables API.

The neutral contract (what `Loader` must offer) is enumerated in
`LoaderApi`. The strangler goal: 1 codebase, any loader / MC.
