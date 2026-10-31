# Organizer chest sort (ideas.txt #14) — implementation plan

Grilled 2026-09-07. Companion to **ADR-0015** (gold BOPs / root-job change),
**ADR-0008** (organizer/fetch warp model + conserving container primitives), and the
`CONTEXT.md` terms **BOP**, **organizer** (job root), **chest** / **container** /
**container target**, **downtime**, **work-seeker**.

The idea was "Organizers hit a sort button on a chest, stacking items up to their
level." Grilling sharpened it to something different, and this plan builds that.

## The design (as grilled)

- **The cost (baseline).** Every item a townie deposits lands **unstacked**. Verified:
  `MinecraftWorldAccess.insertIntoContainer` calls
  `Compat.insertInNextOpenSlot(handler, item, 1)` — the `1` is `targetSize`, and that
  helper stacks a *new* deposit into a matching stack only while `count < targetSize`,
  so at `1` it never stacks; each deposit lands in a fresh empty slot. This is the
  town's baseline cost, and it is intentional, not a bug.
- **The relief.** An **organizer** runs a **sort pass**: it consolidates a chest's
  *existing* contents up to its max (the Inventory-Tweaks behavior the idea was
  inspired by). This is a **new** operation — `insertInNextOpenSlot` stacks at deposit
  time for the item being deposited and has no pass that consolidates a chest already
  full of 1-count stacks, so "raise `targetSize` to N" alone would not relieve the cost.
- **The progression.** `stack_2 → 4 → 8 → 16 → 32 → 64`, each tier unlocked behind the
  previous, each bought with a BOP **on the townie** (not the flag). A new organizer
  starts at `stack_2`. `64` is the ceiling (full vanilla stacking); the residual cost
  is the **coverage gap** — the organizer only sorts *when it is free*, so the cost
  returns whenever it is working, idling, or offline.
- **The trigger.** Opportunistic: the organizer sorts whenever it is free. Realtime
  first. (Not a one-shot player button — that was only the Inventory-Tweaks inspiration.)
- **Getting an organizer.** A new townie may arrive with the organizer as one of its
  random jobs, or a townie may be switched to organizer via the existing job-change
  path. Either is acceptable.
- **Not the numeric `level`.** "Level" in the idea was a misnomer. The per-townie
  numeric `level` (`SimpleVillagerHandle` `Map<UUID,Integer>`, feeding the XP ramp
  `baseExp × rampFactor^(level−1)`) is a *different* axis. The sort capability is a new
  per-townie stat (the max stack size), gated by BOP unlocks.

## Scope

- **Cut 1 = realtime.** The organizer sorts a chest in the live world while the town
  is present. **Warp is deferred** (see the last section): the cost accumulates offline
  and the organizer does not sort while away in cut 1.
- **No UI tab in cut 1** (the villager-UI surface is a server-autotest blind spot); the
  mechanics are verifiable without it. A "stack_N unlocked" readout may ride a later
  slice.

## Vocabulary → code map

| Concept | Where it lives today | Status |
| --- | --- | --- |
| Baseline cost (unstacked deposit) | `MinecraftWorldAccess.insertIntoContainer` → `Compat.insertInNextOpenSlot(handler, item, 1)` | verified |
| Deposit rule that calls it | `jobs/special/AddItemToContainerSpecialRule.afterInsertItem` → `event.world().insertIntoContainer(ws, item)` | verified |
| Stack-a-new-deposit (the helper the sort mirrors) | `Compat.insertInNextOpenSlot` — merges into a same-item / empty-NBT stack under `targetSize`, else a fresh slot | verified |
| A chest as a workable container | `TownContainers.fromChestBlock` / `fromChestBlockMaybe` → `ContainerTarget<MCContainer, MCTownItem>`; `findMatchingContainer`, `getUniqueItems` | verified |
| Conservating container primitives (sort basis) | `TownState.withItemRelocatedTo`, `ContainerTarget.withItemAdded` / `withItemRemoved` (ADR-0008) | verified |
| Free-townie flow (invocation seam) | `WorkSeekerJob` → `changeJobForVisitorFromBoard` (checks `hasBlockOfProgress` before downtime/rest, then seeks work) | verified |
| Downtime (a job, and its gate) | `jobs/declarative/DowntimeWork` (`DowntimeWork.matches(jobId)`); `SimpleVillagerHandle.isReadyForDowntime` / `maxTicksBeforeDowntime` | verified |
| Job definition + special rules | `jobs/Work.java`; parsed by `ResourceJobLoader`; rule strings in `jobs/SpecialRules.java`, registered in `integration/QuestownSpecialRules.java` | verified |
| BOP spend → apply (the spend to mirror) | `gui/JobChangeConfirmScreen` (unlock button) → `core/network/JobRootChangeMessage` → `SimpleVillagerHandle.handleMorning` → `guaranteedRoots` / `NBT_GUARANTEED_ROOTS` | verified |
| Per-townie numeric level (**not** this feature) | `SimpleVillagerHandle.levels` (`Map<UUID,Integer>`), `NBT_LEVELS`, XP-ramp `Math.pow(rampFactor, level−1)` | verified — distinct axis |
| **Sort pass** (consolidate a chest up to max) | **new** — no existing consolidate-all | to build |
| **Sort capability stat** (max stack per townie) | **new** — a per-townie stat like `levels`/moods, on `SimpleVillagerHandle` + `TownState.VillagerData` | to build |
| **stack_n BOP spend** (unlock a tier on the townie) | **new** — a new spend type mirroring `JobRootChangeMessage`/`JobChangeConfirmScreen` but raising the capability, not a root | to build |

