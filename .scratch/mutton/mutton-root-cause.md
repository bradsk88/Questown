# Mutton routed to food instead of stock — root-cause work

Resumed from handoff codeword `mutton` (`/tmp/handoffs/mutton.md`).

## Task
Trace the deposit path (work output -> store room) against the dining
consumption and find where mutton is routed to food instead of stock. The
previous session observed it in a held `village/self_sufficiency` run: the
hunter cycles `dining_no_table` / `dining_raw_food` and mutton never reaches
the store room.

## Code trace (verified in code, 2026-07-09)

### 1. Mutton is both a work output AND a valid raw food
- Hunter's `sword` work
  (`src/main/resources/assets/questown/jobs/specialization/hunter/01_sword.json`):
  - `needs: ["minecraft:wooden_sword", "minecraft:stone_sword", ...]` (a sword)
  - `lootItems: ["#questown:biome_loot"]` -> work OUTPUT is raw mutton,
    porkchop, beef, chicken.
- `biome_loot` tag
  (`src/main/resources/data/questown/tags/item/biome_loot.json`):
  mutton, porkchop, beef, chicken.
- All four are in `#questown:villager_raw_food`
  (`src/main/resources/data/questown/tags/item/villager_raw_food.json:43-47`,
  from `biomes_with_ore.json`).

So the hunter's work output (mutton) is a *valid raw food item*.

### 2. Dining consumes the held food
- `DinerRawFoodWork` (`jobs/declarative/meta/DinerRawFoodWork.java`, id
  `dining_raw_food`): `withNeeds(Tags.VILLAGER_RAW_FOOD)`, `hungerAtLeast:1`.
  EATS a held raw-food item.
- `DinerNoTableWork` (`jobs/declarative/DinerNoTableWork.java`, id
  `dining_no_table`): `withNeeds(Tags.VILLAGER_FOOD)` (cooked), `hungerAtLeast:1`.

So when the hunter holds mutton (its work output) and gets hungry,
`dining_raw_food` is satisfiable and the villager will select it (a hunger
need) over sitting on the mutton.

### 3. Delivery / extraction mechanism
- A work with the `PRIORITIZE_EXTRACTION` rule (`PostExtractHook`) makes the
  villager, after producing a result, transition to `EXTRACTING_PRODUCT` and
  deposit it into a container.
- `NewLeaverWork` (`jobs/gatherer/NewLeaverWork.java`) = the leaver/delivery
  work; its standardRules are exactly `[PRIORITIZE_EXTRACTION,
  NULLIFY_EXCESS_RESULTS]`.
- The hunter's `sword` work has **no rules** in its JSON (no
  PRIORITIZE_EXTRACTION). So after hunting, the hunter does NOT get forced to
  extract/deliver the mutton. The mutton stays in the hunter's inventory.

## The open question (SETTLED in code)
Does the hunter have a *leaver/delivery work* that carries the produced mutton
to the store room, which `dining` pre-empts? OR is there simply no delivery
path for the mutton work output?

**Answer: there is no extraction/delivery rule on the hunter's mutton work.**

