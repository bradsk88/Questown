---
status: accepted
---

# The flag owns the townie; the entity is its body

**Context.** The town's stats for each townie (hunger, mood, damage, experience, proficiencies)
already lived on the flag, and warp already simulated townies from the flag's town state with no
entity at all. But a townie's job, carried items, status and every per-tick decision lived on its
entity (`VisitorMobEntity`), which said 19 times over "TRY NOT TO ADD MORE FUNCTIONALITY TO THIS
ENTITY". Two models of one townie caused bugs on the seam between them:

- the town state was captured from whichever entities were loaded, so an unloaded townie dropped
  out of it, and its body later reloaded into "not present on town state" and never initialized;
- the entity saved its carried items in its own NBT, which loading wrote into a throwaway job and
  then replaced with the town state;
- a townie whose chunk stayed entity-ticking kept working while its flag slept, and the warp on
  waking simulated that window again (code-traced).

**Decision.**

- Each townie is a `TownieRuntime` held by its flag (`TownVillagerHandle`, keyed by UUID): its job,
  carried items, status, work to undo, supply-target counter, freeze ticks, need bubble and
  listeners. The runtime outlives the body: an unloaded townie stays in the town where it last
  stood, and its reloaded body reattaches to it. The flag's town state is captured from the
  runtimes.
- The entity is the townie's `TownieBody`. It moves (the vanilla brain, navigation, doors and
  gates), shows what the flag pushes (visibility, held items, need), and reports
  events (unloaded, killed, gave up reaching). It persists no townie state.
- The flag makes every townie's decisions in its own tick (the `townies` phase, every game tick),
  only for a townie whose body ticked in that game tick. Those are the moments the entity used to
  decide in.
- A town sleeps when no player is within the tick radius of any of its townies (of its flag, before
  any townie arrives). This is the dev ruling of 2026-10-23. A sleeping town's townies make no
  decisions, and warp catches the town up on waking. See `CONTEXT.md` "Town sleep".
- Movement stays the body's. The flag does not simulate pathing: that would mean re-implementing
  vanilla navigation for no gain.

**Consequences.**

- A townie has one source of truth, and warp and a reload see every townie, loaded or not.
- The flag's `TickProfile` now includes the townies' job cost as the `townies` phase (about 33 ms per
  tick for 20 townies on one `perf/town_large` seed). Total server work is unchanged: on one seed,
  side by side, 2000 ticks took 204 s at the base and 206 s with this change.
- A player at the flag of a town whose townies are all far away no longer keeps it awake; a player
  near any townie does, while the flag's own chunk still ticks. The townies' decisions run in the
  flag's block-entity tick, which runs only in a block-ticking chunk, and Questown holds no chunk
  ticket for the flag: a player who follows the townies past the simulation distance from the flag
  finds them standing. Whether the flag should hold a ticket is open.
- The entity keeps thin getters (`getInventory`, `getJobId`, `getNeed`, ...) that forward to the
  runtime, so code holding an entity keeps working. New code should go to the townie through
  `TownFlagBlockEntity.getTownie(uuid)`.
- Not done, and made possible by this: one simulator for realtime and warp over the same townie
  state, and moving the remaining entity callers (menus, commands, autotests) onto the runtime.

Pinned by `flag/awake_near_a_far_townie` and `flag/asleep_far_from_its_townies` (both red under
the flag-only rule) and `TownSleepRadiusTest`; the mechanism is in `town/entity/COMPLEX.md`
"Townie runtime and body".
