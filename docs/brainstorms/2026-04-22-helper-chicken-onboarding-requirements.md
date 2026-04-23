---
date: 2026-04-22
topic: helper-chicken-onboarding
---

# Helper Chicken Onboarding

## Problem Frame

Current onboarding hands the player a Patchouli journal on first arrival and relies on ~11 reading-heavy entries to sequence Phase 0–1 (campfire, wand, room registration, job board, welcome mat, storage). The author finds the journal "just too clunky" — it interrupts a world-building game with a UI-heavy reading task and breaks the immersive feel.

**Diagnosis of "clunkiness" and alternatives considered.** The specific friction is that the journal breaks immersion at exactly the moment the player is forming their first impression of the mod — they pick up a flag, place it, and the game asks them to stop playing and read a book. Cheaper alternatives considered and rejected:
- *Auto-open the next journal entry on each advancement trigger* — still surfaces the reading UI at each beat; only reduces clicks, not the reading-interruption feel.
- *Reduce entries from 11 to 3* — the player still reads, just less; doesn't change the surface.
- *In-world floating arrow above the flag* — effective for a single next-action but loses the narrative charm of the journal and doesn't scale across the ~6 sequenced beats of Phase 1.
- *Simple diegetic signs in the empty_town structure* — static; can't react to player progress.
None of these address the core desire: a guide that *feels alive* and reacts to the world without words. The spark for the chicken came from a playtest moment where the author was followed by a vanilla chicken because they were holding seeds. That emergent, alive-feeling moment is what the new onboarding intentionally replicates.

**Positioning statement.** By shipping this, Questown becomes a systems-heavy village-building mod **with intentional story beats at key moments, starting with onboarding**. The chicken is a deliberate positioning bet toward emergent charm as a differentiator — future features should align with that tone or consciously deviate.

**Why not extend the v4 quest-driven onboarding backward to cover Phase 0–1 instead?** Quest text is the exact "UI-heavy reading" problem the chicken solves — adding more quest entries in Phase 0–1 would perpetuate the issue. The chicken provides diegetic, non-verbal guidance that the quest system cannot. The two systems coexist: the chicken handles Phase 0–1 sequencing, the quest system (v4) handles Phase 1.5+ where teaching is already built on quest-driven action. The chicken's closing beats introduce the player to the villager UI and the flag UI, handing them off to the quest-driven flow.

The chicken *replaces the journal as the active sequencer* for Phase 0–1 only. The journal persists as silent reference material for players who want deeper explanation; it is no longer handed out or sequenced.

---

## Actors

- A1. New Player: first-time Questown player placing their first flag. Learns by observing chicken behavior, reading bubble icons, and optionally clicking the chicken for text clarification.
- A2. Helper Chicken: custom mob entity (extends vanilla `Chicken`; placeholder vanilla-chicken model) spawned at any town flag placed via worldgen that does not yet have its Stone Chicken Statue. Silently guides via pecks, movement, and speech bubbles.
- A3. First Villager: the town's first visitor, always assigned a gatherer job by existing game mechanics (job change requires BOP which is only earned after work). Their first fetch is guaranteed to include a Worldly Seeds item that feeds back into the closing beat.
- A4. Returning Player: a player whose previous towns have been completed. Gets a fresh chicken on each new worldgen-placed flag.

---

## Key Flows

- F1. First Arrival (arrival → wand → lit campfire)
  - **Trigger:** Player approaches a worldgen-placed town flag for the first time (that flag has never spawned a chicken and has no statue)
  - **Actors:** A1, A2
  - **Steps:**
    1. Chicken spawns next to the flag; bubble shows a **stick** icon; chicken pecks the flag
    2. Player uses a stick on the flag (existing stick-on-flag mechanic) → wand is created
    3. Chicken moves to the unlit campfire in the town structure; bubble alternates **wand + unlit-campfire** (1s cadence, hard cut, tool-first); chicken pecks campfire
    4. Player uses wand on unlit campfire → campfire lights (new wand functionality per R14)
  - **Outcome:** Player has a wand, campfire is lit, chicken transitions to sunset bubble
  - **Covered by:** R1, R5, R6, R7, R11, R12

