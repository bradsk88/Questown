# Job proficiency (#269) — implementation plan

Companion to **ADR-0010** (job proficiency: an open-ended, both-paths work-speed
multiplier) and the `CONTEXT.md` terms: **proficiency**, **proficiency id**,
**proficiency leveling**.

Build order is a sequence of tracer-bullet slices: each leaves the game compiling
and adds one observable capability, verified by the in-game autotest suite
(`docs/agents`/`questown-autotest`) and JUnit where the logic is pure. Per
CLAUDE.md: small functions, early returns over nesting, no deprecated APIs, and
**never simulate core logic in tests** — drive the real classes; if an interface
is missing, add the seam (or add a failing assertion naming the gap) rather than
faking it.

**Scope = cut 1** (the mechanical spine). The Proficiencies **UI tab**, the
"increase proficiency" **reward**, and **visitor (up-to-10) seeding** (#268) are
deferred — see the last section.

## Vocabulary → code map

| Concept | Where it lives today |
| --- | --- |
| Work-speed knob (both paths converge here) | `jobs/declarative/AbstractWorkWI.applyWork` → `getWorkSpeedOf10` |
| Realtime work speed | `RealtimeWorldInteraction.getWorkSpeedOf10` (`:111`) → `SimpleVillagerHandle.getWorkSpeed` (`:418`, `(int)(mood×10)`) |
| Warp work speed | `TimeWarpWorldInteraction.getWorkSpeedOf10` (`:111`, `TownVillagerMoods.compute(effects)/10`) |
| "Cooldown" = action duration `D` | job JSON `cooldown_ticks` → `WorkWorldInteractions.actionDuration` |
| Job definition | `jobs/Work.java`; parsed in `jobs/ResourceJobLoader.java` |
| Per-townie realtime state (pattern to mirror) | `town/TownVillagerMoods.java`, holders on `town/entity/SimpleVillagerHandle.java` |
| Warp/serialized per-townie record | `town/TownState.VillagerData` (`:327`) — already carries `effects` |
| Live↔warp bridge | `town/entity/TownFlagState.java` (build `:97-113`, load `:378-381`) |
| Serialization | `TownStateSerializer` (`loadVillagers` / save) |
| Spawn/register seam | `SimpleVillagerHandle.register(entity)` (`:510`) |
| Declared-id pool | `ServerJobsRegistry` (all registered jobs → their proficiency-ids) |
| Config tunables | `core/Config.java` |
| Relocation carry (whole-blob copy) | ADR-0009; `TownRelocation`; `flag/relocate_nearby` autotest + `CARRIED_DATA_KEYS` |

## Phase 0 — Config + job-def field + curve (no behavior yet)

- `Config`: `PROFICIENCY_MIN_MULTIPLIER` (0.5), `PROFICIENCY_MAX_MULTIPLIER` (2.0),
  `PROFICIENCY_SEED_COUNT` (3), `PROFICIENCY_GAIN_PER_TICK` (0.001),
  `PROFICIENCY_DECAY_PER_TICK` (0.0001) — same plumbing as existing values.
- `Work.proficiencyId` — nullable `String`, **optional** JSON field parsed in
  `ResourceJobLoader` (absent → null → flat 1×). Thread it through `Work`'s
  construction/copy paths.
- New pure util `Proficiency` (in `jobs/`): `float multiplierFor(float level,
  float min, float max)` = `min + level×(max−min)`; `float applyGain(...)`,
  `float applyDecay(...)` with `[0,1]` clamps. Keep these **pure** (no MC types)
  so JUnit drives them directly.

**Verify:** `compileJava`; JUnit `ProficiencyTest` — curve bounds (level 0→min,
1→max, break-even), gain/decay clamps.

## Phase 1 — Storage + round-trip (no gameplay effect yet)

Mirror the mood/effects mechanism exactly.

- `TownVillagerProficiencies` (new, mirror `TownVillagerMoods`): `Map<UUID,
  Map<String,Float>>` with `getLevel(uuid,id)`, `getAll(uuid)`, `setLevel(...)`,
  `initialize(...)`, and NBT save/load. Hang it on `SimpleVillagerHandle`
  alongside moods.
- `VillagerData`: add a `Map<String,Float> proficiencies` field (+ constructor
  arg, `withProficiencies(...)` copy helper, `toString`). Follow how `effects`
  is carried.
- `TownFlagState`: on **build**, copy each live townie's proficiency map into its
  `VillagerData`; on **load**, write `VillagerData` proficiencies back into the
  live holder (same round-trip as effects/journal).
- `TownStateSerializer`: read/write the proficiency map (a `CompoundTag` of
  id→float under each villager).
- **Test seam:** expose a read path for a townie's proficiency map (via
  `VillagerHolder`/a debug accessor) — needed by later autotests. If the real
  interface can't expose it, add the accessor (don't fake).

