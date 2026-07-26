# Questown

Domain glossary for the Questown Forge mod (1.19.2). Single-context repo. Seed it lazily — every grilling, planning, or design session adds the terms it sharpens.

## Language

### Town & inhabitants

**Townie**:
A villager-mob inhabitant of a town, drives the town's job pipeline by running jobs in real-time and during warp.
_Avoid_: villager (too generic — minecraft has its own), citizen, NPC.

**Townie name**:
A **townie**'s individual identity, shown the way player and named-pet names are shown (world-space nameplate) rather than in the **need bubble**, which stays icon-only. Townies are currently **anonymous** — `VisitorMobEntity` has no name handling, and the one message that identifies an individual (`message.villager.leveled_up`) prints `UtilClean.truncateMiddle(uuid)`, so the player reads *"a1b2…f9e8 reached level 3"*. Names are a **prerequisite for the per-townie systems to land**: **proficiency** exists so each townie has a distinct contribution, but "Mira is your best baker" is a thought the player cannot currently have. Custom naming is intended to be available at a cost (possibly a **Block of Progress**); the default name source is unsettled — see `docs/todo/townie-names.md`.
_Avoid_: "villager name" (see **Townie**); assuming names live in the bubble.

**Town flag**:
The placed block that marks a town's origin and owns its persistent state (rooms, jobs, beats, registered fixtures).
_Avoid_: banner, marker.

**Registered fixture**:
A position-bearing town fixture the **town flag** tracks by location: **doors**, **fence gates** (farm anchors), **welcome mats** (visitor arrival), and **heal spots** (injured-townie recovery). Stored on a `TownPosition` whose **X/Z is absolute world coords but Y is flag-relative** (`scanLevel = fixtureY − flagY`; `getY(flagY) = flagY + scanLevel`). **Rooms** are *not* fixtures — they reconstitute from registered doors via the room scan.
_Avoid_: treating rooms as a registered fixture; assuming a fixture's Y is absolute.

