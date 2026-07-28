# Threat is pressure, not combat; anticipation lives in the onset, not a calendar

status: accepted
date: 2026-07-27

## Context

Questown's design reference is **Dragon Quest Builders**, and the skeleton already
matches it closely: guide NPC (**helper chicken** + journal), build orders (the quest
garden), room recipes, a townhall (**town flag**), residents arriving as the town grows,
residents who work and cook and farm.

Four DQB pillars are missing. Three are legibility or arc problems handled elsewhere
(a town level — see **Milestone** in `CONTEXT.md`; chapters; residents visibly reacting).
The fourth is **stakes**, and it is absent completely:

- There is no raid, hostile, or attack code anywhere in the mod.
- `DAMAGE_TICKS` is a *healing rate* ("ticks to heal one point"), not a damage source.
- The **injury** system is fully built — `SimpleVillagerHandle` tracks per-townie
  `damage`, heals it during sleep at a per-bed heal factor, surfaces `getDamagePercent`
  as a bar in the villager UI, and `TownFlagBlockEntity:645` already makes a damaged
  townie probabilistically **rest instead of work** — but the **only** caller of
  `addDamage` in the entire mod is the `AddDamageCommand` debug command.

Nothing in normal play can go wrong except "production is slow."

That has a specific consequence under **watching-first** (ADR-0011). If nothing can
threaten the town, the *only* reason to watch is to catch problems — which is exactly
why an idle townie currently reads so badly, and why need bubbles carry so much weight:
unmet needs are the mod's sole source of drama.

Two hard constraints shape any answer:

1. **Questown does not own the items in town storage.** They are vanilla items in
   vanilla chests. Per-item metadata (a spoilage age) would break the moment an item
   left a scanned chest, fight other mods, and make Questown a bad citizen.
2. **Seasons are not Questown's job.** A player who wants seasonal difficulty installs
   a seasons mod.

## Decision

**Threat-as-pressure, not combat.** The player's response to adversity is the *same
verb* as the core loop — supply the town, build the right rooms — with consequences
attached, rather than switching into a combat mode that vanilla Minecraft and every
other village mod already serve. No raids, no waves, no defense. (Combat is not
forbidden forever; it is out of scope now.)

**Exactly two loss layers.** **Injury** (visible on a specific townie, and the sink that
gives planned hospital beds / doctors / hospital rooms a reason to exist) and the
**buffer** (see below). Explicitly rejected: **roster loss** — a townie leaving or dying
— as too heavy for the genre, unrecoverable without a re-recruit path, and because it
turns every absence into anxiety, which fights **warp**; and **proficiency decay as a
penalty**, which is invisible, uncorrelatable to its cause, and punishes precisely the
warp-heavy playstyle the mod's pitch invites.

**Pressure raises demand; it does not destroy supply.** A cold snap makes townies eat
more and need firewood they did not need before. What the player loses is their
*buffer* — the "I thought I had enough" feeling — with nothing deleted, which respects
constraint 1 above and never reads as the mod confiscating your things. The only
destructible targets are **world-owned**: standing crops.

This chains into injury with no new mechanism: buffer drains → **fullness** drops →
underfed townies take damage → they rest instead of working → production falls further.
That spiral is the first legitimate caller `addDamage` would ever have.

**Anticipation lives inside the event's onset, not in a calendar before it.** A beat
that *starts small and worsens* makes its own first stage the warning. **Blight** is the
reference implementation: a town-internal record of blighted positions (positions, never
items), rendered with particles, which de-grows the plants it holds and **spreads to
neighbours**. One sickly crop at the farm's edge is the warning; catching it early costs
one crop, ignoring it costs the farm. A cold snap is a **random event with a ramp**, not
a season.

**The preparation fantasy is bought with upside, not danger.** Onset-shaped pressure
yields "I caught it in time" but never DQB's "I was ready this time." Rather than build
a threat scheduler to recover that, the **post office** supplies it: a **letter** is a
Stardew-style standing order whose deadline *is* the anticipation. Threat is
onset-shaped; opportunity is forecast-shaped.

**Forecasting ships later, as an unlock.** A forecaster job that warns of a cold snap
turns *warning time* into a progression reward — a young town is hit unwarned, a mature
town sees it coming. This is deliberately not v1.

**Pressure ships behind the legibility work.** Need bubbles (ADR-0011), dead-door failure
feedback, the flag crafting-tab fixes, the deed-consumption dupe bug, and a shortened
`WANDER_GIVEUP_TICKS` all land first. Then injury activation, then blight, then the post
office.

## Considered options

**DQB-style monster attack waves** — rejected. Demands a combat layer, AI targeting, and
townie defense behavior, none of which exist; competes directly with vanilla raids; and
switches the player out of the supply-and-build verb that is the whole loop.

**A modeled seasonal calendar** — rejected as out of lane. Left to seasons mods.

**Item spoilage in town storage** — rejected as illegal under constraint 1. Reframed as
demand-side pressure, which turns out to be better anyway: it produces the same
"I thought I had enough" feeling with nothing destroyed.

**Roster loss and proficiency decay** — rejected; see Decision.

**Flat random accidents as the injury source** — rejected. A background damage rate with
no visible cause is indistinguishable from the mod being buggy, which is the exact
perception problem the mod is already fighting. Injury instead comes from the town's own
operation (job hazards, the hunger spiral), so cause and effect are co-located in space
and time and the player *watches it happen*.

**A forecast/announcement channel in v1** — rejected on cost. It needs a scheduler, a
calendar, announcement UI, and — worst — warp semantics for an event firing while the
player is away. Onset-shaped pressure needs none of these.

**A standing stat to price letter failure** — rejected as unnecessary. The cost of a
letter is a townie's *time* (a multi-day **leaver** run), so scarcity and commitment fall
out of throughput with no new stat, no active-letter cap, and no failure penalty.

## Consequences

- The dormant injury system becomes load-bearing. `addDamage` gains real callers on
  **both** the realtime and warp paths — a parity requirement, not an optimization.
- **Beds** and **heal spots** (a registered fixture that currently does very little) gain
  a purpose, and planned hospital rooms / doctors gain a reason to exist.
- **Need bubbles become a prerequisite, not a nice-to-have.** Pressure makes things go
  wrong *on purpose*; shipping it into a mod that cannot yet distinguish "working as
  designed" from "broken" makes the legibility problem worse.
- **Warp yields a degraded-but-recoverable town, never a catastrophe.** Blight spreads to
  its cap while you are away and the farm needs replanting; nobody is lost. Presence is
  the defense, absence is a cost — which is the sharpest available statement of
  watching-first.
- The **builder audience stays unserved.** DQB's town level rewards building for its own
  sake and Questown does not; a real fix needs a "block niceness" evaluation system,
  which is far off. Accepted knowingly.
- **No anticipation-before-the-event in v1.** Until forecasting or the post office lands,
  every beat is reactive.