**Verify:** JUnit round-trip (holder → `VillagerData` → holder is identity);
autotest `BOOT_OK` still fires (serializer change didn't break load).

## Phase 2 — Spawn seeding

- At `SimpleVillagerHandle.register`, **iff** the townie has no persisted
  proficiency map (so loaded/relocated townies keep theirs): seed
  `PROFICIENCY_SEED_COUNT` **distinct** ids drawn from the **union of declared
  proficiency-ids** (`ServerJobsRegistry`), each at a random level in `[0,1]`,
  using a **`Random` seeded from the townie UUID** (reproducible). If fewer than
  the seed count are declared, seed as many as exist.

**Verify:** JUnit — same UUID → identical seed (determinism), distinct ids,
levels in range; autotest — a freshly spawned townie exposes N proficiencies.

## Phase 3 — The multiplier (both paths)

Resolve the townie's **current job**'s `proficiencyId`, look up the townie's
level, compute `multiplierFor(level, min, max)`, and fold it into the `of-10`
work speed at **both** overrides (keep the existing `Math.max(…, 1)` floor):

- `RealtimeWorldInteraction.getWorkSpeedOf10` — multiply `getWorkSpeed(uuid)`.
- `TimeWarpWorldInteraction.getWorkSpeedOf10` — multiply `compute(effects)/10`.

No proficiency-id on the job → skip (1×). Both sites must resolve the id from the
same job definition to stay symmetric.

**Verify:** autotest **speed effect, both paths** — a townie forced (test seam)
to a high proficiency out-produces a baseline townie over a fixed window on the
realtime pass *and* the warp pass. Needs a **force-proficiency test seam**.

## Phase 4 — Leveling + decay (both paths, warp-counts-N)

Hook at the **completed-work-action seam** (`AbstractWorkWI.applyWork`, when an
action completes — co-located with `getWorkSpeedOf10` consumption). For action
duration `D` on proficiency-id `P`:

- `level[P] += GAIN × D` (clamp ≤ 1, create at 0 if absent),
- every **other** held id `−= DECAY × D` (clamp ≥ 0).

- Realtime: fire once per action completion.
- Warp: **credit N actions**, not once per important tick — verify the warp loop
  visits/represents each completed action so the delta matches realtime for the
  same work. This is the parity crux.
- No proficiency-id / timer jobs never reach this seam → inert (by construction).

**Verify (acceptance gate):** autotest **leveling parity** — the *same* amount of
work realtime vs warp yields the *same* level delta on `P` and the *same* decay on
others. Plus autotest **decay-to-zero-over-neglect** — seed `X`, work only a
different `Y`, assert `X` floors at 0. JUnit already covers the clamp math.

## Phase 5 — Relocation carry (mostly test)

- Confirm the proficiency map is in the persisted town blob (Phase 1 serializer),
  so ADR-0009's whole-blob copy carries it with **no bespoke relocation code**.
- Extend the `flag/relocate_nearby` autotest: add the proficiency keys to
  `CARRIED_DATA_KEYS`, seed a known map pre-move, assert it survives the move.

**Verify:** `flag/relocate_nearby` green with the proficiency carry assertion.

## Test ledger

| Slice | Autotest scenario | JUnit |
| --- | --- | --- |
| Curve + clamp math | — | `ProficiencyTest` |
| Storage round-trip | boot OK | holder↔`VillagerData` identity |
| Spawn seeding | townie has N proficiencies | UUID-seed determinism |
| Multiplier (both paths) | speed effect realtime + warp | — |
| Leveling parity **(gate)** | same work → same delta (RT vs warp) | — |
| Decay to zero | neglect floors a proficiency at 0 | clamp |
| Relocation carry | `flag/relocate_nearby` + `CARRIED_DATA_KEYS` | — |

Two **new test seams** are expected (add to the real code, don't simulate):
force a townie to a known proficiency; read a townie's proficiency map back.

## Deferred (out of cut 1)

- **Proficiencies UI tab** (slice 2) — read-only list on the villager UI; the
  "cycle job icons for a shared id" is further polish. GUI is a server-autotest
  blind spot; the mechanics above are fully verifiable without it.
- **"Increase proficiency by X" quest/BOP reward** — depends on the reward
  system; own slice.
- **Visitor seeding (up to 10)** — depends on **#268 (Visitors)**; the payoff of
  the open-ended model (a visitor arrives expert at something no townie has).