**Town shutdown**:
The slow, deliberate ritual that quiesces a town so its **town flag** can be moved (issue #199). All townies path back to the flag and vanish (force-absorbed on timeout so it can't deadlock); when the last one is absorbed, the flag goes **dormant** (stays in the world, holding the town's data) and a **relocation deed** becomes available on the flag for the player to collect. Intentionally costly — moving a town is a potential cheat vector, so it must not be instant: completion is gated on **both** the recall finishing **and** a configurable minimum-duration floor (`TOWN_SHUTDOWN_TICKS`, default 200 = 10s) so even a one-townie town can't pack up reactively as a panic-button. Cancelable until the last villager vanishes (re-wakes the town). Particle effects play over the flag for the duration. No material/resource cost — the real cost is the downtime plus abandoning all physical structures.
_Avoid_: "pack up"; confusing with **warp** (offline catch-up, unrelated).

**Dormant flag**:
A **town flag** left in the world after **town shutdown**, still holding the town's authoritative state but inactive (no ticking, no townies). It is the town's home during transport, which is why losing the **relocation deed** never loses the town. Placing the deed elsewhere destroys the dormant flag and re-creates the town at the new spot.

**Relocation deed**:
The item the player collects from the **dormant flag** once **town shutdown** completes. It is **presented spinning above the flag and collected by interacting with the flag** — the same way a block of progress is taken from a flag — rather than dropping as a ground item, so it can't be lost to entity-culling or grabbed by the wrong mob before the player sees it. It carries **only a reference** to the **dormant flag** (town UUID + original flag pos + dimension), not a snapshot — the data has exactly one home, so the deed is dupe-proof. Placing it **loads the dormant flag, copies its data to a new flag at the new location, then destroys the original** — transferring the **intangible** state (identity, jobs, knowledge, quests, villager roster, economics, …) and the **registered fixtures** as *absolute* positions, while leaving the physical chest/bed/station blocks behind. Placement re-anchors the flag, recomputes each fixture's flag-relative Y, and if carried fixtures fall outside `TOWN_TICK_RADIUS` opens a **confirmation screen** whose copy says the town is **"far away"** (never "out of range" — players don't parse that), defaulting to *leave behind* and requiring an explicit *bring-it-anyway* to retain. The deed can only be placed in its **origin dimension** (cross-dimension relocation is forbidden — fixtures are dimensionless absolute coords with no meaning in another world). If the original can't be reached (chunk unloadable / already gone), placement **fails loudly** rather than half-transferring. Losing the deed after collecting it never strands the town: it can be **re-issued** (a deed becomes available on the flag again) or the town **woken in place**. Eligibility to start shutdown is only *realtime + player present* and *not-already-shutting-down* — there is **no chicken-arc gate**: the arc state is carried like any tile data, and a mid-arc move simply re-derives the arc's flag-relative beats at the new offsets (the tutorial "follows you", since the controller re-reads observable conditions rather than latching).
_Avoid_: implying structures travel with it; a self-contained snapshot item; "town item" (too vague); implying it drops as a ground item (it is collected from the flag).

**Town audience**:
The set of players a town's notifications are *for* — its chat messages, tutorial toasts, and advancement/journal-page unlocks. Not yet a real concept in code, which is the problem: today the same subsystem is simultaneously **too narrow** (advancement triggers fire for `getNearestPlayer` alone, so a co-op partner's journal silently never unlocks) and **too broad** (`TownMessages.broadcastMessage` hits *every player on the server*, so unrelated towns spam each other). **Single-player is the priority; co-op only has to not break.** The forward-compatible shape is to name the audience now and implement it as proximity to the originating **town flag**, so it can later become **town membership** when players can *join* a town (future idea, not scheduled).
_Avoid_: "nearest player" as a stand-in for the audience (it is the current bug, not the concept); assuming full co-op support is a goal.

**Need bubble**:
The world-space speech bubble that surfaces *why a townie (or door) is stuck* to the watching player — the icon of an unmet need over a townie's head, or a "no enclosed room" icon over a **registered fixture** door that never resolved to a room. Generalizes the **helper chicken**'s bubble presentation to the town at large. Shown for the **single** entity nearest the crosshair within an angular threshold (**cursor proximity, not hover**) and the existing 16-block gate, with hysteresis so clustered townies don't flicker. Exists because **watching the town and helping it grow is the primary experience** and **returning** (see **Warp**) is secondary (ADR-0011) — so a townie that *looks* broken is a P0, and the reasoning to explain it (`WithReason`, `registerUnmetNeed`) is already computed but currently reaches only `QT.FLAG_LOGGER`.
_Avoid_: "quest bubble" (it reports need, not objectives); reading it as a HUD element (it is diegetic and world-space); assuming it implies dead-door **deregistration** — diagnosis and cleanup are deliberately separate, and only diagnosis is intended (ADR-0011).

**Warp**:
Offline simulation of what townies would have produced while the player was away from the town. Triggered by chunk reload after the player leaves and returns — **not** by sleeping. Purpose: avoid keeping the town chunk loaded.
_Avoid_: skip, fast-forward, sleep-warp.

### Jobs

**Job phase modifier**:
A pluggable rule (`JobPhaseModifier`) that exposes **hooks** firing at points in the job-tick pipeline. Implementations live in `jobs/special/`, `jobs/_vanilla/`, `jobs/integration/`.
_Avoid_: handler, listener, plugin.

**Special rule**:
Synonym for job phase modifier when emphasizing the JSON-declared form. A special rule is **declared at a phase** (keyed by `ProductionStatus` in the per-job rules map) and **executed by a hook** at that phase's edge — e.g. `SCOUT_LOOT` is declared at the `EXTRACTING_PRODUCT` **phase** and runs in the `afterExtract` **hook**. Some are addressed by string id (`SpecialRules.SCOUT_LOOT = "scout_loot"`, `REMOVE_FROM_WORLD = "remove_from_world"`).
_Avoid_: for `REMOVE_FROM_WORLD` specifically — it is **also** an unrelated `InventoryFullStrategy` enum value ("drop on ground when inventory full"). Same name, different axis; say which you mean.
_Footgun_ (real, load-bearing): a `beforeExtract` rule that hands the villager a product via `event.entity().tryGiveItem(...)` **must return the resulting (non-null) context**. `AbstractWorldInteraction.tryExtractProduct` runs the result generator **only if `preExtractHook` returns null** — the two are mutually exclusive paths. A rule that returns `null` (the trap: seeding from `super.beforeExtract(...)`, whose default returns null = "didn't handle") silently routes extraction to the result generator and **discards the products it just gave**. This was the latent `ChopDownTree` bug; mirror `HarvestCropSpecialRule` (seed from the passed context, return it).

_Not a footgun_: `"result": {"type": "uses_special_rules"}` (and `via_other_job`) are **self-describing markers** — both map to `ResultGenerator.alwaysEmpty()` and are **functionally equivalent to an air `item` result** (the generator is skipped whenever a rule handles extraction anyway). Prefer them over `"minecraft:air"` for readability when the product comes from a rule / another job, but the choice does **not** affect behavior — so there's no loader check to add here.

**Phase-specific rule**:
A special rule declared **under a phase key** in the job JSON — stored in `Map<ProductionStatus, Collection<String>>`, run **per-villager** via that phase's **hook**.
_Avoid_: "state rule" (collides with **processing state**).

**Global rule**:
A special rule declared **outside any phase** — under the JSON `"global"` key (`ResourceJobLoader` `case "global"` → `Work.getSpecialGlobalRules()`). The defining property is **job-wide declaration**, not a particular runtime. Two execution flavors share the key: (1) **job-wide behavior flags** checked ad hoc for one villager (`EXCLUDE_FROM_WARP`, `ALWAYS_CONSIDER`, `GLOBAL_TAKE_RANDOM_INGREDIENT`); (2) **warp world-tick effects** (`CROP_GROWTH_WARP`, `FURNACE_SMELT_WARP`) that are **collected from all active villagers, deduplicated, and run once per important tick town-wide** via `onWarpTick` (see `decision-declare-and-collect-global-rules.md`). Town-wide-warp-dedup is a *property some globals have*, not what makes a rule global.
_Avoid_: equating "global" with "town-wide" — `EXCLUDE_FROM_WARP` is global yet per-villager.

**QT-native rule** (Tier 1):
A `JobPhaseModifier` that reaches the world **only** through `QTWorldAccess` and never calls `asServerLevel()`. Works identically in realtime, warp, and `TestWorldAccess` tests. Declared by implementing the `QTNativeRule` marker interface.
_Avoid_: "pure rule", "abstract rule".

**MC-native rule** (Tier 2):
A `JobPhaseModifier` that calls `event.world().asServerLevel()` (or touches Minecraft APIs directly). May **degrade or be skipped during warp** (where `asServerLevel()` can be null — unless handed a **silent world access**). Carries no marker interface; `SpecialRulesRegistry` logs a startup warning naming every Tier-2 rule, as migration tracking. `SCOUT_LOOT` is the current example.
_Avoid_: calling these "broken" — they are intentionally un-migrated and may work via a real-but-silent level in warp.

**Phase** (villager stage):
One value of `ProductionStatus` — a stage the **townie** moves through: `GOING_TO_JOB`, `COLLECTING_SUPPLIES`, `EXTRACTING_PRODUCT`, `DROPPING_LOOT`, `waitingForTimedState` (a **timed state**), `IDLE`, etc. The class is named `ProductionStatus` for legacy reasons; the **domain word is phase**. Phases key the special-rules map (`Map<ProductionStatus, Collection<String>>`).
_Avoid_: "status" (legacy class name only — not the domain term); "state" (means block progress — see **Processing state**); "stage", "step".

**Hook**:
A `JobPhaseModifier` callback that fires at a **phase edge** (`beforeExtract`/`afterExtract`, `afterInsertItem`, `afterDropLoot`, `beforeMoveToNextState`) or a pipeline moment (`beforeInit`, `beforeTick`, `beforeFindJobSite`, `onWarpTick`, `afterWarpRecovery`). A hook **is not** a phase — it is the *moment around* a phase where rule code runs. `beforeTick` does **not** fire in warp; only `onWarpTick`/`afterExtract`-style hooks run on both paths.
_Avoid_: phase (that's the `ProductionStatus` value), event (that's the record passed *into* a hook), callback.

**Processing state**:
The integer step index (`0…maxState`) of a **job block's** work cycle — block-level *progress*, distinct from a villager **phase**. Lives in `workstatus.State.processingState()`. `maxState` is the final step, at which the block is extraction-ready.
_Avoid_: phase (that's the villager stage), status.

**Work state** (`workstatus.State`):
The per-job-block progress bundle `(processingState, ingredientCount, workLeft)`. Footgun: `workLeft` is stored **10×-scaled** — only mutate it via `setWorkLeft`/`internalSetWorkLeft`.
_Avoid_: bare "state" in prose (ambiguous — qualify as *processing state*, *work state*, or *timed state*).

**Timed state**:
A job state whose advance condition is **elapsed ticks**, not work-done or ingredients-inserted (`IStatusFactory.waitingForTimedState()`). The third advance-mode alongside work- and insert-driven states. Used for a **leaver**'s roam tail (`NEED_ROAM`) and the gatherer-shovel quarter/half-day waits, typically paired with the `REMOVE_FROM_WORLD` special rule.
_Avoid_: cooldown, wait state.

**Leaver job**:
A job whose townie leaves the town to do the work and returns with products — gatherer, hunter, miner, fisher, and explorer — as opposed to an in-town crafter working at a station. Modeled **warp-only** in the autotest suite as a consequence: the townie isn't present to drive live, so its blueprint sets `realtimePhase=false` and the suite asserts only the warp pass.
_Avoid_: remote job, expedition job ("leaver" matches the `jobs/leaver/` package and `NewLeaverWork`).

**Job root**:
The namespace half of a `JobID` (`rootId()`) that groups jobs into one **cycling pool**: a townie assigned a root shuffles only among jobs sharing it (`gatherer` → gather/axe/shovel/…; `explorer` → just explore). Distinct from a **progression parent** — a job's root and its parent's root can differ (the explorer's root is `explorer` but its parent is `gatherer/gather`).
_Avoid_: "root job" for this (that means a **starter job**); job type, category.

**Starter job**:
A job unlocked from the start — `Work.parentID == null` (`ServerJobsRegistry.getAllRootJobs()` / `isUnlockedInitially()`). The opposite is a progression-unlocked job, which names a **progression parent**.
_Avoid_: "root job" (collides with **job root**), default job.

**Progression parent**:
The `Work.parentID` a non-starter job points at; the town must have progressed through that parent before the child becomes available. May live in a different **job root** than the child — that is how a townie crosses from one root into another through progression (e.g. `explorer/explore`'s parent is `gatherer/gather`, like `armorer/*`'s parent is `crafter/stick`).
_Avoid_: prerequisite, unlock requirement.

**Ingredient** (per-state role):
An item a state's work **consumes** by inserting/using it into the workspot (`item.shrink()`). Pulled **fresh** from a container at each state that requires one — so a mid-cycle ingredient cannot be acquired during **warp** (offline supply collection is container-centric and can't reach back for one). A consumed-from-**held** ingredient is fine in warp, which is why the tool→ingredient idiom works (see **Tool**).
_Avoid_: material (fine informally, but "ingredient" matches `INGREDIENTS_REQUIRED_AT_STATES`).

**Tool** (per-state role):
An item a state requires the townie to **hold** but does not itself consume; grabbed once if not already held (re-grab skipped via `villagerAlreadyHolds`), carried for the rest of the cycle, rendered in hand. May be durability-degraded. **Not** synonymous with durable equipment — food and paper are tools. Because ingredient and tool are *per-state roles*, the same item can be a **tool** at an early state and an **ingredient** at a later one: it is grabbed-and-held cheaply, then consumed from held (the **tool→ingredient idiom** — `cook/simple_furnace_food` holds beef as a tool, then consumes it as an ingredient; this also survives warp).
_Avoid_: equipment, implement.

**Tool-encoded consumable**:
A consumable held as a **tool** so it's grabbed-once/rendered and survives warp supply collection, then spent — by one of three mechanisms: durability (real tools), the **tool→ingredient idiom** (cook beef; the explorer's paper, held at NEED_PAPER then consumed at USE_PAPER), or a dedicated **special rule** at extract (`HUNGER_FILL` eats diner food). Prefer the idiom over a special rule when the job has a state to hang the ingredient on — a special rule is the fallback for shapes that don't (e.g. a leaver whose only post-pickup phase is the extract itself, if it has no insert state).

### Proficiency

**Proficiency**:
A per-**townie**, per-**proficiency-id** multiplier on **effective work speed** — the rate at which a townie completes a job's work, applied on **both** the **realtime path** (into `SimpleVillagerHandle.getWorkSpeed(uuid)`) and the **warp path** (into the `timeFactor` / `WorkEffects.calculateTimeFactor`). "Affects work speed" means effective production rate, which **must be path-symmetric** (a proficiency that only hooked realtime would evaporate offline — the classic warp/realtime parity bug). A townie holds an **open-ended** map `proficiency-id → level ∈ [0,1]`, **seeded with 3 random** entries at spawn and growing as they work (RimWorld-style): working a job adds to its proficiency and decays all others. Only proficiency-ids that some **job definition** declares are ever reachable, which bounds the map naturally.
_Avoid_: "skill" (too generic), "experience/XP" (implies monotonic gain — proficiency decays), treating the 3 seeds as a closed set.
_Footgun_ (unresolved, balance-critical): **proficiency and mood compete for the same 1–10 band, so the happier a townie is, the less their skill matters.** Base speed is `(int)(mood × 10)` (`SimpleVillagerHandle:419`) and `applyProficiencyToWorkSpeed` clamps the product to the band `State.decrWork` requires. At the default **neutral mood (75 ⇒ base 7)** the band is roughly **4–10** — a fair spread. At **max mood (base 10)** the ceiling clamps *all* upside away: `PROFICIENCY_MAX_MULTIPLIER` (default 2.0) does nothing and only the 0.5× floor is reachable, so a maxed-out townie's mastery earns exactly the speed it would have had with no proficiency at all. ADR-0010 records this as "partly unreachable at high mood"; the sharper statement is that **the reward shrinks as mood rises and vanishes at the top**, which is backwards — feeding and housing a townie well should not erase their skill differentiation. Wants headroom (a lower well-fed base, or a wider `decrWork` band) before any job JSON declares a `proficiency_id`.

**Proficiency leveling**:
How a townie's proficiency levels change. Fires **once per completed work action** (the `decrWork`/state-advance seam in `AbstractWorkWI`, co-located with where `getWorkSpeedOf10` is consumed) — **not** per game tick. For an action of duration `D` (`cooldown_ticks` / `WorkWorldInteractions.actionDuration`) on a job with proficiency-id `P`: `level[P] += 0.001 × D` (clamped ≤ 1, creating `P` at 0 if absent); every **other** held proficiency decays `−= 0.0001 × D` (clamped ≥ 0) — RimWorld-style familiarity loss. Must credit the **same number of actions on the warp path** as realtime (warp completes many actions per visit — crediting "once per important tick" would diverge offline skill from live; this is the recurring parity footgun). The **work-action seam is the boundary**: **timer jobs** (gatherer/baker, which advance on **timed states**, not work actions) and jobs declaring **no proficiency-id** never reach the hook, so they get flat 1× speed and no leveling with no job-type branching (this is why #269 lists timer jobs as out of scope).
_Avoid_: firing per tick; leveling in warp once-per-important-tick instead of per-action.

**Proficiency ID**:
A free-form string field on a **job definition**; **multiple jobs may share one** (a shared id means shared skill across those jobs). A job that declares **no** proficiency-id yields a flat **1× multiplier** for every townie (proficiency simply doesn't apply). Distinct from a **job root** and a **JobID** — proficiency-id is an orthogonal skill axis, not an identity or cycling-pool key.
_Avoid_: conflating with **job root** / **JobID**.

### Where work happens

These five name "where a townie works" along an **abstraction axis** — coarsest to finest — plus one outlier (`work location`) that is the *definition* generating the others, not a place.

**Job site**:
The **room** a townie travels to for a job (ROOM-scoped, not a block). `EntityCurrentJobSite<ROOM>`, `IStatusFactory.goingToJobSite()/noJobSite()`, `WorkLocation.baseRoom`. When a townie is `goingToJobSite` it is walking to *the room*, then separately picking a **work spot** inside it.
_Avoid_: using "job site" for the block/station — that's a **job block**.

**Job block**:
The specific block worked within a job site (`WorkPosition.jobBlock`). The thing the townie's action transforms / extracts from.
_Avoid_: workstation (informal), job site (that's the room).

**Work position** (`WorkPosition`):
`(jobBlock, entityFeetPos)` — the job block **plus the tile the villager stands on** to work it.
_Avoid_: collapsing with **work spot** (which adds an action + score).

**Work spot** (`WorkSpot`):
A **scored, actioned** work position — `(WorkPosition workPos, action, score)`. The unit **ranked** when choosing among candidate places to work inside a job site.
_Avoid_: work position (a work spot *contains* one), job site.

**Work location** (`WorkLocation`):
The **predicate spec** defining *what qualifies* as a job block — `(isJobBlock, shouldInitializeWorkState, baseRoom)`. A definition that *generates* job blocks; **not** a coordinate.
_Avoid_: using "work location" for any actual position — it is a rule, not a place.

### Warp & ticking

The warp loop does **not** simulate every game tick — it **samples** the ticks that matter and applies their effects in order.

**Realtime path** vs **warp path**:
The two execution pathways for the same job logic. Realtime (`RealtimeWorldInteraction`) runs per game tick while the player is present; warp (`TimeWarpWorldInteraction` + `AbstractAdvanceTime`) runs offline-catch-up. Recurring bug source: the warp path **reimplements** MC mechanics manually, so behavior can diverge — always check parity.
_Avoid_: "online/offline" loosely (warp is triggered by chunk reload, not literal offline); "simulation" for realtime.

**Tick**:
The base Minecraft simulation unit (a game tick). Plain "tick" means this.

**Important tick** (the *when*):
A tick the warp loop actually visits for a villager — when a work cycle completes or a global effect should fire. The warp's **sparse sampling**, computed per-villager by a **Warper** (`ImportantTicks.forVillager`). `Warper.Tick` carries `tick()` and `ticksSincePrevious()`.
_Avoid_: treating it as "every tick" — most ticks are skipped in warp.

**Warp step** (the *what*):
A `(tick → town-mutation)` unit. All villagers' important-tick mutations are merged, **sorted chronologically**, and applied in order (`warpSteps: List<Map.Entry<Long, Function<TOWN,TOWN>>>`). Important tick = when; warp step = what.
_Avoid_: conflating with **important tick** (1:1, but names a different facet).

**Tick delta**:
Ticks elapsed since the previous visit (`WarpTickEvent.tickDelta`, `Warper.Tick.ticksSincePrevious()`). **Footgun:** a warp hook must scale proportional effects by it (crop growth ∝ delta) and never assume a fixed call frequency — forgetting this is a known warp/realtime parity bug (see `decision-proportional-deltas.md`). The `tickDelta` handed to **passive world processes** is **wall-clock** (night-inclusive), not productive — see **Wall-clock timeline** (ADR-0006).

**Productive timeline** (`productiveTicks`):
The **labour** budget of a warp window — elapsed ticks with **night subtracted** (`Signals.calculateProductiveTicks`, cut at `PRODUCTIVE_DAY_END_TICK = 11500` each day). Schedules **important ticks**; townies don't work at night, so labour rides this.
_Avoid_: "warp duration" (ambiguous — could mean wall-clock); "warp ticks".

**Wall-clock timeline** (`wallClockTicks`):
The **passive-world-process** budget — the **full** elapsed window, night included (`MAX`-clamped). Drives `onWarpTick` effects. Named **wall-clock, not "real"**, to avoid colliding with the **realtime path**: wall-clock is a *duration* axis (day-only vs round-the-clock), realtime is an *execution-pathway* axis (live vs offline). `Signals.ProductiveWallClockTimeline.wallClockOffsetAt` maps a productive offset to its wall-clock offset, placing each night at its **true** clock position (precise, not smeared) so a dusk-planted sapling is credited the whole night (ADR-0006).
_Avoid_: "real ticks"/"real timeline" (collides with **realtime path**); "elapsed ticks" (that's pre-clamp).

**Passive world process**:
A world effect that advances **24h a day regardless of villager labour** — crop random-tick growth, furnace smelting, sapling/tree growth — modeled in warp by **global rules** on the `onWarpTick` hook (`GrowCropsWarpRule`, `SmeltFurnaceWarpRule`, `GrowTreesWarpRule`). Rides the **wall-clock timeline**; contrast **labour**, which stops at night and rides the **productive timeline**. An all-night warp runs these with **no** labour.
_Avoid_: "passive rule" (they are **global rules**); conflating with labour.

**First tick**:
A **realtime-only** flag — the first `beforeTick` after init or job-change (`BeforeTickEvent.firstTick`). Does **not** fire in warp; don't hang warp-relevant logic on it.

**Warper**:
The per-villager object that computes a villager's **important ticks** and applies its **warp step** mutation (`Warper.warp(...)`, `Warper.getTicks(...)`). Distinct from **warp** (the overall offline-catch-up process).
_Avoid_: "the warper" for the whole warp system — it is per-villager.

**Silent world access**:
`MinecraftWorldAccess.silent(level)` — a wrapper over a **real** `ServerLevel` that no-ops sound side-effects (`if (silent) return`). The warp `postExtractHook` uses it so an **MC-native (Tier 2)** rule like `SCOUT_LOOT` gets a non-null `asServerLevel()` even offline. A *fourth* world-access flavor, distinct from in-memory `WarpWorldAccess` (see `decision-silent-world-access.md`).
_Avoid_: assuming warp always means a fake/in-memory world — silent access is the real level with muted sound.

**Worldgen shim**:
A `WorldGenLevel` implementation (`VoidLevel`, `SnapshotWorldGenLevel`) that lets a **real MC worldgen feature** (`TreeFeature.place`) run **behind the `QTWorldAccess` seam**. `VoidLevel` delegates reads to the real level and **discards writes** (a dry-run probe — realtime plantability); `SnapshotWorldGenLevel` reads **snapshot-first** and routes writes **into `WarpWorldAccess`'s snapshot/dirty-set** (warp growth), with its own dry-run mode. A **different axis** from a `QTWorldAccess` flavor: a shim is what a **Tier-1 rule**'s world-access method (`canTreeGrowAt`/`growTreeAt`) uses *internally* to invoke vanilla worldgen — rules never see it. The decoupling doc's intended reference example for complex world interaction under warp (ADR-0005).
_Avoid_: calling it a world-access flavor or a `QTWorldAccess`; "fake level".

### Containers & supply

Note the **inversion trap**: a **supply room** *holds* supplies; **rooms needing villager input** *lack* them.

**Chest**:
The placed block. Its inventory is an MC **container**.

**Container** (MC):
The vanilla inventory interface. QT's item-accepting variant is `ContainerTarget.Container<I>`.
_Avoid_: using "container" for the job-facing handle — that's a **container target**.

**Container target** (`ContainerTarget`):
The **job-facing handle** on a container a townie pulls from / inserts into — rankable and filterable. Carries a **rank boost** so a job-specific container outranks a plain chest in decision-making. `ContainerTarget.REMOVED` is the tombstone sentinel.
_Avoid_: plain "container" (that's the MC interface), "chest" (a chest is one *kind* of backing block).

**Supply room** (canonical):
A **registered room** whose containers a townie draws supplies from / deposits products to. Only **registered** rooms (door/gate-anchored) are scanned — meta-rooms (welcome mat, block room) don't count.
_Avoid_: "container room" (informal); using it for rooms that *need* supplies — see **rooms needing villager input**.

**Rooms needing villager input** (`RoomsNeedingVillagerInput`):
The **inverse demand cache** — rooms that **lack** a required ingredient/tool/work, keyed by **processing state** (`Map<Integer, …NVIRoom>`, `dueToWorkOnly` flag). The opposite of a **supply room**: it lists rooms *missing* what's needed, not rooms holding stock. **"Input" = villager-supplied** (a townie must bring items / do work) — **never** player input.
_Avoid_: reading "input" as player input; reading it as a list of rooms that *have* supplies (it's the demand side).

### Dining & mood

**Dining**:
The activity a villager switches to when hungry: it seeks food and eats. Three variants, in fallback order — at a table (`DinerWork`, comfortable), at the town flag (`DinerNoTableWork`, uncomfortable), or raw food when no cooked food is available (`DinerRawFoodWork`). Eating refills **fullness** and applies a **mood effect**, both as special rules at the extract phase — not as produced items (see ADR-0003).
_Avoid_: feeding.

**Fullness**:
A villager's hunger level (0 = starving). Drains over realtime ticks; refilled by dining. Realtime-only — warp does not model it (ADR-0002).

**Mood effect**:
A timed buff/debuff on a villager identified by a `ResourceLocation` (e.g. `comfortable_eating`, `uncomfortable_eating`, `are_raw_food`). Feeds the villager's mood, work-time factor, and visible mood meter.

### Gathering & scouting

**Scouting**:
What the explorer job does: an expedition that brings back a **gatherer map** for a biome and *learns* one loot drop available in that biome — without taking the item. The learned drop is recorded as **known loot**; the explorer doesn't acquire it.
_Avoid_: gathering (gathering = actually collecting items; scouting = discovering what's collectable).

**Known loot**:
The set of (biome, tool-prefix) → items a town has discovered, held in the `KnowledgeStore`. Gates what a gatherer can bring back, but only for biomes the town holds a **gatherer map** for.

**Gatherer map**:
An item stamped with a biome. Its presence in a town chest is what makes that biome (and its known loot) usable by gatherers.

### Requests

**Job board**:
The in-town surface where the player requests products from townies. It offers only **fulfillable** products — the outputs of jobs the town has currently **unlocked** — so what the player can request matches exactly what a townie will build. The same unlock gate the work-seeker uses to pick up work also filters the board (roadmap #182), so the board never offers a request no townie can satisfy. It is **not aspirational**: a **progression-unlocked** product does not appear until its tier is unlocked. Gatherer-style products are additionally narrowed to **known loot** (plus an always-known floor, e.g. wheat seeds), still behind the job's unlock.
_Avoid_: catalog (the board is not "everything craftable in the game"), quest board.

**Work request**:
A player's standing request for a product, placed on the **job board**; a townie's work-seeker *prefers* a requested product over freely chosen work, but falls back to its own choice if the request goes unmet for long enough.

**Stock request**:
A request created at the *clipboard* (obtained from the **town flag**) and carried as a physical item, for a product to be kept in stock — fulfilled by the (deprecated) fetcher, `organizer/fetch`. Drawn from the same fulfillable product set as the **job board** and gated the same way (outputs of unlocked jobs only), so the two request surfaces stay consistent. Revisit this gate if the fetcher/`organizer` is ever redesigned to source items the town can't itself produce.

### Progression

**Block of Progress** (BOP):
The town's progression currency, earned by a **townie** leveling up and spent by the player on the skill tree (job unlocks). Held **one at a time, per townie**: `SimpleVillagerHandle.addExperience` **early-returns while a townie holds an uncollected block**, so that townie accrues no further experience until the player collects it.

That stall is **intentional, and it is the design's load-bearing counterweight to warp**: the player only collects blocks if they **regularly check in with the village**. A town left alone keeps producing items but stops progressing — which is what makes returning a habit rather than an errand, and is consistent with **watching the town being the primary experience** (ADR-0011). Do not "fix" the early return; it is the vision.
_Avoid_: reading the stall as a bug; treating BOPs as an idle/offline resource that accumulates while away.
_Open_: the stall is currently **invisible** — nothing tells the player their town has stopped leveling. The intended surface is a **need bubble** on the holding townie (diegetic and near-range, so it still requires being in town — it makes the check-in *legible* without making it *remote*). Higher BOP rates are a planned reward for future "scientist"-style jobs.

### Quests & rewards

**Morning reward**:
A reward queued for delivery at the next in-game **morning** rather than immediately — `MCDelayedReward.getApplier()` → `town.addMorningReward(child)`, held in `MCMorningRewards` on the flag BE and popped by the flag ticker's `onMorning`/`morningTick`. Not every quest reward is delayed; some apply at once. `hasPendingSpawnVisitor()` answers the **visitor-specific** sub-question ("is a new townie arriving in the morning"), which is **narrower** than "is *any* morning reward queued".
_Avoid_: assuming all quest rewards are delayed; reusing `hasPendingSpawnVisitor()` as a generic "reward pending" signal.

**All quests done** (caught-up state):
The transient state where a town's quest list is **non-empty and every quest is `COMPLETED`** — the lull before the next batch appears. **Not** a terminal "tutorial graduated" milestone: quests regenerate continuously (tutorial phases + the procedural quest garden + reward-spawned batches), so this state recurs and un-sets itself. Surfaced on the **town** flag quests tab as an empty-state (roadmap #236). The empty quest list is **not** "all done" (the vacuous-true trap).
_Avoid_: reading it as "tutorial complete" / a latching flag; treating zero quests as "all done".

### Testing

**Autotest suite**:
The in-game, server-driven test harness invoked with `/_qtdev test <job> <warp>` (one job) or `testall` (all), and headlessly via `./gradlew runServer -Dquestown.autotest=true`. Each **scenario** runs through a `TestBlueprint`: it builds an arena, spawns a townie, runs the job, and asserts on observable outcomes across three axes — inventory deltas, **fullness**, and town **known-loot** growth. A blueprint runs a **warp pass** and, unless it's a **leaver job** (`realtimePhase=false`), a **realtime pass** too. The suite reports `N/N` over scenarios; the headless run can be narrowed to one by name substring (`-Dquestown.autotest.only=<name>`).
_Avoid_: integration tests (those are the separate JUnit suite), harness (too generic).

**Scenario**:
One targetable autotest entry — a single `TestBlueprint` paired with a display `name()` (e.g. `fisher/fish`, `arborist/cut_trees [full_cycle]`, `eating/eat_raw_food`, the chicken-arc `stick_peck_and_follow_spawn`). The unit the suite iterates and the unit `-Dquestown.autotest.only=` matches against. A single **job** may have several scenarios (a base plus edge-case variants), and many scenarios aren't jobs at all (eating, worldgen, chicken-arc).
_Avoid_: job (a job can own multiple scenarios), test case, blueprint (that's the data; the scenario is the named, runnable entry).

### Helper chicken arc

**Helper chicken**:
A guide entity that walks a new player through onboarding by pecking, displaying bubbles, and waiting for player actions.

**Chicken arc**:
The staged onboarding sequence the helper chicken walks the player through: stick → wand → campfire → wall → door → sign → chest → welcome mat → ui → seeds delivery, branched by sunset map.
_Avoid_: tutorial, quest, walkthrough.

**Beat**:
One step of the chicken arc. Identity is the `ChickenBeatState` enum value (e.g. `WAITING_FOR_STICK`, `SUNSET_AND_MAP`).
_Avoid_: state (ambiguous), step, stage.

**BeatPhase**:
A sub-state within a beat for presentation. Selected from `PhaseInputs`. One of: `DEFAULT`, `NEED_TO_FETCH`, `READY_TO_USE`, `READY_TO_PLACE`, `PREPARING`, `AWAITING_NIGHT`. Single-phase beats use `DEFAULT`.

**PhaseInputs**:
The world-state booleans that select a beat's active phase: `(hasItem, chestSpawned, isNight)`.

**Presentation**:
A `(bubble, hintKey, plainKey)` row in the per-(beat, phase) presentation table. Authored once in `ChickenArcPresentation`, read by both the bubble-render path and the click-handler hint path.

## Relationships

- A **Town flag** owns zero or more **Townies** and (during onboarding) zero or one **Helper chicken**.
- A **Helper chicken** drives one **Chicken arc** scoped to its owning **Town flag**.
- A **Chicken arc** is at any time on exactly one **Beat**, in exactly one **BeatPhase** for that beat.
- A **(Beat, BeatPhase)** maps to exactly one **Presentation**.
- A **Townie** runs **Jobs** advanced by **Job phase modifiers**.
- A **Special rule** is declared **at a phase** (or **global**) and executed **by a hook** at that phase's edge.
- A townie travels to a **job site** (the room), ranks **work spots** inside it, and acts on a **job block**; a **work spot** wraps a **work position** = `(job block, feet)`.
- A townie draws supplies from a **supply room**'s **container targets**; **rooms needing villager input** is the demand side (rooms *missing* supplies), keyed by **processing state**.
- The **warp** loop visits each townie's **important ticks** (the *when*); each becomes a **warp step** (the *what*); proportional global effects scale by **tick delta**.
- A warp window carries **two timelines**: **labour** rides the **productive timeline** (stops at night); **passive world processes** ride the **wall-clock timeline** (round-the-clock). The productive offset of each warp step maps to a wall-clock offset that places night precisely (ADR-0006).
- A **Leaver job** is the kind the explorer runs; **Scouting** is its outcome.

## Example dialogue

> **Dev:** "The bubble shows cobblestone but the hint says 'hole in the wall' — which is right?"
> **Domain expert:** "Both come from the same **Presentation**, so they shouldn't disagree. If they do, the **BeatPhase** decision drifted between the two switches — that's the bug."
> **Dev:** "How do I assert this in a chicken-arc test?"
> **Domain expert:** "Set `expectedPhase = READY_TO_PLACE` on the **ChickenArcExpectation**. The result-checker derives the live phase from **PhaseInputs** and compares. Don't assert on the leaf lang key — that's table data."

## Flagged ambiguities

- **"player" in chicken-arc context.** The bubble-render path queries the *nearest* player to the flag; the click-handler path uses the *clicker*. With multiple players these can disagree, producing bubble/hint divergence not captured by the **Presentation** seam. **Partly resolved (2026-07-26):** single-player is the priority and co-op only has to not break, so nearest-player is an acceptable approximation here and this divergence is not worth chasing on its own. It becomes a real defect only when the **town audience** concept lands (and properly when players can *join* a town).
- **"state" vs "beat".** `ChickenBeatState` is the enum type; in conversation we say "beat" for the value. Prefer "beat" in prose; reserve "state" for code references.
- **"phase" across contexts.** In the **jobs** domain, **phase** = a `ProductionStatus` value (a townie's stage). In the **chicken-arc** domain, **BeatPhase** is an unrelated presentation sub-state of a beat. Same word, different bounded contexts — always qualify when both are in play.
- **`REMOVE_FROM_WORLD` overload.** A `SpecialRules` string (timed-state leaver step) **and** an `InventoryFullStrategy` enum value (drop-on-ground). Disambiguate by which type you mean.
- **"input" in `RoomsNeedingVillagerInput`.** Means villager-supplied work/items, **not** player input; and it's the *demand* side (rooms lacking supplies), the inverse of a **supply room**.
- **"real" in the warp subsystem.** Resolved (ADR-0006): the round-the-clock passive budget is **wall-clock** (`wallClockTicks`), never "real" — "real" is reserved for the **realtime path** (live, player-present execution). Two different axes: wall-clock = duration (day-only vs round-the-clock), realtime = pathway (live vs warp). If you see "real ticks"/"real timeline" anywhere, read it as wall-clock and fix it.