- F2. Evening Ritual (sunset → sleep → first visitor)
  - **Trigger:** Campfire is lit
  - **Actors:** A1, A2
  - **Steps:**
    1. Chicken's bubble changes to **sunset**; on the first click (left or right), the chicken gives the player a filled locator map centered on the flag (drops at the chicken's feet if the player's inventory is full). Action-bar text via `Util.onScreenText`: "it seems like it wants you to come back in the evening." Subsequent clicks show the text again but do not grant another map.
    2. Player returns in the evening; chicken's bubble shows **wand + campfire** (alternating 1s). If the campfire was extinguished by prior sleep, the wand lights it first (reusing R14's unlit path); a second wand-click triggers sleep.
    3. Player uses wand on the lit campfire → triggers the existing "sleep at campfire" mechanic → time advances to morning → first visitor spawns via the existing `SpawnVisitorReward` path
  - **Outcome:** Night has passed, first visitor (A3) is present near the flag
  - **Covered by:** R1, R7, R9, R12, R14

- F3. Village Construction (room → registration → job board → storage → gate)
  - **Trigger:** First visitor has arrived
  - **Actors:** A1, A2
  - **Steps:**
    1. Chicken walks to the mostly-built cobblestone room in the augmented town structure. One wall block is missing. Chicken pecks that spot; bubble shows a dynamic **cobblestone** item icon. Player places cobblestone there.
    2. Chicken pecks the empty door slot; bubble shows a **door**; player places a door.
    3. Chicken pecks the placed door; bubble shows the **wand**; player uses the wand on the door → room registered.
    4. Chicken pecks a sign spot inside the registered room; bubble shows **sign**; player places a sign → auto-converts to job board. (If player places the sign earlier, it remains vanilla until the room becomes registered, then auto-converts retroactively.)
    5. Chicken pecks a chest spot; bubble shows **chest**; player places a chest → storage room registered.
    6. Chicken walks back to the flag; bubble shows **pressure-plate**; chicken pecks the flag. Player crafts a pressure plate and right-clicks the flag with it → the flag's existing mechanic converts the pressure plate into a welcome-mat item in hand. Bubble switches to **welcome-mat**; chicken walks to the ground between the two gate columns in the town structure and pecks that spot. Player places the welcome mat → gate registered.
  - **Outcome:** Town has a registered room with job board and storage, plus a welcome-mat gate. Villagers can now take jobs.
  - **Covered by:** R5, R7, R12, R13

- F4. Handoff and Closing Beat (UI introduction → Worldly Seeds → statue)
  - **Trigger:** Welcome mat is placed and at least one villager is active
  - **Actors:** A1, A2, A3
  - **Steps:**
    1. Chicken walks to a villager and pecks in their direction; bubble shows **villager-ui-icon** (a portrait). Player right-clicks the villager → villager UI opens. (The villager UI carries low-text next-action guidance; see separate scope note below.)
    2. Chicken walks to the flag and pecks it; bubble shows **flag-ui-icon** (a quest/scroll mark). Player right-clicks the flag → flag UI opens (quest list). The flag UI carries low-text next-action guidance.
    3. Chicken's bubble switches to **worldly-seeds** and stays there. The gatherer (A3) eventually returns from their first fetch carrying a Worldly Seeds item (R15 guarantees this). The villager deposits the seeds into a town chest as normal.
    4. While a Worldly Seeds item exists in any town container, the chicken's bubble is **visible through walls** (special rendering rule overriding R8's distance-proximity default). The chicken walks to the specific container and pecks at it.
    5. Player retrieves the Worldly Seeds from the chest and gives them to the chicken (right-click chicken with seeds in hand).
    6. Chicken explodes in a cloud of heart particles and is replaced in place by a Stone Chicken Statue decorative block.
  - **Outcome:** Town's onboarding is marked complete on the flag BE; statue is placed; chicken never respawns at this flag. Player knows how to use both the villager UI and the flag UI, ready for v4 quest-driven content.
  - **Covered by:** R4, R8 (through-walls rule), R15, R16, R22

