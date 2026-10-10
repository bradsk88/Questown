# Plan: Migrate the baker job family to the cook job shape

Date: 2026-10-10
Status: **DONE 2026-10-10** — branch `1.20.6-neoforge-wip` (branch stays on -wip; no release owed by this plan)

## Why

The baker family is the last production job family still on the old timer-job shape:
`baker/bread` runs two ingredient states + a `time: 1000` wait, bread **materializes** from
the result definition, and the `bread_oven_block` is a plain `Block` whose `BAKE_STATE`
property is driven purely by the townie's work-state index (visual-only, no item storage).

The cook family is the established target shape (job data v2):

| cook job | role | slot gate | mechanics |
|---|---|---|---|
| `cook/extract` (root, `parent: null`) | takes food from furnace slot 2 | `2/#questown:villager_food` | `questown:take_from_slot_2`, `work: 0.1`, `always_consider` |
| `cook/fuel` (`unlocked_with_root_job`) | inserts coal into slot 1 | `1/minecraft:air` | `insert_into_slot_1` |
| `cook/simple_furnace_food` / `cook/fish` | inserts raw food into slot 0 | `0/minecraft:air` | `insert_into_slot_0`, result `via_other_job → cook/extract` |
| `cook/stock_fuel` / `cook/stock_ingredients` | chest → room stock | — | `add_item_to_container`, `always_consider` + `exclude_from_warp` + `note_warp` |

Consequences of the current shape (CONTEXT.md:144, ADR-0010): timer jobs never reach the
proficiency work-action seam (flat 1× speed, no leveling), and the timer state is the one
job shape the ticker-vs-advancer equivalence tests were not written for.

## Decisions

- **Job IDs**: `baker/bread` is load-bearing — `TownQuests.BAKER_BREAD_JOB`
  (concurrent-jobs onboarding quest) and the autotest entry
  `TestBlueprintRegistry.getTestableJobs()` target it. It survives, re-parented.
  The new root is `baker/extract` (mirror of `cook/extract`).
- **Recipe economics: 1 wheat + 1 coal → 1 bread** (was 2 wheat + 1 coal).
  The cook slot idiom inserts exactly one held item per job cycle into a slot that the
  job gate requires to be empty (`insertIntoSlot` refuses non-empty slots; the
  `has_item_in_slot_initially` gate cannot express "count < N"). A 2-wheat input would
  need a merge-capable insert or a count gate — a mechanism neither lane has.
  Open decision, flagged for the dev.
- **Bake duration**: 1000 ticks, same as the old timer state.
- **Block**: `BreadOvenBlock` becomes a `BaseEntityBlock` with a new
  `BreadOvenBlockEntity` (3-slot `Container`: 0 wheat, 1 coal, 2 bread). The existing
  `bake_state` property + art stay; it becomes content-derived (the BE keeps it in sync)
  instead of work-state-derived. `StatefulJobBlock` comes off the block —
  `TownWorkStatusStore`'s setter probe (`b instanceof StatefulJobBlock`) then skips it.
- **Warp**: `SmeltFurnaceWarpRule` (`furnace_smelt_warp` global rule, already declared by
  the cook jobs) is the warp-advance seam. Both `advanceProcessing` implementations get
  an oven branch: `WarpWorldAccess` (the real warp lane, in-memory slots) and
  `MinecraftWorldAccess` (live-BE loop; kept in parity for any lane that drives it).
- **Onboarding**: no `ChangeJobReward` change. Like the cook family, a plain baker job
  change unlocks the universal starter set (`baker/extract` + `baker/fuel`); the
  `baker/bread` recipe job unlocks via the skill tree / job-change quest (the onboarding
  flow spends the BOP on the baker root; the concurrent quest then targets `baker/bread`).
  Open decision if the dev wants an onboarding-gated starter-recipe grant like
  `cook/mutton` gets.

## Vocabulary → code map

