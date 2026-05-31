# Questown

Domain glossary for the Questown Forge mod (1.19.2). Single-context repo. Seed it lazily — every grilling, planning, or design session adds the terms it sharpens.

## Language

### Town & inhabitants

**Townie**:
A villager-mob inhabitant of a town, drives the town's job pipeline by running jobs in real-time and during warp.
_Avoid_: villager (too generic — minecraft has its own), citizen, NPC.

**Town flag**:
The placed block that marks a town's origin and owns its persistent state (rooms, jobs, beats, registered fixtures).
_Avoid_: banner, marker.

**Warp**:
Offline simulation of what townies would have produced while the player was away from the town. Triggered by chunk reload after the player leaves and returns — **not** by sleeping. Purpose: avoid keeping the town chunk loaded.
_Avoid_: skip, fast-forward, sleep-warp.

### Jobs

**Job phase modifier**:
A pluggable rule that hooks into the job-tick pipeline (init, extract, insert, etc.). Implementations live in `jobs/special/`, `jobs/_vanilla/`, `jobs/integration/`.
_Avoid_: handler, listener, plugin.

**Special rule**:
Synonym for job phase modifier when emphasizing the JSON-declared, per-job-phase form.

**Leaver job**:
A job whose townie leaves the town to do the work and returns with products — gatherer, hunter, miner, fisher, and explorer — as opposed to an in-town crafter working at a station. Modeled **warp-only** in the autotest suite as a consequence: the townie isn't present to drive live, so its blueprint sets `realtimePhase=false` and the suite asserts only the warp pass.
_Avoid_: remote job, expedition job ("leaver" matches the `jobs/leaver/` package and `NewLeaverWork`).

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

### Testing

**Autotest suite**:
The in-game, server-driven test harness invoked with `/_qtdev test <job> <warp>` (one job) or `testall` (all). Each job runs through a `TestBlueprint`: it builds an arena, spawns a townie, runs the job, and asserts on observable outcomes across three axes — inventory deltas, **fullness**, and town **known-loot** growth. A blueprint runs a **warp pass** and, unless it's a **leaver job** (`realtimePhase=false`), a **realtime pass** too.
_Avoid_: integration tests (those are the separate JUnit suite), harness (too generic).

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
- A **Leaver job** is the kind the explorer runs; **Scouting** is its outcome.

## Example dialogue

> **Dev:** "The bubble shows cobblestone but the hint says 'hole in the wall' — which is right?"
> **Domain expert:** "Both come from the same **Presentation**, so they shouldn't disagree. If they do, the **BeatPhase** decision drifted between the two switches — that's the bug."
> **Dev:** "How do I assert this in a chicken-arc test?"
> **Domain expert:** "Set `expectedPhase = READY_TO_PLACE` on the **ChickenArcExpectation**. The result-checker derives the live phase from **PhaseInputs** and compares. Don't assert on the leaf lang key — that's table data."

## Flagged ambiguities

- **"player" in chicken-arc context.** The bubble-render path queries the *nearest* player to the flag; the click-handler path uses the *clicker*. With multiple players these can disagree, producing bubble/hint divergence not captured by the **Presentation** seam. Not yet resolved.
- **"state" vs "beat".** `ChickenBeatState` is the enum type; in conversation we say "beat" for the value. Prefer "beat" in prose; reserve "state" for code references.
