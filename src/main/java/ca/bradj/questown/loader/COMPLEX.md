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
  allowed. `@EventBusSubscriber` client event wiring belongs here with the
  other binders (`ItemsInteract`, `NeoMouseScrollDispatch`): `QuestownHudEvents`
  (the HUD-over-blur redraw for the Questown screens) and
  `GuiLayoutCheckClientEvents` (the dev gui-lint) moved in from `gui/` on
  2026-10-13 for this reason (strangler grandfather 81 → 79; the bus is
  package-independent, so the move is wiring-only and both were verified live).

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

This is a **WIP checkpoint**, now **compiling (main + test)** and the unit
suite is green (965 tests, 43 skipped; the only red is the deliberate
`ChickenArcHintsTest.todo_…` marker, which always fails by design: it lists
beats that need a loader mod-bus harness a plain JVM cannot give).

**Unit-test harness (verified).** Plain JUnit JVMs have no NeoForge mod-bus
window, and the facade requires a `LoaderApi`: `Loader.api()` falls back to a
`ServiceLoader` lookup of `ca.bradj.questown.loader.LoaderApi` (named in
`src/test/resources/META-INF/services/…LoaderApi`) and resolves to
`loader/TestLoader` — a plain-JVM `LoaderApi` impl (lazy registered holders,
`NeoConfigBuilder` config, `IMenuTypeExtension` menus, no-op networking).
An explicit `Loader.init(...)` always wins, so production is untouched.

**1.20.6 traps resolved here (verify in code, not from this note):**
- The vanilla item/block registries are **defaulted**: `BuiltInRegistries.ITEM.get(unknown)`
  returns AIR, not null — `itemFromId`/`blockFromId` must guard with `containsKey` (both `NeoLoader`
  and `TestLoader` do).
- `minecraft:grass` no longer exists in 1.20.6: the short grass plant is
  `minecraft:short_grass` (`Blocks.SHORT_GRASS`), tall is `minecraft:tall_grass`
  (`half` property). `EmptyTownGrassPruner`/`Thinner` recognize the new name
  (old names kept for legacy palettes); a `Blocks.GRASS`→`Blocks.GRASS_BLOCK`
  port would be wrong — the solid floor block is a different plant.
- Criterion trigger registration (`CriteriaTriggers.register`) goes through
  NeoForge's dynamic registry and is **frozen outside the mod-bus registry
  phase** — fine in `QuestownMod`'s constructor, not in a plain test JVM. Tests
  that only need the object construct it directly (see `FlagCommandCrashReproTest`).
- `Ingredient` round-tripping: `Ingredient.CODEC_NONEMPTY` rejects a tag
  ingredient with zero members ("Item array cannot be empty") — encode with
  `Ingredient.CODEC` instead.

Done and
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

Slice ledger (compile-level work is all done as of this checkpoint; what remains is
runtime verification — a held 1.20.6 hold-run autotest — not more ports):

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
- **network** — **DONE, verified.** All 32 message classes are neutral POJOs with
  wire `encode`/`decode` over `RegistryFriendlyByteBuf` + `Channel.Context` handlers;
  `QuestownNetwork.init()` fills the neutral registry (`Questown.onCommonSetup()`, which runs in
  the `FMLCommonSetup` phase — before NeoForge's `RegisterPayloadHandlersEvent`, so the
  registration order holds). `NeoChannel` adapts to NeoForge: one envelope payload
  (`NeoEnvelope`, `questown:network_message`) whose wire is a **varint message id + the message's
  encoder bytes**; `NeoChannel.onRegisterPayloadHandlers` registers it with one
  `IPayloadHandler`, via `registrar.playBidirectional(...)` — the per-direction
  `playToClient`+`playToServer` pair keys on the same ResourceLocation and the second call
  throws "already registered" in the 1.20.6 registry. Main code only sees
  `QuestownNetwork.sendToPlayer/sendToAll/sendToServer` (all send sites re-pointed).
  **Wire round-trip verified** by `NeoChannelWireTest` (it caught a real bug this session: the
  encode side did not write the id varint the decode side reads first — every message would have
  misrouted/crashed on the wire; fixed + regression-test locked). Note for hold-run: message
  bodies that carry `ItemStack` (e.g. `SyncBlockItemMessage`) use the vanilla
  `ItemStack.STREAM_CODEC`, which needs a live `RegistryAccess`; a plain unit JVM's
  `RegistryAccess.EMPTY` has no item registry ("Missing registry minecraft:item"), so those
  bodies are settled by the live hold-run, not the unit suite.
- **events** — **DONE, compiled + verified.** All listed files ported to the Neo namespaces:
  `net.neoforged.bus.api.SubscribeEvent`, standalone `net.neoforged.fml.common.EventBusSubscriber`
  (`bus = Bus.MOD`/`Bus.GAME` — the 1.20.6 constants are `MOD`/`GAME`, not `FORGE`), tick handlers onto `net.neoforged.neoforge.event.tick.ServerTickEvent`
  (the `Phase` enum is gone in 1.20.6), `PlayerWakeUpEvent` / `ServerLifecycleHooks` /
  `ServerAboutToStartEvent` under `net.neoforged.neoforge.*`. (The one remaining `net.minecraftforge.`
  string in the tree is a dead commented-out sample in `_vanilla/entities/FishingHook.java` — comment only.)
- **journal (Patchouli) drop** — **DONE.** `ServerJobsRegistry` + `PagedCardScreen` neutralized
  (TriPredicate via the neutral `loader.TriPredicate`); the journal-open action is a neutral `mc.Journal`
  facade (`QuestsScreen` → `Journal.openBookEntry(...)`) with the NeoForge impl in `loader.neo.NeoJournal`
  (journal dropped per user — see `.scratch/bring-back-journal`).
