# Organizer/fetch relocates items in warp via town-state mutation, not entity replay

status: accepted
date: 2026-06-09

## Context

`organizer/fetch` is the only organizer job. Its realtime behavior is a bespoke
`FetcherHack`: a villager picks up a chest's `StockRequestItem`, walks to a
chest holding the requested ingredient, fetches it, and delivers it into the
request's own chest (the realtime path stamps the request's job block to the
chest it sits in via `TownContainers.setWorkSpot`). The job sat archived
precisely because it had **no time-warp model** — warp's generic state machine
("does any container have the ingredient → advance → drop into some non-full
container") has no notion of directed source→target routing, so warped it
misroutes or no-ops.

A fetch **relocates** an item: the town-wide item total is unchanged, so the
existing town-wide production-delta oracle cannot even tell success from a
no-op. The autotest plan (`docs/plans/2026-06-08-001`) closed that gap with a
per-position conservation oracle and shipped warp as a known-red XFAIL, leaving
the warp model itself as deferred follow-up. This ADR records how that model was
built.

Prior `onWarpTick` global rules (`GrowCropsWarpRule`, `SmeltFurnaceWarpRule`)
mutate **world blocks** through `QTWorldAccess` and return the town unchanged.
None had ever mutated **town-state container contents** inside a warp tick.

## Decision

Model the warp fetch as a town-state container relocation, performed by a
standalone `global` rule — `RelocateRequestedItemWarpRule` — declared in
`organizer_fetcher.json`. Its `onWarpTick(MCTownState, WarpTickEvent)`:

1. Scans containers for chests holding a `StockRequestItem` with a request.
2. For each, treats **the chest holding the request as the delivery target**
   (its own destination — matching what realtime's `setWorkSpot` stamps).
3. Moves the requested ingredient out of some *other* chest and into the target
   chest, one unit at a time, stopping when no other chest holds the ingredient
   or the target is down to its `require_two_free_spots` minimum.

The move is a new conserving primitive,
`TownState.withItemRelocatedTo(ingredientCheck, targetPos)`: it removes one unit
from the first non-target container that matches and adds it to the container at
`targetPos`, returning a new immutable state (or `null` if there is no target
container, it is full, or no source holds a match). It composes
`ContainerTarget.withItemRemoved` with a new `ContainerTarget.withItemAdded`
counterpart. Remove-one / add-one keeps the town-wide total conserved.

## Considered and rejected

- **Move `WorkSpotFromHeldItemSpecialRule`'s `beforeTick` workspot routing onto
  a warp-firing hook** (the deferred item the decoupling doc's Category 6
  predicted). That routing exists so a villager *carrying* the request knows
  where to deliver. Warp has no entity carrying anything, so reproducing the
  held-item state machine offline is wasted machinery. Reading the request
  straight off the chest is simpler and the `beforeTick` caveat stays moot.
- **Teach the generic warp state machine directed routing.** Would entangle a
  one-job concern into the shared `TimeWarpWorldInteraction` insert path used by
  every job. A self-contained global rule isolates the fetch model to the job
  that declares it (and it is only collected when an organizer is active).
- **Relocate from the job-block stamped in the request NBT.** In warp the
  request carries only its `request` NBT — `setWorkSpot` (a realtime entity
  action) never runs — so the job-block stamp is absent. The request's own chest
  is the reliable target.

## Consequences

- `onWarpTick` rules can now mutate town-state containers, not just world
  blocks. `RelocateRequestedItemWarpRule` is the reference for warp town-state
  mutation, as `SnapshotWorldGenLevel` (ADR-0005) is for warp world mutation.
- `withItemRelocatedTo` / `ContainerTarget.withItemAdded` are reusable conserving
  primitives, unit-tested in `TownStateTest`; they are additive (no existing
  call site changed), so other jobs are unaffected.
- The rule is `QTNativeRule` (Tier 1): it reads `StockRequestItem` NBT and
  mutates town state, with no `asServerLevel()` — identical in realtime-free
  unit context and warp.
- The former XFAIL (`organizer/fetch [warp_unsupported]`) is now the green
  `organizer/fetch [warp]` scenario; the per-position conservation oracle gates
  it. Verified: `only=organizer` 2/2, full jobs track 36/36.
- Quantity is best-effort: `WorkRequest` has no quantity field yet, so the rule
  drains available matching units into the target (bounded by free slots and a
  per-tick cap), rather than honoring a requested count.
