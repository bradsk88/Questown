---
status: ready-for-agent
origin: 2026-10-12 playtest — user chat 17:40:26 ("Choosing the job boards orientation is something we'll need to solve. I just placed one and its rotated 90 west from where it should be pointing.")
re-reported: 2026-10-15 playtest — user chat 02:58:27 ("Job board still not working right. It should look at the direction during placement and set its rotation correctly. It's currently rotated 90 degrees left of where itshould be for the irection I was facing.")
---

# Job board placement orientation

Placing a crafted job board sets its facing to the **opposite of the player's
horizontal look direction** (`JobBoardBlock.getStateForPlacement`:
`FACING = ctx.getHorizontalDirection().getOpposite()` — the vanilla "face the
placer" behaviour). The user placed one and found it "rotated 90 west from
where it should be pointing" — i.e. the board should presumably face the
**flag/town** (or the open approach), not the placer.

## Decision needed

What should the board face when placed?

1. The town flag (compute direction toward the flag from the block's NBT
   parent at placement).
2. Away from the flag (face the approach/open side).
3. Keep vanilla placer-facing but fix whatever makes the observed 90° error
   (maybe the block model/texture is asymmetric and "facing the player" reads
   as rotated).

Note: `getStateForPlacement` computes the facing twice (once pre-parent-check,
once post-`super` call) — whatever the answer, both sites change together.

## Ruling + root cause (2026-10-15, user chat 02:58:27)

User re-reported the same 90° error and ruled: keep the placement-direction
semantics (option 3) — "should look at the direction during placement and set
its rotation correctly"; face the placer, just correctly.

**Root cause (verified against model + blockstates JSON, not the state code):**
the block model authors the slab thin along X (readable faces = the ±X faces,
z-axis lean), but the FACING rendering convention is Z-fronted — the
blockstates variants table was the stock `south:0, west:90, north:180,
east:270`. Composed, the readable face renders 90° off from whatever `FACING`
says, for every facing. Both user reports ("90 west" 10-11, "90 left" 10-15)
are the constant-offset signature, consistent with facing the placer being the
intended semantics. The state code (`getHorizontalDirection().getOpposite()`,
both sites) is canonical and stays as-is.

## Fix

Re-based the client-side blockstates variants so the model's −X readable face
matches FACING: `assets/questown/blockstates/job_board_block.json` →
`west:0, north:90, east:180, south:270`. Resource-only change: needs a client
restart to take effect (the blockstate file is a client asset; the FACING
state itself is unchanged, so the server/world is untouched).

**The rotation sign came from the vanilla oracle, not math-from-memory:**
vanilla 1.20.6 `blockstates/furnace.json` + the `block/orientable` template
(front face on model −Z) reads `north:0, east:90, south:180, west:270` — a
`y:90` carries the north face to the east (clockwise from above), the opposite
of the standard-math sign. The first re-base attempt flipped that sign
(`south:90, north:270`) and the live test confirmed exactly that: user report
2026-10-15 03:13:02 — "Facing west, placement was correct. But when I was
facing south it pointed the wrong way" — west:0 was right, south:90 pointed
north. Swapped south/north; the corrected table is the one committed.

## Verification

Live: user placement test 2026-10-15 03:12–03:13 after the first re-base
client restart: **west ✓, south ✗ (pointed north)** — one sign flip, as
predicted by the oracle. Corrected table pending one more placement check
(south/east) after the next client restart. Other asymmetric FACING
block-rooms (bread oven, food display, fishing station, axe rack) get the same
model-axis-vs-blockstates-table check before being assumed correct.
