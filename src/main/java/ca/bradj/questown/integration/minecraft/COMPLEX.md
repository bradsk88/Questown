# integration/minecraft — notes for future sessions

## MCHeldItem / MCTownItem NBT unwrapping contract (the "journal emptied on load" bug, 2026-10-14)

`MCTownItem.serializeNBT()` writes `{item: {item: <name>, count: <int>, data: <compound>}}` —
the item payload is **nested under an "item" key**. `MCTownItem.of(tag)` is the **single
point of unwrapping**: it does `tag.getCompound("item")` and then reads the new/legacy
fields.

The 1.19.2 pre-migration shape was **flatter**: `MCHeldItem.fromTag`/`deserialize` unwrapped
the "item" compound themselves and passed the *inner* compound to a 1.19.2-era
`MCTownItem.of` that expected it. The 1.20.6 migration (`e2577363` checkpoint) rewrote
`MCTownItem.of` to unwrap *again* (new-format branch), but left the pre-unwrap in
`MCHeldItem.fromTag`/`deserialize` — so a load did **two unwraps**: the second
`getCompound("item")` on the inner (flat `{item: "...", count: 1}`) compound returned an
empty compound, fell to the `else` branch, and became `MCTownItem(AIR, 0)`.

Effect: every villager's `journal_items` in the flag-tile NBT silently became AIR on
`TownStateSerializer.load`. Symptom chain: offline warp sims (downtime warper) saw every
townie empty-handed → claim/tool-gated jobs never advanced in the warp window (e.g.
`farmer/harvest_wheat` stuck at `NO_SUPPLIES` for the whole run; the `held_tool_warp`
autotest's "wheat 11 -> 11" failure). The `CLAIM_SPOT` constant mismatch
(`SpecialRules.CLAIM_SPOT` was `"claim_spot"`, all 12 job JSONs say `"claim_workspot"`)
was a second, independent gating bug on the same path.

Fix: `fromTag`/`deserialize` hand the **outer** tag to `MCTownItem.of` (one unwrap).
Pinned by `MCHeldItemTagRoundTripTest` (the pure seam) + the `held_tool_warp` autotest
(the full round-trip, which is registry-bound and cannot run as a unit test).

Rule going forward: **never pre-unwrap the "item" compound before calling
`MCTownItem.of`.** `TownStateSerializer`'s container path (`MCTownItem.of(icTag)` on the
whole stored compound) is the reference shape.

## Displays count as storage, but take no deposits (2026-10-28)

Dev: armor on a stand "count[s] as storage". `DisplaySlots` presents an armor stand's four armor
slots, or an item frame's item, as a vanilla `Container`; `MCDisplayContainer` (an `MCContainer`
that refuses `canAcceptIfSpaceAllows`) marks it. `TownContainers.getAllContainersAndDisplays`
adds every display standing in a town room's scanned positions, and `getAllStacks` (item quests,
the board's 8-in-storage pause, the warp summary) reads that. `getAllContainers` does **not**
include displays: it picks deposit targets (`Jobs.tryDropLoot` writes the first empty slot of
whatever target it is given), supply sources and "is there space" answers, so including them
would put cobblestone on a stand, and a display job could move armor from stand to stand.

## Chest lids open when villagers use them (dev playtest 2026-10-29)

"When villagers insert loot into chests (or take items) it would be nice if the chest would open
and close and make a sound like it does when a player interacts." `TownContainers` builds each
town chest's `MCContainer` with a touch callback (`ChestTouches.touch(level, pos)`), which
`removeItem`/`setItem` run. The first touch sends vanilla's lid block event (type 1, openers 1)
to the chest and its other half, and plays `CHEST_OPEN`; `ChestLidTimers` keeps the lid up for 10
ticks after the latest touch, so several items in a row do not flap it. `TownFlagBlockEntity.tick`
calls `ChestTouches.tick`, which sends openers 0 and `CHEST_CLOSE` when due. A chest a player has
open (`ChestBlockEntity.getOpenCount > 0`) is left alone. Timing pinned in `ChestLidTimersTest`;
the lid was seen in game 2026-10-30: open while a hunter took from the store chest
(`.scratch/dev-playtest-2026-10-29-fresh/overnight-shots/chest-lid-open.png`, caught at tick rate 10
in a 0.6 s screenshot burst, diffing the chest's top face). The sound was not checked (no audio here).

## A Block of Progress is not loot (2026-10-30)

`MCTownItem.isBlockOfProgress` (`instanceof BlockOfProgressItem`, no registry lookup, so unit
JVMs stay safe) and `MCHeldItem`'s delegate feed `HeldItem.staysWithVillager`, which keeps a BOP
out of every loot drop: `jobs/COMPLEX.md` "A Block of Progress never goes into a chest".
