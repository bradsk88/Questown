# Job proficiency: an open-ended, both-paths work-speed multiplier

status: proposed
date: 2026-07-01

## Context

Issue #269 ("Job Proficiency") asks for a per-**townie** skill that scales **work
speed**, gives each townie a distinct contribution, motivates **visitors** (#268,
who arrive with proficiencies), and opens a new quest/BOP reward ("increase
proficiency by X"). The issue sketches the mechanic in the abstract; two of its
terms map onto concrete — and slightly awkward — existing code:

1. **"Work speed"** is computed on **two code paths** by different formulas. The
   **realtime path** (`RealtimeWorldInteraction.getWorkSpeedOf10`) returns
   `SimpleVillagerHandle.getWorkSpeed(uuid) = (int)(mood × 10)`; the **warp path**
   (`TimeWarpWorldInteraction.getWorkSpeedOf10`) returns
   `TownVillagerMoods.compute(effects) / 10`. Both feed `AbstractWorkWI.applyWork`,
   which subtracts the value from a state's 10×-scaled `workLeft` via `decrWork`.
   A great deal of production happens on the **warp path** (player away), so a
   speed modifier that hooked only realtime would silently do nothing offline —
   the recurring warp/realtime **parity** bug this codebase keeps flagging.

2. **"Cooldown"** in the issue's leveling formula is the job JSON's
   `cooldown_ticks` → `WorkWorldInteractions.actionDuration`: the **duration of one
   work action**. So leveling is a **per-completed-action** quantity, scaled by how
   long each action takes — not a per-tick quantity (per-tick would max a skill in
   ~1 second).

Existing per-townie intangible state (mood/effects) is stored with a deliberate
split: dedicated per-uuid holders on `SimpleVillagerHandle` (realtime) plus a
field on `TownState.VillagerData` (warp), bridged each warp by `TownFlagState`
and persisted by `TownStateSerializer`. Relocation (ADR-0009) copies the whole
block-stored tag blob, so anything serialized under the roster NBT travels for
free.

## Decision

Model **proficiency** as **one canonical per-(townie, proficiency-id) multiplier
on effective work speed**, and:

1. **Both paths, one seam.** Apply the multiplier where both paths already
   converge — `getWorkSpeedOf10` (realtime *and* warp overrides) — so effective
   production rate is path-symmetric by construction. `proficiency-id` is a new
   free-form string field on the **job definition** (`Work.proficiencyId`,
   parsed by `ResourceJobLoader`); multiple jobs may share one. A job that
   declares no proficiency-id is a flat **1×** (the system is inert for it).

2. **Linear, configurable band, multiplicative with mood.**
   `multiplier = MIN + level × (MAX − MIN)`, `level ∈ [0,1]`, with `MIN`/`MAX` as
   `Config` values defaulting to **0.5 / 2.0** (break-even ≈ level 0.33). It
   stacks multiplicatively with the existing mood term
   (`mood × 10 × profMult`) and keeps the existing `Math.max(…, 1)` floor.

3. **Open-ended proficiency set, seeded with 3.** A townie holds a map
   `proficiency-id → level`, **seeded with 3 random entries** (drawn from the
   union of proficiency-ids declared by any registered job, via a **UUID-seeded
   deterministic RNG** so the draw is reproducible and testable) at the
   `SimpleVillagerHandle.register` seam, and grows open-endedly as they work.

4. **Leveling at the work-action seam, per action, warp-counts-N.** On each
   **completed work action** of a proficiency-id `P` with duration `D`:
   `level[P] += 0.001 × D` (clamp ≤ 1, create at 0 if absent); every **other**
   held proficiency `−= 0.0001 × D` (clamp ≥ 0). The warp path must credit the
   **same number of actions** as realtime for the same work, not "once per
   important tick."

5. **Storage mirrors mood/effects.** A per-uuid realtime holder
   (`TownVillagerProficiencies`) + a field on `VillagerData` + the `TownFlagState`
   bridge + `TownStateSerializer`. Relocation-carry is then automatic via the
   whole-blob copy — but is asserted by an **explicit acceptance test**, not
   assumed.

Cut 1 ships the mechanical spine (items 1–5) plus tests. The **Proficiencies
UI** tab is deferred to slice 2 (GUI is a server-autotest blind spot and the
mechanics are fully verifiable without it); the **"increase proficiency" reward**
and **visitor (up-to-10) seeding** are deferred to their own work (#268).

## Alternatives considered

- **Realtime-only multiplier (first cut, defer warp).** Rejected: proficiency
  would evaporate during offline production — the exact scenario where speed
  differences matter most.
- **Closed set of exactly 3 proficiencies.** Rejected: creates dead-end townies
  structurally incapable of ever being skilled at the job you need, fighting the
  town-building fantasy and the issue's RimWorld framing. Open-ended is naturally
  bounded by the finite set of declared proficiency-ids.
- **Leveling per tick.** Rejected: maxes a skill in ~1s; the `cooldown`-scaled
  formula only makes sense per action.
- **Explicit job-type branch to exclude timer jobs (gatherer/baker).** Rejected
  in favor of the **work-action seam as the implicit boundary**: timer jobs
  advance on **timed states**, never reach `decrWork`, so they get flat 1× and no
  leveling with no special-casing. (This is why #269 lists them as out of scope.)

## Consequences

- Effective work speed is path-symmetric; the **realtime-vs-warp leveling-parity
  autotest** is the feature's acceptance gate — if it's red, the feature is wrong
  by definition. Built as `farmer/harvest_wheat [proficiency_parity]`; it caught a
  real defect on its first green-capable run (below).
- **The multiplier must be clamped to the `decrWork` 1–10 contract.** `getWorkSpeedOf10`
  feeds `State.decrWork`, which throws outside that band. Mood alone respects it, but
  scaling by a multiplier above 1 does not: a well-fed townie past level ~0.33 computes
  11+ and throws *inside the warp loop*, whose handler downgrades the crash to
  "continuing as if nothing happened in town while player was away" — silent offline
  production loss, worst for the player's most skilled townies. Consequence:
  the upper half of the configured band is partly unreachable at high mood, since the
  product saturates at the ceiling.
- Proficiency levels bake into save NBT (per-uuid holder + `VillagerData`), so a
  later change to the leveling semantics or storage shape needs a migration —
  hence this record.
- Timer jobs (gatherer/baker) and no-proficiency jobs are inert by construction.
  If a timer job ever needs proficiency, it needs a **separate, explicit**
  mechanism (there is no work-action to hang leveling on).
- Test coverage: JUnit for the curve, leveling/decay clamps, and seed
  determinism; autotests for speed effect (both paths), leveling-parity (crown
  jewel), decay-to-zero-over-neglect, and relocation-carry.
- Visitors (#268) will extend seeding (up to 10) and are the payoff for the
  open-ended model — a visitor can arrive already expert at something no townie
  has touched.

## Amendment (2026-07-26): re-center the band so mastery has headroom

The Consequences above understate a balance problem: proficiency and **mood**
compete for the same 1–10 `decrWork` band. Base speed is `(int)(mood × 10)`, so at
the default neutral mood (75 ⇒ base 7) the proficiency band is a fair ~4–10, but at
**max mood (base 10) the ceiling clamps every bit of upside away** — only the 0.5×
floor remains reachable. The reward therefore *shrinks as mood rises and vanishes
at the top*: feeding and housing a townie well erases the skill differentiation
that #269 exists to create.

Decision: treat "mastery has no upside at high mood" as a **bug, not a deliberate
ceiling**, and re-center so that today's speed sits near the **middle** of the
achievable range rather than at its maximum. Without this, #269 cannot deliver its
stated motivation ("gives each townie a distinct contribution") for exactly the
towns the player has invested the most in.

Deliberately not specified here: whether to lower the well-fed base or widen the
`decrWork` band. Both have wide blast radius on existing balance and want their own
measurement pass. What is settled is that **no job JSON should declare a
`proficiency_id` until the band is re-centered** — until then the feature can only
subtract.