## Phase 0 — Config + capability storage (no behavior)

- `Config`: the tier ladder (a fixed `2 → 4 → 8 → 16 → 32 → 64`), the per-tier BOP cost
  (open: 1 each vs escalating), and the organizer's **starting tier** (`stack_2`).
- `SimpleVillagerHandle`: a per-townie **sort capability** = the max stack size (an
  `int`, or an index into the ladder). New holder field + accessor, mirroring how
  `levels`/moods hang on the handle. Default = `stack_2` for an organizer, else
  "unlocked" = no capability (a non-organizer cannot sort).
- `TownState.VillagerData`: carry the capability (mirror how `levels`/`effects` are
  carried); `TownFlagState` build/load round-trip; `TownStateSerializer` read/write.
- **Verify:** `compileJava`; JUnit round-trip (holder → `VillagerData` → holder is
  identity); autotest `BOOT_OK` still fires.

## Phase 1 — The sort pass (consolidate a chest up to max)

- **Pure core (JUnit):** a consolidator over a list of `(item, count)` slots given a
  `max`, producing consolidated stacks up to `max`, **never merging across NBT** (mirror
  `insertInNextOpenSlot`'s empty-NBT guard). Keep it MC-free so JUnit drives it
  directly, the way `ProficiencyTest` drives `Proficiency`.
- **MC wiring:** run the consolidator against a chest via `TownContainers.fromChestBlock`
  / `ContainerTarget`, writing the result back through the conserving primitives
  (ADR-0008: `withItemAdded` / `withItemRemoved`) or `Compat.insertInNextOpenSlot` with
  the townie's `max` as `targetSize`. This is the operation a sort job/rule invokes.
- **Verify:** JUnit — consolidator bounds (n singles → ⌈n/max⌉ stacks of size ≤ max,
  NBT-tagged stacks are not merged, a full chest is unchanged); autotest — a chest
  pre-filled with `n` unstacked items, after one sort has ⌈n/max⌉ slots and the
  town-wide item total is conserved (ADR-0008 per-position oracle).

## Phase 2 — The organizer runs the sort (invocation)

- **Open decision (resolve here):** is the sort a **job** the organizer picks up when
  free (a new job under the `organizer` root, e.g. `organizer/sort`, that the
  work-seeker selects as fallback work) or a **downtime action** (sort instead of
  `RANDOM_DOWNTIME_POSE`)? Both are jobs in the domain; confirm which the work-seeker's
  fallback path accepts. Lean: a job, since the work-seeker already picks fallback work
  and downtime is itself a job.
- **Which chest(s):** the organizer sorts a chest in town (nearest / a supply chest /
  any). Confirm the selection against `TownContainers` / `findMatchingContainer`.
- **Gating:** only when the townie **has** the capability (a non-organizer, or an
  organizer at the base with nothing to do, is unaffected). The sort consumes the
  townie's action while it is free; the cost returns when it is not.
- **Verify:** autotest — an organizer with `stack_4` in a town that just deposited
  unstacked items, given free time, consolidates them up to 4; a non-organizer leaves
  them unstacked.

## Phase 3 — The BOP spend to unlock `stack_n`

- A **new spend type** (not a root-job change): spend a BOP on a townie to raise its
  sort capability to the next tier. Mirror `JobRootChangeMessage` +
  `JobChangeConfirmScreen` + the `handleMorning` apply, but the effect is
  `capability → nextTier` and it is gated behind "previous tier unlocked" (the ladder is
  sequential). The gold-BOP "guaranteed root" concept (ADR-0015) is about root jobs and
  does not apply here.
- **Verify:** autotest — spend a BOP on an organizer at `stack_2` → `stack_4`; attempt
  to skip to `stack_16` is refused (sequential gate); the applied max changes the sort
  pass output (composes with Phase 1/2).

## Test ledger

| Slice | Autotest scenario | JUnit |
| --- | --- | --- |
| Capability storage | boot OK | holder↔`VillagerData` round-trip |
| Sort pass (consolidate) | pre-fill chest, sort, assert ⌈n/max⌉ + total conserved | consolidator bounds + NBT guard |
| Organizer runs it | organizer+free time consolidates; non-organizer does not | — |
| `stack_n` spend | BOP raises tier; skip refused (sequential) | — |

Two **new test seams** are expected (add to the real code, do not simulate):
force a townie's sort capability to a known tier; read a chest's slot layout back for
assertion.

## Implementation choices (decided — these are "how", not open premise decisions)

- **Invocation:** a fallback **job** the work-seeker picks up when the organizer has no board
  work and isn't latched. Not a downtime action — downtime is mood-driven, which would couple
  sorting to mood.
- **BOP cost:** **1 BOP per tier**; the ladder is sequential (each tier behind the prior).
  **Starting tier `stack_2`** (settled in the grilling). A tunable config, not a fork.
- **Which chest:** one organizer sorts one chest at a time (a constraint, not a choice); target
  the **nearest chest** to the organizer — per-chest coverage matches the per-chest cost.

Confirm during implementation (not decisions): both organizer-acquisition paths (new townie with
a random job; the job-change path) actually route to the `organizer` root and default the
capability to `stack_2`.

## Deferred (warp)

- **Sorting while away.** In cut 1 the organizer does not sort in warp, so the cost
  (unstacked deposits) accumulates offline and the player returns to a fuller chest.
  The deferred work is a warp model for the sort, mirroring ADR-0008's
  `RelocateRequestedItemWarpRule` (a `global` rule that mutates town-state container
  contents on `onWarpTick` — the reference for warp town-state mutation). Decide then
  whether sorting runs in warp and how the coverage gap reads across a warp window.