| concept | where |
|---|---|
| job JSON | `src/main/resources/data/questown/questown_jobs/baker_*.json` |
| slot gate | `jobs/SlotPrecondition` (`has_item_in_slot_initially`, parsed in `ResourceJobLoader:992`) |
| insert/take rules | `integration/InsertIntoSlotSpecialRule`, `integration/TakeFromSlotSpecialRule` → `QTWorldAccess.insertIntoSlot` / `extractFromSlot` |
| container resolution | `world/MinecraftWorldAccess.resolveContainer` (chest or `BlockEntity implements Container`) |
| warp smelt advance | `jobs/special/SmeltFurnaceWarpRule` → `WarpWorldAccess.advanceProcessing` (in-memory) / `MinecraftWorldAccess.advanceProcessing` (live BE) |
| root/starter set | `Work.isUnlockedInitially` (`parentID == null` or `unlocked_with_root_job`) |
| job-change starter set | `town/rewards/ChangeJobReward.getApplier` → `ServerJobsRegistry.getDefaultWork` |
| oven block / BE | `blocks/BreadOvenBlock` (+ new `blocks/BreadOvenBlockEntity`), `core/init/TilesInit` |
| visual state | `bake_state` property, `assets/questown/blockstates/bread_oven_block.json` (unchanged) |
| equivalence oracle | `jobs/declarative/CookEquivalenceTest` + `EquivalenceTestFramework` + `world/TestWorldAccess` (new `BakerEquivalenceTest`) |
| warp oracle | `world/WarpWorldAccessTest` (advanceProcessing section) |
| autotest fixture | `commands/test/TestBlueprintRegistry.bakerBlueprint` (oven + chest + wheat/coal; no change) |

## Phases

All phases completed and verified 2026-10-10 (targeted suites green; full `:test`
1131 tests green apart from the known harness-gated `todo_…` reds — 4 baseline
+ 2 `TownVillagerMoodsTest.sleepless`; `build -x test` with importCheck +
stranglerCheck executed; `sh tools/strangler-check` exit 0).

> **Implementation note (post-plan):** the bake arithmetic that P2/P3 first
> wrote into `BreadOvenBlockEntity` and `WarpWorldAccess.advanceBreadOven`
> (a duplicated copy) was extracted into the shared registry-free
> `blocks/BreadOvenBake` state machine — the BE and the warp lane now run the
> SAME machine (the warp lane keeps one `BreadOvenBake` per oven position,
> bound to its in-memory slot list). `BreadOvenBlockEntity` cannot be
> constructed in a unit test (its constructor resolves `TilesInit.BREAD_OVEN`,
> whose supplier constructs the mod's `BreadOvenBlock` → registry-bound
> `Block.<init>` post-bootstrap), so the bake test is `BreadOvenBakeTest`
> (8 tests, incl. cross-call progress and interruption-reset), not the plan's
> `BreadOvenBlockEntityTest`. Tag-gotcha: `#minecraft:coals` is seeded onto
> the coal/charcoal holders via `builtInRegistryHolder().bindTags(...)` after
> bootstrap (unit-test tag registries are empty; see AGENTS 1.20.6 drift).
> The three old timer-shape ticker tests in `JobIntegrationTest` were deleted
> (the shape they asserted no longer exists); `BakerEquivalenceTest` is the
> behaviour oracle.

### P1 — Job data (5 files) — verified 2026-10-10

- `baker_extract.json` (new): `baker/extract`, v2, root, `priority 1000`,
  `initial_request minecraft:bread`, result `uses_special_rules`,
  block `{id: questown:bread_oven_block, has_item_in_slot_initially: "2/minecraft:bread"}`,
  room `breadmaker`, `[{work: 0.1}]`, cooldown 50, sound `entity.item.pickup`,
  global `[always_consider, furnace_smelt_warp]`,
  core_state EXTRACTING_PRODUCT `[questown:take_from_slot_2]`.
- `baker_bread.json` (rewrite): `baker/bread`, v2, `parent baker/extract`,
  `priority 2000`, `initial_request minecraft:wheat`, result
  `via_other_job [baker/extract]`, block gate `0/minecraft:air`,
  `[{tools: wheat, work: 0.1}, {ingredients: wheat}]`,
  processing_state 1 → `insert_into_slot_0`, global `[furnace_smelt_warp]`,
  sound `block.slime_block.hit`.
- `baker_fuel.json` (new): `baker/fuel`, v2, `parent baker/extract`,
  `unlocked_with_root_job true`, `priority 10000`, `initial_request minecraft:bread`,
  result `uses_special_rules`, gate `1/minecraft:air`,
  `[{tools: #minecraft:coals, work: 0.1}, {ingredients: #minecraft:coals}]`,
  processing_state 1 → `insert_into_slot_1`,
  global `[always_consider, furnace_smelt_warp]`, sound `block.stone.hit`.