- F5. Chicken Safety and Removal
  - **Trigger:** Chicken takes damage OR admin removal command is issued
  - **Actors:** A1, A2
  - **Steps:**
    1. The chicken is **invulnerable to all damage sources** in v1. Damage attempts (melee, projectile, explosion, lava, fall) play a harmless peck animation on the chicken but do not reduce its health or kill it.
    2. The chicken despawns cleanly when the player leaves the flag's activation radius, matching existing villager despawn behavior. Beat state persists on the flag BE during despawn. Chicken respawns at the flag on player return.
    3. An admin command (`/questown chicken remove` or equivalent) removes the chicken and marks its arc as forfeit (no future respawn, no statue). For players who want to be rid of the chicken despite its invulnerability.
  - **Outcome:** Chicken is griefproof by design; the 3-strike aggressive revenge mechanic originally considered is deferred to a future iteration.
  - **Covered by:** R17, R23

---

## Requirements

**Chicken entity and lifecycle**
- R1. The chicken is a custom entity extending vanilla `Chicken` (placeholder for a future custom model) that spawns adjacent to a town flag on the first time a player approaches the flag within the existing `ApproachTownTrigger.FirstVisit` radius, provided: (a) the flag was placed via worldgen (not via command), (b) the flag has no completed Stone Chicken Statue, and (c) the flag BE's `chicken-ever-spawned` bit is false. On spawn, the `chicken-ever-spawned` bit is set on the flag BE and persisted via `writeTownData`.
- R2. Exactly one chicken exists per eligible flag at any given time.
- R3. Each town flag independently tracks its chicken's beat state and completion status; new flags placed by a player who has completed the arc elsewhere get a fresh chicken that runs the full curriculum.
- R4. When the closing beat resolves, the chicken is replaced in place by a new Stone Chicken Statue decorative block. The statue is breakable with a pickaxe, drops itself as an item, and can be placed anywhere as decoration. The flag itself persists a "completed" flag that blocks any future chicken spawn at this location, even if the statue is subsequently broken or moved.
- R4b. The chicken spawns and runs its curriculum in all game modes, including creative and spectator. Creative-mode players may bypass beats via commands or item-granting, but no logic special-cases game mode to skip or fast-forward the arc.

**Commands**
- R22. The flag-placement command (currently the only non-worldgen path for placing a flag) accepts a `skip-chicken` argument. When present, the resulting flag starts with `chicken-ever-spawned` pre-set to true, so no chicken ever spawns at that flag. Command-placed flags without the flag still skip chicken spawning (command placement never triggers the chicken arc regardless, per R1).
- R23. An admin command removes the chicken from a flag's arc. Effect: despawn the chicken if present, mark the arc as forfeit on the flag BE (no future respawn, no statue), leave all other town state intact.

