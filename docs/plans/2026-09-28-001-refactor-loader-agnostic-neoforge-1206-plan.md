# Plan: Loader-agnostic Questown + RoomRecipes (target NeoForge 1.20.6, Forge 1.19.2 kept as legacy)

Date: 2026-09-28
Status: approved design; Phase 0 in progress (RoomRecipes → Neo 1.20.6)

## Why

The immediate want is a held autotest that runs faster in wall-clock time: that is
vanilla `/tick rate <float>` (snapshot `23w43a`, stable 1.20.3+). It is not in 1.20.1,
so the working version must be ≥ 1.20.3 to get it.

Independent of that one want, the user stated two durable constraints that decide the
loader and shape of this work:

1. **"We'll need to migrate to NeoForge eventually anyway if we want to keep up with MC
   versions."** NeoForge is the community's forward line (the 1.21+ era is NeoForge; the
   Forge line is not the future they will track). So the *heading* is NeoForge.
2. **"Can we use the strangler pattern to centralize forge/neo-forge imports?"** — i.e.
   make the loader (forge↔neo) a *seam* like the codebase already treats
   version-specifics, so "keep up with MC" is a bounded re-shim + one more loader impl,
   not a rewrite.

Plus the standing goal: **keep the mod easily back-portable** — concretely, keep a
**1.19.2 Forge build** alive for 1.19.2 (Forge is the only loader with a 1.19.x line;
NeoForge stops at 1.20.2).

## Decisions (recorded)

- **(B) Full loader abstraction** the loader surface behind a facade, not a per-file
  loader package, so one source compiles across loader + MC.
- **Target NeoForge 1.20.6** for the new work (primary build; gets `/tick rate`).
- **Events** = **shared core + per-loader binding** (a thin per-loader class subscribes
  via the loader's `@SubscribeEvent` + event class and delegates to a shared method
  handler). Not a re-implementation of MC's event system.
- **Dist markers** (`@OnlyIn`, 32 call sites) → an explicit `isClient()` facade check,
  except on fields/methods that are already client-bound.
- **1.19.2 legacy** = **keep the existing Forge 1.19.2 branches (both repos) as the
  legacy artifacts**, not re-derived from the facade now. (Re-derive later only if the
  facade is needed to keep them in sync.)

## Design

Extend the existing strangler — `ca.bradj.questown.mc.Compat`, explicitly "centralize
everything that changes between Minecraft versions to minimize forward/back-porting" —
from **vanilla-version-specific** to **loader-agnostic (forge↔neo) + version-specific**.

- **Shared (main source set):** all loader-agnostic logic, including the network payload
  classes (they already encode/decode on the vanilla `net.minecraft.network.FriendlyByteBuf`,
  not a loader type).
- **Per-loader source set:** the loader-specific parts — the `@Mod` entry, `mods.toml` /
  `neoforge.mods.toml`, access transformers, `Compat`'s loader body, the channel setup,
  and the event bindings.
- **Neutral types** so core code stops importing `net.minecraftforge.*` / `net.neoforged.*`:
  `Holder<T>`, `EventBus`, `ConfigValue<T>`, an item-handler view, a `MessageCodec`.
- **Build:** one Gradle project; a `neo` source set (primary, 1.20.6) and a `forge`
  source set (legacy, 1.19.2); a `-Ploader=neo|forge` flag picks the deps + toml +
  source set. (Mechanism finalized in Phase 4; a per-loader `@Mod` + toml is the floor.)

### The seam (loader surface)

| Concern | Shared (main) | Per-loader |
|---|---|---|
| Registries (`ForgeRegistries`/`DeferredRegister`/`RegistryObject`, ~200 refs) | `Holder<T>` + `Registries.register(...)` | the holder/`DeferredRegister` impl, registry keys |
| Item handlers (`IItemHandler`, 16) | logic operating on a neutral view | view accessors |
| Network (51) | payload classes encode/decode on vanilla `FriendlyByteBuf` (shared) | channel setup + dispatch (Neo Payload API vs Forge `SimpleChannel`) |
| Events (`@SubscribeEvent` 21 files + event classes) | shared handler methods | thin per-loader binding classes |
| Dist (`@OnlyIn`, 32) | `isClient()` facade check | — |
| Config (`ForgeConfigSpec`) | `ConfigValue<T>` | spec building |
| `@Mod` + toml + AT | — | per-loader (two thin `@Mod`s, one toml each) |

