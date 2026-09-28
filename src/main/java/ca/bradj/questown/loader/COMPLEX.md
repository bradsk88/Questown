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
  `IPayloadHandler` for `playToClient`/`playToServer`. Main code only sees
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
  (`bus = Bus.MOD`/`Bus.FORGE` unchanged), tick handlers onto `net.neoforged.neoforge.event.tick.ServerTickEvent`
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

The neutral contract (what `Loader` must offer) is enumerated in
`LoaderApi`. The strangler goal: 1 codebase, any loader / MC.