**Chicken movement and behavior**
- R5. The chicken extends vanilla `Chicken` and uses goal-based AI. A copy of vanilla `TemptGoal` is adapted with its predicate swapped from "player is holding seeds" to "player is within radius N of this chicken's flag." This produces the same emergent following behavior that inspired the feature, without requiring seeds in the player's hand. Radius N value is deferred to planning.
- R6. When the chicken has a curriculum beat targeting a specific position (flag, campfire, wall-block slot, door slot, sign spot, chest spot, gate-center, specific container), it path-navigates to that position and pecks it. Beat-target positions are **hardcoded offsets relative to the flag position**, authored alongside the `empty_town.nbt` structure. If the chicken cannot path to its target for 30 seconds, it teleports to the target with a poof particle. If the target is still invalid (replaced by lava, filled in, etc.), the beat is force-marked complete.
- R6b. The chicken despawns when the player leaves the flag's activation radius (matching existing villager despawn behavior). Beat state persists on the flag BE during the despawn. Chicken respawns at the flag on player return.
- R7. Chicken state transitions are driven by observable world conditions (campfire lit, wand in player inventory, door placed, room registered, welcome mat placed, Worldly Seeds in a town container, player-hand-to-chicken with Worldly Seeds) rather than a chat-command or menu-driven state machine.
- R7b. Beat state machine is **order-independent on the acceptance side**: any beat whose observable condition becomes satisfied is marked complete, regardless of which beat the chicken was currently pecking. The chicken skips to the next incomplete beat. For beats with multiple conditions, completion fires when all conditions are satisfied.

**Speech bubble and player interaction**
- R8. A speech bubble renders above the chicken's head. Default visibility: **bubble is visible when the player is within N blocks of the chicken** (N~=16), regardless of crosshair direction. Beyond N and within the flag's activation radius, the bubble renders at reduced opacity (distance fade). The bubble is hidden while any screen/menu (inventory, container UI, etc.) is open, and click-interactions on the chicken are not delivered during that state. **Through-walls exception:** while a Worldly Seeds item is present in any town container, the bubble is visible through blocks (ignoring line-of-sight occlusion) until the player delivers the seeds to the chicken.
- R9. The bubble displays a contextual icon sprite matching the current beat. Sprite inventory: `stick`, `wand`, `unlit-campfire`, `lit-campfire`, `sunset`, `map`, `door`, `sign`, `chest`, `welcome-mat`, `pressure-plate`, `worldly-seeds`, `villager-ui-icon`, `flag-ui-icon`. Block-placement beats use the actual block's item icon dynamically (no pre-authored "wall-block-type" sprite). Alternating beats reference two icons from this list in order (1-second cadence, hard cut, tool-first → target-second, conveying "use X on Y"). There are no compound "X-on-Y" sprites. During navigation between beats, the bubble shows the icon of the upcoming beat (not blank).
- R10. Left-click and right-click on the chicken both act as interactions that display a short on-screen text interpretation of the current bubble via `Util.onScreenText` (the existing helper that writes to the action bar). Left-click on the chicken does not deal damage (chicken is invulnerable per R17). **Interaction grammar:** when the bubble shows a single icon, the action target is the chicken itself (click or hand over item). When the bubble alternates two icons, the action target is the block shown second, using the item shown first; the chicken is not the click target during alternating beats.
- R11. Interaction text is a short sentence in natural in-character voice (e.g., "it seems like it wants you to come back in the evening"), not an authoritative tutorial string.

**Curriculum and beats**
- R12. The chicken's curriculum covers Phase 0 and all of Phase 1 (through the final gate/welcome-mat beat), plus two handoff beats (villager UI + flag UI) and the closing beat. The chicken does not attempt to teach Phase 1.5 food production, Phase 2, Phase 3, or any v4 extended-tutorial content. The handoff beats (F4 steps 1-2) hand the player off to the villager UI and flag UI; those UIs need "low-text next-action guidance" enhancements so they can take over as the player's guide. That UI enhancement work is called out as a **separate scope** in Dependencies — it is not part of this plan's requirements but is a prerequisite for the handoff to work.
- R13. The empty town structure (`src/main/resources/data/questown/structures/empty_town.nbt`) is augmented to include: an unlit campfire near the flag, a mostly-built cobblestone room with exactly **one missing wall block** and no door, and two gate columns with empty space between them. This environmental scaffolding replaces the need for the chicken to silently pantomime abstract multi-block concepts.
- R14. The wand's campfire interaction is redefined:
  - **Existing behavior (unchanged):** using a wand on a lit campfire triggers the existing "sleep at campfire" mechanic (`CampfireSleepHandler.beginCampfireSleep`). Currently fires on any lit campfire anywhere.
  - **New behavior (v1):** using a wand on an unlit campfire lights it (equivalent to flint-and-steel).
  - **New constraint (v1):** both wand-on-campfire actions are **gated to campfires within the activation radius of a town flag**; outside any flag's radius, the wand-on-campfire interaction is a no-op. This is a behavior change to the existing sleep path — players currently able to use wand-on-any-campfire for sleep will lose that ability for non-flag-adjacent campfires.
