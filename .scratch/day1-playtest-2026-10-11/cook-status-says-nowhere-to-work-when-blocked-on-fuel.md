---
title: Cook status says "no place to work / no tools" when the real blocker is fuel
status: ready-for-agent
created: 2026-10-12
---

## Context

Playtest chat, 2026-10-12 (`/tmp/dev-server-7.log`):

- 19:16:29: > My cooks status said "they cant find anywhere to work or they
  don't have tools". But the true blocker was that they needed coal or a
  fueled furnace. We should make that more obvious to the player.
- 19:16:50: > i.e. I would have expected the cook to be asking for coal in
  their bubble and nothing else.
- 19:17:23: > They did the fuel job after I added some to a quest. But now
  they are again saying "nowhere to work"...

So twice in three minutes: a cook blocked on coal/furnace-fuel shows the
generic "no place to work / no tools" status instead of the fuel need.

## Acceptance criteria

- When a cook is blocked on fuel, the unmet-need bubble / status names the
  fuel item (coal) — the `TownieNeed.UNMET_ITEM` bubble pattern already does
  exactly this for missing work items (ADR-0011; `registerUnmetNeed`).
- Re-check after the fuel deposit: the status should not regress to
  "nowhere to work" if the next real blocker is again fuel (19:17:23).

## Notes

Related: `gannet-top-need/top-need-measure.md` (the top-need side of the same
blind spot — see `econ-tab-top-blocker-missing-coal.md` in this folder). The
decision layer is `RoomsWithWorkableStatefulBlocks` "no spots found" — a
fuel-less furnace reads as "no workable block" rather than "blocked on
input"; check how `GateReason.supplyItem()` / the `Lacking` dump sees the
furnace state.

## Implementation (2026-10-13, ruling b - surfacing only)

User ruling: "I'd try b first for sure" - the decision layer
(`RoomsWithWorkableStatefulBlocks`: what counts as a workable block) is
**untouched**. The fix names the fuel at the surfacing seam instead:

- `TownPossibleWork.smelterFuelBlocker(t, dj)`: when a smelter job
  (`furnace_smelt_warp` global rule) fails its room check AND an
  `AbstractFurnaceBlock` that is **not `LIT`** physically exists in the
  base room -> the missing input is fuel (`SMELTER_FUEL_KEY` =
  `#minecraft:coals`, the tag the family's own fuel jobs declare), not the
  site. A lit smelter failing the room check for another reason is NOT
  misnamed.
- `JobFacts.blockedOnLabel` carries it; `gateReason` maps a fuel-labelled
  facts to `GateReason.Blocked(0, [coals], [])` - so `registerUnmetNeeds`
  routes it to the item store (`reason.supplyItem()`): the UNMET_ITEM
  bubble names coal (client resolves via `Ingredients.fromString`) AND
  `econ.registerUnmetNeed` puts it in the economics tab (same seam as
  `econ-tab-top-blocker-missing-coal.md`).
- `registerUnmetNeeds` uses the best-scoring job's gate only - previously
  the cook's best job (`cook/beef`, NoRoom on the unlit furnace) swallowed
  the fuel job's own "Lacking coal"; the fuel label un-swallows it.

Test: `SmelterFuelGateReasonTest` (fuel key string + Blocked-as-supply
routing; the live LIT/room-scan half is `@Disabled` registry-bound,
held-run covered). Full suite 1195/0/52skipped.

In-game check still owed: a cook blocked on coal shows the UNMET_ITEM
bubble naming coal, and the economics tab lists coal; refueling ends the
need (clearUnmetItemNeeds on viability).