- **loot API** — **DONE.** `Loots` + `ResourceJobLoader` on the vanilla 1.20.6 loot API
  (`LootTable`/`LootParams`/`LootContextParamSets` in `net.minecraft.world.level.storage.loot.*`);
  `LootDataManager` is gone in 1.20.6.
- **gui FrameType** — **DONE.** `VillagerAdvancementsWidget` / `VillagerAdvancementsScreen`: 1.20.6 dropped
  the `FrameType` enum; `getFrameType()` now returns the vanilla advancement-sprite row the enum's
  `getTexture()` pointed at (task=10, goal=9, challenge=8).

- **runtime boot (dedicated server)** — **DONE, verified end to end**
  (`runServer -Dquestown.autotest=true -Dquestown.autotest.only=perf/town_large`: `1/1 passed`).
  Findings that are API facts, not one-offs:
  - **Client/common segregation is enforced at class-load time.** The dist-scanner
    (RuntimeDistCleaner) rejects any *class* whose constant pool names an `@OnlyIn(CLIENT)`
    type on a dedicated server — imports in the pool are enough, whether the method runs.
    `Questown` (screen registration, renderer setup, the 12 client screen types), the
    network client send, and `NeoMouseScrollDispatch` moved to a separate
    `questownclient` mod entry (`@Mod(value="questownclient", dist=Dist.CLIENT)` — a distinct
    mod id; a dotted id fails toml validation in 1.20.6). `NeoChannel.sendToServer` is now a
    neutral hook installed by the client island; `LoaderApi.ScreenFactory` returns `Object`
    so the common interface pool carries no `Screen`. `RegisterMenuScreensEvent` was removed
    (client-only in 1.20.6, and screens are registered by the client entry instead).
    Mod entry constructor order is not guaranteed between the two entries, so the client
    entry does nothing that can reach `Loader.api()` in its constructor: the island init
    runs in `FMLClientSetupEvent` `enqueueWork` (always after every mod constructor). The
    menu-screen capture runs at `RegisterMenuScreensEvent` instead: that event fires
    *before* the FMLClientSetupEvent work queue runs, so a capture parked in the queue
    would see an empty pending list and silently drop every menu screen. The client entry
    guards (`pendingScreens().isEmpty()`), so if the queue call does run first the later
    event is a no-op. Running either in the constructor crashed the client with
    "No loader installed" whenever `questownclient` was constructed first.
  - **Event bus classification matters per event, and 1.20.6 changed which side several events
    live on.** A `@SubscribeEvent` on the wrong bus throws "not valid for this busclass" at
    construction — and Neo *rejects* a subscriber class with no `@SubscribeEvent` methods
    outright. Classification is by the event's interfaces: `IModBusEvent` (e.g.
    `EntityAttributeCreationEvent`, `FMLCommonSetup`, `NewRegistryEvent`) → `Bus.MOD`;
    bare `bus.api.Event` (`ServerStartedEvent`, `ServerTick`, `PlayerWakeUp`, `Screen*`,
    `ServerAboutToStartEvent`, `RegisterCommands`, `AdvancementEvent`) → `Bus.GAME`. A mixed
    handler class must be split (see `HelperChickenEntityEvents`); client-event classes take
    `Bus.GAME, value = Dist.CLIENT`.
  - **Criterion triggers are baked at Bootstrap in 1.20.6.**
    `CriteriaTriggers.register(...)` from any mod phase throws "Registry is already frozen",
    and no `RegisterEvent` arrives for the frozen registry. The sanctioned path (Neo 20.6 docs):
    `DeferredRegister.create(Registries.TRIGGER_TYPE, MOD)` + one
    `register(name, Trigger::new)` per trigger, bound in the mod constructor
    (`AdvancementsInit.init`). The trigger classes must be `codec()`-driven (they already were).
  - **The recipe `id` moved onto the holder (1.19.3+).**
    `RecipeManager.getAllRecipesFor(...)` returns `RecipeHolder<T>`; the codec-constructed
    recipe object has a **null** `id` by design. Key maps on `recipe.getId()` build a null-key
    entry (Guava `ImmutableMap` NPEs). `RoomRecipes.hydrate`/`getById` now read the holder id.
  - **Data format changes (1.20.5+ recipe/taxonomy migration).** Single-ingredient objects keep
    `{"item": ...}` / `{"tag": ...}` (NOT the 1.20.5-array form, which the 1.20.6 codec rejects);
    `result` is `{"id", "count"}`. `forge:` tags are gone — use the Neo `c:` conventions
    (`forge:chests`→`c:chests`, `forge:mushrooms`→`c:mushrooms`, which exist in the Neo jar for
    both item and block taxonomies); the removed `minecraft:grass` block becomes
    `minecraft:short_grass` (not `grass_block`); `#forge:is_plains` / `#forge:seeds` have no
    `c:` biome equivalent, so the affected tags enumerate explicit biome/item values.
    `RoomRecipes`' `roomrecipes:room` recipe gained a required `farm` boolean — every Questown
    recipe carries it (`*_farm` rooms true, the rest false).
  - **RoomRecipes boilerplate:** the `@Mod` constructor's `NeoForge.EVENT_BUS.register(this)` on a
    handler-less class is rejected by the Neo bus; removed in the RoomRecipes 1.20.6 branch.

The neutral contract (what `Loader` must offer) is enumerated in
`LoaderApi`. The strangler goal: 1 codebase, any loader / MC.