## Phases

- **Phase 0 — RoomRecipes → Neo 1.20.6 (IN PROGRESS).** Toolchain (NeoGradle, Gradle 8.14,
  Java 21) done; Forge→Neo package rename done; remaining = vanilla `Recipe`/`RecipeSerializer`
  1.20.6 API in `RoomRecipe.java` + 1 spot in `RecipeDetection.java`. Then unit tests +
  `publishToMavenLocal`. RoomRecipes is the dependency Questown compiles against — it must
  land first.
- **Phase 1 — Questown facade + Neo impl.** Introduce the neutral types; split `Compat`
  into the loader-agnostic face + a Neo impl; re-route the scattered `net.minecraftforge.*`
  call sites onto the facade; Questown compiles against **NeoForge 1.20.6**.
- **Phase 2 — Events + dist.** Refactor the `@SubscribeEvent` handlers to shared core +
  per-loader binding; `@OnlyIn` → `isClient()`.
- **Phase 3 — Forge-legacy impl (optional, deferred).** Re-derive the 1.19.2 build from the
  facade. Default: keep the existing Forge 1.19.2 branch as the legacy artifact (decided),
  so this phase is only needed if the legacy branch drifts.
- **Phase 4 — Build toggle + journal drop.** `-Ploader=neo|forge` (or per-loader `@Mod` +
  toml); **drop the journal** (the 4 Patchouli files — `QuestsScreen`, `PagedCardScreen/
  AbstractPagedCardScreen`, `ServerJobsRegistry`, `AdvancementEvents`) and reimplement the
  GUI on a non-Patchouli base, against Neo 1.20.6. (See `.scratch/bring-back-journal/`.)
- **Phase 5 — Verify.** A held `runServer` autotest with an explicit `/tick rate 30` (or
  equivalent) to *measure* the real-time speedup — the actual point of the migration.

## Vocabulary → code map

- `Compat` (seam) → `ca.bradj.questown.mc.Compat` (existing), extended; `ca.bradj.questown.mc.Util` beside it.
- `Holder<T>` (neutral registry holder) → new type in `mc/` (replaces `RegistryObject`/`DeferredHolder` leaking).
- `EventBus` (neutral) → new type (replaces `IEventBus` leaking in signatures).
- `ConfigValue<T>` (neutral) → new type (replaces `ForgeConfigSpec.ConfigValue`).
- `MessageCodec` / channel → `ca.bradj.questown.core.network` (payload classes shared; channel dispatch per-loader).
- `isClient()` (neutral dist check) → new method in `Compat` (replaces `@OnlyIn`).
- RoomRecipes seam → its 4 loader-bound classes (entry, `RecipesInit`, `RoomRecipe` condition, `LevelRoomDetector` TriPredicate).

## Test ledger

