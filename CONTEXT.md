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

### Dining & mood

**Dining**:
The activity a villager switches to when hungry: it seeks food and eats. Three variants, in fallback order — at a table (`DinerWork`, comfortable), at the town flag (`DinerNoTableWork`, uncomfortable), or raw food when no cooked food is available (`DinerRawFoodWork`). Eating refills **fullness** and applies a **mood effect**, both as special rules at the extract phase — not as produced items (see ADR-0003).
_Avoid_: feeding.

**Fullness**:
A villager's hunger level (0 = starving). Drains over realtime ticks; refilled by dining. Realtime-only — warp does not model it (ADR-0002).

**Mood effect**:
A timed buff/debuff on a villager identified by a `ResourceLocation` (e.g. `comfortable_eating`, `uncomfortable_eating`, `are_raw_food`). Feeds the villager's mood, work-time factor, and visible mood meter.

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

## Example dialogue

> **Dev:** "The bubble shows cobblestone but the hint says 'hole in the wall' — which is right?"
> **Domain expert:** "Both come from the same **Presentation**, so they shouldn't disagree. If they do, the **BeatPhase** decision drifted between the two switches — that's the bug."
> **Dev:** "How do I assert this in a chicken-arc test?"
> **Domain expert:** "Set `expectedPhase = READY_TO_PLACE` on the **ChickenArcExpectation**. The result-checker derives the live phase from **PhaseInputs** and compares. Don't assert on the leaf lang key — that's table data."

## Flagged ambiguities

- **"player" in chicken-arc context.** The bubble-render path queries the *nearest* player to the flag; the click-handler path uses the *clicker*. With multiple players these can disagree, producing bubble/hint divergence not captured by the **Presentation** seam. Not yet resolved.
- **"state" vs "beat".** `ChickenBeatState` is the enum type; in conversation we say "beat" for the value. Prefer "beat" in prose; reserve "state" for code references.
