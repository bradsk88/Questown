# Sleepless: a debuff for townies who had no bed last night

**Status:** held-run verified; upstream bed-placement bug found mid-run, **fixed & live-verified** (see ledger + findings)
**Trigger:** the self-sufficiency held run had overlooked bedrooms — the run's town
*does* build a bedroom (the room builder handles the `questown:bedroom` recipe) but
the watch loop skips every night with `time set day`, so no townie has ever slept in
any self-sufficiency run (run-2 log: 4 nights skipped, zero sleep lines). And in
real play, a townie without a bed simply doesn't sleep — with **no simulation
consequence** beyond the unmet-room demand that opens the bedroom quest. The
player gets away with never building a bedroom.

## What already exists (verified in code, 2026-10-09)

| Piece | Where |
|---|---|
| Bedroom quest rolled out early | tutorial batch #1.5 in `TownQuests` (flavor: "Villagers need rest"); `SpecialQuests.BEDROOM` |
| Room recipes | `data/questown/recipes/bedroom.json` (light + 2 beds), `big_bedroom.json` (light + 4 beds) |
| Bed assignment | `TownVillagerBedsHandle` — per-villager best bed, beds must be inside a *matched room recipe* ("a bed on the floor" does not count); sets the `HOME` memory every tick |
| Sleep pipeline | villager schedule → `Activity.REST` at tick 12500 → `SetWalkTargetFromBlockMemory(HOME)` + vanilla `SleepInBed`; wake fires `VillagerSleptEvent(duration, bedPos)` |
| Per-bed heal | `SimpleVillagerHandle` sleep listener: damage heals on sleep at `getHealFactor(bed)` |
| No-bed detection | `getBestBed` empty → `Economics.registerUnmetRoom(BEDROOM)` (drives the quest; currently the *only* consequence) |
| Mood machinery | `TownVillagerMoods` (timed effects, `neutral + sum(effects)`), mood drives `getWorkSpeed` (mood×10) and `getAffectedTime` ((1+(neutral−mood))×time) |
| Decided direction | CONTEXT.md 2026-07-27: "mood becomes the town-quality score" — new inputs are *having a bed / bed quality* and *having a bedroom*; "the band widens well beyond ±10" |

## The debuff (design ruling)

A townie who **had no bed for the night** applies a `SLEEPLESS` mood effect at dawn:
they work more slowly (via the existing mood→work-speed plumbing) for the day, and
the effect re-applies each subsequent dawn until the town provides a bed for them.

Ruling notes:
- The "works more slowly" shape is the one the docs already decided (the 2026-07-27
  mood decision names beds as a mood input). The alternative the dev floated — "a
  chain of back-to-back rests before morning work" — is not in the docs, and it
  collides with the `DowntimeState` invariant ("rest is a reward for work since the
  last rest"; a fresh townie earns no rest before its first work — the run-19
  soft-lock shape). It remains a possible *escalation* once the mild debuff is
  observed, not the starting point.
- Keyed on **bed absence at nightfall**, not on "failed to sleep": a townie whose
  bed exists but who got stuck on a path is a soft-lock the need-bubble already
  diagnoses; penalizing it too would double-punish the player for our own pathing.
- No clear-on-rest in v1 (the effect lasts until its expiry, ~a day): a clear on
  `markRested` would be bypassed by townies who happen to be in DowntimeWork at
  dawn, and "tired all the day after a sleepless night" is the simpler read.
  Clear-on-rest stays an open tuning decision.

## Phases

