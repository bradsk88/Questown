# Vanilla lifts a solid path target to the first non-solid block above it

**The long-lived "villager walks on the roof / stands facing a wall" bug (2026-10-17 to r20, 2026-10-25).**

## The mechanism

`GroundPathNavigation.createPath(BlockPos, int)` (read in the 1.20.6 neoForm source; 1.19.2 not checked) paths to
the target as-is only when the target block is **not** `isSolid()`. Otherwise it walks the
target **up** until it finds a non-solid block and paths there. `isSolid()` is the legacy
"solid" flag, which is true for blocks that are not full cubes too: a **closed door** counts as
solid (full height). So a walk target placed *in* a doorway, a chest, a job block or any other
solid cell is silently moved onto whatever is above it. Inside a roofed room that is the roof.

The pathfinder then cannot reach the lifted target and returns a partial path to the reachable
node closest to it, often a cell on the other side of a wall, next to the real target. The
villager walks there and stands facing the wall (seen live, r20). Where a roof *is* reachable the
same lift would send it up onto the roof: that is the likely cause of the "walks on the roof"
reports, not yet confirmed with the probe on one of them.

## The r20 case (receipt)

A 3x3 store room (door 150 71 48) whose only interior cell is its chest: the chest is used from
the doorway, so `InteractionSpots.find` returned the door cell, correctly. The live probe
(`_qtdev nav <villager>`) read:

    walkTarget=150 71 48  pathTarget=150 75 48  pathEnd=151 72 46  canReach=false node=0/1

The door's two halves and the wall above it (y71-74) are solid, so the target went to y75, the
roof. The nearest reachable node was the bed in the next room (151 72 46), against the shared
wall. Breaking the door made the doorway air and "fixed" it, which is why every earlier
sighting looked like a path-cost problem.

## The rules

1. **Read the path's target, not just the walk target.** A walk target can be correct while the
   navigator aims somewhere else. `_qtdev nav` prints both; `walkTarget != pathTarget` means the
   navigation layer moved it.
2. **A door is a valid standing cell for a door-opening villager.**
   `VillagerPathNavigation.createPath` stops the lift at a door: a target that is a door, or a
   solid target with a door right above it (r21: the cook's target came out one block low, on the
   floor under the doorway, `walkTarget=150 70 48`, and was lifted to the roof the same way). It
   paths there with `createPath(ImmutableSet.of(pos), 8, false, accuracy)`, the call vanilla's own
   overload makes. Where the one-block-low target comes from is still untraced.
3. **A target under another solid block goes beside, never on top (dev: "the roof never is").** `besideSolidStack` picks the first door or standable neighbour at the target's level. **A single solid block is still lifted one block.** Pick targets that are standable (non-solid foot
   and torso cells, solid ground: `WalkTargets.isStandable`), not the block being used. If a new
   caller must target a solid block, extend the `VillagerPathNavigation` override rather than
   adding a cost tweak.
4. **Do not fix this with path costs.** The 2026-10-15 door discount made open doors impassable
   (a negative malus is unwalkable) and was reverted (`mobs/visitor/COMPLEX.md`).
