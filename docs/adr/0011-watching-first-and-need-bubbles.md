# Watching the town is the primary experience; needs are surfaced as cursor-proximity bubbles

status: accepted
date: 2026-07-26

## Context

Questown's pitch — and much of its engineering investment — reads **warp-first**:
"the village runs itself while you explore." `project-qa-2.md` records warp as the
top priority; `onboarding-player-experience.md` treats the Phase 7 warp payoff as
the moment the player is "hooked."

That reading is wrong, and a future reader will otherwise assume it. Asked
directly which is the primary enjoyment surface, the answer was: **watching the
town and helping it grow is the main experience; returning to find it worked is
secondary.**

This inverts the priority of a known, long-standing defect. `docs/conventions/
realtime-job-cycling.md` documents a townie standing still, cycling through
unviable jobs before eventually working — and that doc has a *Background* and a
*Problem* section with **no solution**. Under a warp-first reading, that's
cosmetic. Under a watching-first reading, it is a **P0**: the main experience is
looking at a townie, and the townie looks broken.

The reasoning the player needs already exists and is thrown away at the player
boundary:

- `TownPossibleWork` scores every candidate job as `WithReason<Double>` — a value
  paired with prose (`"Special rule ALWAYS_CONSIDER present"`).
- `registerUnmetNeed(tick, uuid, "minecraft:coal")` records the exact missing item
  per townie.
- `TownPossibleWork.bigLog()` dumps the full ranked list with reasons.

All three go only to `QT.FLAG_LOGGER` debug output. There is no player-facing
string for "why is this townie idle" anywhere in `en_us.json`. Meanwhile
`TownVillagerData.tryFallback` (the `preferredBuffer` throttle) makes a townie
*deliberately* stand still while `buffering == true` — the mod manufactures the
exact visual that reads as a bug, and says nothing.

## Decision

**Watching is primary.** Where watching-mode legibility and warp-mode convenience
compete for effort, legibility wins.

**Surface a townie's unmet need as a world-space speech bubble**, generalizing the
helper chicken's proven presentation pattern (`HelperChickenBubbleLayer` is already
a reusable static, not a `RenderLayer`, and already carries a 16-block distance
gate). A townie standing still under a coal icon is not broken — it is *asking for
coal*, which converts the mod's worst-looking moment into its core "help the town
grow" loop.

**Trigger on cursor proximity, not hover, and show exactly one bubble at a time.**
The bubble goes to the townie nearest the crosshair within an angular threshold,
bounded by the existing 16-block gate. Hysteresis keeps the current winner until
another beats it by a margin, so clustered townies don't flicker.

**Doors and unreachable targets reuse the same channel.** A registered door that
never resolves to a room gets a bubble saying so; so does a townie that cannot
path to its target. One mechanism, three consumers, one player verb ("sweep the
crosshair; whoever needs help answers").

**Silence means working.** A townie with nothing wrong shows no bubble. An
always-on indicator would make the display ambient and therefore ignorable; the
bubble is worth looking at precisely because its presence is itself the signal.

**Shorten `WanderGiveUpTicks`.** It defaults to **2000 ticks — 100 seconds** of a
townie walking at a target it cannot reach before giving up. In a player-built
town (stairs, fences, a chest behind a door) this is common, and 100 seconds of
visible futility is far past the point where a watching player concludes the mod
is broken. A townie that bubbles "can't reach" after roughly ten seconds is
*informative*; the same townie silently pacing for a hundred is *defective*.

**Do not deregister dead doors.** Diagnosis and cleanup are separate concerns and
only diagnosis ships.

## Considered options

**Strict hover (`mc.hitResult` as `EntityHitResult`)**, as
`CampfireSleepClientEvents:39` already does — rejected. Vanilla's *entity* pick
range is ~3 blocks, so diagnosing a townie would cost a walk across town. That
fights the "stand in the square and survey" posture that watching-first implies.
The scan-at-a-glance gesture *is* the feature; a cone preserves it, a hover
destroys it.

**Bubbles on all townies at once** — rejected as visual noise. A twenty-townie
town becomes a wall of icons, and the signal that matters (one townie is stuck)
drowns in the nineteen that are fine.

**Uncommenting `TownRoomsMap.dropDeadDoors()`** as `docs/todo/dead-door-detection.md`
proposes (and speeding it from 100 ticks to 20) — rejected. That code calls
`deregisterDoor()`: it *undoes the player's action*. The normal build flow is
"wand the door, wander off for roof material, come back" — under that fix the mod
silently revokes the registration five seconds in, the player finishes the roof,
and nothing happens. Silence is confusing; **silent undo is worse**, and it fires
hardest during the tutorial when the player is slowest. A half-built room is a
normal state, not an error.

## Consequences

- The perf TODO that got dead-door detection commented out
  (`TownRoomsMap.java:237`) is sidestepped, not solved: rendering is client-side
  and needs no per-scan `registeredDoors × rooms` cross-product. Only a per-door
  "currently part of a room?" boolean is needed, which the scan already produces.
- Zombie door registrations still accumulate when a player tears down a shed, and
  town relocation (ADR-0009) drags them along. That is a real cost and argues for
  cleanup keyed on **the door block being gone**, not on the room being
  unfinished. Deliberately left open.
- `WithReason` becomes load-bearing for presentation, not just logging. Its prose
  is currently developer-facing and would need player-facing phrasing before any
  of it is shown verbatim.
