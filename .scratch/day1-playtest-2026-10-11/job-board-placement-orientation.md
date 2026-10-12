---
status: ready-for-human
origin: 2026-10-12 playtest — user chat 17:40:26 ("Choosing the job boards orientation is something we'll need to solve. I just placed one and its rotated 90 west from where it should be pointing.")
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