- The hunter's mutton work is `src/main/resources/data/questown/questown_jobs/
  hunter_unmapped_sword_short.json` (also `_med` / `_full`), id `hunter/sword`:
  - `initial_request: "minecraft:mutton"` (the work is triggered by a mutton
    demand / work request).
  - `result: { type: "biome_loot", prefix: "jobs/hunter_sword" }` -> the work
    OUTPUT is mutton/porkchop/etc.
  - `block: questown:welcome_mat_block`, `room: special_quest.town_gate`.
  - `special`: the global rules are **empty `[]`** (only
    `remove_from_world` on processing/core states). **No
    `PRIORITIZE_EXTRACTION`, no leaver rule.**
- `NewLeaverWork` (the leaver/delivery base) lives in the **gatherer** package
  and its `standardRules()` are exactly `[PRIORITIZE_EXTRACTION,
  NULLIFY_EXCESS_RESULTS]`. The hunter's sword work is NOT a leaver and does
  not inherit those rules.
- `DeclarativeJob.prioritizesExtraction()` returns whether the work has
  `PRIORITIZE_EXTRACTION`. For the sword work it is false, so
  `findJobSite(..., prioritizesExtraction(), ...)` does not add an extraction
  spot. The produced mutton is NOT deposited into a container by the work.
- The mutton is produced as a **held item** (the `resultGenerator` /
  `getResults` path just hands the biome_loot back to the villager; there is no
  container deposit). And mutton is a **valid raw food** (`villager_raw_food`
  includes mutton, porkchop, beef, chicken).
- So: mutton sits in the hunter's inventory as a held raw-food item. When the
  hunter is hungry, `dining_raw_food` (consumes held `villager_raw_food`) is
  satisfiable and the work-seeker selects it -> the hunter **eats** the mutton.

**The routing point:** the produced mutton is a held raw-food item with no
extraction/delivery rule on the sword work, so `dining_raw_food` consumes it
(routed to food) instead of it ever being deposited in the store room. The
store room only ever sees it if some other path moves it there; there is no
such path on the hunter's mutton work.

The scenario's `ContainerFlowTracker` counts "produced" as items *added to the
store chest*. Since the mutton is eaten before it is ever added to the chest,
mutton "produced" stays 0 -> "mutton never reached the store room."

### Dining variants (settled)
- `dining_no_table` (`DinerNoTableWork`) consumes **`#questown:villager_food`
  = COOKED only** (cooked_mutton, cooked_beef, bread, apple, stews, soup...). The
  tag does NOT reference `villager_raw_food`.
- `dining_raw_food` (`DinerRawFoodWork`) consumes **`#questown:villager_raw_food`
  = RAW** (mutton, beef, chicken, cod, salmon, rabbit, potato, mushrooms, seeds).
- So the raw mutton (work output) can only be eaten via `dining_raw_food`, and
  only once the hunter has no cooked food. Sequence in a run: (1) hunter hunts
  -> raw mutton held; (2) eats `dining_no_table` (cooked food from the store
  seed) while it lasts; (3) when the cooked seed is gone, eats the mutton via
  `dining_raw_food`. Either way the mutton is never deposited in the store
  chest -> "produced=0". The handoff's "cycles dining_no_table /
  dining_raw_food" = the hunter eating cooked food, then eventually its own
  raw mutton once the cooked runs out.

## Held-run observations (2026-07-09)

Both runs: server-only `runServer -Dquestown.autotest.hold=true
-Dquestown.autotest.only=village/self_sufficiency` + `runClient -Pautojoin`.
The flag needs a nearby player to tick ("No players are found on server"
otherwise); the client provides it.

### Run 1 (`/tmp/self-suff-mutton.log`)
- Sword quest satisfied (wooden sword deposited).
- Hunter (5401f824) went `seeking_work -> downtime` then was REMOVED at
  `SimpleVillagerHandle.validateEntity` ("Visitor mob's parent has no record of
  entity. Removing visitor"). Flaky entity-validation race (entity dropped from
  the handle's list, likely a chunk/reload timing issue from the client
  joining). No mutton produced.
- Gatherer (175f3459) stayed; went into `dining_no_table` (eating the
  seeded cooked food). That is the gatherer's survival food (expected), not the
  mutton bug.

