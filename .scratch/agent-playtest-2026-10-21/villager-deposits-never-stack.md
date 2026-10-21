---
title: 'Villager deposits never stack (by design, not a bug)'
status: wontfix
origin: 2026-10-21 agent playtest (fresh world, day 6)
---

**Resolution (2026-10-21, dev): by design.** Villagers cannot stack items; it is a deliberate
storage cost (`docs/project-qa.md`). The agent missed it because the rule was only in
`docs/project-qa.md`/`docs/onboarding-v4.md`/the organizer plan, not in CONTEXT.md or at the
deposit code. Now in CONTEXT.md "Unstacked deposit", `docs/INDEX.md`, and comments at
`Jobs.tryDropLoot` and `MinecraftWorldAccess.insertIntoContainer`. The "Next" section below
is void.

## Facts

- `Jobs.tryDropLoot` puts each held item into the **first empty slot** of the target container
  (`if (target.getItem(i).isEmpty()) { ... setItem(i, mct.toItem()) }`). It never merges into an
  existing stack of the same item.
- Live, day 6: the storeroom's second chest held coal x1 in four separate slots, chicken x1 in five,
  porkchop x1 / beef x1 repeated, every one a villager deposit. Stacks placed by the player
  (bread x5) or by the bonus-food claim (carrot x20) are normal stacks.
- So a 27-slot chest holds 27 deposited items instead of up to 27 x 64. With a hunter, a miner and a
  cook, two chests were nearly full after ~5 in-game days.

## Why it matters

Likely root cause of `.scratch/playtest-2026-10-21/miner-stops-cycling.md`: the dev's miner stopped
finishing trips once storage filled (NO_SPACE + 150 s resets, reproduced by
`miner/coal [storage_full]`). Storage was full far sooner than the item count suggests.

## Next

- Check whether any other deposit path (cook output, `ContainerTarget.withItemAdded`, the warp
  path) has the same first-empty-slot rule; the warp side may stack differently, which would
  make realtime and warp storage diverge.
- Fix: merge into a matching, non-full stack first, then fall back to an empty slot (vanilla
  `Container`/`HopperBlockEntity.addItem` semantics). Pin with a unit test on `tryDropLoot` and
  a realtime autotest that deposits 2 of the same item and asserts one slot.
