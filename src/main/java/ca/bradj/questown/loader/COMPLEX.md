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
mod entry split (`Questown` bootstrap + `QuestownMod` + `ItemsInteract`), and
the neutral facade types above. Not yet migrated to the facade, to be handled
as slices:

- **facade's own type references** — a few `Loader`/`LoaderApi`/`NeoLoader`
  signatures still name 1.20.6 packages that moved or were removed
  (`AbstractInventory` → `Container`, `MenuType`, `SoundEvent`).
- **item-handler capability** slice (6 files) — `IItemHandler` /
  `getCapability(ForgeCapabilities.ITEM_HANDLER)` / `LazyOptional` behind
  `Loader.handlerOfEntity/BlockEntity` returning the neutral `ItemHandler`.
- **network** slice (8 files) — `PacketDistributor.send` and the
  `QuestownNetwork.CHANNEL` send paths onto the Neo Payload + `StreamCodec`
  APIs (currently a log-and-skip no-op at `QuestownNetwork.init()`).
- **events** slice (6 files) — `@SubscribeEvent` / `Mod.EventBusSubscriber`
  and `MinecraftForge.EVENT_BUS` behind the neutral core + a Neo bus binder.
- **client rendering** — `@OnlyIn(Dist.CLIENT)` markers (dropped where
  annotation-only) and the hospital-bed `Material`/texture path.

The neutral contract (what `Loader` must offer) is enumerated in
`LoaderApi`. The strangler goal: 1 codebase, any loader / MC.