### Run 2 (`/tmp/self-suff-mutton2.log`)
- Hunter (5fabf71d) is ALIVE (no "Removing visitor").
- Sword quest satisfied at 23:29:57 (wooden sword deposited).
- Soft-locked ~4 min on `UNMET_ITEM` (#questown:swords) in
  `seeking_work`/`downtime` (the known soft-lock, line 570), then RECOVERED:
  at 23:34:47 the hunter entered `hunter/sword` and cycled it (hunting) through
  ~23:36:06, then went to `dining_no_table` (23:36:29) — eating the seeded
  COOKED food. The raw mutton it produced accumulates (no extraction rule) and
  would be eaten via `dining_raw_food` only once the cooked seed is gone.
- Gatherer (bb996280) similarly cycled `gather`/`rod`/`axe`/`dining_no_table`.
- This is a clean window to confirm the mutton flow, but the cooked-food seed
  (144 items) means `dining_raw_food` (eating the mutton) may not trigger within
  the run's lifetime.

## New issue surfaced (secondary)
The hunter does not transition to hunting after the sword is deposited:
`UNMET_ITEM` persists in `seeking_work`/`downtime`. Two possible causes:
(a) the hunter is not fetching the sword from the store room, or (b) the
`UNMET_ITEM` is something other than the sword. This blocks the mutton
observation (no hunting -> no mutton).

## Fix options (design question for the user)
The mutton work produces mutton but has no delivery path, and mutton is a
valid raw food, so it gets eaten. To route mutton to stock instead of food:

- **A. Add an extraction/delivery rule to the hunter's mutton work** (e.g.
  `PRIORITIZE_EXTRACTION`) so the produced mutton is deposited into a
  container after hunting. Need to confirm the target container (the work's
  block/room is the town gate, not the store chest) so the deposit lands in
  the store room.
- **B. Priority: delivery wins over dining** so a villager carrying its own
  work output delivers it before it eats it (or dining is not satisfiable with
  the producer's own work output).
- **C. Exclude the producer's work output from its own dining consumption**
  (the hunter eats cooked food from the store, not its own raw mutton).

A is the most localized; B/C are broader. This is a design decision, not a
pure bug fix.

## Next steps
1. The held runs both soft-locked the hunter on `UNMET_ITEM` (the sword), so
   the mutton flow could not be observed live. The scenario documents this
   soft-lock (line 570: "UNMET_ITEM(Swords) forever"). A clean observation
   needs a run where the hunter actually hunts.
2. If pursuing the live observation, also investigate the `UNMET_ITEM` soft
   lock (why the deposited sword does not clear the hunter's need).
3. Present the fix options (A/B/C) to the user for a design decision.

## LIVE CONFIRMATION (run 2, 2026-07-09)
Caught the mutton flow in `/tmp/self-suff-mutton2.log`:

- `23:34:47`+ hunter (5fabf71d) in `hunter/sword` (hunting, producing raw
  mutton).
- `23:36:29` hunter -> `dining_no_table` (eating the seeded cooked food).
- **`23:37:20` hunter `dining_no_table` -> `dining_raw_food`** (eating its raw
  mutton). Heartbeats 23:37:27 / 23:37:47 show it sitting in
  `hunter/dining_raw_food`; it exits to `seeking_work` at 23:38:00.

This is the handoff's observation reproduced: the hunter cycles
`dining_no_table` (cooked) then `dining_raw_food` (its own raw mutton) and the
mutton is consumed, never deposited in the store chest (no extraction rule on
the sword work). Run stayed healthy (no "Removing visitor"), and the town
evolved (roster 2 -> 3 by 23:39, a new townie minted and jobs reassigned).

### Day-2 economy report (23:39:02)
`economy produced=46 consumed=0 stock=142 demand=418`. This is an **aggregate
across all items**, not a per-item breakdown (the `ContainerFlowCounter` keeps
per-item maps internally but the scenario only logs the sum). The 46 produced
is the **gatherer's** wheat (the gatherer is a leaver work that deposits to the
chest); the mutton, having no extraction rule, is eaten (`dining_raw_food`,
caught above) and is not part of that 46. To log the per-item breakdown
(mutton produced=0) explicitly, add a line to the scenario that iterates
`ContainerFlowCounter`'s per-item `produced` map.

## Secondary (lower priority, from handoff)
A JOB_CHANGE quest opens intermittently in this scenario. A held run does not
reliably open one, and its trigger is not yet identified.
