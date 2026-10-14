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