- R15. The first villager at a town (structurally guaranteed to be a gatherer, since job changes require BOP which requires prior work) has their first fetch's loot guaranteed to include at least one **Worldly Seeds** item. This guarantee fires once per town, tracked on the flag BE. Subsequent fetches are normal loot.
- R16. When a Worldly Seeds item is present in any town container, the chicken walks to that container and pecks at it; its bubble is **through-walls-visible** (per R8) until the player delivers the seeds to the chicken. When the player gives Worldly Seeds to the chicken (right-click chicken with seeds in hand), the chicken transforms into the Stone Chicken Statue block (hearts + replace).
- R24. **Worldly Seeds** is a new custom item. It has no use outside of the chicken arc (or it can be used like normal seeds, TBD in planning). It is obtainable exclusively from the first gatherer's first fetch at a chicken-active town.

**Mortality and safety**
- R17. In v1, the chicken is **invulnerable to all damage sources** (melee, projectile, explosion, lava, fall, mob attack). Damage attempts play a peck animation but do not reduce health. The 3-strike player-caused-kill-counter and aggressive revenge variant originally considered are deferred to a future iteration (see "Deferred for later").

**Journal coexistence**
- R20. The "Old Journal" Patchouli book is no longer handed to the player automatically on first arrival and is no longer the active sequencer for Phase 0–1. Existing journal entries continue to exist and remain available as silent reference material.
- R21. No journal entry referenced in the Phase 0–1 chicken curriculum is deleted; they continue to unlock on their existing advancement triggers for players who want to consult them.

---

## Acceptance Examples

- AE1. **Covers R1, R5, R13.** Given a player approaches a worldgen-placed town flag for the first time in a freshly-generated empty_town structure, the chicken spawns adjacent to the flag, begins following the player (via the seed-follow AI keyed on flag-radius), and its bubble displays a stick icon while it pecks the flag.
- AE4. **Covers R14.** Given a player holds the wand and right-clicks on an unlit campfire adjacent to their town flag, the campfire lights. Given the same player, during evening, right-clicks the now-lit campfire with the wand, the existing "sleep at campfire" sequence triggers and the player wakes at morning. Given the same wand is used on a campfire *not* adjacent to any town flag, the click is a no-op.
- AE5. **Covers R15, R16, R24, R4.** Given a town has an active chicken and the first gatherer returns from their first fetch, their inventory contains at least one Worldly Seeds item. When that item enters a town chest, the chicken's bubble switches to the worldly-seeds icon and becomes visible through walls. The chicken walks to the specific chest and pecks at it. When the player retrieves the Worldly Seeds and gives them to the chicken, heart particles explode and a Stone Chicken Statue block replaces the chicken's position.
- AE6. **Covers R3.** Given a player has a completed statue at Town A, when they place a new town flag at Town B via worldgen, a fresh chicken spawns at Town B's flag and runs the full Phase 0–1 curriculum. Each flag's state is independent.
- AE7. **Covers R22, R1.** Given a player places a flag via command (the only non-worldgen placement path), no chicken spawns at that flag regardless of the `skip-chicken` argument. The town is treated as "chicken arc skipped."

---

## Success Criteria