| Phase | Verification | Status |
|---|---|---|
| 0 | `./gradlew compileJava` (RoomRecipes) green; `./gradlew test` green; `publishToMavenLocal` | in progress |
| 0 | the 1 known-red `todo_…` + the 1 pre-existing `shouldPeckRun` SIGN-beat mismatch remain the only failures (not regressions) | pending |
| 1 | `./gradlew compileJava` (Questown) green against Neo 1.20.6; `./gradlew test` green | pending |
| 2 | event paths still fire (a held run tick/placement smoke) | pending |
| 4 | `gui-lint` oracle on the reworked screens: open in dev client, `[gui-lint] <Screen> … no layout violations` | verified (2026-09-30: wip client log, all six ported screens OK at 634x675 — InventoryAndStatusScreen, QuestsScreen, VillagerStatsScreen, VillagerAdvancementsScreen, VillagerEconomicsScreen, VillagerBlockofProgressScreen) |
| 4 | `tools/porttest` pixel oracle: 6 golden screens. Center-region diff: parent 2.2%, stats 2.8%, economics 1.0%, bop 7.8%, quests 10.6%, skills 22.7%. All residuals are fixture content: skills = different job sets → different widgets + scroll phase. **OPEN (2026-09-30, dev-observed in the live 1.20.6 client, confirmed by side-by-side vision on the banks):** within the central pane, 1.20.6 fails to blit several chrome textures — the skills widget slot frames + `{ }` connectors and the stats tab-strip icons (tool/hammer, leftmost) render as blank squares; the background behind icon areas is a solid near-black (24,15,7) where 1.19.2 has light-grey (222,222,222) pane + frames. Same texture-atlas/blit-UV class as the already-fixed pane-background bug (`gui/COMPLEX.md`) — a second, unfixed member. Oracle blind spot: the manifest center rects (y ≥ 248) can exclude the failing rows, so `run.sh diff` passes on screens with broken chrome — a center-rect pass must not be read as "parity verified" while this is open. Root cause in the wip blit path not yet found. **Quests gap closed as fixture content (2026-09-30):** vision-checked both banks one-at-a-time — both render the identical empty state ("Page 1/0" + blank body); menu interior structure and coordinates match (5px-cell maps line up; interior delta is minor AA noise, the only visible mark is a small central + on the wip side). The center band above the menu's bottom edge is world background in the crop (golden: sky + red terrain; wip: grass) — the documented "judge the menu region, not the skybox" residual, same class as bop 14.8%/stats 9.8%. Quest generation on 1.20.6 already confirmed running (live broadcast "New quests are available") → closed as fixture content, not slow-vs-broken | open (chrome-blit failures; quests closed) |
| 5 | held `runServer` autotest with `/tick rate` set, wall-clock measured | pending |

## Open decisions / risks

- **Events binding granularity** — per-event-class binding class (cleaner, more classes) vs
  one binding class per event "family" (fewer classes). Decide in Phase 2.
- **Network Neo API** — NeoForge 1.20.2+ moved to `Payload`/`StreamCodec` (the old
  `SimpleChannel` is deprecated/removed in newer Neo). The Neo impl must use the new API;
  the Forge impl keeps `SimpleChannel`. **RISK: highest** — this is the one subsystem
  where the two loaders differ most.
- **Source-set vs fork** — one project with two source sets (decided direction) vs two
  parallel branches. Confirm the exact Gradle mechanics in Phase 4.
- **Build environment:** MC 1.20.6 requires **Java 21** (present: `/usr/lib/jvm/java-21-openjdk-amd64`)
  and **Gradle 8.14** (downloaded). Forge 1.19.2 legacy uses Java 17 / Gradle 7.2. A per-profile
  toolchain is needed so the two don't fight.
- **Reachable mavens (unchanged constraint):** `maven.neoforged.net`, `maven.blamejared.com`,
  `maven.minecraftforge.net`, `dvs1.progwml6.com`. JEI + NeoForge-Patchouli resolve on blamejared.
- **Scale:** this is the largest piece of the whole 1.20.6 push — multi-session. Phase 0 is
  small; Phases 1+2 are the bulk.

## Progress (session log)

- 2026-09-28 — Questown (Phase 1+2): **Slice 1 (advancement triggers) done, cleared.** Rewrote `TutorialTrigger` / `ApproachTownTrigger` / `VisitorTrigger` / `RoomTrigger` to the 1.20.4+ `SimpleCriterionTrigger` shape (nested `record Instance(Optional<ContextAwarePredicate> player, Triggers …) implements SimpleCriterionTrigger.SimpleInstance`, a `Triggers.CODEC`, `RecordCodecBuilder` instance CODEC, `codec()` override) and `AdvancementsInit` to `CriteriaTriggers.register(id, trigger)`. All four + init drop off the compile-error list. Remaining: item-handler capability, network, events, journal Patchouli drop, loot API. Still not green.