- `baker_stock_wheat.json` / `baker_stock_coal.json` (update to v2): keep
  `claim_workspot` + `add_item_to_container`, add `always_consider` +
  `exclude_from_warp` + `note_warp`, `work: 0.1`, priorities 15000 / 20000,
  parents `baker/extract` / `baker/fuel` (mirror cook stock parents),
  `initial_request minecraft:bread`.

### P2 — Block + entity — verified 2026-10-10

(as specced, plus: destruction drops slot contents via `onRemove` +
`Containers.dropItemStack`; `getDrops` returns the block item only)

- `BreadOvenBlock extends BaseEntityBlock`: `newBlockEntity` → new BE; `getTicker`
  (server-side BE tick); `useWithoutItem` takes bread from slot 2 of the BE (player
  right-click unchanged in effect); `getDrops` includes BE contents; `onRemove`
  `clearContent`; `StatefulJobBlock` dropped; `bake_state` + art unchanged.
- `BreadOvenBlockEntity extends ContainerBlockEntity implements Container`:
  3 slots, `canPlace` per slot (0 wheat, 1 `#minecraft:coals`, 2 bread),
  bake tick: wheat in slot 0 + coal in slot 1 + slot 2 empty → `bakeProgress++`,
  at 1000 → 1 wheat + 1 coal consumed, 1 bread into slot 2; `bake_state` property
  kept in sync from contents (0 empty / 1 wheat only / 2 filled / 3 baking /
  4 bread ready); NBT save/load of slots + progress.
- `TilesInit.BREAD_OVEN` registration.
- `MinecraftWorldAccess.advanceProcessing`: oven branch (live BE tick loop, break when
  no bake possible).

### P3 — Warp lane — verified 2026-10-10

(via the shared `BreadOvenBake`; live-BE lane: `MinecraftWorldAccess.advanceProcessing`
oven branch calls `oven.advanceBake(ticks)`)

- `WarpWorldAccess`: oven progress map (`ovenProgress`, per position), `advanceProcessing`
  branch for positions whose `blockStates` entry is the bread-oven block — simulates the
  same 1:1:1, 1000-tick bake against the in-memory slots, `dirtyContainers` on completion.

### P4 — Tests — verified 2026-10-10

- `BakerEquivalenceTest` (mirror of `CookEquivalenceTest`): `baker_bread`,
  `baker_fuel`, `baker_extract` (incl. the work-only state-0 / empty-inventory extract
  case that `cook_extract` pins) — ticker vs advancer equivalence + real completion. 3/3 green.
- `BreadOvenBakeTest` (replaces the plan's `BreadOvenBlockEntityTest` — see note above).
- `WarpWorldAccessTest`: the oven branch itself is registry-bound (cannot be
  reached from a unit test — pinned by `oven_warpBranch_requiresRegistryBoundBlock_cannotReachInUnitTest`); end-to-end
  warp-lane coverage is tracked in
  `.scratch/baker-cook-shape-migration/warp-oven-branch-autotest.md` (autotest scenario
  to add when the fixture gets a live oven).
- Regression: full `:test` green apart from the known `todo_…` reds (see header).

### P5 — Docs — verified 2026-10-10

- `CONTEXT.md`: the "timer jobs (gatherer/baker)" line — baker is no longer timer-driven.
- `docs/special-rules-decoupling.md`: baker/* row now uses `insert_into_slot_*` /
  `take_from_slot_2` (+ `add_item_to_container` for the stock jobs).
- `blocks/COMPLEX.md`: new "The bread oven" section (its own BE type, shared
  `BreadOvenBake`, visual state, destruction, the unit-testability trap).
- `docs/adr/0010`: one-line note that the decision-time "gatherer/baker" timer set
  shrank when the baker migrated.

## Open decisions

1. **1 wheat → 1 bread** vs keeping 2 wheat (needs a new insert/gate mechanism).
   **Default applied (1:1) — pending dev objection.** Revert to 2 wheat by
   re-shaping `baker_bread.json` work_states + a merge-capable insert only if
   the economics decision demands it.
2. **Onboarding starter recipe** for a newly-changed baker (`baker/bread` grant, mirror of
   the cook's `cook/mutton` onboarding grant). **Intentionally not added** — the
   skill-tree / quest path covers it; revive (a `ChangeJobReward` branch) only if
   onboarding is observed to stall on the universal set. No `.scratch` issue owed:
   the revival criterion is observational, and this line is the home.