- Qualitative playtest feedback consistently leans toward "charming," "alive," or "delightful" rather than "confusing," "annoying," or "unclear." The author is the primary oracle.
- A new player reaches their first registered room and job board without ever opening the Patchouli journal and without getting stuck on what to do next.
- `/ce-plan` can be invoked on this document and produce a concrete implementation plan without having to invent product behavior, beat sequencing, or scope boundaries.

---

## Scope Boundaries

### Deferred for later

- Custom chicken entity model and bespoke animation; vanilla chicken model is used as placeholder for initial ship.
- **Bespoke art and geometry for Stone Chicken Statue.** Placeholder model/texture/blockstate/loot table are **required for ship** (Forge requires block model JSON at registration time) but can use any simple geometry (e.g., a stone-textured chicken shape). Bespoke art is deferred; registration is not.
- **3-strike aggressive revenge mechanic.** The original design included a per-player kill counter, an aggressive variant on the 4th player-caused kill, and permanent flag abandonment. Deferred to a future iteration after v1 playtest establishes whether griefing is a real issue. In v1 the chicken is invulnerable (R17).
- v4 extended tutorial phases (crafter onboarding, BOP spending quests, supply-chain quests, warp reveal) remain quest-driven; the chicken has no role in them.
- Optional narrative flourishes: chicken reactions to player actions beyond the curriculum (e.g., chicken doing something cute when you hand it a specific item) are nice-to-haves, not v1.
- Advanced chicken AI tuning (e.g., chicken avoiding lava, pathfinding around complex terrain) beyond what the vanilla chicken already does.
- Localization of bubble text interpretations beyond English.
- "Move flag" feature (GitHub issue #199) — if and when that ships, multi-town chicken/statue handling may need revisit.

### Outside this product's identity

- A pet or companion minigame: the chicken is not tameable, not feedable beyond the scripted closing beat, does not evolve, and does not persist beyond its closing beat. We are not building a Minecraft Pikmin.
- A boss-fight minigame: any future aggressive variant would be a moral consequence for griefing, not a designed combat encounter with tuned difficulty, attack patterns, or loot.
- A full-game tutor: the chicken covers Phase 0–1 + two UI-handoff beats, then turns to stone. We are not building a game-long mascot that nags the player about late-game mechanics.
- A fetch-quest NPC: the chicken does not hand the player a to-do list. Every prompt it gives is a single next action contextual to the world state.
- A replacement for ALL journal entries: lesson-style reference content (campfire sleep, farms, BOP, crafter, storage, room recipes, upgrades) stays in the journal as reference material.
- A multiplayer party mechanic: the chicken does not have group-aware behavior. MP behavior is best-effort from the single-chicken-per-flag model.

---

## Key Decisions

- **Journal becomes silent reference, not active sequencer.** Keeps the chicken's scope defined (sequencing) and avoids forcing it to silently convey every concept in the current book.
- **Scope is Phase 0–1 plus a UI-handoff bridge.** The chicken covers the original curriculum AND introduces the player to the villager UI and flag UI before turning to stone. The natural end is when villagers start requesting quest completions — at that point the UIs take over the next-action role.
- **Handoff bridge to quest-driven onboarding.** Rather than ending at the welcome-mat with a hard cliff, the chicken has two final "pointing" beats that open the villager UI and flag UI. Those UIs need low-text next-action guidance enhancements — called out as a separate scope under Dependencies.
- **Environmental scaffolding replaces silent pantomime for abstract concepts.** `empty_town.nbt` is augmented so that room, gate, and storage are 90% pre-built; the chicken only has to mime the last block/door/sign/chest. Scaffolding uses **cobblestone** with **exactly one missing wall block**.
- **Left-click and right-click both interact; chicken is invulnerable in v1.** No kill counter, no aggressive variant. Defending against griefing is deferred pending playtest evidence that it's a real problem.
- **Aggressive revenge deferred; admin remove command added.** Players who want to be rid of the chicken despite invulnerability use a remove command; no combat mechanic ships in v1.
- **Wand gains new functionality (light unlit campfire) + flag-radius gate on sleep path.** Consolidates the wand's role inside the town while bounding it to town-level context. The wand remains a town-level tool only; future "wand on X" extensions should be judged against the same flag-adjacency rule rather than becoming general utility.
- **Chicken extends vanilla `Chicken` + swaps `TemptGoal` predicate.** Simplest architecture for the target mob. `VisitorMobEntity` (Brain-based) is a reference for general patterns but the chicken uses the goal system. Much less code than reimplementing as a Brain behavior.
- **Movement despawns on walk-away, respawns on return.** Matches existing villager behavior; beat state persists on the flag BE during despawn.
- **Beats complete out of order.** Any beat whose condition becomes satisfied is marked complete regardless of the chicken's current animation target. Robust to players who shortcut (flint-and-steel, pre-existing wand, etc.).
- **Bubble is distance-proximity, not crosshair-based.** Player within N blocks sees the bubble regardless of where they're looking. Through-walls rendering specifically when Worldly Seeds are in a town container (closing-beat visibility).
- **Worldly Seeds is the closing-beat currency.** A new custom item produced exclusively by the first gatherer's first fetch. Player retrieves the seeds from a chest and hands them to the chicken. The transformation is **player-initiated**, not passive. Preserves "village produces → player delivers → chicken transforms" narrative.
- **First villager is structurally a gatherer.** No cross-job guarantee mechanism needed; the loot override lives at the gatherer's first-fetch hook.
- **Hardcoded offsets for structure-aware beats.** Chicken beat targets are compile-time constants tied to flag position + offset, matching the augmented `empty_town.nbt` layout. Simpler and more deterministic than NBT-diffing. Structure authoring and chicken code are co-updated.
- **Stone Chicken Statue as durable per-flag marker.** Flag-side completion flag is the source of truth for "no future chicken here"; the statue is a decorative trophy. Players can relocate their statue anywhere.
- **Command-placed flags skip the chicken arc entirely.** Only worldgen-placed flags spawn the chicken. Command placement has a `skip-chicken` flag for admin clarity; either way no chicken spawns there.
- **All game modes spawn the chicken (on worldgen flags).** No creative/spectator special-casing.

---

## Maintenance Surface

The plan commits to the following permanent additions that must keep working across Minecraft/Forge updates. Named explicitly so future scope additions can be weighed against the existing commitment:

- Custom entity (`HelperChickenEntity` extending vanilla `Chicken`)
- Custom entity renderer (speech bubble overlay + through-walls rendering rule)
- Custom AI goal (adapted `TemptGoal` with flag-radius predicate)
- New block (`StoneChickenStatue`)
- New item (`Worldly Seeds`)
- Expanded wand behavior (unlit campfire → light; flag-radius gate on existing sleep path)
- Loot-table augmentation at the gatherer's first-fetch site
- Flag BE persistent state (`chicken-ever-spawned`, `chicken-beat-state`, `first-gather-worldly-seeds-fired`, `chicken-arc-forfeit`) via `writeTownData`
- Two admin/command surfaces (`skip-chicken` flag argument; `/questown chicken remove` command)
- Villager UI and flag UI low-text guidance enhancements (separate scope prerequisite — see Dependencies)

---

## Dependencies / Assumptions

- `VisitorMobEntity` (`src/main/java/ca/bradj/questown/mobs/visitor/`) is referenced for general entity-registration patterns, but the chicken extends vanilla `Chicken` and uses goal-based AI. Vanilla `TemptGoal` source is extracted/adapted rather than reimplemented as a Brain behavior.
- `TownFlagBlockEntity` persistent state must be added via `writeTownData` (not `saveAdditional` — the latter is documented as broken at `TownFlagBlockEntity.java:288-312`). New state: `chicken-ever-spawned` (bool), `chicken-beat-state` (enum), `first-gather-worldly-seeds-fired` (bool), `chicken-arc-forfeit` (bool).
- `CampfireSleepHandler.beginCampfireSleep` continues to be the sleep trigger; wand-on-unlit-campfire (R14 new behavior) adds a `setBlockAndUpdate` to set `CampfireBlock.LIT=true` before dispatching the sleep handler. The existing `onWake` extinguish behavior is preserved; F2's re-lit case is handled by re-invoking wand-on-unlit-campfire.
- The flag's existing `TownFlagBlock.use()` pressure-plate-to-welcome-mat conversion (`TownFlagBlock.java:235-237`) is used as-is. The chicken's F3 step 6 flow relies on it being unchanged; no auto-placement of welcome mat by the flag.
- The first-gather Worldly Seeds guarantee hooks the gatherer's first fetch output — narrow scope since the first villager is structurally forced to be a gatherer.
- Forge 1.19.2 does not have native speech-bubble rendering. Custom entity renderer is required (entity overlay layer, billboard mesh, or similar). Implementation approach deferred to planning.
- **Separate scope prerequisite — Villager UI and Flag UI enhancements.** The chicken's closing handoff beats (F4 steps 1-2) introduce the player to these two UIs, with the expectation that the UIs themselves carry low-text next-action guidance. Those UIs currently show systems information but are not designed as the player's onboarding guide post-chicken. A separate brainstorm/plan should define what "low-text guidance" looks like in each UI. **If that work isn't done, the chicken's handoff lands on UIs that aren't ready to take over.** Flagged prominently so planning can sequence accordingly.
- Bubble icon art assets (stick, wand, unlit-campfire, lit-campfire, sunset, map, door, sign, chest, welcome-mat, pressure-plate, worldly-seeds, villager-ui-icon, flag-ui-icon) will be authored as custom sprites. Dynamic block-item icons are fetched at render time, not authored.
- The Patchouli journal's current advancement-driven unlock mechanism is unchanged; entries simply no longer get delivered to the player at first visit — specifically, the `"root".equals(path)` branch in `AdvancementEvents.java` is modified to skip the `addItem(getBookStack)` call. The `PatchouliAPI.get().openBookEntry` calls for subsequent advancements need verification against the "player has no book in inventory" case; assumption is that Patchouli handles this gracefully.

---

## Outstanding Questions

### Resolve Before Planning

_(All previously-blocking product decisions have been resolved and folded into Requirements and Key Decisions.)_

### Deferred to Planning

- [Affects R8, R9][Technical] Speech-bubble rendering approach: entity overlay renderer, nameplate variant, billboard quad, or custom shader. Includes the through-walls render path for R16.
- [Affects R5][Technical] Exact value of "radius N of flag" for the follow-AI predicate. Likely related to existing flag-visitor or approach-trigger radii (10 blocks?). Same radius or larger for R8 bubble visibility.
- [Affects R1, R3][Technical] Flag BE persistent state schema: exactly which bits are added, migration story for saves that predate these bits.
- [Affects R14][Technical] Whether `TownWand.onRightClicked`'s existing "any campfire, any location" sleep path needs a deprecation warning for players mid-session when the flag-radius gate lands.
- [Affects R15, R24][Technical] Worldly Seeds item: does it stack, can it be placed, does it grow into anything if planted, or is it a pure trigger currency?
- [Affects R20, R21][Technical] Patchouli `openBookEntry` behavior when the book isn't in the player's inventory — verify in practice.
- [Affects handoff][Scope] Villager UI and Flag UI low-text guidance enhancement work is scoped as a separate brainstorm/plan. Before `/ce-work` on this plan, that prerequisite should be initiated or at least scoped.

---

## Next Steps

-> `/ce-plan` for structured implementation planning. Note the Villager UI / Flag UI low-text enhancement scope as a parallel prerequisite.