- 2026-09-28 — Questown (Phase 1+2): **WIP checkpoint 2, not green.** Closed out the mechanical/straggler layer on top of checkpoint 1: the facade's own 1.20.6 type refs (`AbstractInventory`→`Container`, `MenuType`→`inventory.MenuType`, `SoundEvent`→`sounds.SoundEvent`), a `biomeKeyOf` facade lookup, plus the straggler files (TownFlagBlock `Tags` drop, TownRoomsMap `Tags`→`ItemTags`, HospitalBedBlock `IForgeBlock` drop, TownQuests/TownKnownBiomes/MCTownItem/UIQuest `ForgeRegistries`→`Loader`), and the client renderers (FishingHook/Bubble `@OnlyIn` markers dropped, hospital-bed `Material` via `net.minecraft.client.resources.model.Material` + `TextureAtlas.LOCATION_BLOCKS`). `loader/COMPLEX.md` now enumerates the six remaining focused slices (advancement triggers, item-handler capability, network, events, journal Patchouli drop, loot API) with their exact file lists. Still does not compile.
- 2026-09-28 — Questown (Phase 1+2): **WIP checkpoint, not green.** Toolchain switched to NeoForge 1.20.6 (NeoGradle 7.1.39 / Gradle 8.14 / Java 21); `settings.gradle` + `neoforge.mods.toml` added, Forge `mods.toml` dropped. Neutral `loader/` facade (Holder, HolderRegistry, Config, MenuFactory, ItemHandler, Channel, Loader/LoaderApi) + `loader.neo/` impls (NeoLoader, NeoHolder/Registry, NeoItemHandler, NeoConfig*, QuestownMod, ItemsInteract) in place. Neutralized to the facade: all `*Init` registries + new `RegistryDefinitions`, Config + RecipeItemConfig, MenuTypesInit, Compat, the mod entry split (Questown bootstrap + QuestownMod). Mechanical batches applied (ForgeRegistries lookups, IForgeBlock, ChunkStatus, FakePlayer, Tags→ItemTags, RegistryObject→Holder, first `@OnlyIn` removals). `src/main/java/ca/bradj/questown/loader/COMPLEX.md` maps the seam. Remaining (documented in that file): the facade's own wrong 1.20.6 type refs (`AbstractInventory`→`Container`, `MenuType`, `SoundEvent`), item-handler capability slice (6 files), network payload slice (8), events slice (6), and client rendering `Material`/`OnlyIn`. Questown does not compile yet — this is a checkpoint commit, not a finished state.
- 2026-09-28 — RoomRecipes (Phase 0): DONE. Migrated to NeoForge 1.20.6 (Gradle 8.14 / NeoGradle 7.1.39, Java 21, `maven.neoforged.net` + `plugins.gradle.org/m2`). Vanilla 1.20.6 API migration (HolderLookup.Provider, getActiveRecipes→getAllRecipesFor, RecipeSerializer.codec/streamCodec). Compiled + 10/10 tests green. Published `ca.bradj:roomrecipes:1.20.6-neoforge` to Maven Local.
  - **Codec idiom (1.20.2+):** `RecordCodecBuilder.mapCodec(instance -> instance.group(fieldA, fieldB, …).apply(instance, (a, b, …) -> new X(a, b, …)))`. `group()` returns a datafixers `Products$Pn` and `.apply(applicative, FunctionN)` builds the App. The `instance.ap3(X::new, …)` / `instance.point(…)` forms fail javac inference ("App is not a functional interface") when the Serializer `implements RecipeSerializer<X>` — avoid them; use `group/apply`. Imports: `com.mojang.serialization.codecs.RecordCodecBuilder` (NOT top-level `com.mojang.serialization.RecordCodecBuilder`); `com.mojang.serialization.MapCodec`. `Recipe.getId()` is gone; `RecipeManager.getActiveRecipes()` is gone (use `getAllRecipesFor(...)` → `List<RecipeHolder<T>>`).
