---
title: Economics tab top-blocker list omits coal even though coal is the current top blocker
status: done
created: 2026-10-12
---

## Context

Playtest chat, 2026-10-12 19:25:04 (`/tmp/dev-server-7.log`):

> Economics tab does not show coal. But that's the top blocker right now.

At that moment the town's cooks were blocked on coal/fueled furnace (see
`cook-status-says-nowhere-to-work-when-blocked-on-fuel.md`, same session),
yet the economics tab's top-need view did not list coal.

## Acceptance criteria

- When a work's real blocker is a fuel/supply input, the economics tab's
  top-need list includes that item (coal) with a reason, not just the
  product-level needs.

## Notes

Same blind spot as the status-text ticket, seen from the economics side.
Start at `gannet-top-need/top-need-measure.md` (the top-need computation and
its "NoRoom.itemKey() is the product, not the missing input" correction)
and `GateReason.supplyItem()` — the tracker must key to the BLOCKING INPUT,
never the product.

## Implementation (2026-10-13, ruling b - surfacing only)

See the sibling ticket's implementation note
(`cook-status-says-nowhere-to-work-when-blocked-on-fuel.md`): the fuel
blocker now classifies as `GateReason.Blocked` on `#minecraft:coals`
instead of `NoRoom`, so `registerUnmetNeeds` -> `econ.registerUnmetNeed`
keys the BLOCKING INPUT (the tracker never keys the product). Decision
layer untouched per the user's ruling.

## Resolved (audit 2026-10-22)

f1b5967c: an unlit smelter's failing room check classifies as `GateReason.Blocked` on `#minecraft:coals` (`TownPossibleWork.smelterFuelBlocker`, line 608; `SMELTER_FUEL_KEY` line 64), so `econ.registerUnmetNeed` keys coal. The remaining flapping is the coin flip in .scratch/cook-scoring-tie/always-consider-noop-tie-mechanism.md (decision layer, queued for the dev).
