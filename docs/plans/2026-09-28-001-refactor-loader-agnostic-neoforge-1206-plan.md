# Plan: Loader-agnostic Questown + RoomRecipes (target NeoForge 1.20.6, Forge 1.19.2 kept as legacy)

Date: 2026-09-28
Status: **DONE — closed out 2026-10-09.** 1.20.6/NeoForge is the current track
(branch `1.20.6-neoforge-wip`); the 1.19.2 Forge branch is the frozen legacy
artifact. Phase 3 (re-derive the legacy build from the facade) stays
intentionally deferred — see Open decisions.

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

- **Phase 0 — RoomRecipes → Neo 1.20.6 (DONE).** Toolchain (NeoGradle, Gradle 8.14,
  Java 21) done; Forge→Neo package rename done; vanilla `Recipe`/`RecipeSerializer`
  1.20.6 API in `RoomRecipe.java` + `RecipeDetection.java` done. Unit tests green,
  `ca.bradj:roomrecipes:1.20.6-neoforge` published to Maven Local (2026-09-28).
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
| 0 | `./gradlew compileJava` (RoomRecipes) green; `./gradlew test` green; `publishToMavenLocal` | verified (2026-09-28: compiled, 10/10 tests green, `roomrecipes-1.20.6-neoforge.jar` in build/libs + released to `~/.m2` |
| 0 | the 1 known-red `todo_…` + the 1 pre-existing `shouldPeckRun` SIGN-beat mismatch remain the only failures (not regressions) | verified (2026-09-28, RoomRecipes suite) |
| 1 | `./gradlew build` (compile + jar + importCheck + stranglerCheck) green against Neo 1.20.6; `./gradlew test` green apart from the 3 deliberate `todo_…` registry-bound markers (ChickenArcHintsTest, ServerJobsRegistryCanFitTest, WorkStatusStoreMatSeedTest — not regressions) | verified (2026-10-09 close-out: 1055 tests, 43 skipped, only those 3 reds; importCheck found 12 unused imports at that commit — fixed by `import-clean apply` and re-run green) |
| 2 | event paths still fire (a held run tick/placement smoke) | verified (2026-10-13 held run: jobs cycled, meal served with `Fullness -1330→5000`, watchtower intact; 2026-10-21 self-sufficiency sweep GREEN on 2 seeds; 2026-10-09 `flag/bop_boundary_63` + `flag/bop_full_64` autotests 2/2 PASS — the BOP deposit path is live, confirming the item-handler capability + events + network all fire) |
| 4 | `gui-lint` oracle on the reworked screens: open in dev client, `[gui-lint] <Screen> … no layout violations` | verified (2026-09-30: wip client log, all six ported screens OK at 634x675 — InventoryAndStatusScreen, QuestsScreen, VillagerStatsScreen, VillagerAdvancementsScreen, VillagerEconomicsScreen, VillagerBlockofProgressScreen) |
| 4 | `tools/porttest` pixel oracle: 6 golden screens. Center-region diff: parent 2.2%, stats 2.8%, economics 1.0%, bop 7.8%, quests 10.6%, skills 22.7%. All residuals are fixture content: skills = different job sets → different widgets + scroll phase. **OPEN (2026-09-30, dev-observed in the live 1.20.6 client, confirmed by side-by-side vision on the banks):** within the central pane, 1.20.6 fails to blit several chrome textures — the skills widget slot frames + `{ }` connectors and the stats tab-strip icons (tool/hammer, leftmost) render as blank squares; the background behind icon areas was reported as a solid near-black (24,15,7) vs light-grey (222,222,222) pane on 1.19.2 — on re-check (2026-09-30) that region is WORLD BACKGROUND seen outside the panel, not a UI defect: the dev capture's own left (1.19.2) half is dark-blue-grey and the right (1.20.6) half is green + a tree trunk, and the stone pane texture renders in both banks. Same texture-atlas/blit-UV class as the already-fixed pane-background bug (`gui/COMPLEX.md`) — the frame member **is fixed now, the background member never was a UI issue**. Oracle blind spot: the manifest center rects (y ≥ 248) can exclude the failing rows, so `run.sh diff` passes on screens with broken chrome — a center-rect pass must not be read as "parity verified" while this is open. Root cause (2026-09-30): `VillagerAdvancementsWidget.getFrameType()` carried hardcoded frame u-coords 10/9/8 from the 1.19.2 vanilla sheet — on the ported bundled sheet (`questown:textures/gui/advancements/widgets.png`, whose frame columns sit at u=1 task / 26 challenge / 54 goal) those landed mid-frame on transparent pixels, so only the item icons and fills drew. Fix: 1/54/26 per the 1.19.2 semantics (unlocked→task, canUnlock→goal, creative→challenge); `Util.blitState()` kept as the documented 1.20.6 render-state parity shim. Verified (2026-09-30): creative-mode capture shows the silver unobtained frames in the pane (pixel evidence) and vision confirms "square slot frames: yes… icons inside frames: yes" on a side-by-side vs the 1.19.2 golden; tab-strip icon patterns match the golden. The `TexDiag` temp diagnostic (source of the bogus `Failed to load texture` FNF log lines — it probes ids without the `.png` extension) removed. **Quests gap closed as fixture content (2026-09-30):** vision-checked both banks one-at-a-time — both render the identical empty state ("Page 1/0" + blank body); menu interior structure and coordinates match (5px-cell maps line up; interior delta is minor AA noise, the only visible mark is a small central + on the wip side). The center band above the menu's bottom edge is world background in the crop (golden: sky + red terrain; wip: grass) — the documented "judge the menu region, not the skybox" residual, same class as bop 14.8%/stats 9.8%. Quest generation on 1.20.6 already confirmed running (live broadcast "New quests are available") → closed as fixture content, not slow-vs-broken. **Tabs claim retrenched (2026-09-30):** the earlier "tab-strip icon patterns match the golden" line was too strong — the dev reported and the pre-fix pixel check confirmed the stats and skills tab icons were blank (their cells held pure tab-base grey, the other four tabs fine). Root cause: the port mistranslated the 1.19.2 `GuiComponent.blit(stack, x, y, z, u, v, w, h, texW, texH)` calls (all-int 10-arg resolves to the `(…, int z, float u, float v, …)` overload; pinned from the 1.19.2 bytecode — `z` is depth, unused by GUI blits, and has no 1.20.6 slot). The golden regions are stats u=0, v=0, 9×9 and skills u=17, v=7, 30×30; the wip called had v=9 (stats) and u=0, v=17, 7×30 (skills), both landing on transparent atlas rows. Fix: the golden regions through `Util.blit` (`gui/VillagerTabs`). **Background-bleed defect (same dev report, fixed 2026-09-30):** `VillagerAdvancementsContent.drawContents` blits the 16px stone-tile loop to ~x456/y421, past the 234×113 pane; the port dropped the 1.19.2 colorMask/depth clip with no 1.20.6 replacement. The clip is `GuiGraphics.enableScissor(x, y, right, bottom)` — the last two args are absolute canvas EDGES, not width/height (1.20.6 bytecode: `ScreenRectangle(x, y, p3 - x, p4 - y)`); passing width/height makes a 34×–172 degenerate rect that clips the entire pane (hit in this session, the pane content simply vanished), so pass `x+234, y+113`. Applied in `VillagerAdvancementsScreen.renderInside` around the pose push/translate. Both verified on the fresh capture: pane interior stone fraction 0.788 (pre-fix bank 0.905, golden 0.864), bleed bands zero (right x444–470 and bottom y408–424: pre-fix 0.857/0.934, golden 0.000, fresh 0.000), stats icon cell 27→colored green pixels and skills cell 36 white/shaded pixels where the bank had 0 in both, and vision confirms all six tab icons, a populated pane, and tile edges stopping at the window frame | resolved (2026-09-30 final: frames + braces fixed; stats/skills tab icons and the background bleed found dev-reported, fixed and pixel-verified on a fresh capture; (24,15,7) re-attributed to world background, not a UI defect; quests closed as fixture content) |
| 5 | held `runServer` autotest with `/tick rate` set, wall-clock measured | **verified (2026-10-13).** `self_sufficiency` held run, client joined (`Dev` near flag), `tick rate 30` via FIFO — server log: `Set the target tick rate to 30.0 per second`. Wall-clock: the periodic 200-tick cycle ran **10.0s/cycle at 20 t/s → ~6.8s/cycle at 30 t/s** (intervals 23:15:26→33→39→46→53; expected 6.67s; log granularity ±0.3s) = **1.43–1.5× speedup, no `Can't keep up` stalls**, town loop healthy (jobs cycled, meal served `Fullness -1330→5000`, watchtower intact). Command verified in bytecode: `net.minecraft.server.commands.TickCommand` group `tick` {`query`,`rate`,`step`,`sprint`,`stop`,`time`,`freeze`,`unfreeze`}, `ServerTickRateManager.setTickRate(float)`; stable since 1.20.3 (`23w43a`), absent pre-1.20.3 (so the kept 1.19.2 line cannot use it — `AUTOTESTS.md` TPS section updated to match) |

## Open decisions / risks (all settled as of 2026-10-09 close-out)

- **Events binding granularity — SETTLED (per-event-family binding classes).** The chosen
  shape: one thin binder class per event class/family in `loader.neo/` (`TownEvents`,
  `VisitorMobEntityEvents`, `HelperChickenEntityEvents`, `NeoTownWakeEvents`,
  `JobFileEvents`, `NeedBubbleClientEvents`, `QuestownHudEvents`,
  `GuiLayoutCheckClientEvents`, `CampfireSleep{Client,Forge}Events`, `RegistryEvents`,
  `TownFlagCapabilityBinder`), with the shared logic in main code. A handler class that is
  mixed mod/game bus must be split (documented in `loader/COMPLEX.md`, e.g. the
  `HelperChickenEntityEvents` split).
- **Network Neo API — SETTLED (used the new `Payload`/`StreamCodec` stack).**
  `NeoChannel` registers ONE envelope payload (`NeoEnvelope`, `questown:network_message`)
  whose `StreamCodec` keys off a varint message id + the per-message encoder bytes, with
  one `IPayloadHandler` via `registrar.playBidirectional(...)` dispatching by the id. All
  32 message classes stayed neutral POJOs over `RegistryFriendlyByteBuf`. Wire round-trip
  verified by `NeoChannelWireTest` (it caught a real encode/decode id-varint mismatch);
  the `ItemStack`-carrying bodies are settled by the live run, not the unit suite
  (they need a live `RegistryAccess`).
- **Source-set vs fork — SETTLED (one project, one source set per branch; no build
  toggle).** The 1.20.6/Neo line is a single main source set split into a neutral
  `loader/` facade + `loader.neo/` impls; a second loader would add `loader.forge/` with
  zero changes to `loader/` or main code (the facade is a standing seam, not a one-off
  port tactic). The 1.19.2 Forge line stays a separate frozen branch (legacy artifact);
  the per-project `-Ploader=neo|forge` toggle idea was dropped as unnecessary.
- **Build environment — SETTLED (per-branch `gradle.properties` pins).** This branch
  pins Gradle 8.14 + `org.gradle.java.home=/usr/lib/jvm/java-21-openjdk-amd64` (Java 21);
  the 1.19.2 branch pins JDK 17 + Gradle 7.2. The two toolchains never fight because they
  live on different branches. (The system default `java`=21 still traps a bare `gradlew`
  on the 1.19.2 branch only — see AGENTS "Build environment".)
- **Reachable mavens (unchanged constraint):** `maven.neoforged.net`,
  `maven.blamejared.com`, `maven.minecraftforge.net`, `dvs1.progwml6.com`. JEI and
  NeoForge-Patchouli resolve on blamejared. (Patchouli is intentionally off this
  classpath — see Phase 4 / the journal note below.)
- **Scale — CLOSED.** Largest piece of the 1.20.6 push, shipped across the sessions that
  logged under the Progress section; closed out 2026-10-09.
- **Journal (Phase 4) — DROPPED BY DEV DECISION (2026-09-23), not lost.** The Patchouli
  book is intentionally absent from 1.20.6; the chicken + onboarding-quests path is the
  preferred learning mechanism. The re-introduction (a homegrown vs. Neo-Patchouli
  book, its content, and the two now-de-coupled Patchouli hooks) lives in
  `.scratch/bring-back-journal/bring-back-journal.md` (status: needs-triage) and is a
  separate, later decision — it is not a close-out gap for this migration.
- **Phase 3 (Forge-legacy re-derive) — INTENTIONALLY DEFERRED.** The existing 1.19.2
  Forge branch remains the legacy artifact by recorded decision; it is frozen and still
  builds. Re-derive from the facade only if that line is revived or needs to track the
  facade; until then this phase is closed-as-deferred, not dropped from scope.

## Progress (session log)

- 2026-09-28 — Questown (Phase 1+2): **Slice 1 (advancement triggers) done, cleared.** Rewrote `TutorialTrigger` / `ApproachTownTrigger` / `VisitorTrigger` / `RoomTrigger` to the 1.20.4+ `SimpleCriterionTrigger` shape (nested `record Instance(Optional<ContextAwarePredicate> player, Triggers …) implements SimpleCriterionTrigger.SimpleInstance`, a `Triggers.CODEC`, `RecordCodecBuilder` instance CODEC, `codec()` override) and `AdvancementsInit` to `CriteriaTriggers.register(id, trigger)`. All four + init drop off the compile-error list. Remaining: item-handler capability, network, events, journal Patchouli drop, loot API. Still not green.

- 2026-09-28 — Questown (Phase 1+2): **WIP checkpoint 2, not green.** Closed out the mechanical/straggler layer on top of checkpoint 1: the facade's own 1.20.6 type refs (`AbstractInventory`→`Container`, `MenuType`→`inventory.MenuType`, `SoundEvent`→`sounds.SoundEvent`), a `biomeKeyOf` facade lookup, plus the straggler files (TownFlagBlock `Tags` drop, TownRoomsMap `Tags`→`ItemTags`, HospitalBedBlock `IForgeBlock` drop, TownQuests/TownKnownBiomes/MCTownItem/UIQuest `ForgeRegistries`→`Loader`), and the client renderers (FishingHook/Bubble `@OnlyIn` markers dropped, hospital-bed `Material` via `net.minecraft.client.resources.model.Material` + `TextureAtlas.LOCATION_BLOCKS`). `loader/COMPLEX.md` now enumerates the six remaining focused slices (advancement triggers, item-handler capability, network, events, journal Patchouli drop, loot API) with their exact file lists. Still does not compile.
- 2026-09-28 — Questown (Phase 1+2): **WIP checkpoint, not green.** Toolchain switched to NeoForge 1.20.6 (NeoGradle 7.1.39 / Gradle 8.14 / Java 21); `settings.gradle` + `neoforge.mods.toml` added, Forge `mods.toml` dropped. Neutral `loader/` facade (Holder, HolderRegistry, Config, MenuFactory, ItemHandler, Channel, Loader/LoaderApi) + `loader.neo/` impls (NeoLoader, NeoHolder/Registry, NeoItemHandler, NeoConfig*, QuestownMod, ItemsInteract) in place. Neutralized to the facade: all `*Init` registries + new `RegistryDefinitions`, Config + RecipeItemConfig, MenuTypesInit, Compat, the mod entry split (Questown bootstrap + QuestownMod). Mechanical batches applied (ForgeRegistries lookups, IForgeBlock, ChunkStatus, FakePlayer, Tags→ItemTags, RegistryObject→Holder, first `@OnlyIn` removals). `src/main/java/ca/bradj/questown/loader/COMPLEX.md` maps the seam. Remaining (documented in that file): the facade's own wrong 1.20.6 type refs (`AbstractInventory`→`Container`, `MenuType`, `SoundEvent`), item-handler capability slice (6 files), network payload slice (8), events slice (6), and client rendering `Material`/`OnlyIn`. Questown does not compile yet — this is a checkpoint commit, not a finished state.
- 2026-09-28 — RoomRecipes (Phase 0): DONE. Migrated to NeoForge 1.20.6 (Gradle 8.14 / NeoGradle 7.1.39, Java 21, `maven.neoforged.net` + `plugins.gradle.org/m2`). Vanilla 1.20.6 API migration (HolderLookup.Provider, getActiveRecipes→getAllRecipesFor, RecipeSerializer.codec/streamCodec). Compiled + 10/10 tests green. Published `ca.bradj:roomrecipes:1.20.6-neoforge` to Maven Local.
  - **Codec idiom (1.20.2+):** `RecordCodecBuilder.mapCodec(instance -> instance.group(fieldA, fieldB, …).apply(instance, (a, b, …) -> new X(a, b, …)))`. `group()` returns a datafixers `Products$Pn` and `.apply(applicative, FunctionN)` builds the App. The `instance.ap3(X::new, …)` / `instance.point(…)` forms fail javac inference ("App is not a functional interface") when the Serializer `implements RecipeSerializer<X>` — avoid them; use `group/apply`. Imports: `com.mojang.serialization.codecs.RecordCodecBuilder` (NOT top-level `com.mojang.serialization.RecordCodecBuilder`); `com.mojang.serialization.MapCodec`. `Recipe.getId()` is gone; `RecipeManager.getActiveRecipes()` is gone (use `getAllRecipesFor(...)` → `List<RecipeHolder<T>>`).

---

## Close-out (2026-10-09)

Closed the migration out on top of the 2026-10-13/10-21 live held-run sweeps (which already
proved the 1.20.6/Neo build is the working primary track):

- **Build gate re-verified fresh (not up-to-date):** `./gradlew build -x test` green after the
  `importCheck` gate caught 12 unused imports at HEAD (fixed with `import-clean apply`);
  `./gradlew test` then re-run: **1055 tests, 43 skipped, only the 3 deliberate `todo_…`
  registry-bound reds** (`ChickenArcHintsTest`, `ServerJobsRegistryCanFitTest`,
  `WorkStatusStoreMatSeedTest`) — no regressions. Ledger rows 0/1/2 promoted to verified.
- **The one real migration-level defect found and fixed (TDD-style via the live BOP autotest as
  red/green):** the flag's BOP item-handler was never exposed as a capability on Neo 1.20.6, so
  `Loader.handlerOf(be)` — the leaver's deposit path (`MinecraftWorldAccess.insertIntoContainer`)
  and the flag's container-change watch — silently degraded (live log evidence: `no item-handler
  capability for TownFlagBlockEntity`, 2026-10-03 held run). Fix, in `loader.neo` per the seam
  rule: a `TownFlagCapabilityBinder` MOD-bus binder registers the flag's neutral
  `bopItemHandler()` to `Capabilities.ItemHandler.BLOCK` via `RegisterCapabilitiesEvent`, wrapped
  in the reverse `TownFlagBOPHandlerBridge` (neutral `ItemHandler` → Neo `IItemHandler`, deposit
  only). `NeoLoader.handler(BlockEntity)` already retrieves via the Neo capability API, so no
  facade change was needed. **Root cause exposed by the fix's first live run:** the deposit
  handler stored the deposit's `copy()` of a consumed (count-0) stack, which on 1.20.6 **is the
  shared `ItemStack.EMPTY` singleton** (`setCount` doesn't reset its item ref), so the flag's BOP
  list picked up a globally-poisoned EMPTY and the flag tick crashed on the next save with
  `IllegalStateException: Cannot encode empty ItemStack` (1.19.2's `save()` tolerated empties, so
  this path was latent there too). Fix at the seam: guard the deposit with `isEmpty()` and skip
  empties defensively in `serializeBOP`. Result: `flag/bop_boundary_63` + `flag/bop_full_64` went
  from crash to **2/2 PASS** — `bopCount 63→64`, `BOP_FULL=true`, the full-flag reject path still
  clears the latch, and zero "no item-handler capability" warnings — exactly the runtime evidence
  the ledger's row 2 was missing.
- **Docs closed to match code:** `loader/COMPLEX.md` — `Channel` row (was "a stub; Neo Payload
  migration pending"), the BOP-capability follow-up (was "not wired"), and a new 1.20.6 trap entry
  (the `EMPTY`-singleton / `save()`-throws-on-empty class) — all now reflect the shipped code.
  `NeoLoader` javadoc — the "Still to complete … the remaining migration slice" note is replaced
  with the implemented state. All "Open decisions" are settled (see that section); Phase 3
  (re-derive the 1.19.2 Forge legacy build from the facade) is **intentionally deferred** — the
  existing 1.19.2 branch is the frozen legacy artifact, so re-deriving is a separate, revivable
  decision, not an open close-out gap. The journal re-introduction is **dropped by dev decision
  (2026-09-23)** and parked in `.scratch/bring-back-journal/` (needs-triage) — not a close-out
  gap either.
