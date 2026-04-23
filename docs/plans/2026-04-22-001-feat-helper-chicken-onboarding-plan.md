---
title: Helper Chicken Onboarding
type: feat
status: active
date: 2026-04-22
origin: docs/brainstorms/2026-04-22-helper-chicken-onboarding-requirements.md
---

# Helper Chicken Onboarding

## Overview

Replace the Patchouli-journal-driven active sequencer of Phase 0–1 onboarding with a silent helper chicken entity that spawns beside worldgen-placed town flags, guides the player through wand creation → campfire → room → gate via speech-bubble mimery + environmental scaffolding, introduces the villager UI and flag UI as the post-chicken teachers, and transforms into a Stone Chicken Statue after the player delivers a **Worldly Seeds** item (produced exclusively by the first gatherer's first fetch). Journal entries stay in the game as silent reference; they are no longer handed out on first visit.

This plan implements the full requirements doc. It is cross-cutting: one new custom entity extending vanilla `Chicken`, one new custom renderer with a world-space speech-bubble layer (distance-proximity visibility + through-walls variant), one new block, one new item, flag BE state extensions via `writeTownData`, expanded wand behavior (light unlit campfire + flag-radius gate on sleep), a first-gatherer loot override, an advancement-event surgical edit, structure NBT augmentation, and two commands. The chicken is **invulnerable in v1**; aggressive-revenge variant is explicitly deferred per the origin doc.

---

## Problem Frame

The current Patchouli journal is a reading-UI that interrupts a world-building game at exactly the moment a first-time player is forming their impression of the mod. The author's diagnosis and the considered alternatives (auto-open, reduced-entries, floating arrow, diegetic signs) are documented in the origin; none solved the core problem of a guide that feels *alive*. The chicken is the chosen bet: diegetic, nonverbal, emergent-feeling guidance that hands the player off to the villager UI and flag UI once the village is "alive enough" to take over. Success criteria are qualitative (playtest feedback leans charming/alive/delightful) with the author as primary oracle.

See origin: `docs/brainstorms/2026-04-22-helper-chicken-onboarding-requirements.md`.

---

## Requirements Trace

The plan implements these origin requirements. Where an origin requirement has sub-letters (`R4a`, `R6b`, `R7b`), the letter is preserved.

- R1, R2, R3, R4, R4b. Chicken lifecycle + per-flag state + statue as decorative trophy + all-game-modes spawn
- R5, R6, R6b, R7, R7b. Chicken movement (adapted TemptGoal + flag-radius predicate), beat targets via hardcoded offsets, despawn/respawn matching villagers, observable-condition transitions, out-of-order beat acceptance
- R8, R9, R10, R11. Speech-bubble rendering + distance-proximity + through-walls exception; sprite inventory + alternation cadence + dynamic item-icons + interaction grammar; `Util.onScreenText` for text
- R12, R13. Curriculum scope (Phase 0–1 + UI handoff + closing); `empty_town.nbt` augmentation
- R14. Wand gains unlit-campfire-light behavior; flag-radius gate on both sleep and light paths
- R15, R16, R24. First-gatherer first-fetch Worldly Seeds guarantee; chicken pecks container + through-walls bubble until player delivers; Worldly Seeds is a new custom item
- R17. Chicken invulnerable in v1 (revenge deferred per origin Scope Boundaries)
- R20, R21. Patchouli book no longer handed to player on first visit; entries still exist and unlock as silent reference
- R22, R23. `skip-chicken` flag-placement command argument; `/questown chicken remove` admin command

**Origin actors:** A1 (New Player), A2 (Helper Chicken), A3 (First Villager — structurally a gatherer), A4 (Returning Player).

**Origin flows:** F1 (First Arrival), F2 (Evening Ritual), F3 (Village Construction), F4 (Handoff and Closing Beat), F5 (Chicken Safety and Removal).

**Origin acceptance examples:** AE1, AE4, AE5, AE6, AE7 (AE2, AE3 were cut with R17-R19 in the brainstorm review).

---

## Scope Boundaries

### Deferred for later

[Carried from origin — product/version sequencing. Work that will be done eventually but not in v1.]

- Custom chicken entity model and bespoke animation; vanilla chicken model is the placeholder for v1.
- Bespoke art and geometry for Stone Chicken Statue; **placeholder model/texture/blockstate/loot table are required for ship** (Forge registration requirement) but any simple stone-textured chicken geometry is acceptable for v1.
- 3-strike aggressive revenge mechanic (per-player kill counter, aggressive variant on 4th kill, permanent flag abandonment). In v1 the chicken is invulnerable (R17).
- v4 extended tutorial phases remain quest-driven; chicken has no role in Phase 1.5+.
- Optional narrative flourishes (chicken reactions outside the curriculum).
- Advanced chicken AI tuning beyond vanilla `Chicken` + the adapted `TemptGoal`.
- Localization of bubble text interpretations beyond English.
- "Move flag" feature (GitHub issue #199) — if it ships, multi-town chicken/statue handling may need revisit.

### Outside this product's identity

[Carried from origin — positioning rejection. Adjacent product the plan must not accidentally build.]

- Pet/companion minigame: chicken is not tameable, evolvable, or persistent beyond the closing beat.
- Boss-fight minigame: any future aggressive variant is a moral consequence, not a tuned combat encounter.
- Full-game tutor: chicken covers Phase 0–1 + UI handoff, then turns to stone.
- Fetch-quest NPC: no to-do list; every prompt is a single next action contextual to world state.
- Replacement for ALL journal entries: lesson-style reference content stays in the journal.
- Multiplayer party mechanic: co-op behavior is best-effort from the single-chicken-per-flag model (per origin).

### Deferred to Follow-Up Work

[Plan-local — implementation work intentionally split into separate effort.]

- **Villager UI and Flag UI low-text guidance enhancements.** The chicken's F4 handoff beats introduce these two UIs with the expectation they carry low-text next-action guidance. That enhancement is a distinct brainstorm + plan. This plan flags the dependency but does not implement the UI changes. If the UI enhancement work is not done, the chicken's handoff lands on UIs that aren't ready to take over — the chicken arc itself still ships, but the handoff feels incomplete until the UI work follows.

---

## Context & Research

### Relevant Code and Patterns

- **Entity registration — `src/main/java/ca/bradj/questown/core/init/EntitiesInit.java`.** `DeferredRegister<EntityType<?>>`; pattern: `ENTITIES.register(ID, () -> EntityType.Builder.of(ctor, MobCategory.CREATURE).sized(w, h).build(rl))`. `VisitorMobEntity` is the only existing custom entity — it extends `PathfinderMob` (Brain-based). The helper chicken will be the first custom entity extending a vanilla mob; attribute supplier can override `Chicken.createAttributes` or omit to inherit.
- **Entity attributes — `src/main/java/ca/bradj/questown/mobs/visitor/VisitorMobEntityEvents.java`.** `@Mod.EventBusSubscriber(bus = MOD)` with `@SubscribeEvent` on `EntityAttributeCreationEvent`. Follow for helper chicken attribute registration.
- **Renderer registration — `src/main/java/ca/bradj/questown/Questown.java:114-117` (client-setup).** `event.enqueueWork(() -> EntityRenderers.register(HELPER_CHICKEN.get(), HelperChickenRenderer::new))`.
- **Custom layer reference — `src/main/java/ca/bradj/questown/mobs/visitor/SpinningCubeLayer.java`.** Only existing custom layer. Builds billboard-ish quads manually with `VertexConsumer`. Same scaffolding (push pose, translate above head, fullbright, custom verts) applies to the bubble layer — but swap to a front-facing quad billboarded via `EntityRenderDispatcher.cameraOrientation()` and use `ItemRenderer.renderStatic` for item-icon rendering.
- **Billboard + see-through pattern** (external reference): MCA Reborn's chat bubble renderer and Buuz135's Emojiful emoji layer use the exact billboard + `NO_DEPTH_TEST` pattern. 1.19.2 note: use `com.mojang.math.Quaternion` (NOT JOML, which arrived in 1.19.3).
- **Item-icon world-space rendering:** `Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemTransforms.TransformType.GUI, packedLight, NO_OVERLAY, poseStack, bufferSource, 0)`. `TransformType.GUI` gives flat sprite-card rendering; `FIXED` is item-frame appearance.
- **Block registration — `src/main/java/ca/bradj/questown/core/init/BlocksInit.java`.** `DeferredRegister<Block>`. Simple decorative pattern: `FalseWallBlock` (`src/main/java/ca/bradj/questown/blocks/FalseWallBlock.java`) extends `Block` with `VoxelShape`, `Properties.of(Material.X, ...)`, override `getRenderShape`/`getShape`. Paired `BlockItem` entry in `ItemsInit.java`. Assets at:
  - `src/main/resources/assets/questown/blockstates/<id>.json`
  - `src/main/resources/assets/questown/models/block/<id>.json`
  - `src/main/resources/assets/questown/models/item/<id>.json`
  - `src/main/resources/assets/questown/textures/blocks/<id>.png`
  Loot table at `src/main/resources/data/questown/loot_tables/blocks/<id>.json` (pattern not currently used in-repo; establish per this plan).
- **Item registration — `src/main/java/ca/bradj/questown/core/init/items/ItemsInit.java`.** `DeferredRegister<Item>`. Items with no recipe (admin/advancement-granted): `TownWand`, `StockRequestItem`, `KnowledgeMetaItem` — pattern-match for Worldly Seeds.
- **Item-hand-to-entity interaction:** `ItemsInit.onInteractBlock` (`ItemsInit.java:143-172`) is a `PlayerInteractEvent.RightClickBlock` subscriber pattern. For Worldly-Seeds-on-chicken, handle via `Item.interactLivingEntity` directly (simpler; no subscriber).
- **Command registration — `src/main/java/ca/bradj/questown/commands/CommandInit.java`.** `@SubscribeEvent on(RegisterCommandsEvent)`. Pattern for subcommand tree with optional args: `FlagCommand.java` + `TestCommand.java`. Creative-gate via `.requires(AddExperienceCommand::isCreative)`. For `skip-chicken` argument: add `.then(Commands.literal("skip-chicken"))` branch alongside existing executes on `FlagCommand`. For `/questown chicken remove`: new literal tree.
- **Loot override sites — `src/main/java/ca/bradj/questown/jobs/gatherer/Loots.java:32-90`.** Called from `ResourceJobLoader.java:690,721`, `ExplorerWork.java:68,101,143`, `GathererUnmappedShovelWorkFullDay`, `GathererUnmappedNoToolWorkQtrDay`, etc. Not a single chokepoint. Narrowing: first villager is structurally a gatherer, so the override site is the no-tool gatherer's `ResultGenerator` lambda.
- **Flag BE state — `src/main/java/ca/bradj/questown/town/entity/TownFlagBlockEntity.java:282-354`.** `saveAdditional` is **broken** (documented in source). Use `writeTownData` via `TownFlagTileData.InitPair` (`src/main/java/ca/bradj/questown/town/entity/TownFlagTileData.java:41-57`). `InitPair(fromTag BiFunction, onPlace Consumer)` registered in `initialize()` under an NBT key.
- **Advancement suppression — `src/main/java/ca/bradj/questown/core/advancements/AdvancementEvents.java:78-82`.** The `"root"` branch (`addItem(getBookStack)`) is isolated from the `openBookEntry` branch at line 88. Surgical edit: gate the `addItem` call behind a condition (e.g., `false` for always-off, or a new `questown.disable-journal-on-first-visit` flag).
- **Wand + campfire — `src/main/java/ca/bradj/questown/items/TownWand.java:68-71`** routes to `CampfireSleepHandler.beginCampfireSleep`. `CampfireSleepHandler.java:33-83` early-returns on unlit (message `message.wand.campfire.not_lit`), then runs `TownCycle.findCampfire(parent.getBlockPos(), level)` which is already a flag-registration check. So "flag-radius gate on sleep" already exists for the sleep path — the novelty is the unlit-light path, which is missing today and must share the same gate. Extinguish-on-wake is preserved; F2's re-light case reuses the new unlit-light path.
- **Flag pressure-plate conversion — `src/main/java/ca/bradj/questown/blocks/TownFlagBlock.java:235-237`.** Unchanged by this plan. `TownFlagBlock.use()` is called at lines 413-437 → delegates to `convertItemInHand` (161-308), a flat sequence of if-blocks.
- **First-visit trigger — `src/main/java/ca/bradj/questown/town/entity/TownFlagBlockEntity.java:476-489`** fires every tick the player is within ~10 blocks of a flag. The advancement only grants once per player, but the trigger itself isn't a single-shot. Spawn gate must use a per-flag `chicken-ever-spawned` BE bit, NOT the advancement itself.
- **Util.onScreenText — `src/main/java/ca/bradj/questown/mc/Util.java:375-384`.** Sends `OnScreenTextMessage` → `ClientAccess.showHint` → `Minecraft.getInstance().gui.setOverlayMessage(...)`. Confirmed action-bar delivery.
- **Structure NBT editing — `src/main/resources/data/questown/structures/empty_town.nbt`.** Binary gzipped NBT, loaded via the jigsaw system (`data/questown/worldgen/structure/empty_town.json`, `worldgen/structure_set/empty_town.json`, `worldgen/template_pool/empty_town/start_pool.json`). **No bespoke tooling in repo.** Established workflow (not documented): `/give @s minecraft:structure_block` → load `questown:empty_town` → modify → save under same name → copy exported `.nbt` from `saves/<world>/generated/minecraft/structures/` to `src/main/resources/data/questown/structures/empty_town.nbt`. Alternate: NBTExplorer external tool.

### Institutional Learnings

- **`TownFlagBlockEntity` is an intentional God object.** `docs/project-qa.md:203-219` makes this architectural choice explicit ("the entire game should live inside one flag so it can be serialized as one 'game file'"). The plan's choice to park all 4 new bits on the flag BE matches the project's deliberate architecture. In-source note "TRY NOT TO ADD MORE FUNCTIONALITY TO THIS ENTITY" — state bits are fine; **behavior belongs elsewhere** (a `ChickenArcController` driven by the BE's bits, not methods on the BE itself).
- **"100 unit tests, 0 integration tests" — fake implementations hide real bugs.** `docs/project-qa.md:140-179`, `docs/project-qa-2.md:80-90`. The chicken's state machine (R7/R7b) and the R15 loot-guarantee path are exactly the shapes of logic that "pass mocks but break live." Prefer integration tests using `TestWorldAccess` + `@BeforeAll Bootstrap.bootStrap()` pattern from `src/test/java/ca/bradj/questown/jobs/special/FarmerRuleTest.java`. Per `CLAUDE.md`: do NOT simulate core Questown logic; at an untestable seam, write a failing assertion naming the gap rather than mocking.
- **Warp-vs-realtime parity must be considered explicitly.** `docs/project-qa-2.md:52-76`, `docs/decision-declare-and-collect-global-rules.md`, `docs/bugs/warp-ignores-night.md`. The chicken entity itself is realtime-only (player must be present), but **two effects cross the warp boundary**: (a) R15 first-gatherer loot guarantee can resolve during warp; (b) R14 wand-on-unlit-campfire must behave identically whether reached through realtime or warp-driven code paths. Explicit acceptance of warp behavior is required in implementation units that touch these.
- **Closed Java enums over strings.** `docs/decision-qttoolaction-closed-enum.md` + `MEMORY.md` "Forge ToolAction names ≠ constant names". The chicken's beat state and bubble icon inventory are fixed lists — persist enums by `.name()` with defensive `valueOf` + forfeit fallback on unknown values (save migrations). Dynamic block-item icons (R9) pass through as `ItemStack`/`ResourceLocation`; the enum only covers the authored sprite inventory.
- **Integration-test substrate (established):** `src/test/java/ca/bradj/questown/world/TestWorldAccess.java` + `FarmerRuleTest.java` use `SharedConstants.tryDetectVersion()` + `Bootstrap.bootStrap()` in `@BeforeAll` to load MC item/sound types without a server. Renderer + goal AI + entity lifecycle remain in-game-test territory (`/_qtdev` framework per `MEMORY.md`).

### External References

- **MCA Reborn** (`https://github.com/Luke100000/minecraft-comes-alive`): `mca.client.render.layer.*` — chat bubbles above villagers using billboard + `textSeeThrough` recipe. Closest public reference for the exact pattern we need.
- **Emojiful / Buuz135** (`https://github.com/Buuz135/Emojiful`): tiny MIT-licensed mod; renders emoji sprites above players via `RenderPlayerEvent.Post` using `entityCutoutNoCull`. Good minimal reference for billboard item-icon rendering.
- **Custom NPCs (Noppes)** (`https://github.com/Noppes/CustomNPCs`): speech bubbles with per-NPC state + alternating-icon behavior; 1.19.x branch.
- Forge 1.19.2 docs — `RenderType.textSeeThrough(tex)` is the ready-to-use through-walls variant; custom `RenderType` via `RenderType.create(...)` with `NO_DEPTH_TEST` composite state is the recipe for the bubble's through-walls exception.

---

## Key Technical Decisions

- **Helper chicken extends vanilla `Chicken`** (not `PathfinderMob` + Brain like `VisitorMobEntity`). Rationale: simpler for a chicken-shaped mob; `TemptGoal` extraction is ~40 lines; Brain reimplementation would be much larger. First extension of a vanilla mob in this codebase; establishes the pattern.
- **Speech bubble is an `EntityRenderLayer` on `HelperChickenRenderer`**, not a separate overlay entity or particle. Reuses the `SpinningCubeLayer`/`VisitorArrowLayer` pattern local to this repo. Item-icon content is rendered via `ItemRenderer.renderStatic(stack, TransformType.GUI, ...)`; dynamic block/item icons pass through without pre-authored sprites.
- **Through-walls rendering is a second `RenderType`**, not a separate layer. Default path uses `entityCutoutNoCull`; through-walls path uses a custom `RenderType` with `NO_DEPTH_TEST` + `WriteMask.COLOR_WRITE` (depth writes off to avoid breaking other transparent geometry). The layer selects per-frame based on the flag BE's "Worldly Seeds in any town container" observation.
- **Beat state is a closed Java enum** (`ChickenBeatState`), persisted as `.name()` in flag BE NBT. Bubble icon inventory is a parallel enum (`ChickenBubbleIcon`). Unknown values on load fall back to `FORFEIT` — safe-by-default.
- **Beat target positions are structure-local `BlockPos` constants rotated at runtime via `BlockPos.rotate(Rotation)`.** `HelperChickenBeatOffsets` holds offsets in the structure's local frame (authored alongside `empty_town.nbt`). At first-tick of a worldgen-placed flag BE, the detector records the placement rotation as `chicken-structure-rotation` (one of `NONE`/`CLOCKWISE_90`/`CLOCKWISE_180`/`COUNTERCLOCKWISE_90`). Runtime lookups call `localOffset.rotate(rotation)` and add `flagPos`. (Verified via javap of the 1.19.2 mapped Forge jar: `BlockPos.rotate(Rotation)` is a live instance method. `StructureTemplate.transform(BlockPos, Mirror, Rotation, BlockPos)` is the alternative if mirror support is ever needed, but v1 does not need mirror handling.) If rotation detection fails (anchor missing or ambiguous), the flag is force-forfeited (`chicken-arc-forfeit=true`). Alternative (NBT structure-diffing at runtime) was considered and rejected as over-engineered; alternative (rotation-lock the jigsaw start pool) was considered and rejected because 1.19.2 jigsaw provides no clean JSON-level rotation lock.
- **Spawn gate is a BE bit (`chicken-ever-spawned`) on the flag**, checked alongside `chicken-arc-forfeit == false` AND `chicken-rotation-detected == true` before spawn. `ApproachTownTrigger.FirstVisit` is NOT used as the spawn signal — that trigger fires every tick while a player is in range. Instead, a new `HelperChickenSpawnController.tick(flag)` runs in the existing `TownFlagBlockEntity.tick` path (a real per-tick method, distinct from the one-shot `EnteringSection` Forge event listener) with the same distance-to-player gate used by the first-visit check. Rotation detection (`HelperChickenRotationDetector.detectIfNeeded`) runs on the same tick before spawn-gate check, so the sentinel is true by the time the gate fires.
- **Worldgen-vs-command distinction done at the command path, not at `setPlacedBy`.** `TownFlagBlock.setPlacedBy` is NOT a reliable hook for jigsaw-placed blocks — in 1.19.2 `StructureTemplate.placeInWorld` calls `ServerLevelAccessor.setBlock` which bypasses `setPlacedBy` entirely. Instead, `FlagCommand.java` pre-sets `chicken-ever-spawned=true` AND `chicken-rotation-detected=true` on command-placed flags (both the default branch and the `skip-chicken` branch). Worldgen-placed flags have no such hook; their first tick runs the rotation detector and marks `chicken-rotation-detected=true` there. A single BE bit (`chicken-ever-spawned`) serves the "arc has run or is ineligible" role; the sentinel bit (`chicken-rotation-detected`) serves the "detection has attempted at least once" role.
- **Chicken invulnerability** is implemented by overriding `hurt(DamageSource, float)` to return `false` unconditionally in v1. Peck animation still plays (cosmetic). Revenge mechanics (R17-R19 original) are NOT implemented; the override point is preserved for a v1.1 restoration.
- **First-gatherer loot override** hooks the no-tool gatherer's `ResultGenerator` at `GathererUnmappedNoToolWorkQtrDay` (or equivalent first-fetch class). Guarded by `first-gather-worldly-seeds-fired` BE bit + `chicken-ever-spawned` BE bit (only fires when arc is active). Fires once per town. Must work in both realtime and warp paths — verified by placing the guard at the loot-generation site that both paths funnel through.
- **Advancement suppression** is a single-branch gate: the `"root"` branch at `AdvancementEvents.java:80` becomes conditional (`if (journalDisabledByChickenArc) skip` — effectively permanent-off in v1). Subsequent `openBookEntry` calls are not changed. Assumes Patchouli handles the no-book-in-inventory case gracefully; if not, a secondary change adds an inventory-check gate on those calls.
- **Commands namespace.** `FlagCommand` uses `/qt flag ...`. New command tree uses `/questown chicken remove <pos>`. The existing flag-placement command gains `skip-chicken` as an optional argument.
- **Bubble alternation cadence** is `(entity.tickCount / 20) % 2` — hard-cut every 20 ticks (1s); tool-first then target-second. `tickCount`-based instead of `ageInTicks` to avoid partial-tick interpolation producing a sub-tick flicker.
- **Distance-proximity visibility** is a simple `dispatcher.distanceToSqr(entity) < 16*16` guard in the layer's `render`. The crosshair-gating in vanilla `EntityRenderer.renderNameTag` is NOT reached by a custom layer, so "no crosshair needed" is automatic. Nametag attribute (`ForgeMod.NAMETAG_DISTANCE`) is not touched.

---

## Open Questions

### Resolved During Planning

- **Entity base class:** extend vanilla `Chicken` (decided above).
- **Bubble rendering approach:** layer renderer with billboard + `ItemRenderer.renderStatic` + custom `RenderType` for through-walls (decided above).
- **Beat state enum:** 14 canonical names (`WAITING_FOR_STICK`, `WAITING_FOR_WAND_ON_CAMPFIRE`, `SUNSET_AND_MAP`, `WAITING_FOR_WALL_BLOCK`, `WAITING_FOR_DOOR`, `WAITING_FOR_WAND_ON_DOOR`, `WAITING_FOR_SIGN`, `WAITING_FOR_CHEST`, `WAITING_FOR_PRESSURE_PLATE`, `WAITING_FOR_VILLAGER_UI`, `WAITING_FOR_FLAG_UI`, `AWAITING_WORLDLY_SEEDS_DELIVERY`, `COMPLETE`, `FORFEIT`) — U1 enum and U6 state-machine use these identically.
- **Beat state storage:** closed enum `.name()` in flag BE via `TownFlagTileData.InitPair` (decided above).
- **Structure offsets and rotation:** authored in structure-local frame, rotated at runtime via `BlockPos.rotate(Rotation)` using the flag BE's persisted `chicken-structure-rotation`. Detected at **first-tick** by `HelperChickenRotationDetector` scanning the 4 candidate anchor-block positions — NOT at `setPlacedBy`, which does not fire for jigsaw-placed blocks in 1.19.2. No rotation-lock on the jigsaw start pool (1.19.2 doesn't provide a clean JSON-level lock).
- **Worldgen-vs-command detection:** command-placed flags are pre-set `chicken-ever-spawned=true` + `chicken-rotation-detected=true` by `FlagCommand.java` at placement time. Worldgen flags have no placement hook; the first-tick rotation detector runs on them and either sets the rotation + detected sentinel (arc becomes eligible) or forfeits (arc is dead). No separate `placed-via-worldgen` bit, no `setPlacedBy` override.
- **Spawn gate:** three BE conditions (`chicken-ever-spawned==false`, `chicken-arc-forfeit==false`, `chicken-rotation-detected==true`) plus a player-in-radius check. Hooked in `TownFlagBlockEntity.tick` (the real per-tick path, not the one-shot `EnteringSection` listener).
- **Loot override site:** `ExplorerWork.asWork()` lines 118-152 — this is where the first-villager gatherer's `ResultGenerator` lambda is constructed. Both realtime and warp paths funnel through the same generator. `GathererUnmappedNoToolWorkQtrDay.java` is a 7-line JobID constant with no loot logic and is NOT modified.
- **Advancement suppression site:** `AdvancementEvents.java:80` root branch gate (decided above).
- **Commands:** `/qt flag ... skip-chicken` optional arg + new `/questown chicken remove <pos>` (decided above).

### Deferred to Implementation

- **Exact radius value N** for R5 follow-AI + R8 bubble visibility + spawn check. Likely matches the existing `ApproachTownTrigger.FirstVisit` radius (~10 blocks squared = distanceToSqr < 100) for the follow AI, and 16 blocks for the bubble (standard MC render distance). Confirm during U3.
- **Through-walls `RenderType` exact composite** — whether `RenderType.textSeeThrough(tex)` is reusable-as-is or a custom `RenderType.create(...)` is needed. Try the vanilla one first in U2; fall back to custom only if it interacts badly with item-icon rendering.
- **Worldly Seeds item behaviors** — is it stackable (yes, probably to 64 like vanilla seeds)? Plantable (no, at least in v1)? Usable outside the chicken arc (no in v1 — pure trigger currency)? Final decisions in U1.
- **Structure Block workflow doc** — the plan references the round-trip workflow for `empty_town.nbt` editing. Whether this gets its own `docs/conventions/editing-structure-nbts.md` file or lives as comments in U5 is an implementation call.
- **Patchouli `openBookEntry` behavior when the book isn't in inventory** — unverified at plan time. If it errors or opens an empty shell, U7 adds an inventory-check gate at `AdvancementEvents.java:88`. If it handles gracefully, no extra change needed.
- **Attribute supplier for HelperChickenEntity** — whether to inherit `Chicken.createAttributes()` unchanged or tune (e.g., remove jump/walk speed modifiers that don't matter for a stationary guide). Default: inherit in U1, tune only if playtest needs it.
- **Integration test strategy for beat state machine** — whether `TestWorldAccess` can be extended to cover container-scan + Worldly-Seeds observation, or whether those assertions need to be in-game tests. First attempt in U6; fall back to in-game `/_qtdev` coverage if the test substrate can't reach entity lifecycle.
- **Warp-vs-realtime placement of the R15 guard** — confirm during U7 that placing the override at the `ResultGenerator` level catches both `DeclarativeJobTicker` and `ProductionTimeWarper` callers.

---

## High-Level Technical Design

> *This illustrates the intended approach and is directional guidance for review, not implementation specification. The implementing agent should treat it as context, not code to reproduce.*

```
┌────────────────────────────────────────────────────────────────────┐
│                     HelperChickenEntity (server)                   │
│  extends vanilla Chicken · invulnerable · belongs to flag BlockPos │
│                                                                    │
│  registerGoals():                                                  │
│    (priority order)                                                │
│    1. FloatGoal (inherited)                                        │
│    2. HelperChickenFollowPlayerNearFlagGoal    ← adapted TemptGoal │
│    3. HelperChickenBeatPeckGoal                ← walks to offset   │
│    4. LookAtPlayerGoal (inherited)                                 │
│    5. RandomLookAroundGoal (inherited)                             │
│                                                                    │
│  SynchedEntityData:                                                │
│    DATA_CURRENT_BUBBLE_ICON_A : ItemStack                          │
│    DATA_CURRENT_BUBBLE_ICON_B : ItemStack (empty = single-icon)    │
│    DATA_THROUGH_WALLS         : boolean                            │
│                                                                    │
│  hurt(...) → return false (v1 invulnerable)                        │
└────────────────────────────────────────────────────────────────────┘
                   ▲                              │
                   │ beat advances on server      │ synced to client
                   │ updates SynchedEntityData    │
                   │                              ▼
┌─────────────────────────────────┐   ┌──────────────────────────────┐
│  ChickenArcController           │   │    HelperChickenRenderer     │
│  (per-flag, server-side)        │   │    extends ChickenRenderer   │
│                                 │   │                              │
│  - Observes world conditions:   │   │    addLayer(                 │
│    * flag-state, item-in-hand,  │   │      HelperChickenBubbleLayer│
│      block-placed, room-reg'd,  │   │    )                         │
│      seeds-in-chest, etc.       │   └──────────────────────────────┘
│  - Drives ChickenBeatState      │              │
│    enum transitions             │              ▼
│  - Out-of-order acceptance      │   ┌──────────────────────────────┐
│  - 30s stuck → teleport         │   │   HelperChickenBubbleLayer   │
│  - Still-unreachable → force    │   │                              │
│    complete                     │   │  render(...):                │
│  - Writes SynchedEntityData +   │   │    if distanceToSqr > 16*16  │
│    Worldly-Seeds-present bit    │   │       && !throughWalls → skip│
│  - Drives beat-target BlockPos  │   │    pushPose()                │
│    from HelperChickenBeatOffsets│   │    translate above head      │
│                                 │   │    mulPose(cameraOrientation)│
│  - On CLOSING beat +            │   │    pickIcon = tickCount/20 %2│
│    seeds-given-to-chicken:      │   │    chooseRenderType(         │
│      replace entity with        │   │      throughWalls)           │
│      StoneChickenStatue block   │   │    renderStatic(icon,...,GUI)│
│      set flag COMPLETED bit     │   │    popPose()                 │
└─────────────────────────────────┘   └──────────────────────────────┘
                ▲
                │ reads/writes
                ▼
┌─────────────────────────────────────────────────────────────────┐
│            TownFlagBlockEntity (via writeTownData)              │
│                                                                 │
│  new InitPair entries on TownFlagTileData:                      │
│    - chicken-ever-spawned : boolean                             │
│    - chicken-beat-state   : String (ChickenBeatState.name())    │
│    - chicken-first-gather-worldly-seeds-fired : boolean         │
│    - chicken-arc-forfeit  : boolean                             │
│    - chicken-structure-rotation : String (Rotation.name())      │
│    - chicken-rotation-detected : boolean (sentinel)             │
│                                                                 │
│  Existing writeTownData/loadNextTick pattern — do NOT use       │
│  saveAdditional (documented broken in source).                  │
└─────────────────────────────────────────────────────────────────┘
```

Beat sequencing is a linear state machine (ChickenBeatState enum) with out-of-order acceptance — any state whose observable condition is satisfied is marked complete in the flag BE's beat-state field; the controller advances to the lowest-numbered incomplete state.

---

## Implementation Units

- [ ] U1. **Entity, item, block, enum, and BE-state foundations**

**Goal:** Register the new custom entity, block, item, enums, and flag-BE persistent fields. No gameplay-visible behavior yet — this is the scaffolding every other unit depends on.

**Requirements:** R1, R2, R3, R4, R4b, R24, plus state for R7, R15, R17.

**Dependencies:** None.

**Files:**
- Create: `src/main/java/ca/bradj/questown/mobs/helperchicken/HelperChickenEntity.java` (extends `net.minecraft.world.entity.animal.Chicken`; invulnerable; `SynchedEntityData` for bubble icons and through-walls bit; placeholder `registerGoals`)
- Create: `src/main/java/ca/bradj/questown/mobs/helperchicken/HelperChickenEntityEvents.java` (attribute supplier subscriber)
- Create: `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenBeatState.java` (closed enum with these exact ordinal-stable names, matching U6's state-machine table verbatim so `.name()` round-trips cleanly through the flag BE NBT): `WAITING_FOR_STICK`, `WAITING_FOR_WAND_ON_CAMPFIRE`, `SUNSET_AND_MAP`, `WAITING_FOR_WALL_BLOCK`, `WAITING_FOR_DOOR`, `WAITING_FOR_WAND_ON_DOOR`, `WAITING_FOR_SIGN`, `WAITING_FOR_CHEST`, `WAITING_FOR_PRESSURE_PLATE`, `WAITING_FOR_VILLAGER_UI`, `WAITING_FOR_FLAG_UI`, `AWAITING_WORLDLY_SEEDS_DELIVERY`, `COMPLETE`, `FORFEIT`. These 14 names are authoritative and must not drift between U1 and U6.
- Create: `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenBubbleIcon.java` (closed enum — stick, wand, unlit_campfire, lit_campfire, sunset, map, door, sign, chest, welcome_mat, pressure_plate, worldly_seeds, villager_ui, flag_ui)
- Create: `src/main/java/ca/bradj/questown/blocks/StoneChickenStatue.java` (extends `Block` with simple `Properties.of(Material.STONE)`, pickaxe tool, drops self via `Properties.strength(1.5f).requiresCorrectToolForDrops()` or loot table)
- Create: `src/main/java/ca/bradj/questown/items/WorldlySeeds.java` (extends `Item`; stack-to-64; no other behaviors in v1)
- Create: `src/main/resources/assets/questown/blockstates/stone_chicken_statue.json`
- Create: `src/main/resources/assets/questown/models/block/stone_chicken_statue.json` (placeholder stone-textured cube)
- Create: `src/main/resources/assets/questown/models/item/stone_chicken_statue.json`
- Create: `src/main/resources/assets/questown/models/item/worldly_seeds.json`
- Create: `src/main/resources/assets/questown/textures/blocks/stone_chicken_statue.png` (placeholder stone texture)
- Create: `src/main/resources/assets/questown/textures/items/worldly_seeds.png` (placeholder seeds texture)
- Create: `src/main/resources/data/questown/loot_tables/blocks/stone_chicken_statue.json` (drops self on any tool)
- Create: `src/test/java/ca/bradj/questown/mobs/helperchicken/FoundationRegistrationTest.java`
- Modify: `src/main/java/ca/bradj/questown/core/init/EntitiesInit.java` (add HELPER_CHICKEN `DeferredRegister` entry)
- Modify: `src/main/java/ca/bradj/questown/core/init/BlocksInit.java` (add STONE_CHICKEN_STATUE)
- Modify: `src/main/java/ca/bradj/questown/core/init/items/ItemsInit.java` (add WORLDLY_SEEDS + BlockItem for STONE_CHICKEN_STATUE)
- Modify: `src/main/java/ca/bradj/questown/town/entity/TownFlagTileData.java` (add 6 InitPair entries for chicken state — `chicken-ever-spawned` (bool), `chicken-beat-state` (String, enum name), `chicken-first-gather-worldly-seeds-fired` (bool), `chicken-arc-forfeit` (bool), `chicken-structure-rotation` (String, enum name from `net.minecraft.world.level.block.Rotation`: `NONE`/`CLOCKWISE_90`/`CLOCKWISE_180`/`COUNTERCLOCKWISE_90`), `chicken-rotation-detected` (bool) — the last field is a sentinel that rotation detection has run at least once on this flag BE, distinguishing unset-default-NONE from legitimately-detected-NONE)
- Modify: `src/main/java/ca/bradj/questown/town/entity/TownFlagBlockEntity.java` (add fields + public accessors + NBT key constants alongside the InitPair)
- Modify: `src/main/resources/assets/questown/lang/en_us.json` (entity / block / item display names)

**Approach:**
- Entity class is stub — no AI goals yet (U3), no rendering yet (U2). `hurt(...)` returns `false` for v1 invulnerability. `SynchedEntityData` accessors for `bubble-icon-A`, `bubble-icon-B`, `through-walls`.
- Stone Chicken Statue uses placeholder geometry — registration must compile and load; bespoke art is deferred per Scope Boundaries. A simple full-block cube model with a stone texture is sufficient.
- Worldly Seeds has no custom behavior — no recipe, no plantability, no right-click handler in v1. Pure trigger currency.
- Closed enums live under `mobs/helperchicken/` package for locality with the entity.
- Flag BE state additions follow the existing `InitPair` pattern exactly — `fromTag` reads the NBT key, `onPlace` initializes to the unspawned default (everything `false`, beat state `WAITING_FOR_STICK`).
- Per the God-object pattern, state bits live on the flag BE, but behavior driving those bits is intentionally moved to U3's `ChickenArcController` (new class) — the BE gets storage only.

**Patterns to follow:**
- `src/main/java/ca/bradj/questown/mobs/visitor/VisitorMobEntityEvents.java` for attribute supplier subscriber
- `src/main/java/ca/bradj/questown/blocks/FalseWallBlock.java` for simple decorative block class + assets
- `src/main/java/ca/bradj/questown/items/TownWand.java` registration for no-recipe item
- `src/main/java/ca/bradj/questown/town/entity/TownFlagTileData.java:41-57` InitPair pattern — specifically `initBonusGiven` at lines 66-76 as the simplest boolean example
- `docs/decision-qttoolaction-closed-enum.md` for closed-enum pattern

**Test scenarios:**
- Happy path: entity, block, and item register successfully at mod init; no exceptions.
- Happy path: flag BE persists all 6 new chicken fields across a save/load cycle (verify via `TestWorldAccess`-style harness or in-game). Specifically: set `chicken-ever-spawned=true` + `chicken-beat-state=WAITING_FOR_WAND_ON_CAMPFIRE` + `chicken-structure-rotation=CLOCKWISE_90` + `chicken-rotation-detected=true`, unload/reload flag chunk, assert fields restored.
- Edge case: `chicken-beat-state` NBT value of `"INVALID_UNKNOWN"` loads as `FORFEIT` (enum fallback), not an exception. Same for `chicken-structure-rotation` with an invalid name.
- Edge case: flag BE loaded from pre-change save (NBT keys absent) defaults all 6 fields — bools to `false`, beat-state to `WAITING_FOR_STICK`, rotation to `NONE` — without throwing. Note that `chicken-rotation-detected=false` (default) causes U3's first-tick detector to run on the next tick, which corrects the rotation to match the actual placement — covered by a U3 test scenario.
- Integration: block breaks with pickaxe and drops the statue item; placing the item gives back the block.

**Verification:**
- `./gradlew compileJava` succeeds.
- Mod loads in a dev client without registration errors.
- Creative inventory shows Stone Chicken Statue and Worldly Seeds items.
- Flag BE round-trips all 4 new fields (test or in-game).

---

- [ ] U2. **Speech bubble renderer, layer, and icon assets**

**Goal:** Render the world-space speech bubble above the helper chicken's head, with distance-proximity visibility, alternating-icon cadence, through-walls rendering when the flag BE reports Worldly-Seeds-in-container, and dynamic item-icon content (no pre-authored compound sprites).

**Requirements:** R8, R9, R10, R11.

**Dependencies:** U1 (entity exists and has `SynchedEntityData` bubble fields).

**Files:**
- Create: `src/main/java/ca/bradj/questown/mobs/helperchicken/HelperChickenRenderer.java` (extends `ChickenRenderer`; adds bubble layer)
- Create: `src/main/java/ca/bradj/questown/mobs/helperchicken/HelperChickenBubbleLayer.java` (the layer that renders the bubble)
- Create: `src/main/java/ca/bradj/questown/mobs/helperchicken/BubbleRenderType.java` (custom `RenderType` with `NO_DEPTH_TEST` for through-walls)
- Create: `src/main/resources/assets/questown/textures/bubble/stick.png`
- Create: `src/main/resources/assets/questown/textures/bubble/wand.png`
- Create: `src/main/resources/assets/questown/textures/bubble/unlit_campfire.png`
- Create: `src/main/resources/assets/questown/textures/bubble/lit_campfire.png`
- Create: `src/main/resources/assets/questown/textures/bubble/sunset.png`
- Create: `src/main/resources/assets/questown/textures/bubble/map.png`
- Create: `src/main/resources/assets/questown/textures/bubble/door.png`
- Create: `src/main/resources/assets/questown/textures/bubble/sign.png`
- Create: `src/main/resources/assets/questown/textures/bubble/chest.png`
- Create: `src/main/resources/assets/questown/textures/bubble/welcome_mat.png`
- Create: `src/main/resources/assets/questown/textures/bubble/pressure_plate.png`
- Create: `src/main/resources/assets/questown/textures/bubble/worldly_seeds.png`
- Create: `src/main/resources/assets/questown/textures/bubble/villager_ui.png`
- Create: `src/main/resources/assets/questown/textures/bubble/flag_ui.png`
- Modify: `src/main/java/ca/bradj/questown/Questown.java` (register `HelperChickenRenderer` in `FMLClientSetupEvent`)

**Approach:**
- Renderer extends `ChickenRenderer` (vanilla) so the chicken model is free. Layer is added in the renderer's ctor via `this.addLayer(new HelperChickenBubbleLayer<>(this, ctx))` where `ctx` is the `EntityRendererProvider.Context` (for item renderer access).
- Layer's `render(...)` pushes pose, translates above head (`0, entity.getBbHeight() + 0.5, 0`), multiplies by `EntityRenderDispatcher.cameraOrientation()` for billboard, scales.
- Icon selection: `ItemStack icon = (entity.tickCount / 20) % 2 == 0 ? iconA : (iconB.isEmpty() ? iconA : iconB)`. Hard-cut alternation; single-icon path when `iconB` is empty.
- Content: `ItemRenderer.renderStatic(icon, TransformType.GUI, packedLight, OverlayTexture.NO_OVERLAY, poseStack, bufferSource, 0)`. `TransformType.GUI` gives flat sprite-card rendering. Authored bubble textures (non-item, e.g., sunset / map / villager_ui / flag_ui) render as a textured quad via a custom render type with the bubble PNG as the texture.
- Distance gate: `if (dispatcher.distanceToSqr(entity) > 16.0 * 16.0 && !entity.isThroughWallsMode()) return;` — through-walls mode bypasses the distance gate (or extends it, decided during implementation).
- Through-walls render type: try `RenderType.textSeeThrough(tex)` first; fall back to a custom `RenderType.create(...)` with composite state `DepthTestState=NO_DEPTH_TEST + WriteMask.COLOR_WRITE` if the vanilla one misbehaves. `RenderType` selected per-frame based on `entity.isThroughWallsMode()`.
- Menu-open + click behavior per R8/R10 is automatic: when the menu is open the client game loop doesn't process entity clicks and the crosshair is gone. No extra suppression needed in the renderer. The layer still renders but hit-testing is menu-absorbed.
- Left-click-is-interact: handled by entity-side logic (see U6), not in the renderer.

**Technical design:** *(directional — not implementation specification)*

```
HelperChickenBubbleLayer.render(...):
  if not throughWalls && distanceToSqr > 256:
    return
  ItemStack icon = chooseAlternatingIcon(entity.tickCount, iconA, iconB)
  poseStack.pushPose()
  poseStack.translate(0, bbHeight + 0.5, 0)
  poseStack.mulPose(dispatcher.cameraOrientation())
  poseStack.scale(0.5, -0.5, 0.5)  // billboard-sprite-facing
  RenderType type = throughWalls ? BubbleRenderType.THROUGH_WALLS : entityCutoutNoCull(DEFAULT_BUBBLE_TEX)
  if iconIsItem:
    itemRenderer.renderStatic(icon, GUI, light, NO_OVERLAY, poseStack, buffer, 0)
  else:
    renderQuad(poseStack, buffer, iconTexture, type)
  poseStack.popPose()
```

**Patterns to follow:**
- `src/main/java/ca/bradj/questown/mobs/visitor/SpinningCubeLayer.java` for pose stack + custom layer render
- `src/main/java/ca/bradj/questown/mobs/visitor/VisitorArrowLayer.java` for layer-with-context ctor capturing `EntityRenderDispatcher`
- External (MIT): Buuz135's Emojiful for item-icon-as-sprite-above-entity
- External: MCA Reborn's chat bubble layer for the billboard + through-walls combination

**Test scenarios:**
- Happy path (in-game test): helper chicken in a dev world renders with a stick bubble above its head when within 16 blocks; bubble disappears beyond 16 blocks.
- Happy path (in-game test): bubble set to alternate `wand + unlit_campfire` — observe hard-cut every ~1 second.
- Edge case (in-game test): `entity.setThroughWallsMode(true)` with `worldly_seeds` icon — walk behind a stone wall, bubble still visible; walk 20 blocks away, bubble still visible.
- Edge case (in-game test): open inventory — bubble pauses rendering / crosshair-click-to-interact not delivered (behavior is automatic from MC menu system; verify no regression).
- Integration (in-game test): render with pos stack alongside vanilla `renderNameTag` — no z-fighting or visual conflict.
- Happy path: bubble icon content is driven by `SynchedEntityData` fields from U1 — server-side state change reflects client-side within a tick.

**Execution note:** Visual correctness cannot be covered by integration tests; this is in-game `/_qtdev`-style verification territory. Unit/integration tests limited to `SynchedEntityData` plumbing.

**Verification:**
- Chicken placed in dev world renders a stick bubble at ≤16 blocks.
- Through-walls mode visible through a stone wall.
- Alternation cadence visually stable (no sub-tick flicker).
- No rendering regressions on `VisitorMobEntity` (existing `SpinningCubeLayer` still works).

---

- [ ] U3. **Chicken AI goals, lifecycle hooks, and beat-offset constants**

**Goal:** Install the chicken's follow AI (adapted `TemptGoal` with flag-radius predicate), despawn/respawn-on-player-proximity (matching villager behavior), spawn gate on worldgen-only + `chicken-ever-spawned` BE bit, and the hardcoded beat-offset constants keyed to `empty_town.nbt` (offsets authored alongside U5's structure edit).

**Requirements:** R1, R5, R6, R6b (teleport-after-30s-stuck + force-complete), spawn gate derivatives of R1 + R22.

**Dependencies:** U1.

**Files:**
- Create: `src/main/java/ca/bradj/questown/mobs/helperchicken/HelperChickenFollowNearFlagGoal.java` (adapted from vanilla `TemptGoal` with predicate swap)
- Create: `src/main/java/ca/bradj/questown/mobs/helperchicken/HelperChickenBeatPeckGoal.java` (walks to beat-target `BlockPos`, pecks; teleports after 30s stuck; force-completes if target still invalid)
- Create: `src/main/java/ca/bradj/questown/mobs/helperchicken/HelperChickenBeatOffsets.java` (constants class — structure-local `BlockPos` offsets for campfire, wall-block, door, sign, chest, gate-center; `forState(state, flagPos, rotation)` method applies `BlockPos.rotate(Rotation)` before adding to `flagPos`)
- Create: `src/main/java/ca/bradj/questown/town/HelperChickenSpawnController.java` (hooks into existing flag-tick path; checks radius + BE bits; spawns/despawns)
- Create: `src/main/java/ca/bradj/questown/town/HelperChickenRotationDetector.java` (at worldgen placement, scans the 4 candidate anchor positions for the authored anchor block type; persists the detected `Rotation` on the flag BE; force-forfeits the arc if no rotation matches)
- Create: `src/test/java/ca/bradj/questown/mobs/helperchicken/SpawnLifecycleTest.java` (integration-test using `TestWorldAccess`)
- Modify: `src/main/java/ca/bradj/questown/mobs/helperchicken/HelperChickenEntity.java` (wire goals in `registerGoals`)
- Modify: `src/main/java/ca/bradj/questown/town/entity/TownFlagBlockEntity.java` (add per-tick `HelperChickenSpawnController.tick(flag)` call inside the existing `tick(Level, BlockPos, BlockState, TownFlagBlockEntity)` method at line ~240, delegating to the same path `TownFlagTicker` uses. Also add a first-tick hook that invokes `HelperChickenRotationDetector.detectIfNeeded(flag)` BEFORE the spawn controller's first pass — this is the worldgen-and-rotation-detection entry point, replacing the previously-considered `setPlacedBy` approach (which does not fire for jigsaw-placed blocks in 1.19.2).)
- Modify: `src/main/java/ca/bradj/questown/commands/FlagCommand.java` (in both the default flag-place branch and the `skip-chicken` branch: after `setBlockAndUpdate(...)`, call `initializeFreshFlag(true)` on the new BE explicitly, THEN write `chicken-ever-spawned=true` and `chicken-rotation-detected=true` via `writeTownData`. Writing the bits BEFORE init would be stomped by the InitPair defaults; writing AFTER ensures the command's intent survives. This is the command/creative placement detection — commands explicitly mark the flag as ineligible for the arc because only worldgen-placed flags satisfy R13's scaffolding assumption.)

**Approach:**
- Copy vanilla `TemptGoal` source into `HelperChickenFollowNearFlagGoal` and replace the `"is player holding temptation items"` predicate with `"is player within radius N of this chicken's flag AND chicken-arc is active"`. Priority position 2 (after FloatGoal, before peck).
- `HelperChickenBeatPeckGoal` reads the chicken's current beat state from its owning flag BE (via a reference set at spawn time), computes the beat-target `BlockPos` via `HelperChickenBeatOffsets.resolveTarget(state, flagPos, rotation)` which applies rotation via `StructureTemplate.transform(localOffset, Mirror.NONE, rotation, BlockPos.ZERO)` — note the **rotation argument** (see rotation-awareness below), runs pathfinding, on arrival triggers a peck animation (simple swing / custom animation). Path-failure handling has **two distinct exits**: (a) pathing fails for 30s while the target block is still valid → teleport to target with a poof particle, beat stays open; (b) target block is permanently invalid (replaced with lava, filled by player-placed solid) → force-complete the beat. The two cases are NOT collapsed — an obstructed but valid target gives the player a chance to clear the path, not a silent skip.
- **First-tick detection hook** (runs once per flag BE lifetime, gated by `chicken-rotation-detected` sentinel): inside `TownFlagBlockEntity.tick`, before the spawn controller's check, invoke `HelperChickenRotationDetector.detectIfNeeded(flag)`. The detector:
  1. Returns early if `chicken-rotation-detected == true` (already run).
  2. Scans the 4 candidate world positions for the authored anchor block (unlit campfire at structure-local offset `(3, 0, 5)` or similar asymmetric offset chosen in U5) — applying `StructureTemplate.transform(localOffset, Mirror.NONE, rot, BlockPos.ZERO)` for each of the 4 Rotation values.
  3. Exactly one rotation should match (if U5's anchor offset is asymmetric under all 4 rotations, guaranteed). Persists the matching `Rotation.name()` to `chicken-structure-rotation`.
  4. Sets `chicken-rotation-detected=true`. Writes via `writeTownData`.
  5. If zero matches on a single-tick check, grant a retry budget: keep `chicken-rotation-detected=false` and re-run detection on subsequent ticks for up to 20 ticks (1 second). If still zero after the budget, force-forfeit the arc (`chicken-arc-forfeit=true`) with `chicken-rotation-detected=true`. This guards the edge case where the anchor block's chunk hasn't loaded yet on the flag BE's first tick — normal play should resolve within 2-3 ticks, but the budget is the safety net.
  6. If multiple matches (anchor position symmetric under some rotations, or foreign campfire within scan radius): also force-forfeit — safer than picking a wrong rotation.
  7. **Why first-tick, not `setPlacedBy`:** `setPlacedBy` is not called by `StructureTemplate.placeInWorld` in Forge 1.19.2. Jigsaw piece placement writes blocks via `ServerLevelAccessor.setBlock` which bypasses the `setPlacedBy` hook. First-tick on the BE is the first moment the block is both placed and accessible from server-thread code that can read the surrounding world state.
- **Worldgen-vs-command detection**: commands explicitly mark command-placed flags as ineligible. `FlagCommand.java` pre-sets `chicken-ever-spawned=true` AND `chicken-rotation-detected=true` at placement time — this skips both the spawn controller and the rotation detector for command-placed flags. Worldgen has no such hook; worldgen-placed flags rely on the first-tick path above. This matches the origin requirement (R22: command-placed flags skip the arc entirely) without depending on `setPlacedBy` semantics.
- **Spawn gate**: inside `HelperChickenSpawnController.tick(flag)`:
  1. Returns early if `chicken-ever-spawned == true` or `chicken-arc-forfeit == true`.
  2. Returns early if `chicken-rotation-detected == false` (detection hasn't run yet; first-tick detector runs before this on the same tick, so this is a belt-and-suspenders check).
  3. Returns early if no player is within ~10 blocks of the flag.
  4. Spawns `HelperChickenEntity` adjacent to the flag; sets `chicken-ever-spawned=true` on the flag BE via `writeTownData`.
- Despawn/respawn: when no player is within follow radius for N ticks, the chicken's client-entity is removed (mirroring villager despawn). Beat state AND rotation persist on flag BE — on player return, a new chicken is re-spawned from the spawn controller, with state restored from the flag BE.
- `HelperChickenBeatOffsets` is authored alongside U5's `empty_town.nbt` edits. Offsets are `BlockPos` constants in the structure's **local frame** (pre-rotation), referencing flag-relative coordinates:
  - `CAMPFIRE_OFFSET` (where the unlit campfire lives in the structure-local frame — this is also the rotation-detection anchor)
  - `WALL_BLOCK_OFFSET` (the single missing wall-block spot, local frame)
  - `DOOR_OFFSET` (where the door goes, local frame)
  - `SIGN_OFFSET` (inside the registered room, local frame)
  - `CHEST_OFFSET` (inside the registered room, local frame)
  - `GATE_CENTER_OFFSET` (between the two gate columns, local frame)
  At runtime, each lookup goes through `resolveTarget(state, flagPos, rotation)` which calls `localOffset.rotate(rotation)` and adds `flagPos`. Verified via javap that `BlockPos.rotate(Rotation)` is a live instance method in 1.19.2; `StructureTemplate.transform(BlockPos, Mirror, Rotation, BlockPos)` is the alternative if mirror support is ever needed (v1 does not need mirror).

**Patterns to follow:**
- Vanilla `net.minecraft.world.entity.ai.goal.TemptGoal` (1.19.2 mapped source) — copy + swap predicate
- `src/main/java/ca/bradj/questown/mobs/visitor/VisitorMobEntity.java` goal registration — though that's Brain-based; the helper chicken uses the simpler `goalSelector.addGoal(priority, goal)` pattern from vanilla `Chicken.registerGoals`
- `src/main/java/ca/bradj/questown/town/entity/TownFlagBlockEntity.java:476-489` for the existing per-tick flag-radius player check pattern

**Test scenarios:**
- Happy path: worldgen places a flag + structure → flag BE's first tick runs `HelperChickenRotationDetector.detectIfNeeded(flag)` → rotation detected + `chicken-rotation-detected=true` → player approaches within radius → chicken spawns → `chicken-ever-spawned` becomes `true`.
- Happy path: chicken follows player when within flag radius; stops following when player leaves radius.
- Happy path: on beat-state transition to `WAITING_FOR_WAND_ON_CAMPFIRE`, chicken's beat-peck goal resolves target via `CAMPFIRE_OFFSET.rotate(rotation).offset(flagPos)` and pecks on arrival.
- Happy path (rotation-awareness): place the structure at each of the 4 rotations (`NONE`, `CLOCKWISE_90`, `CLOCKWISE_180`, `COUNTERCLOCKWISE_90`); on first tick, rotation detector correctly identifies each; chicken's beat-peck target matches the in-world campfire position for each rotation.
- Edge case (rotation ambiguity): place structure but spawn an extra unlit campfire at one of the OTHER 3 rotated candidate positions (simulating foreign campfire); rotation detector sees 2+ matches → force-forfeits (`chicken-arc-forfeit=true` and `chicken-rotation-detected=true`); no chicken spawns.
- Edge case (rotation no-match): remove the authored anchor block before first tick (simulating structure corruption); rotation detector sees 0 matches → force-forfeits; no chicken spawns.
- Edge case: player walls in the chicken's current beat target; after 30 seconds of failed pathing, chicken teleports to target with a poof particle; beat stays open (not force-completed, per the two-exit rule).
- Edge case: beat target is permanently invalid (replaced with lava); chicken's teleport attempt detects invalid target → goal force-completes the beat; chicken moves on.
- Edge case: `chicken-ever-spawned == true` + no existing chicken entity → spawn controller respawns the chicken on player approach (despawn-resume case).
- Edge case: `chicken-arc-forfeit == true` → no chicken spawns regardless of other state.
- Edge case: flag placed via command → `FlagCommand` pre-sets `chicken-ever-spawned=true` and `chicken-rotation-detected=true` → no rotation detection runs; no chicken ever spawns.
- Edge case (pre-change save): load save where a worldgen flag exists but new NBT fields are absent → first tick after load runs rotation detection → correct rotation persists → subsequent first-visit spawns chicken with correct rotation. (Covers R3 independence across saves AND the migration concern for existing pre-update worldgen flags.)
- Integration: beat state AND rotation persist across entity despawn + respawn; chicken resumes at the correct rotated target on return.

**Execution note:** Integration-test the spawn controller + beat state machine using `TestWorldAccess`-style harness. Renderer and goal pathfinding remain in-game `/_qtdev`-style verification.

**Verification:**
- Chicken spawns at worldgen-placed flag on first player approach, not on subsequent approaches.
- Follow behavior works within radius; despawn/respawn smooth on exit/entry.
- Teleport-after-30s + force-complete both observable in dev world.

---

- [ ] U4. **Chicken arc controller — beat state transitions and observable-condition driver**

**Goal:** Implement the per-flag `ChickenArcController` that observes world conditions, drives `ChickenBeatState` transitions in the flag BE, updates chicken `SynchedEntityData` (bubble icons + through-walls bit), and is the integration point for F1/F2/F3/F4 beat sequencing.

**Requirements:** R7, R7b, R10 (click-for-text delivery), R12 (curriculum scope), R16 (Worldly-Seeds-in-container observation + through-walls bubble trigger).

**Dependencies:** U1, U3.

**Files:**
- Create: `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenArcController.java`
- Create: `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenArcConditions.java` (pure-function observers — does player have a wand, is campfire lit, are Worldly Seeds in any town container, etc.)
- Create: `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenArcBubbles.java` (maps `ChickenBeatState` → `(iconA, iconB, throughWalls)` tuple)
- Create: `src/test/java/ca/bradj/questown/mobs/helperchicken/ChickenArcControllerTest.java` (uses `TestWorldAccess`)
- Modify: `src/main/java/ca/bradj/questown/mobs/helperchicken/HelperChickenEntity.java` (interact handler for click-for-text via `Util.onScreenText`)
- Modify: `src/main/java/ca/bradj/questown/town/entity/TownFlagBlockEntity.java` (per-tick hook to `ChickenArcController.tick(flag)`)
- Modify: `src/main/resources/assets/questown/lang/en_us.json` (interaction text strings, one per bubble state — `chicken.hint.stick`, `chicken.hint.wand_on_campfire`, `chicken.hint.sunset`, etc.)

**Approach:**
- Controller runs per-flag per-tick (or every N ticks if perf warrants). Reads current beat state from flag BE; evaluates observable conditions via `ChickenArcConditions`; writes the next state back if conditions advance.
- Out-of-order acceptance per R7b: controller evaluates ALL remaining beat conditions each tick (not just the current one). Any beat whose condition becomes satisfied is marked complete. The chicken's *animation target* (via `HelperChickenBeatPeckGoal`) tracks the lowest-numbered incomplete state.
- Observable conditions inventory (all via `ChickenArcConditions`):
  - `hasWandInInventory(player)`, `isCampfireLit(flagPos+offset)`, `hasMapInInventory(player)`, `hasSleepHappened(flag)`, `isWallBlockPlaced(flagPos+offset)`, `isDoorPlaced(flagPos+offset)`, `isRoomRegistered(flag)`, `isSignPlacedAndConverted(flagPos+offset)`, `isChestPlaced(flagPos+offset)`, `isWelcomeMatPlaced(flag)`, `hasOpenedVillagerUi(player)`, `hasOpenedFlagUi(player)`, `areWorldlySeedsInAnyTownContainer(flag)`, `playerGaveWorldlySeedsToChicken(chicken)`.
  - Some of these (UI-opened) require new server-side signals — U6 adds those.
- **`first-gather-worldly-seeds-fired` bit-flip responsibility belongs to this unit (U4), not U7.** When `ChickenArcConditions.areWorldlySeedsInAnyTownContainer(flag)` transitions from false to true AND `first-gather-worldly-seeds-fired == false`, the controller flips the bit (via `writeTownData`) as part of its state-transition handling. U7's wrappers at `RealtimeWorldInteraction.getResults` and `TimeWarpWorldInteraction.getResults` check-and-prepend only; they do NOT write the bit. This avoids duplicate writes across the two U7 wrap sites and localizes all BE write logic in U4's controller.
- Bubble mapping (`ChickenArcBubbles.forState(beatState)`): returns `(iconA, iconB, throughWalls)`. Most states are single-icon; F1/F2 campfire beats alternate wand+campfire; F4 seed-delivery is single `worldly_seeds` + through-walls true.
- Click-for-text: entity's `interactAt(Player, Vec3, InteractionHand)` reads the current beat state from the flag BE and sends the matching lang key via `Util.onScreenText`. Both left-click (via `attackLivingEntity` override returning `false` + text-send) and right-click dispatch to the same path.
- **Warp consideration:** Controller runs in the realtime flag-tick path only (chicken is realtime-only). World conditions it observes can be set up by warp (e.g., sleep advancing time, villager depositing seeds) — the controller observes the post-warp state on next realtime tick and advances accordingly. No warp-side state poking.

**Patterns to follow:**
- `src/main/java/ca/bradj/questown/jobs/special/...` for per-tick observer patterns
- `docs/decision-declare-and-collect-global-rules.md` for realtime-vs-warp separation principle

**Test scenarios:**
- Happy path: beat state = WAITING_FOR_STICK; player obtains wand (stick-on-flag fires); controller advances to WAITING_FOR_WAND_ON_CAMPFIRE next tick.
- Happy path: beat state = WAITING_FOR_WALL_BLOCK; wall block placed at correct offset; controller advances to WAITING_FOR_DOOR next tick.
- Happy path (R7b out-of-order): player completes WAITING_FOR_CHEST before WAITING_FOR_SIGN (places chest first); both beats complete in one tick when sign is placed, in correct order; chicken's animation target updates correctly.
- Happy path (Covers AE5): Worldly Seeds item appears in any town container; controller sets chicken's `through-walls` bit true + bubble icon = `worldly_seeds`; when player right-clicks chicken with Worldly Seeds in hand, transition to COMPLETE fires (statue placement handled by U6).
- Edge case: player empties inventory mid-arc (drops wand); controller does NOT regress — once a beat is COMPLETE, it stays complete.
- Edge case: two players nearby; either's wand/action advances the beat (shared arc per R4a origin Key Decisions).
- Error path: flag BE has `chicken-beat-state = FORFEIT`; controller is a no-op.
- Integration (Covers F1): full arc F1 sequence (stick → wand → wand-on-campfire → campfire lit) with real flag BE state; all transitions fire in order; chicken's bubble icons update via `SynchedEntityData`.
- Integration (Covers AE5): Worldly Seeds in chest → bubble through-walls → player hands seeds → COMPLETE.
- Happy path: click-for-text — left-click delivers text via `Util.onScreenText`, no damage dealt; right-click also delivers text.
- Edge case: rapid-fire clicks — text re-appears each click, no cooldown.

**Execution note:** Integration tests using `TestWorldAccess` are feasible for the beat state machine and observable-condition plumbing. Run through F1 and F4 end-to-end in tests. F2 and F3 beats can be covered via in-game `/_qtdev` since they involve entity pathfinding.

**Verification:**
- All beat transitions verified by integration test for F1 + F4.
- `/_qtdev` in-game runthrough of F2 + F3 completes without deadlocks.
- `Util.onScreenText` delivery on both left- and right-click confirmed in dev world.

---

- [ ] U5. **`empty_town.nbt` augmentation and offset coupling**

**Goal:** Edit the `empty_town.nbt` structure template to include the chicken's scaffolding (unlit campfire, mostly-built cobblestone room with one missing wall and no door, two gate columns). Produce the `BlockPos` offset constants in `HelperChickenBeatOffsets` (from U3) that match the authored layout.

**Requirements:** R13.

**Dependencies:** U3 (consumer of the offsets).

**Files:**
- Modify: `src/main/resources/data/questown/structures/empty_town.nbt` (binary NBT)
- Modify: `src/main/java/ca/bradj/questown/mobs/helperchicken/HelperChickenBeatOffsets.java` (fill in offsets from the authored layout)
- Create: `docs/conventions/editing-empty-town-nbt.md` (workflow doc for future edits — Structure Block round-trip process)

**Approach:**
- Standard MC Structure Block round-trip: in a dev world, `/give @s minecraft:structure_block`, load `questown:empty_town`, edit the structure in-place (add unlit campfire at offset, build a mostly-complete cobblestone room with exactly one missing wall block and no door frame, place two gate columns with empty space between), save under the same name, copy the exported `.nbt` from the world's saves directory back into `src/main/resources/data/questown/structures/empty_town.nbt`.
- **Room specification (per the brainstorm's Finding 19 resolution):** cobblestone walls (abundant, no biome constraint), **exactly one missing wall block**, no door (doorway is a gap in the wall).
- Record each beat target's structure-local `BlockPos` offset in `HelperChickenBeatOffsets`. Offsets are authored in the **structure-local frame** (i.e., the frame where the structure was saved in the Structure Block before placement rotation). At runtime, U3's rotation-aware lookup applies `BlockPos.rotate(Rotation)` using the flag BE's persisted rotation before resolving world coordinates.
- Write a small convention doc (`docs/conventions/editing-empty-town-nbt.md`) capturing the Structure Block workflow so future structure edits don't re-discover it. Per institutional-learnings guidance. Include a note on the Structure Block export format: 1.19.2 exports uncompressed `.nbt` while the repo-resident file may be gzipped — MC reads either format at load, but diffing requires consistent compression.
- **Anchor block for rotation detection**: the unlit campfire placed by this structure edit is the authored anchor block used by U3's `HelperChickenRotationDetector`. Its structure-local offset must be unique among the 4 rotations (i.e., not symmetric) so detection can disambiguate.

**Patterns to follow:**
- Existing structure files in `src/main/resources/data/questown/structures/` (and how jigsaw references them in `src/main/resources/data/questown/worldgen/`)

**Test scenarios:**
- Happy path (in-game): `/locate structure questown:empty_town` places an instance with the augmented scaffolding visible (unlit campfire, mostly-built room with 1 missing wall + no door, 2 gate columns with gap between).
- Happy path (in-game): place flag at structure's flag position (`NONE` rotation); verify `flagPos + CAMPFIRE_OFFSET` resolves to the in-world campfire position; same for each of the 6 structure-local offsets.
- Happy path (in-game): force each of the 4 rotations via repeated worldgen or via `/place template` with explicit rotation; verify the rotation detector identifies each correctly and the chicken pecks the in-world campfire at each rotation.
- Edge case: anchor block (campfire) is symmetric across two rotations (anchor at structure-local position `(3, 0, 3)`) — rotation detector ambiguity. Mitigation: author the anchor at an asymmetric offset like `(3, 0, 5)` so the four candidate positions are all distinct.

**Execution note:** Offset correctness is verified by visual in-game inspection and by a quick integration assertion that chicken's beat goals path to the right block positions.

**Verification:**
- Fresh worldgen town contains the augmented scaffolding.
- All 6 offsets match in-world positions.
- Existing structure-spawn mechanics (jigsaw, biome filtering) unaffected.

---

- [ ] U6. **Curriculum beat wiring — F1, F2, F3, F4 sequencing**

**Goal:** Wire each origin flow's beats to the `ChickenArcController` observations + bubble mappings. This unit exists to deliver F1 (stick → wand → campfire-lit), F2 (sunset → sleep), F3 (room → door → registration → sign → chest → welcome mat), and F4 (villager UI → flag UI → Worldly Seeds → statue) as a complete playable experience.

**Requirements:** R12, plus all of F1/F2/F3/F4 including handoff (villager UI + flag UI prompts).

**Dependencies:** U1, U2, U3, U4, U5.

**Files:**
- Modify: `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenArcController.java` (extended state machine covering all ~15 beats)
- Modify: `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenArcConditions.java` (add villager-UI-opened + flag-UI-opened observers — these require hooking existing UI open events to set a per-player per-flag "has opened this UI" bit)
- Modify: `src/main/java/ca/bradj/questown/mobs/helperchicken/HelperChickenEntity.java` (Worldly-Seeds right-click-to-deliver handler)
- Modify: `src/main/java/ca/bradj/questown/mobs/helperchicken/HelperChickenBeatPeckGoal.java` (F4 step 4 — chicken walks to specific container holding Worldly Seeds, not the static CHEST_OFFSET)
- Create: `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenStatueTransformHandler.java` (handles the COMPLETE transition — replaces chicken entity with Stone Chicken Statue block, sets flag `chicken-arc-forfeit` or `chicken-ever-spawned` terminal state + completion bit, plays heart particles)
- Create: `src/test/java/ca/bradj/questown/mobs/helperchicken/CurriculumSequenceTest.java` (integration test for F1 + F4 end-to-end)
- Modify: `src/main/java/ca/bradj/questown/gui/VillagerUiScreen.java` (or equivalent — signal "opened" event to server for chicken observer)
- Modify: `src/main/java/ca/bradj/questown/gui/FlagUiScreen.java` (or equivalent — same signal)

**Approach:**
- For each beat state, define the observable condition, the bubble icon(s), and the next state. Build the transition table in `ChickenArcController`; most beats are 1:1 with the enum defined in U1.
- F4 step 4's through-walls rule: when `areWorldlySeedsInAnyTownContainer(flag)` becomes true, controller sets chicken's bubble to `worldly_seeds` icon + `through-walls = true`, and routes the chicken's beat-peck goal to the specific `BlockPos` of the container holding the seeds (walk up to it, peck). Container lookup: the flag BE already tracks registered containers; filter to those holding Worldly Seeds.
- F4 step 5 (player gives seeds): entity's `interactAt` handler checks `player.getItemInHand(hand).is(ItemsInit.WORLDLY_SEEDS.get())` and the current beat state is `AWAITING_WORLDLY_SEEDS_DELIVERY`; on match, consume one Worldly Seeds from the player's stack and fire `ChickenStatueTransformHandler.transform(chicken, flag)`.
- `ChickenStatueTransformHandler.transform(...)` replaces the entity's block position with a `StoneChickenStatue` block, sets flag BE state to terminal (chicken-ever-spawned stays true; a new `chicken-arc-complete` bit could be added, or reuse chicken-arc-forfeit with a separate flag — decide during implementation), plays heart particles via `ServerLevel.sendParticles`.
- Villager UI + Flag UI "opened" observation: add a server-side signal from each UI's open handler. Simplest: a `ChickenArcEvents.playerOpenedVillagerUi(player, flag)` static-method call invoked at the bottom of the UI's open path. Sets a per-player-per-flag bit somewhere — simplest on the chicken entity's `SynchedEntityData` as transient state, or on the flag BE as a player-keyed map.

**Technical design:** *(state machine sketch — directional)*

```
WAITING_FOR_STICK              → hasWand(player)                 → WAITING_FOR_WAND_ON_CAMPFIRE
WAITING_FOR_WAND_ON_CAMPFIRE   → isCampfireLit(flagPos+OFFSET)   → SUNSET_AND_MAP
SUNSET_AND_MAP                 → (sunset bubble; first-click gives map; persists)
                               → hasSleepHappenedToday(flag)     → WAITING_FOR_WALL_BLOCK
WAITING_FOR_WALL_BLOCK         → isWallBlockPlaced(offset)       → WAITING_FOR_DOOR
WAITING_FOR_DOOR               → isDoorPlaced(offset)            → WAITING_FOR_WAND_ON_DOOR
WAITING_FOR_WAND_ON_DOOR       → isRoomRegistered(flag)          → WAITING_FOR_SIGN
WAITING_FOR_SIGN               → isSignPlacedAsJobBoard(offset)  → WAITING_FOR_CHEST
WAITING_FOR_CHEST              → isChestPlaced(offset)           → WAITING_FOR_PRESSURE_PLATE
WAITING_FOR_PRESSURE_PLATE     → isWelcomeMatPlaced(flag)        → WAITING_FOR_VILLAGER_UI
WAITING_FOR_VILLAGER_UI        → playerOpenedVillagerUi(p, flag) → WAITING_FOR_FLAG_UI
WAITING_FOR_FLAG_UI            → playerOpenedFlagUi(p, flag)     → AWAITING_WORLDLY_SEEDS_DELIVERY
AWAITING_WORLDLY_SEEDS_DELIVERY→ (bubble shows worldly_seeds)
                               → isWorldlySeedsInContainer(flag) → (chicken walks to container, pecks)
                               → playerGaveSeedsToChicken(chicken)→ COMPLETE (transform to statue)
COMPLETE                       → (terminal state)
FORFEIT                        → (terminal — /questown chicken remove was invoked)
```

**Patterns to follow:**
- `src/main/java/ca/bradj/questown/jobs/declarative/AbstractAdvanceTime.java` (and its tick structure) for per-tick-condition-driven state machines

**Test scenarios:**
- Happy path (Covers F1): full F1 sequence with real flag BE — stick on flag → wand created → wand-on-unlit-campfire (via U7) → campfire lights → state advances to SUNSET_AND_MAP.
- Happy path (Covers F2): player clicks chicken during SUNSET_AND_MAP → receives locator map centered on flag; subsequent click shows text only, no additional map.
- Happy path (Covers F3): all 6 placement beats complete in order → state reaches WAITING_FOR_VILLAGER_UI.
- Happy path (Covers AE5 / F4): Worldly Seeds deposited in town chest by gatherer (via U7 override) → chicken's bubble turns worldly_seeds + through-walls → chicken walks to that specific chest → player retrieves seeds and right-clicks chicken → heart particles + statue block placed + chicken despawned.
- Edge case (R7b): player places chest before door is placed; after door is placed and room is registered, chest beat auto-completes retroactively.
- Edge case (Covers AE6): player has a completed statue at Town A → places new flag at Town B via worldgen → new chicken spawns + full arc runs (verify independence of flag BE state).
- Edge case: player drops Worldly Seeds back into the chest mid-arc; chicken's bubble stays on worldly_seeds + through-walls; arc does NOT regress.
- Error path: `chicken-arc-forfeit == true` → controller is a no-op for all state evaluation.
- Integration (Covers F1 + F2 + F3 + F4): `CurriculumSequenceTest` drives the full arc in a simulated environment using `TestWorldAccess` + real flag BE.

**Execution note:** Integration test covers F1, F4, and as much of F3 as the test substrate can simulate (placement observations should be feasible). F2 sleep + F3 pathfinding remain in-game `/_qtdev` verification.

**Verification:**
- `CurriculumSequenceTest` passes.
- Manual `/_qtdev` run from new worldgen-spawned flag → complete arc → statue appears.
- Villager UI + Flag UI "opened" signal fires reliably and advances the state.

---

- [ ] U7. **Wand expansion, first-gatherer loot override, advancement suppression, and commands**

**Goal:** Implement the supporting integrations that are independent of the chicken entity itself: wand lights unlit campfires + flag-radius gate on the sleep path; first-gatherer first-fetch Worldly Seeds guarantee (realtime + warp); journal no longer handed to the player on `root` advancement; `skip-chicken` flag-placement argument + `/questown chicken remove` admin command.

**Requirements:** R14, R15 (+R24 scope dependency), R20, R21, R22, R23.

**Dependencies:** U1 (Worldly Seeds item must exist).

**Files:**
- Modify: `src/main/java/ca/bradj/questown/items/TownWand.java` (add unlit-campfire-light branch before the existing lit-campfire-sleep branch; both share the `TownCycle.findCampfire` flag-radius check)
- Modify: `src/main/java/ca/bradj/questown/items/CampfireSleepHandler.java` (if needed; the unlit-light branch may live here instead of in `TownWand`)
- Modify: `src/main/java/ca/bradj/questown/jobs/declarative/RealtimeWorldInteraction.java` (wrap `getResults` around line 236: check the flag BE for chicken-arc-active + first-gather-not-fired, and if so prepend one Worldly Seeds to the result list. Flag BE reachable via `inputs.town().getTownFlagBasePos()`.)
- Modify: `src/main/java/ca/bradj/questown/jobs/declarative/TimeWarpWorldInteraction.java` (wrap `getResults` around line 374: same guard logic. Flag BE reachable via the instance's `townPos` ctor field.)
- Note: the actual `ResultGenerator` lambda lives in `ExplorerWork.asWork()` lines 118-152, but it has no townPos parameter in scope (zero-arg static + `ResultGenerator.generate(ServerLevel, Collection<T>)` signature has no BlockPos). Do NOT modify ExplorerWork — wrap at the two WorldInteraction call sites instead. Duplicate guard is idempotent (keyed on `first-gather-worldly-seeds-fired` which ensures the prepend fires at most once regardless of how many call sites wrap).
- Modify: `src/main/java/ca/bradj/questown/core/advancements/AdvancementEvents.java:78-82` (gate the `addItem(getBookStack)` call behind a new `questown.chicken-arc-active-at-any-flag` check or simply remove it; the `openBookEntry` branch at line 88 is untouched)
- Modify: `src/main/java/ca/bradj/questown/commands/FlagCommand.java` (add `skip-chicken` optional literal branch — note U3 also modifies this file to pre-set `chicken-ever-spawned=true` on all command-placed flags; U7's `skip-chicken` branch is layered on top of that base)
- Create: `src/main/java/ca/bradj/questown/commands/ChickenRemoveCommand.java` (new `/questown chicken remove <pos>` admin command)
- Modify: `src/main/java/ca/bradj/questown/commands/CommandInit.java` (register `ChickenRemoveCommand`)
- Create: `src/test/java/ca/bradj/questown/items/WandLightCampfireTest.java`
- Create: `src/test/java/ca/bradj/questown/jobs/gatherer/FirstGatherWorldlySeedsTest.java`

**Approach:**
- **R14 wand-on-unlit-campfire:** In `TownWand.onRightClicked:68-71`, check block state — if `Blocks.CAMPFIRE` and NOT lit AND `TownCycle.findCampfire(...)` matches, call `level.setBlockAndUpdate(clickedPos, state.setValue(CampfireBlock.LIT, true))` and return. If lit, fall through to existing `beginCampfireSleep` path. Flag-radius gate on BOTH branches (existing `findCampfire` check).
- **Behavior-change warning:** the existing sleep path currently fires on any lit campfire (no flag-radius gate in `beginCampfireSleep` beyond the `findCampfire` town-registration check). After this change, both branches require town-flag-adjacency. Document this in the code as "wand-on-campfire is a town-level interaction — outside the town, wand is inert on campfires."
- **R15 Worldly Seeds guarantee:** The first-villager gatherer's `ResultGenerator` is constructed in `ExplorerWork.asWork()` (lines 118-152), NOT in `GathererUnmappedNoToolWorkQtrDay.java` (which is a 7-line JobID constant with no loot logic). However, `ExplorerWork.asWork()` is a zero-arg static method and `ResultGenerator.generate(ServerLevel, Collection<T>)` has no townPos parameter — so a naive wrap at ExplorerWork has no way to identify which town the fetch is for. Use this approach instead: **wrap at the call-site level, not at the generator level.** Override `getResults` in both `RealtimeWorldInteraction.getResults` (which can reach the flag via `inputs.town().getTownFlagBasePos()`) AND `TimeWarpWorldInteraction.getResults` (which has `townPos` as a ctor field). In each, check the flag BE for `chicken-ever-spawned=true && chicken-arc-forfeit=false && first-gather-worldly-seeds-fired=false`, and if the check passes, prepend a `Worldly Seeds` to the result list returned by the inner `resultGenerator.apply(...)` call. Duplicating the guard at both sites is acceptable because the guard is a few lines and is idempotent — the `first-gather-worldly-seeds-fired` bit ensures it fires only once. **Flip the bit AFTER deposit, not at generation** — otherwise a gatherer killed between generation and deposit silently deadlocks the closing beat. Hook the deposit-confirmation: the `WorksBehaviour` / `MCExtra` pipeline produces a "seeds landed in container" observable that U4's `ChickenArcConditions` already watches (`areWorldlySeedsInAnyTownContainer`). **Bit-flip responsibility is U4's:** when U4's observer sees Worldly Seeds land in a container and `first-gather-worldly-seeds-fired == false`, it flips the bit as part of its state-transition handling. U7's wrappers (at Realtime/Warp call sites) check-and-prepend only; they do NOT write the bit. This avoids duplicate writes across the two call sites.
- **R20/R21 advancement suppression:** At `AdvancementEvents.java:80`, comment out or gate the `sp.addItem(PatchouliAPI.get().getBookStack(BOOK_ID))` line. Keep the chat message ("You find an old journal on the ground") — or replace with a chicken-flavored message referring to the sudden arrival of a curious bird. Subsequent `openBookEntry` calls at line 88 are untouched; Patchouli's handling of "book not in inventory" is the deferred-to-implementation uncertainty.
- **R22 `skip-chicken`:** In `FlagCommand`, U3 already establishes the base behavior — all command-placed flags pre-set `chicken-ever-spawned=true` + `chicken-rotation-detected=true`. The `skip-chicken` literal branch is additional ergonomic sugar that explicitly surfaces intent at the command line; functionally identical to the non-skip command path. Argument kept for explicit-intent documentation and to leave a clear reader-visible signal if a future placement path requires a different default.
- **R23 `/questown chicken remove <pos>`:** New command in `ChickenRemoveCommand`. Creative-gated (`requires(AddExperienceCommand::isCreative)`). On execute, look up flag BE at given position; set `chicken-arc-forfeit = true`; kill any existing helper chicken entity at the flag; do NOT place a statue; leave all other town state intact.

**Patterns to follow:**
- `src/main/java/ca/bradj/questown/items/TownWand.java:68-71` for the campfire branch pattern
- `src/main/java/ca/bradj/questown/items/CampfireSleepHandler.java:33-83` for flag-radius gate via `TownCycle.findCampfire`
- `src/main/java/ca/bradj/questown/commands/FlagCommand.java` for creative-gated command branches
- `src/main/java/ca/bradj/questown/jobs/gatherer/ExplorerWork.java:118-152` for the `ResultGenerator` lambda construction — this is where the R15 guarantee wrapper installs
- `src/main/java/ca/bradj/questown/jobs/gatherer/ExplorerWork.java:118-152` for `ResultGenerator` lambda pattern

**Test scenarios:**
- Happy path (Covers AE4): wand on unlit campfire within flag radius → campfire lights (`CampfireBlock.LIT` becomes true).
- Happy path (Covers AE4): wand on lit campfire within flag radius → existing sleep-at-campfire triggers, player wakes at morning.
- Edge case (Covers AE4): wand on campfire NOT within any flag radius → no-op (flag-radius gate denies both branches). This is a behavior change from current; document in a learning.
- Happy path (Covers AE5 / AE7): flag has `chicken-ever-spawned=true && chicken-arc-forfeit=false && first-gather-worldly-seeds-fired=false` + first gatherer completes first fetch → the wrapped `ExplorerWork.asWork()` ResultGenerator prepends one Worldly Seeds item → villager carries seeds back → seeds land in a town chest → `first-gather-worldly-seeds-fired` flips to `true` (at deposit, not at generation).
- Happy path (warp parity): first-gatherer guarantee fires during warp — simulate an initial warp that completes the first gather via `TimeWarpWorldInteraction.getResults`; assert the wrapper runs, seeds land in storage, bit flips. Same `ResultGenerator.generate` call site is used by both realtime (`RealtimeWorldInteraction.getResults`) and warp — a single wrap at `ExplorerWork.asWork()` covers both.
- Edge case (gatherer killed between generation and deposit): kill the gatherer entity mid-return-trip → seeds were generated but never deposited → `first-gather-worldly-seeds-fired` stays `false` (deferred flip rule) → next gatherer's first fetch re-fires the guarantee. Arc does NOT deadlock.
- Edge case (chest full at deposit): gatherer returns with Worldly Seeds but the town's chest is already full → seeds drop as item entity at chest position → `first-gather-worldly-seeds-fired` stays `false` until player picks up and places seeds into any town container (which triggers R16's observation). Arc does NOT deadlock; eventually advances once seeds reach a container.
- Edge case: second fetch by same gatherer after first succeeded — `first-gather-worldly-seeds-fired == true` blocks re-firing; normal loot.
- Edge case: flag has `chicken-ever-spawned=true` but `chicken-arc-forfeit=true` → no Worldly Seeds guarantee; normal loot.
- Happy path (Covers R20): new player places worldgen-placed flag → `ApproachTownTrigger.FirstVisit` fires → chat message (flavor) displays → Patchouli book is NOT added to inventory.
- Integration (Covers R21): subsequent advancement triggers that call `openBookEntry` either work gracefully (Patchouli handles no-book case) or fail gracefully. If failure surfaces, add an inventory-check gate at line 88 as a follow-up commit.
- Happy path (Covers R22): `/qt flag ... skip-chicken` → flag placed with `chicken-ever-spawned=true` → no chicken spawns.
- Happy path (Covers R23): `/questown chicken remove <pos>` → existing chicken entity despawns; `chicken-arc-forfeit=true`; no future chicken or statue.
- Error path: `/questown chicken remove` on a non-flag position or a flag without a chicken → graceful no-op with feedback message.

**Execution note:** Integration-test the Worldly Seeds guarantee in both realtime and warp paths — this is exactly the warp-parity risk called out in institutional learnings. Use `TestWorldAccess` for realtime; warp can be driven via the existing warp-simulation test harness if one exists, else in-game `/_qtdev testall` coverage.

**Verification:**
- Wand lights unlit campfires only when flag-adjacent; same for sleep trigger.
- First gatherer's first fetch reliably produces one Worldly Seeds; subsequent fetches normal.
- Worldly Seeds guarantee works in both realtime and warp.
- `ApproachTownTrigger.FirstVisit` no longer hands out the Patchouli book; journal entries still unlock as reference.
- Both commands work; `skip-chicken` flag is a no-op at spawn controller level (documented).

---

## System-Wide Impact

- **Interaction graph:**
  - `ApproachTownTrigger.FirstVisit` — now no longer a spawn signal for the chicken; spawn is driven by a new `HelperChickenSpawnController` tick hook on the flag BE. Existing advancement-grant behavior unchanged.
  - `CampfireSleepHandler.beginCampfireSleep` — same trigger path, but the wand-on-campfire call site now also handles the unlit → lit branch. Players currently using wand-on-any-campfire for sleep outside a flag's radius will lose that capability (behavior change).
  - `TownFlagBlock.convertItemInHand` — unchanged; chicken's F3 step 6 reuses existing pressure-plate conversion.
  - `AdvancementEvents` root-branch addItem call — gated / removed; subsequent `openBookEntry` calls unchanged.
  - First-gatherer `ResultGenerator` — new guard adds Worldly Seeds when flag's chicken arc is active. All non-first-gather gatherer loot paths unchanged.
- **Error propagation:** Chicken entity runtime errors (pathfinding deadlock, renderer crash) should not cascade to flag BE state — beat state persists regardless of entity liveness. Statue transformation is atomic: either the block is placed AND the chicken despawns AND the flag completion bit is set, or none of it happens.
- **State lifecycle risks:**
  - **Partial-write on closing beat:** if the server crashes mid-transformation (chicken despawned but statue not placed yet), the flag BE's `chicken-ever-spawned` is true but no statue and no completion bit. Recovery: on next flag tick, the spawn controller would re-spawn the chicken with `AWAITING_WORLDLY_SEEDS_DELIVERY` state (because `first-gather-worldly-seeds-fired == true` but no completion). Player can re-trigger by handing Worldly Seeds again — they should still have one (not consumed until transform completes).
  - **Chunk-unload during beat transit:** chicken despawns cleanly (U3 behavior); beat state stays on flag BE; on player return, chicken respawns at the right beat target.
  - **Flag BE state schema migration:** new fields default cleanly when absent (per `TownFlagTileData` behavior). Saves from before this change load into the pre-spawn default state.
- **API surface parity:** Chicken entity is realtime-only — no API surface in the warp path. Worldly Seeds guarantee crosses the boundary; the guard lives at the loot-generation site to ensure both paths behave identically.
- **Integration coverage:** End-to-end acceptance requires covering F1→F4 with real flag BE + real chicken entity + real first-gatherer loot path. Unit tests of the state machine in isolation are necessary but not sufficient.
- **Unchanged invariants:**
  - Existing wand mechanics on doors (room registration), flag (pressure-plate conversion), and other interactions are **untouched**.
  - Existing `VisitorMobEntity` (villager) behavior unchanged.
  - Existing quest system, BOP mechanics, job assignment flow, room registration mechanics — all unchanged.
  - Existing Patchouli journal entries continue to exist and unlock on advancement triggers; only the first-visit book-add is suppressed.
  - Existing empty_town structure's jigsaw behavior unchanged — the structure still spawns in the same biomes, at the same frequency.

---

## Risks & Dependencies

| Risk | Mitigation |
|------|------------|
| Custom through-walls render type is novel in this codebase — might conflict with OptiFine/Iris shaders on 1.19.2 | Start with `RenderType.textSeeThrough(tex)` (vanilla, shader-tested); fall back to custom only if needed. Test under Oculus in dev. |
| Flag BE `saveAdditional` is documented broken — easy to wire new state incorrectly | Use `TownFlagTileData.InitPair` exclusively; add a regression test in U1 that round-trips all 4 fields. |
| `empty_town.nbt` Structure Block round-trip is undocumented — easy to break the structure | Capture the workflow as `docs/conventions/editing-empty-town-nbt.md` in U5; always test with a fresh worldgen after each edit. |
| Warp-vs-realtime divergence on first-gatherer guarantee | Place the guard at the `ResultGenerator` lambda (common path); test both realtime and warp paths explicitly in U7. |
| Patchouli `openBookEntry` may error when the player has no book in inventory (R21's assumption) | Test during U7 implementation; if it errors, add an inventory-check gate at line 88 as a follow-up. Worst case: players who triggered subsequent advancements without opening the journal see an error — not catastrophic. |
| `HelperChickenBeatOffsets` diverges from `empty_town.nbt` if one is edited without the other | Keep U5's structure edit and U3/U6's offset constants in the same commit; add a dev-only self-check (e.g., sanity-assert that beat targets resolve to blocks of the expected type in a freshly-generated structure). |
| Jigsaw placement rotates `empty_town.nbt` randomly — naive hardcoded offsets would break in ~75% of placements | Structure offsets authored in local frame; `HelperChickenRotationDetector` runs on the flag BE's first tick (NOT at `setPlacedBy`, which doesn't fire for jigsaw) via anchor-block scan; flag BE persists the rotation; `BlockPos.rotate(Rotation)` applied at runtime. Hard-failure forfeit if rotation can't be detected within N ticks (safer than silent pathing errors). |
| Flag BE first-tick may run before cross-chunk structure blocks are loaded — detector could see 0 matches and forfeit a legitimately-placed structure | Run detection with an N-tick retry budget (e.g., 20 ticks = 1 second) before deciding zero-matches means corruption. Only forfeit if the budget expires with still-zero matches. Empty_town is small enough that its chunks should co-load with the flag's chunk under normal play, but this guards the edge case. |
| FlagCommand pre-set of `chicken-ever-spawned=true` may be overwritten by `initializeFreshFlag`'s InitPair defaults | Call `initializeFreshFlag(true)` from FlagCommand explicitly after `setBlockAndUpdate`, THEN apply the pre-set writes (post-init) so the defaults don't stomp the command's intent. Alternative: thread an "init-from-command" flag into the InitPair mechanism. First option is simpler. |
| Rotation detector anchor-block scan could produce ambiguous matches if the authored anchor is at a symmetric structure-local position | Author the anchor (unlit campfire) at a structure-local offset that is unique under all 4 rotations (e.g., `(3, 0, 5)` rather than `(3, 0, 3)`). U5 test scenario enforces this. |
| The state machine's out-of-order acceptance (R7b) could misfire if multiple beats' conditions become satisfied simultaneously | Controller evaluates in enum-ordinal order; earliest unsatisfied beat advances first. Integration test covers this explicitly. |
| Villager UI / Flag UI low-text-guidance prerequisite work is not yet scoped | U6 wires the signal but the UI experience depends on separate work (explicitly deferred per Scope Boundaries). Chicken arc still ships without those enhancements — handoff will feel weak until UI work follows. |
| First-time players see the chicken's invulnerability as "this chicken ignores me when I attack" — may feel wrong | Left-click is interact (shows text), not damage; players get positive feedback. Only a persistent sustained attack with an external damage source could expose the invulnerability. Accept this as minor. |

---

## Documentation / Operational Notes

- **`docs/conventions/editing-empty-town-nbt.md`** — new workflow doc authored alongside U5.
- **No migration needed for existing playtest saves** per origin doc's Scope Boundaries — playtest-only saves; existing playtesters will restart or will not see retroactive chicken spawns (mature towns won't trigger the spawn controller since their `ApproachTownTrigger` conditions differ; worst case they see the journal suppression and the wand-on-campfire behavior change but no chicken).
- **Wand-on-any-campfire sleep** — existing behavior is narrowed by R14's flag-radius gate. Note this in release notes / tutorial update.
- **Post-ship institutional knowledge capture** — per institutional-learnings-researcher recommendation, after this lands, consider formalizing:
  - `docs/decision-town-flag-be-is-broken-save-additional.md` — capturing the source-comment warning as a formal decision doc.
  - `docs/decision-helper-chicken-architecture.md` — the "extend vanilla mob + custom renderer + closed-enum beat state" pattern, since this is the first use in the codebase.
- **In-game testing** — add `/_qtdev test chicken` or equivalent to cover the full arc in a dev world without requiring a worldgen spawn.

---

## Sources & References

- **Origin document:** `docs/brainstorms/2026-04-22-helper-chicken-onboarding-requirements.md`
- **Related project docs:** `docs/project-qa.md` (God-object architecture, testing philosophy), `docs/project-qa-2.md` (warp-vs-realtime parity, concrete-types principle), `docs/decision-qttoolaction-closed-enum.md` (enum pattern), `docs/decision-declare-and-collect-global-rules.md` (realtime/warp separation), `docs/todo/flag-speech-bubble.md` (prior bubble-rendering sketch — different location, same conceptual territory)
- **Critical code paths referenced:**
  - `src/main/java/ca/bradj/questown/core/init/EntitiesInit.java`
  - `src/main/java/ca/bradj/questown/core/init/BlocksInit.java`
  - `src/main/java/ca/bradj/questown/core/init/items/ItemsInit.java`
  - `src/main/java/ca/bradj/questown/Questown.java`
  - `src/main/java/ca/bradj/questown/mobs/visitor/VisitorMobEntity.java`
  - `src/main/java/ca/bradj/questown/mobs/visitor/VisitorMobRenderer.java`
  - `src/main/java/ca/bradj/questown/mobs/visitor/SpinningCubeLayer.java`
  - `src/main/java/ca/bradj/questown/blocks/FalseWallBlock.java`
  - `src/main/java/ca/bradj/questown/blocks/TownFlagBlock.java`
  - `src/main/java/ca/bradj/questown/items/TownWand.java`
  - `src/main/java/ca/bradj/questown/items/CampfireSleepHandler.java`
  - `src/main/java/ca/bradj/questown/core/advancements/AdvancementEvents.java`
  - `src/main/java/ca/bradj/questown/town/entity/TownFlagBlockEntity.java`
  - `src/main/java/ca/bradj/questown/town/entity/TownFlagTileData.java`
  - `src/main/java/ca/bradj/questown/jobs/gatherer/Loots.java`
  - `src/main/java/ca/bradj/questown/commands/FlagCommand.java`
  - `src/main/java/ca/bradj/questown/commands/CommandInit.java`
  - `src/main/java/ca/bradj/questown/mc/Util.java`
  - `src/main/resources/data/questown/structures/empty_town.nbt`
- **External references:**
  - MCA Reborn (https://github.com/Luke100000/minecraft-comes-alive) — chat bubble renderer
  - Emojiful by Buuz135 (https://github.com/Buuz135/Emojiful) — item-icon-above-entity
  - Custom NPCs by Noppes (https://github.com/Noppes/CustomNPCs) — per-NPC speech bubble patterns
  - Forge 1.19.2 MCP mappings for `TemptGoal`, `RenderType`, `ItemRenderer.renderStatic`, `EntityRenderDispatcher.cameraOrientation`
- **Related issues:** GitHub issue #199 (move-flag feature — may affect multi-town chicken/statue handling in a future iteration)