1. **Core debuff** (this doc's main work)
   - `MoodEffects.SLEEPLESS` + buff map entry `−15` (neutral 75 → 60: work speed
     7→6, ~14% slower; `getAffectedTime` +15%). Band per the "well beyond ±10" note.
   - `BedlessLedger` (new, pure, `town/entity/`): captures the townies without a
     bed on the nightfall edge (`Signals` first becomes `EVENING`, tick 11500 —
     before the REST schedule starts at 12500); drains once at morning.
   - `SimpleVillagerHandle`: feed the ledger in `tick(signals)` (query
     `delegator.getSleepModule().hasBed` only on the edge); apply the effect in
     `handleMorning` for each drained uuid, logged at INFO.
   - `VillagerSleepModule.hasBed(ENTITY)` / `TownVillagerSleepModule` impl;
     `Delegator.applyMoodEffect(uuid, effect, untilTick)`; `TownVillagerMoods`
     `hasEffect`; `Config.MOOD_EFFECT_DURATION_SLEEPLESS` (default 23000).
   - `TownVillagerHandle` accessors for the report: `hasBed(v)`,
     `hasMoodEffect(uuid, rl)`.
2. **Held run: let the night pass**
   - `VillageSelfSufficiencyExecutor`: `questown.village.selfsuff.skipnight`
     property (default `false` → real nights; `true` restores the `time set day`
     skip). Day counter moves to the isNight→day dawn edge.
   - Heartbeat gains a `:SLEEPING` marker; the daily report gains a
     `sleep bedless=K sleepless=M` line.
   - At 200 t/s a night is ~62 s — the original "10 min per night" rationale was
     20 TPS and is gone.
3. **Verification**
   - Unit: `BedlessLedgerTest` (pure edge logic), `TownVillagerMoodsTest`
     (SLEEPLESS magnitude, `hasEffect`).
   - Held: 3-day self-sufficiency at 200 t/s, real nights. Evidence in the log:
     townies SLEEPING in heartbeats, the dawn "SLEEPLESS applied" INFO line,
     `sleep bedless=1 sleepless=1` in the daily reports (3 townies vs 2 beds →
     exactly one bedless every night until the town builds more beds).
   - Open question the run should answer: does the unmet bedroom demand roll a
     *second* bedroom / big_bedroom quest (the run-16 procedural-batch room
     requests) so the town self-sustains its way out of the debuff?

## Test ledger

| Test | Status |
|---|---|
| `BedlessLedgerTest` — edge fires once at the NOON→EVENING jump; not on EVENING→EVENING; restart-mid-night waits for the next real evening; restart-mid-evening captures once; drain empties; re-arms next night | **done** (6/6 green, full `:test`, 2026-10-09) |
| `SimpleVillagerHandleTest` SLEEPLESS wiring — dawn applies SLEEPLESS to the bedless townie only (until = tick + configured duration); re-arms on the next consecutive bedless night; no bedless townies → nothing applied | **done** (3 new tests green, 19/19, full `:test`, 2026-10-09. Note: the 3 pre-existing `scheduleJobRootChange_*` tests fail in a *filtered* run that boots no vanilla test first — a bootstrap-ordering artifact, green in the full suite at HEAD and here) |
| `TownVillagerMoodsTest` — SLEEPLESS magnitude / `hasEffect` | **harness-gated** (ctor reads `Config.MOOD_TICK_INTERVAL`, `compute` reads `Config.NEUTRAL_MOOD` — NeoForge config is unloaded in plain JVM; documented as `todo_...` markers per the repo pattern; magnitude verified in the held run instead) |
| Held run 3d real nights — SLEEPING markers, dawn SLEEPLESS line, report line | **done** (2026-10-09, run-1 no-probe: night 1 "3 of 3 townies have no bed" → dawn 2 `SLEEPLESS ×3`; night 2 "7 of 7" → dawn 3 `SLEEPLESS ×7`; zero sleep events — but see the upstream bug row below: nobody could sleep, so SLEEPLESS applied to *everyone*) |
| Held re-run after the placement fix (5d, 200 t/s, real nights, probe on) | **done** (2026-10-09, run-2 `.scratch/sleepless-debuff/runs/run2-beds-fixed.log`: probe `bed blocks=4 beds after head filter=2` (was `2 … 0`); bedroom quest still closed — the matcher tolerates 4 live bed blocks vs 2 recipe entries; night 1 `Nightfall: 1 of 3` (was 3 of 3), night 2 `5 of 7` after roster growth; dawn `SLEEPLESS applied` **only** for the bedless townie; bedded townies actually slept (SLEEPING overnight, awake + working by day 3); daily report `sleep bedless=N sleepless=0` — the `sleepless` counter reads before the flag-morning application, so it is always 0 at report time (out-of-phase, per the COMPLEX.md note), the `SLEEPLESS applied until tick N` line is the signal) |
| **Upstream bug (found by the held run): no bed is ever usable — the room builder places a lone FOOT bed block** | **done** (2026-10-09: `buildRoomForRecipe` (`TownFlagBlockEntity` L1430) placed `recipeBlocks.get(i).defaultBlockState()` — for a bed that is the FOOT part only, never the HEAD. Probe evidence: `DEV-DIAG:beds` shows `bed blocks=2 beds after head filter=0` while `look_room` shows one 'r' glyph per bed; `TownVillagerBedsHandle.isHead` then filters 100% of beds out of `getAllBedHeads` → zero assignment → `hasBed` false for all → no HOME, no sleep, all SLEEPLESS. **Fix (same day, `buildRoomForRecipe`):** any recipe block whose state has the `BED_PART` property (vanilla `BedBlock` and `HospitalBedBlock` both extend `HorizontalDirectionalBlock`) reserves two adjacent interior cells — foot with explicit orientation, HEAD at `foot.relative(FACING)` in `PART=HEAD` state (vanilla `BedBlock.setPlacedBy` semantics); no free pair → bed skipped with a warning, never a lone foot. Probe removed with the fix, `questown.beddiag` forward reverted, `skipnight` forward kept) |

## Findings (2026-10-09 held run)

- **The SLEEPLESS mechanism works end-to-end on real nights**: capture →
  dawn-apply → re-arm on consecutive bedless nights, at 200 t/s with the
  midnight daily report and the flag-morning application correctly out of
  phase (day-N report shows the *previous* night's state).
- **The bed pipeline was broken upstream of this feature** (pre-existing, not a
  SLEEPLESS regression) and is now fixed on the placement side: see the
  ledger's upstream-bug row (both halves placed, head cell reserved). The
  lookup (`TownVillagerBedsHandle`) was correct once the world has whole
  beds — foot AND head are both in the room scan's `containedBlocks`, and
  `isHead` then passes exactly the head — so the fix stayed in
  `buildRoomForRecipe`.
- **Secondary effects while bedless** (noted, not fixed): `registerUnmetRoom(
  BEDROOM)` fires for every bedless townie every handle tick (demand-spam
  candidate), and no townie ever gets a HOME memory (HOME = the assigned bed).

## Open decisions

- **Magnitude / duration:** −15 for 23000 ticks are first-cut defaults, tunable
  via the new config value; the held run's daily reports are the tuning oracle.
- **Clear-on-rest:** not in v1 (see ruling notes); revisit after the first
  real-night run.
- **Second-bedroom roll-out:** whether unmet `BEDROOM` demand opens a *new* bedroom
  quest (vs the single tutorial one): **answered by run-2, 2026-10-09 — it does
  not.** With 5 of 7 townies bedless, `registerUnmetRoom(BEDROOM)` demand fired
  all night but no second bedroom quest opened (jcscan: single bedroom
  COMPLETED, no new bedroom ROOM/ACTIVE). Still a design open question whether
  it *should*.
- **Mood breakdown tab** (the 2026-07-27 decision's UI half, "why is this townie at
  62%") is *not* in scope here; the mood bar already shows the dip.
