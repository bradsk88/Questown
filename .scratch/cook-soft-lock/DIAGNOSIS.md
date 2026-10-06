# Why the cook/food chain sometimes never staffs (self_sufficiency soft-lock)

2026-10-04 (battery v3 + canFit clock-window resolution + **live-gate pin**) + a
**2026-10-05 live `_qtdev inspect` mode confirmation** appended. Verified from 19 fresh
`village/self_sufficiency` samples (~50%–80% soft-lock, env/seed-dependent) + code reads.
This is a **continuation** of the DEV-DIAG `raw_food hunt` investigation (2026-10-02 →
10-04; `DevWorkScore`/`DevLootFire` exist).

## Verified chain (soft seeds vs healthy)

The cook is **never offered** in soft seeds, not "offers and fails":

```
cook quest offered only in tutorial phase 2.5        TownQuests.tryPhase2_5_jobChangeAndFood (L677)
   └─ gated on  hunterRawFoodLootComplete()         L1113
       └─ = a LOOT for  villager_simple_furnace_food_raw  is .isComplete()
           └─ closes only when a townie EXTRACTS raw meat   (onLootExtracted → processLootQuests)
```

| | cook quest opened | `extract … → true` | meat-grant trace | end-state |
|---|---|---|---|---|
| **soft** | **0** | **0** | **0** | `594` × `hunter/seeking_work` |
| **healthy** | 1 (staffed) | 2 (Raw Mutton, Apple) | present | `87` × `cook/extract` |

## The improvement fixed the wrong link

This session's "LOOT-quest closure on extraction" work fixed **`extract → LOOT closes`**.
That half works. The self_sufficiency soft-lock is the **upstream** half: **the hunter
never extracts in the first place.** The claim is not the blocker; the hunter stall is.

## CORRECTION (2026-10-04, handoff `pintail`): the stuck stage is the **cook**, not the hunter window

The `THE ANSWER` / Tier-3 / trace-(b) sections below attribute the soft-lock to the hunter's
`canFit` in-day window (lates-day `REJECT@canFit`). That was a mis-attribution, caught by
measuring the **per-stage terminal state** in fresh SOFT runs (`/tmp/tier3b-server.log`,
`/tmp/tier4-server.log`):

```
Stage (quest)                               tier3b            tier4
Hunter raw LOOT  villager_simple_furnace…   COMPLETED 620     COMPLETED 768   ← hunter IS producing raw food
Cook JOB_CHANGE  cook_qt_simple_furnace_f…  COMPLETED  0      COMPLETED  0    ← the stuck stage
Cook ProductionStatus                       NO_JOBSITE / NO_SPACE (both runs)
```

The hunter stage is already **`COMPLETED`** (it extracts raw meat); the lates-day
`REJECT@canFit` loop is a **lates-day cap, a side symptom, not the cause**. The stuck stage is
the **cook**: its JOB_CHANGE quest stays `ACTIVE` and **never reaches `COMPLETED`**, and the
driver is `NO_JOBSITE` / `NO_SPACE`. `scripts/stage-probe.py` on both runs shows `kitchen_small`
and the raw-food LOOT are both **COMPLETED** here — so the room *exists* and the hunter produced;
the cook is stuck at the furnace-level "no workable block / can't deposit" (NO_JOBSITE / NO_SPACE).
That also **disproves** the "missing room" (egret) reading for this scenario. Confirm which mode
with `_qtdev inspect villagers <pos>` (NO_JOBSITE = no workable block; NO_SPACE = can't deposit;
NO_SUPPLIES = lacks the item); `scripts/stage-probe.py <serverlog>` names the stuck stage from any
past log — **not** the hunter's `canFit` window. The Tier-3/b.2 window
analysis here is therefore of the **upstream (working)** stage — the wrong layer; keep it for the
`canFit` arithmetic (Tier-1 pin) but read the bottleneck as the **cook staffing**.

## CONFIRMED (2026-10-05, live `_qtdev inspect villagers`) — the exact mode of the stuck stage

The CORRECTION above landed the stuck stage on the **cook** but left its *mode* to confirm
(“confirm which mode with `_qtdev inspect villagers`”). Done on a fresh **cook-stuck** seed
(fresh code-built seed, 200 t/s **pace-good**, day-2 `STAGES stuck=JOB_CHANGE:cook_qt_simple_
furnace_food=ACTIVE`), `_qtdev inspect villagers 0 64 0` (flag at 0,64,0):

- **The cook’s exact mode:** the cook townie is on sub-job **`cook/fuel`** with status
  **`NO_JOBSITE`** (need=NONE) — it cannot find a workable `minecraft:furnace`.
  `cook/fuel` (`questown_jobs/cook_fuel.json`) requests `#minecraft:coals`, works the
  `kitchen_small` furnace, and **already has** `always_consider` on. But the village’s stuck
  quest is **`cook_qt_simple_furnace_food`** (cook beef) — a *different* sub-job than the one
  the cook is parked on. So the cook is **not working the wanted food job**; it is stuck on
  the fuel sub-job (`NO_JOBSITE`). That is a **sub-job mismatch + no workable furnace**.
- **The village-wide dominant stall:** every active producer is blocked by “cannot deposit :
  **every town chest is full**” — the **`NO_SPACE`** mode — carried loot they can’t drop:
  - hunter (downtime) carrying `wooden_sword` + `cooked_mutton`
  - hunter (seeking_work) carrying `stone_sword` + `leather` + `cooked_mutton`
  - gatherer (seeking_work) carrying `brown_mushroom`

  This is the `NO_SPACE` aggregate the stage-probe tallied (68k) — it is the **main** stall,
  not a downstream symptom.
- **Confirmed NOT the bottleneck (live):** the hunter IS producing (`stone_sword`/
  `wooden_sword` + raw `villager_simple_furnace_food_raw` `COMPLETE`, cooked mutton carried)
  → **not** the `canFit` window (the handoff’s leading fix); `kitchen_small` `COMPLETE` →
  **not** a missing kitchen; raw `COMPLETE` → **not** a supply gap. All three earlier
  hypotheses (window / kitchen / supply) are ruled out by the same run.

**Reframed mechanism:**
- **Verified:** the soft-lock is a **store-capacity / deposit stall** (chests full →
  `NO_SPACE` blocks every producer) **plus a cook job-site + sub-job stall** (`cook/fuel`
  `NO_JOBSITE`; not doing the wanted food job).
- **Inferred (not yet pinned):** the chest stays full because food accumulates with no
  consumption/expansion sink in this seed (the stored cooked meat is not visibly being eaten).

**Fix implication:** neither handoff fix #1 (hunter `always_consider`) nor “missing kitchen”
touches the confirmed stall. The levers are (a) the **cook sub-job / furnace availability**
(wanted food job vs. the `cook/fuel` `NO_JOBSITE` it’s parked on), and (b) the **`NO_SPACE` /
store-full** stall (a consumption sink, or a bigger store room when one fills). The hunting-
window analysis below (Tier 1–b.2) stays correct for a job that *reaches* the gate, but it is
**not** the bottleneck in these seeds — the stuck stage does not get there (or is the cook, not
the hunter).

> Evidence (live, this run): stage table `STAGES stuck=JOB_CHANGE:cook_qt_simple_furnace_
> food=ACTIVE  done=… hunter_qt_sword=COMPLETE … villager_simple_furnace_food_raw=COMPLETE …
> kitchen_small=COMPLETE  status=… NO_SPACE=…`; per-villager inspect (cook=`cook/fuel`
> `NO_JOBSITE`; 2 hunters + 1 gatherer “cannot deposit: every town chest is full”;
> 2 gatherers `WAITING_FOR_TIMED_STATE` at `-5,64,0`). Full log: the session’s
> `/tmp/cookmode-server.log`.

## PINNED (2026-10-05, follow-up) — the stove is placed; the cook’s work-site lookup returns null

The CONFIRMED section left “why no workable furnace” open. Pinned from the same run’s
room-builder log + the inspect (the cook is townie `00f99e63`):

- **The furnace IS placed and latched** — and the cook was requested to the wanted food job
  the *same instant* the kitchen finished:
  ```
  [02:40:02] [buildRoomForRecipe] built 5x5 room at corner 3,64,1 for door 5,64,5,
             placed [Block{minecraft:furnace}] inside, door registered
  [02:40:02] [room-builder] questown:kitchen_small: quest complete, room latched
  [02:40:02] [job-change]   requested JobID{rootId='cook', jobId='simple_furnace_food'} for townie 00f99e63
  [02:42:08] [_qtdev inspect] villager 00f99e63  job=cook/fuel   need=NONE   status: NO_JOBSITE
  ```
- **So it is not** a missing kitchen, **not** a missing furnace, and **not** a coal shortage
  (`need=NONE`; `NO_JOBSITE` is the work-site status, not the `NO_SUPPLIES` item status — a
  coal gap would read `need=UNMET_ITEM` + `NO_SUPPLIES`).
- **The pinned bug is the work-site lookup:** in `JobLogic`, `NO_JOBSITE` tracks
  `getWorkSpot()==null` — the cook can’t resolve the placed, latched `minecraft:furnace` as
  its work site. At the time it is on a `cook/fuel` sub-job and was requested to
  `cook/simple_furnace_food` (the wanted quest); both carry `block: minecraft:furnace` /
  `room: questown:kitchen_small` (all three — `fuel`, `simple_furnace_food`, and the shared
  `extract` root — do), and neither can find that block as a work site.
- **CORRECTION (chased, 2026-10-05) — the work-site-null premise is REFUTED:** a `DEV-DIAG`
  in `JobLogic.doTryWorking`’s work-spot-null branch (gated on `ENABLE_DEV_COMMANDS`) fired
  **0 times across two cook-stuck runs** (the cook sits `NO_JOBSITE` the whole time). The
  `NO_JOBSITE` status is in fact a **state classification** from the `JobStatuses` decision
  tree (`jobs/JobStatuses.java` L193-213). The leading `noJobSite` branch (L201-202) is
  `dTownHasSupplies + dInventoryEmpty` — “stop when nowhere to work and town has items” — and
  it **does not check workable blocks** (`dHasNoWorkableBlocks` only appears in the *second*,
  “no supplies” branch at L215-217; the code’s own `TODO` at `DeclarativeJob:645` says the
  “`isJobBlockReady`” predicate is still needed). With the stove placed (kitchen latched) and
  `need=NONE` (not coal-out), the cook is classified `noJobSite` by being **idle** (empty
  hands) while the town is full — an *idle* classification, **not** a “furnace missing” signal.
  So the soft-lock is **not** a missing-furnace / work-site bug.
- **The real question, re-located:** off the room/furnace/work-site layer entirely →
  **why does the cook sit idle on `cook/fuel` (empty hands, town full) and never work the
  wanted `simple_furnace_food`?** That is a **job-routing/selection** question — the next,
  different, deeper layer, not yet instrumented.
- **ROUTING (traced 2026-10-05, from the `[jobcyc]` board-selection history):** the cook
  loops/idles on the **`always_consider` setup sub-jobs** `cook/fuel` + `cook/extract`
  (both `cas=true` → always `ACCEPT`; the cook ping-pongs `extract→fuel→extract…` or sits
  idle on one, seed-dependent). The **actual food-cooking job** that appears as a candidate
  (`cook/mutton`) is **`REJECT@canSatisfy` (gate2, `satAny=false`) on every board ask** —
  so the cook never does the real cook → the food quest never completes. `canSatisfy`
  (ServerJobsRegistry:579) is a **pure job-output ⇄ board-request match** (no NO_SPACE /
  deposit check): the cook's food job is rejected because its **output doesn't match the
  board's current requests** (tier3b request list was raw `mutton`×3, `wheat_seeds`×2,
  `bread`, `cooked_cod` — the cook's `mutton` job produces *cooked* mutton, which none
  of those is). → **the routing mechanism of the soft-lock: gate2 (`canSatisfy`) prunes
  the real cook job, leaving only the `always_consider` setup jobs, so the cook loops
  on them forever.** "
- **DESIGN GROUNDING (from `CONTEXT.md` + ADRs, 2026-10-05 — not code-guess):**
  - `CONTEXT.md` L111: **`cook/extract` + `cook/fuel` are the cook's *initial* job set** —
    "a working pair instead of a single self-re-selecting job"; `cook/mutton` is a **one-off
    starter-recipe grant** (onboarding `TownQuests`), *not* a starter. **So the cook looping
    `extract↔fuel` *is* looping its own initial pair; the `mutton` REJECT is the one rejected
    starter recipe.** (Matches the `[jobcyc]` candidates exactly: `fuel`, `mutton`, `extract`.)
  - `CONTEXT.md` L101/123 + ADR-0007/0003 + INDEX: the food chain is **leaver (hunter→raw)
    → in-town cook (cooks) → dining** — `cook/simple_furnace_food` cooks beef; dining has
    `ATE_RAW_FOOD` *and* cooked. The cook is a first-class producer (not redundant).
  - **Request generation is job-driven:** each job-change **posts the job's output as a board
    request** (`AddWorkFromUI` / `ChangeJobReward`); the `self_sufficiency` seed posts **raw
    `mutton` + `wheat_seeds`** (`VillageSelfSufficiencyExecutor:711`). Selection is
    **request-driven** (`JobCycler.isValid`): a job is picked only if its **output matches a
    board request** (or it's `always_consider`) — so the cook's recipe is dead unless the
    board asks for something its recipe *produces*.
- **REFINED ROOT (design/data level):** the cook's cooking-recipe outputs (cooked mutton /
  cooked beef, per recipe) **don't name-match the board's requests** (raw `mutton`,
  `wheat_seeds`, `bread`, `cooked_cod`), so gate2 prunes the recipe and the cook loops its
  initial `always_consider` setup pair forever. **The real fix is a data/design question:**
  make the board's food request name a cooked output *the cook can produce* (or the dine /
  demand system request the right item) so gate2 admits a real cook job. Not a selection-code
  bug.
- **VALIDATED (2026-10-05) — production is NOT the bug; the seed is:** there are **two dine
  jobs** with **opposite tags** (`DinerWork`/`DinerRawFoodWork`):
    * `dining` (COOKED) requests **`villager_food`** = `cooked_mutton, cooked_beef, cooked_cod,
      bread, cooked_chicken/porkchop/salmon/rabbit, stews, baked_potato…` (cooked).
    * `dining_raw_food` (RAW) requests **`villager_raw_food`** = `mutton, beef, cod, wheat_seeds,
      potato, mushrooms, …` (raw).
  The COOK's recipes produce the **cooked** set (`cook/mutton`→cooked_mutton,
  `simple_furnace_food`→cooked_beef) — which **matches `villager_food` (the cooked dine) and
  is selected fine.** The RAW dine (`villager_raw_food`: raw `mutton`) is satisfied by the
  **hunter**, and a cook making *cooked* mutton does **not** match that raw request.
  **→ the `self_sufficiency` soft-lock is a scenario seed/design mismatch:** it models a
  **RAW** dine chain (`WorkRequest.of(MUTTON)` + `WHEAT_SEEDS`, "villagers eat raw meat")
  while **onboarding independently staffs the COOK** (whose recipes are *cooked*). The board
  only ever asks for raw / non-cook items the cook can't produce → gate2 prunes the cook's
  recipe → it loops its initial setup pair. **Production cooked-dine towns request
  `villager_food` (cooked) and select the cook — no production bug.** The fix is a scenario
  decision: either request a cooked dish the cook can make (cooked dine) or don't staff the
  cook in a raw-only scenario.
  *(The `JobLogic.doTryWorking` `DEV-DIAG` was **removed** — the work-site hypothesis it
  tested was refuted and it had zero value; the tree is clean.)*

## THE ANSWER: `canFit` is an in-day clock window — the hunt only starts between dawn and ~9:07

- Selection gate: `JobCycler.isValid` (`jobs/town/JobCycler.java:50`) rejects a candidate
  when `!canAlwaysStart.test(c) && !canFitInDay.test(c)`. `canSatisfy` is NOT the
  blocker — the scoreDump shows the sword job `score>1.0, all tools available, Satisfies
  request: mutton` in the soft runs.
- **`canFitInDay` is `ServerJobsRegistry.canFit` (L710-723) — it is a wall-clock-in-the-day
  window, NOT the supply check the `ALWAYS_CONSIDER` javadoc describes** (the
  `SpecialRules.java:95-98` “abundance of supplies” comment misnames the mechanism it
  bypasses):

  ```java
  long jobDuration = w.jobFunc.apply(villagerID).getTotalDuration();  // sword hunt: 2000 (sum of time-states, DeclarativeJob:159)
  long finalTick = currentTick.dayTime() + jobDuration;
  if (fromDayTime(finalTick).compareTo(fromDayTime(currentTick)) < 0) return false;
  return {MORNING, NOON}.contains(fromDayTime(finalTick));
  ```

- With the `Signals` segments — MORNING [0,6000), NOON [6000,11500), EVENING
  [11500,22000), NIGHT [22000,24000) — and D=2000, the **acceptance window is
  day-time `t ∈ [0, 9500)`**: the hunter may only START the hunt between dawn and ~9:07
  in-game. 9500 → next dawn (60% of the clock) is a hard NO regardless of tools/score/request.
  *(Unit-pinned by `DayWindowTest`, see the evidence note further down.)*
- So "fitting in a day" = "can finish by end-of-noon". The stall is a **scheduling**
  interaction, not an availability one: the hunter (re)asks on the board only at its
  state-transitions, the seek job itself bails to downtime after 1000 ticks
  (`WorkSeekerJob:92` `withMaxTickLimit(1000).withMaxTickFallback(DowntimeWork)`), and in
  a SOFT seed those evaluations never land inside [dawn, 9:07). In a GREEN seed the hunter
  asks immediately after the sword is posted and adopts within seconds (probe-s1: posted
  20:51:33, adopted ~20:51:35; GREEN 1933_f4 same shape).
- **Evidence gap (honest) → resolved by Tier 3 (2026-10-04):** the per-line rejection
  (`Villager will not do {hunter/sword} because there is not enough time
  left in the day` — logged by that very `canFit` closure,
  `TownFlagBlockEntity.changeJobForVisitorFromBoard`) was NOT captured in Tier 2
  (that seed's hunter never reached the out-of-window ask — a scheduling mode);
  **Tier 3 captured it live: 490× `REJECT@canFit(gate1)` (`fit=false`), 0 canSatisfy
  rejects, `cas=false`.** See the Tier-3 section below. Every other link is code/log-verified.
- **Window arithmetic — now unit-pinned (2026-10-04, handoff `pintail` Tier 1):**
  `DayWindowTest` (`src/test/java/ca/bradj/questown/jobs/DayWindowTest.java`) rests the whole-day
  sweep of the `canFit` predicate on the registry-free `Signals` seam and asserts the accepted
  start set is **exactly `t ∈ [0, 9500)`** (boundary pair 9499→NOON fit / 9500→EVENING miss,
  no overnight wrap, window contiguous), plus the `fromDayTime` segment boundaries (6000, 11500,
  22000). Green 7/7. The registry-bound half of `canFit` itself (the `Work` supplier +
  `getTotalDuration` lookup, `D=2000` known from the `hunter/sword` job JSON
  `work_states["time":2000]`) is NOT unit-loadable without a Forge harness — pinned by
  `ServerJobsRegistryCanFitTest`'s single `todo_canFit…` failing assertion per the repo
  doctrine, so the suite stays honest about what the Forge environment still owns.
- **Tier 2 RAN (2026-10-04) — the per-ask rejection is a measured ZERO, not a gap:** the
  held SOFT run (6 days, `Questown:Flag` at DEBUG) reproduced the stall and captured **zero**
  `not enough time left in the day` lines — see the Tier-2 section below. The window's
  *shape* is a unit fact; the live stall is at a gate this rejection line does not cover.
- **Fix implication — REJECTED (user, 2026-10-04):** `always_consider` on the sword jobs
  IS the bypass for exactly this predicate (`!canAlwaysStart && !canFit → skip`) — per the
  rule's own comment and the only Java-side precedent (`DowntimeWork.java:93`). **The dev
  does not want `always_consider` on this job; do not re-propose it.** (And see the
  Tier-2 probe below — the predicate may not even be the live gate.)

## Battery v3 — the guarantee helped, but the stall survives (post `08249841`)

**Result: 4/8 GREEN (50%)**, vs 7/9 soft (78%) pre-fix. 8 seeds @ 200 t/s, early-exit on
`cookUnlocked=true`, 5-day watch otherwise. (`run/samples/BATCH-fast-SUMMARY.md`; batch v3
of `run/suff-batch-fast.sh`.)

1. **The guarantee reaches only the extraction path.** In GREEN `1933_f4` the LOOT closed
   on a **natural `Raw Chicken` roll** (chicken is 15% of the loot table — a raw food too),
   with the prepended mutton landing *after* the close. The prepend = insurance on the
   "a hunt completes" branch, not a rescue of the "no hunt ever completes" branch — which
   is where most of the residual soft lives.
2. **The residual SOFTs (4) split into two distinct variants, both upstream of any
   extraction:**
   - **Variant B (3/4: `1922_f3`, `1937_f6`, `1950_f8`) — the work-picker never adopts
     the hunt.** Swords CRAFTED (`ITEM/COMPLETED wooden+stone sword`),
     `JOB_CHANGE/hunter_qt_sword` OFFERED, then **0** `hunter/sword` transitions, **298**
     `hunter/seeking_work` watch-samples to the end. Per “THE ANSWER”: rejection is
     exactly `canFit`'s clock window never being entered — a scheduling stall.
   - **Variant C (1/4: `1912_f2`) — room-builder gave up upstream.**
     `[room-builder] could not resolve recipe blocks for questown:fisher_qt_fish` and
     `… farmer_qt_harvest_wheat`; the sword ITEM quests never even opened; roster frozen
     {gatherer=2, hunter=1}. A **separate** RoomRecipes-resolution bug, not the hunt gate.

## Tier-2 live probe — the predicted canFit rejection NEVER fires (2026-10-04, handoff `pintail`)

A held `village/self_sufficiency` SOFT seed at ~200 t/s (6 in-game days), `Questown:Flag`
logger at DEBUG so the rejection line would surface. Evidence: `run/samples/tier2-canfit-
probe-20261004.md` (local; `run/` is gitignored).

- **Stall reproduces live (verified):** the hunter sits in `hunter/seeking_work`
  (31.5k journal-snapshot lines), wooden+stone swords `COMPLETED`,
  `JOB_CHANGE/hunter_qt_sword` `ACTIVE`, 0 adoption, hands empty.
- **Score/canSatisfy ruled out live (verified):** the executor's stall score-dump (the
  DEV-DIAG in `VillageSelfSufficiencyExecutor.maybeEmitSeekingStallDiag`) shows
  `hunter/sword` **1.009**, `sword_half_day` **1.005**, `sword_full_day` **1.008** —
  "Satisfies request: mutton; all ingredients and tools available."
- **THE PREDICTED REJECTION IS ABSENT (verified):** across all 6 days there are **zero**
  `Villager will not do … not enough time left in the day` lines, although `canFit`
  denies 60% of each day. (The capture is proven to work — other `Questown:Flag` DEBUG
  lines DO fire in the same run; the logger is not the gap.)
- **Inference (not yet proven):** in `JobCycler.isValid` that line is *the* runtime
  signature of an ask that **reached** the clock-window gate and was **denied**
  (`!canAlwaysStart && !canFit → return false`; `always_consider` is off, so
  `canAlwaysStart=false`). Six days without it means the stalling hunter is **not** being
  denied per-ask — the board-ask either yields **no adoptable candidate** (empty/filtered
  `possible` set → cycler returns null with no gate evaluation → no line, no adopt) or the
  seek never reaches `nextForVillager`, or — the one silent branch — `canFit` passes
  (in-window) and `canSatisfy` fails on the live `requestedResults`, which `JobCycler`
  rejects with **no log line**. **This refutes "asked-but-clock-rejected" for this SOFT
  seed** and does not invalidate the Tier-1 unit pin (the window math is still correct for
  a job that actually reaches the gate). The live stall is at a gate the rejection line
  does not cover.

## Tier-3 live-gate pin — the pin IS `canFit` (2026-10-04, handoff `pintail`)

A held `village/self_sufficiency` seed at ~200 t/s, `Questown:Flag` at DEBUG,
**`ENABLE_DEV_COMMANDS=1` in the fresh gradle-daemon env**, with a targeted `DEV-DIAG`
block in `changeJobForVisitorFromBoard` that mirrors `JobCycler.isValid` per candidate
(logs each candidate + which `return false`). Evidence:
`run/samples/tier3-canfit-livegate-20261004.md` (`/tmp/tier3-server.log` full log).

- **THE GATE IS `canFit` (gate 1), verified live:** the hunter's `hunter/sword` ask,
  evaluated after the in-day window (day-time ≥ 9500 ≈ 09:30), is
  `cas=false fit=false satAny=false → REJECT@canFit(gate1)`. The cycler
  **has** a candidate (`candidates=1/3` → `hunter/sword` is the sole unlocked one) and it
  is **rejected** — this is *not* the "empty `possible` set" branch.
- **Tallies:** `hunter/sword` → **490** `REJECT@canFit`, **24** `ACCEPT(satisfy)`
  (in-window, the hunter does adopt — `cur → sword → dining`), **0** `REJECT@canSatisfy`.
  `always_consider` stays **off** (`cas=false`). The `canFit` closure's own "not enough
  time" line fires **13,326×** this run — the logger/capture is proven to work.
- **Reconciliation (honest):** Tier 2 and this are *different* seeds. Tier 2's stall was
  **Mode B** — the hunter never reached the in-window ask (a scheduling symptom of the same
  narrow window). This seed was **Mode A** — the hunter reaches the ask and is denied by
  `canFit` out-of-window, adopts in-window. **Both are downstream of `canFit` being a narrow
  in-start-window check** (the Tier-1 `DayWindowTest` `t ∈ [0,9500)` pin). So the pinned
  rejection gate is `canFit`; the residual SOFT question is whether the hunter's seeks land
  inside the window (Mode B), not which predicate denies them outside it.
- **Confirms the original diagnosis** and the Tier-1 unit pin; refines (does not refute) the
  Tier-2 "zero per-ask rejection" reading, which was a seed-specific scheduling mode.

## Trace (b) — why the SOFT hunter's seeks miss the window (2026-10-04, handoff `pintail`)

A live `village/self_sufficiency` SOFT seed (hunt/food chain stalled: `cook_qt_simple_
furnace_food` `ACTIVE`, cook `NO_JOBSITE` in a `[lock]` dump, `2 hunter` townies stuck at
`selected=null` for 2,000+ board-asks), `ENABLE_DEV_COMMANDS` in the daemon env, the
`[jobcyc]` mirror of `JobCycler.isValid` per candidate. `/tmp/tier3b-server.log` (local;
`run/` gitignored).

- **The SOFT hunter REACHES the board and asks** (not bailing before it): **2,036**
  `REJECT@canFit(gate1)` vs **24** `ACCEPT(satisfy)` (in-window). Zero `REJECT@canSatisfy`
  for the hunter. So this is **mode b2** (ask reaches the gate + out-of-window), not b1
  (empty-candidate / bail before the board). `canFit` is re-confirmed as *the* live gate
  on a real SOFT seed (Tier-3 was a Mode-A/GREEN seed where the hunter adopted 24×).
- **The DAY CYCLES** — 8 nights slept, day reports 1–5, `time set day` firing (the
  harness skips night: `if (level.isNight()) time set day`, → day-time 1000). So the
  in-window band `[1000, 9500)` is present every day; the stall is **not** a stuck clock.
- **THE ASKS ARE PHASE-LOCKED OUT-OF-WINDOW:** even with the in-window band present daily
  (the effective harness day is `[1000,13000)`, in-window = `[1000,9500)` = 71%), the
  hunter's board-asks land overwhelmingly in the out-of-window band `[9500,13000]`
  (2,036 out-of-window asks vs 24 in-window). The acceptance window
  (`DayWindowTest` `t ∈ [0,9500)` = dawn→~09:30, D=2000) denies them; the hunter
  (re)enters seeking_work in a cycle (sword hunt D=2000 → dining → seek-retry) whose ask
  phase is scheduling-locked to the out-of-window band, so `canFit` (gate 1)
  (`ServerJobsRegistry.canFit`, `fit=false`) denies and the hunter never re-adopts → the
  hunt/food chain can't keep up → the cook food quest stays `ACTIVE` and the cook is left
  partial (`NO_JOBSITE`).

**Answer to (b):** the SOFT hunter's seeks miss the window because the **ask phase is
phase-locked to the out-of-window band** (a scheduling/interference between the hunter's
hunt→dine→seek cycle and the day clock), and **`canFit` is the gate that then denies
them**. The precise phase-lock mechanism (why it locks) and its effect on the chain are
pinned below with the dayTime column (trace b.2).

## Trace b.2 — the phase-lock, pinned with the dayTime column (2026-10-04, handoff `pintail`)

`[jobcyc]` now emits `dt=` (day-time, [0,24000)); re-ran `village/self_sufficiency` at 200
t/s (`/tmp/tier4-server.log`, local). The hunter's asks are **bimodal in the day — no
overlap, a dead middle band** (`dt` in ticks; in-window band = `[1000, ~9500]` per the
Tier-1 pin `t∈[0,9500)`):

- **Morning asks = ACCEPT (adoptive):** `dt` only in `[1376, 8881]` — values 1376, 1436,
  1573, 3761, 3851, 6484, 6567, 8410, 8791, 8881 (all in-window → the hunter adopts the
  sword hunt, hunts, dines, re-adopts the next in-window slot).
- **Lates-day asks = REJECT@canFit (stalled):** `dt` only in `[11986, ~13999]` — 28 at
  `[11986,11999]`, 466 at `[12000,13999]` (~494 total). `dt=[9500,11986]` and the band
  `[8881,9500]` have **zero** asks (the hunter simply does not ask there).
- **Structure:** the day's in-window band is *early* (`[1000,~9500]`) and the hunter's
  lates-day asks (its "I want to hunt again but canFit says not enough time left" retries)
  fall in the *late* out-of-window band `[9500,13999]`, which sits at the tail of the
  harness day (before `time set day` night-wraps → 1000). So lates-day seeks miss the
  window **structurally**: the next in-window opportunity is only after the night-wrap
  (morning). The hunter therefore hunts productively in the morning, then idles in a
  lates-day seek→REJECT loop (~494 lates-day asks) until the day wraps and the morning
  window returns. 8 day-wraps observed.
- **Effect in THIS run (honest):** the chain still advanced — `hunter_qt_sword` and
  `villager_simple_furnace_food_raw` are **COMPLETED**, `cook_qt_simple_furnace_food`
  **ACTIVE** (the morning hunts sufficed to open the cook). So here the narrow window is
  a *symptom* (a lates-day seek-reject loop), not a full SOFT lock. The full SOFT lock
  occurs when the town's food demand needs **more hunts than the morning in-window band
  can absorb** — the window then caps the hunter's daily productive time to the morning
  (no lates-day hunting), and the chain stalls. The lates-day REJECT loop is the visible
  signature of that cap.

**Answer to (b), refined:** the SOFT hunter's seeks that miss the window are its **lates-day
asks**; they are out-of-window because the in-day window (dawn→~09:30, D=2000) is early in
the day and those asks fall in the lates-day band `[~9500, ~14000]`, which is out-of-window
by design (`canFit`). The gate is `canFit`; the cap is the narrow early in-window band that
gives the hunter only a morning slot for hunting, so lates-day demand hits the reject loop
(and the chain stalls if the morning can't absorb the day's food demand).

## Remaining options (none applied; `always_consider` rejected by the dev)

1. `always_consider` — **REJECTED (dev, 2026-10-04); do not re-propose.**
2. ~~Pin the live gate~~ — **RESOLVED (Tier 3, 2026-10-04):** a targeted `DEV-DIAG`
   block in `changeJobForVisitorFromBoard` (mirrors `JobCycler.isValid` per candidate)
   on a live SOFT-arena seed shows the hunter's `hunter/sword` ask is rejected **by
   `canFit` (gate 1, `fit=false`)** 490×, with **0** `REJECT@canSatisfy` and
   `cas=false`. The live rejection gate is `canFit` — confirming the Tier-1 unit pin
   (`t ∈ [0,9500)`) and the original diagnosis. The residual SOFT question is now
   *scheduling* (Mode B: the hunter's seeks miss the narrow [dawn, ~09:30) window), not
   *which predicate denies* (that is `canFit`). `run/samples/tier3-canfit-livegate-20261004.md`.
3. **Variant C** (room-builder `could not resolve recipe blocks`) — separate bug, separate
   repro. 1/4 of the residual but a permanent stall when it fires.
4. **Widen the admissible band / shorten the hunt / accept morning-only starts** (dev's
   call, not applied) — the non-`always_consider` fixes for the gate; note they don't help
   a Mode-B hunter that never *seeks* inside the window. Deprioritized: the SOFTs sat in
   `seeking_work` the whole 5-day watch.

## Confound worth checking with the observer
Have the hunter "stuck early, no cook ever unlocks" been seen on a live server? That pins
whether the autotest arena's day clock is representative of live play.

## EXECUTION PIN (2026-10-05, live-instrumented) — the soft-lock is a FLAKY work-site resolution
This is the deepest, most likely real root, verified across two seeds of the same scenario.

- **The execution gate** (idle vs. working) is `status.isWorkingOnProduction()`, computed by the
  `JobStatuses.usualRoutine` decision tree. Its load-bearing dependency is
  **`dHasWorkableBlocks = town.containsWorkableBlocksAtAnyState()`** (JobTownStates:33 →
  `roomsWithWorkableStatefulBlocks()` → `RoomsWithWorkableBlocksAtAnyState.apply()`).
  **When it is FALSE, the tree returns `NO_JOBSITE`**, and JobLogic:217 then skips
  `doTryWorking` — the cook sits **idle, hands empty, produces nothing**.
- **Live-instrumented the FALSE verdict** (gated `DEV-DIAG` in `RoomsWithWorkableBlocksAtAnyState.apply`,
  then reverted): the reason is **`RoomsContainWorkState=false because no spots found` for every
  state `[0,1,2,3,4,5,6,7,8]`** — **zero work spots are resolvable in any room, any state.**
- **It is NON-DETERMINISTIC (flaky)** — same `self_sufficiency` seed, opposite outcomes:
    * `cookexec` run: kitchen built (`kitchen_small` COMPLETED) + work-spot **resolves** → cook works
      → `extract 'Cooked Mutton' vs LOOT quest questown:villager_food -> true` (produces cooked mutton).
    * `cookmode` run: kitchen built + latched but work-spot **does not resolve** → cook stuck →
      `villager_food` stays `ACTIVE`.
  → **Same setup, opposite result: the kitchen's furnace work-spot/room resolution is flaky.**
- **Selection layer (compounding):** the cook is pinned on set-up jobs
  (`fuel` 422 / `extract` 424 / `mutton` 0); `cook/fuel`'s **broad `#questown:villager_food`
  `initial_request` + `always_consider`** is a genuine contributor (a non-producing job selectable
  exactly like a real recipe). So even the jobs the cook does get selected are the wrong ones AND
  can't work (no spot).

**Bottom line:** the soft-lock is **not a single cause.** It is (1) a **flaky work-spot/room
resolution** of the placed kitchen's furnace (the dominant, real root — when the spot doesn't
resolve, *no* cook job can work), compounded by (2) **selection pinning** onto non-producing set-up
jobs (`fuel`'s broad `villager_food` claim). Both are **production** code/data paths (room/work-spot
resolution + `cook_fuel.json`), not merely a test-seed gap. The `canFit`/hunter-window / "missing
kitchen" / "fuel shortage" theories are all **ruled out** by this.

## HUNT RESULT (2026-10-05) — the flaky factor, code-traced to `getRoomsMatching`
The decisive factor for "can the cook work" is
**`getRoomHandle().getRoomsMatching(job.location().baseRoom())`** (DeclarativeJobTickerDependencies
`getJobSites()` L70, recipe `questown:kitchen_small`). Tracing that:

- `TownRoomsMap.getRoomsMatching(recipeId)` (L528) returns a room **only if**:
  1. the room's **door is registered** in `registeredDoors`/`registeredFenceGates`, **AND**
  2. a **room with that `doorPos` exists** in the live room set (`rooms.getAll()`), **AND**
  3. the room matches the recipe.
- These are **live, transient detection state** — `activeRooms`/`activeRecipes` (L43,45) are
  updated by the **resumable, spread-across-ticks room detector** (`pendingRooms.proceed()` L210),
  and `RandomShortLivedWorkSpot` explicitly handles the "town has no valid room" retry state.
  So the kitchen match is **not a stable registration** — it comes and goes with detection timing.

*Verified*: the mechanism (cook working = the non-empty `getRoomsMatching(kitchen_small)` join;
rooms persist; the "no spots" verdict is constant background in both runs; the kitchen is built in both;
the discriminating factor is the cook's **bursty** production — it produced enough in `cookexec` and not
enough in `cookmode`/`Tier3b`, where the cook was caught even *holding* a `Cooked Mutton`).
*Verified NOT the factor*: door dropping — `dropDeadDoors`/`deregisterDoor` is the only door removal path,
and **neither run has a single deregistration event**, so it is not the flaky cause. `canFit`/hunter-window,
missing-kitchen, fuel-shortage: all **ruled out**.

*Unverified (my earlier over-claim)*: that the specific flaky factor is a **door/room race.** I asserted it
from a code skim without per-kitchen evidence; **the logs do not confirm it.** The discriminating data —
for the kitchen specifically, at the moments the cook worked vs. was stuck: (a) is its door in
`registeredDoors`? (b) is there a room with that `doorPos`? (c) does the room match `kitchen_small`? —
**was not captured in either run.** That is the real remaining unknown. Re-pin with per-kitchen
instrumentation of `getRoomHandle().getRoomsMatching(job.location().baseRoom())` (logged for the cook
instance), correlated with the cook's produce/stuck moments — do NOT assert a root from the code skim alone.

## HANDOFF — dedicated-investigation runbook (2026-10-06)
**State.** Verified: (1) the soft-lock is real + seed-dependent — the kitchen builds and the cook staffs
in *every* run, and only the cooked-food outcome varies (`villager_food` LOOT completes in some seeds,
not others). (2) The **room/door/location layer is exonerated and stable** — `getRoomsMatching(kitchen_small)`
returns the kitchen consistently where it is built (1 match, all doors resolve, never 0). (3) The stuck cook
sits at **`NO_JOBSITE`**, which is one of **two** branches in `JobStatuses.productionRoutine` — **A: town-has
supplies && hands empty** (a supply-pickup stall), or **B: hands empty && town-no-supplies && no workable
blocks** — and is **not** the "no spots found" gate (which fires even in runs where the cook worked, 548×).
**Unresolved:** which branch the stuck cook is on, and *why* — the seed-dependent discriminator. That is the
question to answer.

**Two probes are in the tree (gated on `ENABLE_DEV_COMMANDS=1`; remove on close-out):**
1. **`DEV-DIAG:cook`** — `jobs/declarative/DeclarativeJobTicker.java`, `getStateComputer`. For a COOK job at
   `NO_JOBSITE`, logs `hasSupplies` / `hasSpace` / `workable` (via the same `JobTownStates.forTown(jtp)`
   `TownStateProvider` the decision uses) plus inventory. **This directly separates Branch A from B.**
2. **`DEV-DIAG:kitchen`** — `town/rooms/TownRoomsMap.java`, `getRoomsMatching`. Logs the join breakdown
   (`matches / doors / doorsWithRoom / totalRooms / roomsMatchingRecipe`) for the kitchen recipe. Confirms
   the room layer is present (expected: `matches=1`).

**Repro (a single cook-stuck seed is what's flaky — expect to run until the kitchen builds AND the cook is
staffed AND stuck; some seeds work (cook produces cooked food) and some stall before the kitchen):**
```
ss -tlnp | grep ':25565 '; run/ffeed.sh setup          # port free + FIFO holder
ENABLE_DEV_COMMANDS=1 LANG=C.UTF-8 JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  nohup ./gradlew runServer -Dquestown.autotest=true -Dquestown.autotest.hold=true \
    -Dquestown.autotest.only=village/self_sufficiency -Dquestown.tp.fifo=true \
    -Dquestown.village.selfsuff.days=5 > /tmp/pin.log 2>&1 &
bash run/ffeed.sh feed "/tick rate 200" --expect "Set the target tick rate" --timeout 15   # pace once realtime
```
**Read / interpret:**
```
grep -a 'DEV-DIAG:cook' /tmp/pin.log     # the decisive line
# hasSupplies=TRUE (town has supplies)  -> BRANCH A: the cook has empty hands while the town holds supplies
#   -> the stall is in COLLECTING supplies / supply pickup (why does the cook not pull the supplies?).
# hasSupplies=FALSE (town has no supplies) -> BRANCH B: nothing to collect -> trace UPSTREAM (who was to
#   produce the supplies, and why did they not). Both branches keep the cook in NO_JOBSITE; the DIFFERENT
#   `hasSupplies` is the discriminator.
grep -a 'DEV-DIAG:kitchen' /tmp/pin.log  # expect matches=1 (room layer present, not the factor)
```
**Tear down:** kill the `forageserveruserdev` + `runServer` PIDs, `pkill -f 'sleep 999999'`, stop the gradle
daemon; confirm `:25565` free. Compile with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew compileJava`.
**Do NOT** assert a root from a code skim — the discriminator is the runtime `hasSupplies` value above.


## SESSION FINDINGS (2026-10-06, `linnet` resume; mission: fix BOTH stalls A + B)

**Constraint (dev):** do not use `always_consider` as a fix; do not "seed an unrealistically
supplied village" in the test; drive insight via logs.

### Fixed this session (code, unit-verified or compile-verified)
- **Server crash (reproduced + fixed):** the already-in-tree `DEV-DIAG:cook` probe crashed the
  server (`ReportedException: Ticking entity → NPE in describe()`) the instant the cook hit
  `NO_JOBSITE` — it called `RoomsWithWorkableStatefulBlocks.describe()` on a node the state
  machine never `populate()`d (an unevaluated LZCD branch reads `value` == null).
  *Fix:* `describe()` is now null-safe (returns `<not populated>`/`<no reason>`). Unit-tested
  `RoomsWithWorkableStatefulBlocksTest` (3/3: safe-before-populate, no-match, match).
  The probe itself now reads via a populate-first `safeReason()` (no crash).
- **`canSatisfy` special-job ERROR (fixed):** `canSatisfy` on a special-job id
  (`{root}/downtime`, observed via `updateWorkersAfterRequestChange` → `getWorkSupplier`) fell
  into the `getWorkSupplier` same-root fallback (wrong job + ERROR spam on every run).
  *Fix:* `canSatisfy` now short-circuits special jobs (`specialJobs.idTest`) the same way as
  `isSeekingWork` → `false`. (Worked with the `always_consider`-free constraint.)

### New live evidence (re-pinned today, 200 t/s, `ENABLE_DEV_COMMANDS=1` probes)
- **Variant B is NOT a board-offer problem.** On a seed that stalled with hunters at
  `seeking_work`, live `_qtdev work_score <flag> hunter` shows the board prepared
  `[sword, sword_half_day, sword_full_day]` with `sword score=1.009`, "Highest possible job state:
  3 … All ingredients and tools available for all job states." The board *offers* a fully
  satisfiable hunt; the seekers simply **never ask/adopt** it (zero `DEV-DIAG:ask` lines, zero
  `Timer added` / `Clobbered` — no hunt state was ever started). ⇒ the B stall is a
  seed-dependent **seeker ask/adopt-loop** failure, not gate2/`canSatisfy`/room.
- **Seed variance confirmed across the chain:** seed-3 reached the cook (kitchen built, raw
  `mutton`/`porkchop`+coal in the box) and stalled at `NO_JOBSITE` (where it crashed via the
  probe, now fixed); seed-4 stalled earlier at the hunters (seeker won't ask). The soft-lock is
  seed-dependent and spans hunter→cook, consistent with the VALIDATED demand↔stewardship root.
- **`canFit` window is a real in-day gate but not the observed blocker today** (`work_score`
  showed a satisfiable `sword` at day-1 morning; the seekers never reached the ask that would
  hit it). Keep it for the arithmetic, read the bottleneck as the ask-loop + demand coherence.

### 5-run storage sweep (2026-10-05 evening, 150 t/s, probes off — confirms the stall)
**Verdict capture (raw, reproducible from repo): `evidence-5run.txt` next to this file** —
per-run first-[lock] line, lock mix, journal histogram, stores line, progress, worldTime,
extracted verbatim from the run logs (still in /tmp as of 2026-10-05: `sweep5-run1.log`,
`sweep-run1.log`, `run4.log`, `run5.log`).
Task: "do 5 runs and confirm they all stall on storage space." **Result: 5/5 runs stall, and
in all 5 the stores are physically stuffed with cooked surplus (48× = ¾ of a max stack of
`cooked_mutton`/`cooked_beef`/`cooked_porkchop`), with `NO_SPACE` the dominant townie state (3/5
runs) or the `NO_FORWARD_PROGRESS` lock's consequence (2/5, lock line: stock frozen at ~165,
questsDone=7 — the raw-food quest never completes in any run).** The cook bounces
`fuel↔extract` (the `always_consider` setup pair) in every run; hunters never complete the
LOOT quest. This is the same VALIDATED demand↔stewardship root the fix-decision section hangs
on — the sweep adds the *mechanism* on top of the root: cooked surplus clogs the store room →
`NO_SPACE` blocks every producer, not just the cook. So the "open question" above (cook's
`hasSupplies` discriminator) is superseded: the cook's bounce is one symptom of a storage wall
that hits all townies. Run-by-run detail + verdict-capture commands:
`questown-held-run` skill, *Batch-verification sweep* (the sweep protocol itself was the
2026-10-05 learn: no per-run blocking envelope — 8× the box-time and one lost run).

### The open question (superseded by the sweep above; probe stays safe)
linnet's discriminator — the cook's `hasSupplies` at `NO_JOBSITE` — is no longer the gating
unknown: the 5-run sweep shows the wall is `NO_SPACE` (stores clogged with cooked surplus)
affecting every townie, with the cook's `fuel↔extract` bounce as one symptom. The crash-safe
`hasSupplies` probe remains available if a future run stalls *without* the storage signature.

### The fix decision the dev must make (VALIDATED root, A+B)
The soft-lock's validated root is a **demand↔stewardship/food mismatch**: the scenario models a
**raw** dine while onboarding a **cook** whose recipes are *cooked*, so gate2 prunes the real
cook job (cook loops its `always_consider` setup pair) and cooked surplus accumulates → `NO_SPACE`
(chests full) blocks every producer. Three non-cheat fix loci (all satisfy the constraints):
  (a) demand-side: make self_sufficiency request a **cooked dish the cook can produce** (cooked
      dine), so the cook is selected and the town *consumes* the cooked food (resolves `NO_SPACE`);
  (b) onboarding-side: **demand-aware staffing** — don't staff the cook at all when the board's
      demand is raw-only / unsatisfiable by the cook's recipes (production logic, not test seed);
  (c) capacity-side: a **consumption/expansion sink** so a full store room stops being a hard
      `NO_SPACE` wall (production logic).
None of these uses `always_consider` or pre-supplies the village. Which locus the dev wants is the
single decision that unblocks implementing "fix both A and B."

**Probe-strip pairing (2026-10-05 retro, rec 3):** the six uncommitted DEV-DIAG probe files
(`jobs/JobLogic.java`, `jobs/declarative/AbstractWorldInteraction.java`,
`jobs/declarative/DeclarativeJobTicker.java`, `jobs/declarative/WorkSeekerJob.java`,
`town/entity/TownFlagBlockEntity.java`, `town/rooms/TownRoomsMap.java`) are throwaway and are
removed in the **same session that commits the chosen fix locus** — the `safeReason`
`hasSupplies` probe (DeclarativeJobTicker) stays available until then, and the sweep's
storage-wall verdict no longer depends on any of them.

## 2026-10-05 session: (a) implemented, then raw-stall root pinned fresh (supersedes prior framing)

**State at compaction.** Branch `1.20.6-neoforge-wip @ 8a08cbf1`. Codeword `snipe`, but this session
split into two tracks: (1) implement the (a) demand-side fix, (2) diagnose the RAW stall (the
upstream raw-meat-chain that gates cook-staffing — user: "dozens of prior sessions").

### Track 1 — the (a) cooked-dine fix: IMPLEMENTED + compile-green (NOT yet behavior-verified)
- User picked locus **(a)**. Implemented: added a **cooked-food work request** to the board in
  `VillageSelfSufficiencyExecutor.java` (~:714, after the `WHEAT_SEEDS` request):
  `flag.getWorkHandle().requestWork(WorkRequest.of(TagsInit.Items.VILLAGER_FOOD))` — the TAG
  `#questown:villager_food` (broad, matches whichever cook recipe is staffed), added **alongside**
  the existing raw `MUTTON`+`WHEAT_SEEDS` requests (keeps the raw/hunter chain).
- **Re-pinned facts (2026-10-05):** there is NO `cooked_villager_food` tag — cooked set is
  `#questown:villager_food`, raw is `#questown:villager_raw_food`. The staffed cook job
  `cook/simple_furnace_food` outputs **`cooked_beef`** (JSON `initial_request: cooked_beef`) which
  ∈ `#villager_food` → gate2 `canSatisfy` (output⇄request match) now admits it. `WorkRequest` has an
  `of(TagKey<Item>)` factory (line 35). **Data-layer proof is solid; the live NO_SPACE-resolution
  proof was never reached** because the seed stalled upstream (raw chain) before the cook staffed.
- `./gradlew compileJava --rerun-tasks` green (JDK21 neoforge branch).
- Board request confirmed on the board in run logs: `WorkRequest{tag=questown:villager_food}`.

### Track 2 — RAW stall: FRESH root pin (reproduced 2026-10-05 on run1 + run2, NOT re-asserted)
**Reproduced:** both leavers (hunter→mutton, gatherer→wheat_seeds) sit in `seeking_work` the
**whole run** (run1: 44,498 samples, log line 988→1,477,327). Raw-meat LOOT
`villager_simple_furnace_food_raw` never closes → `cookUnlocked=false` → cook never staffs.

**The board is NOT the gap.** Populated the whole time: `Prepared for hunter: [sword,
sword_half_day,sword_full_day]` 8852× / `hunter: []` 32×; `gatherer: [gather…]` 12496× /
`gatherer: []` 70× (run1 `applyScores`). Satisfiable candidate on offer (INFO scoreDump:
`score≈1.006, satisfies mutton, all tools`).

**The ask never fires (both runs):** `ask firing at board` (WorkSeekerJob.tryExtractProduct) = **0**;
`changeJobForVisitorFromBoard` = **0**; `reached cycler` DEV-DIAG:ask = **0**; `DowntimeWork` 98×;
`ResterWork` 0×; `signal=MORNING` (in-window). So the DIAGNOSIS's `canFit` in-day window is **not**
the active reject — the ask never reaches the cycler at all.

**THE GATE (decisive split, run2 probe `DEV-DIAG:loop` in `JobLogic.tick` ~:104, throttled %200):**
```
[DEV-DIAG:loop] hunter/seeking_work   inJobSite=false status=NO_WORK_POSSIBLE
[DEV-DIAG:loop] gatherer/seeking_work inJobSite=false status=NO_WORK_POSSIBLE
```
The ask gate is `JobLogic:219 if (isEntityInJobSite && status.isWorkingOnProduction())`.
**Both inputs false:** `inJobSite=false` (townie not at its job site = the job board) AND
`status=NO_WORK_POSSIBLE` (non-working: `isWorkingOnProduction = EXTRACTING_PRODUCT || value<10`; IDLE=12/
NO_WORK_POSSIBLE=20 both fail). `WorkSeekerJob` job-site = `SpecialQuests.JOB_BOARD`; the board room IS
placed (seed completed `questown:job_board`). So it's not a missing board — the leaver neither arrives at
the board nor enters a working/go-to-board state. `NO_WORK_POSSIBLE` is the stuck state; `inJobSite=false`
looks like its consequence (not en-route to the board).
`hasBOP=false` on both leavers too (so the BOP adopt path in `changeJobForVisitorFromBoard:823` would
also be bypassed — secondary, not the primary gate).

**Root (fresh):** the leaver `seeking_work` job is stuck in `NO_WORK_POSSIBLE` + `inJobSite=false`; it never
transitions to a GOING_TO_JOB / working state, so it never reaches the board / never asks. **Open
question the next dig answers:** why does the special-job status computer return `NO_WORK_POSSIBLE` (or
leave `inJobSite=false`) when the board is populated+placed, instead of a working/moving status? That is
the `getStateComputer` standard-status path for the seeker (the `getComputeStatusOverrideForSpecialJobs`
returns null when board non-empty → standard computation). NOT yet confirmed which of the two (status vs
job-site) is the causal driver.

### Probe state in the working tree (uncommitted; strip per the pairing rule when a fix commits)
- 6 DEV-DIAG probe files from before (JobLogic, AbstractWorldInteraction, DeclarativeJobTicker,
  WorkSeekerJob, TownFlagBlockEntity, TownRoomsMap) + **NEW this session: `JobLogic` `DEV-DIAG:loop`
  probe flipped `.debug`→`.warn`** so it surfaces at the sweep's `Questown:Jobs=info` level (it was
  filtered at debug). All gated on `ENABLE_DEV_COMMANDS != null`.
- Launch: `ENABLE_DEV_COMMANDS=1` (+ `run/log4j2.sweep.xml`) surfaces them. `QT.JOB_LOGGER=Questown:Jobs`.
- Untracked unrelated: `docs/todo/build-and-release.md`.
- run2 held server was live at compaction (kill by pid: forge `pgrep -f 'forgeserveruserdev'` filtered
  on `launchTarget`; fifo holder via `run/ffeed.sh`; verify teardown in a LATER call + port 25565).

### Next (uncommitted, continue) — dig
Trace the seeker's standard status computation: why `NO_WORK_POSSIBLE` when board is populated. Look at
`getStateComputer`/`usualRoute` for the WorkSeekerJob (root `seeking_work`), the job-site resolution
(`getWorkSpot`) → `isEntityInJobSite`, and the `NO_WORK_POSSIBLE` register path (`registerUnmetNeeds`).
Distinguish: (a) status computer picks NO_WORK_POSSIBLE (no work → the seeker's `possible` list empty at
query time), vs (b) job-site unresolved so inJobSite=false. Then decide the fix locus (seed vs code) for
the user.

### Dig result (run2, 23:15–23:26, worldTime up to ~9173 = day ~2) — REFUTES the DIAGNOSIS mechanism
**run2 hard pin:** raw-meat LOOT `1186 ACTIVE / 0 COMPLETED`; cookUnlocked=false; and DECISIVELY:
- `changeJobForVisitorFromBoard` (the board-ask) fired **0×** the whole run.
- `hasBlockOfProgress` (`hasBOP`) was **0× true**.
- Every `DEV-DIAG:seeker-branch` sample through 23:16:16 showed `preselectedEmpty=true` (board empty
  AT QUERIES), while the board for hunter was actually `[]` until ~23:16:20 then populated
  (`sword`, Satisfies mutton) by 23:16:23 (`scoreDump`). So board and seek-view DISAGREED in time:
  the seeker sees `getFor(getId())` empty while scoreDump (`tfbe.getPossibleWork()`) later shows populated.

**The ask (adopt a job from the board) is UNREACHABLE on BOTH paths (fresh, probe-measured):**
1. **Special-job override** (`WorkSeekerJob.getComputeStatusOverrideForSpecialJobs:155`): the ask
   `changeJobForVisitorFromBoard` is wrapped in `if (hasBlockOfProgress(ownerUUID))`. For a fresh leaver
   `hasBOP` is **never true** (0×) → this path never fires. `hasBlockOfProgress` is a *pending
   job-change transaction* (the GUI `BopTransactionSyncer`/`JobUnlockConfirmMenu.path` concept, not
   "made progress") — i.e., the *confirm* flow, not the first-job-acquire flow.
2. **JobLogic work-ask** (`JobLogic:219  if (isEntityInJobSite && status.isWorkingOnProduction())` →
   `doTryWorking` → `tryExtractProduct` → `changeJobForVisitorFromBoard` via `DeclarativeLogicWorld:135`):
   gated on `inJobSite` AND a *working* status. Both false: `inJobSite=false`, `status=NO_WORK_POSSIBLE`.

**`inJobSite` is a PHYSICAL-PRESENCE check** (`DeclarativeJobTicker:321,350` → `getEntityCurrentJobSite` →
`JobsClean.getEntityCurrentJobSite:204`): non-null only if the entity block position is inside/near a room
in `roomsWithCompletedProduct` OR `roomsNeedingIngredientsOrTools`. A leaver at the **job board** is not
inside a work room → `inJobSite=false`. So the board-ask the leaver needs (adopt `sword` from the job board)
is gated behind a *work-room-presence* check it never satisfies — **the ask never fires, so the
DIAGNOSIS's "the ask fires and fails" is refuted: the ask is gated OFF (inJobSite=false /
NO_WORK_POSSIBLE / hasBOP=false), not fired-and-rejected.**

**Open design question (fix locus, for the user):** for a leaver's first job, the board-ask
(`changeJobForVisitorFromBoard`) is only reachable through (1) a `hasBlockOfProgress` confirm transaction,
or (2) a `inJobSite && working` work-cycle. Neither is in the right state for a *fresh* leaver at the job
board. So the root is: **the seek→adopt path a new leaver is supposed to use is gated on a condition that
is false at that moment — the leaver can't even reach `changeJobForVisitorFromBoard`.** Which is wrong:
(a) the ask should be reachable for a leaver at the job board without being inside a *work* room (the
`inJobSite` gate is the wrong gate for special/seek jobs), or (b) the leaver is supposed to be positioned
inside/near the job-board room when it asks (a positioning / room-registration bug — is JOB_BOARD in
`roomsNeedingIngredientsOrTools`/`roomsWithCompletedProduct` for the seek job?) — these are two different
fixes.

### Dig result 2 (run2, 23:15:40-23:16:18) - REFRAME: it is a patience/race, NEITHER (a) NOR (b)
The leavers did NOT get stuck *at the board*; they **gave up on seeking before the board finished
populating** and dropped into a `DowntimeWork` that has no job site, then never re-seek.
Observed timeline (/tmp/run2.log):
- `23:15:40 Prepared for hunter: []` - board empty; leavers seeking, NO_WORK_POSSIBLE.
- `23:16:15/16 DEV-DIAG:loop ... NO_WORK_POSSIBLE inJobSite=false (t=800 -> 1000)`.
- `23:16:16 Job changed [gatherer/downtime] [from seeking_work]` AND `[hunter/downtime] [from seeking_work]`
  - both leavers **fell to DowntimeWork** at `ticksSinceStart=1000` (the seek job's
  `withMaxTickLimit(1000)` fallback -> `DowntimeWork::getIdFor`).
- `23:16:16 Prepared for hunter: [sword,sword_half_day,sword_full_day]` - board becomes satisfiable the SAME second.
- `23:16:17 watch: 684g/downtime . 3a1c/downtime` (third townie `f15` still seeking).
- `23:16:18 [job-change] requested JobID{hunter/sword} for 684b (ChangeJobReward)` - leavers ALREADY on Downtime.
- `23:16:17+ journal downtime -> IDLE(15) -> NO_JOBSITE(19)`; raw-meat LOOT stays ACTIVE; never re-seeks.

ROOT (fresh, run2): the seek job gives up at its `withMaxTickLimit(1000)` (-> DowntimeWork) at the exact
moment the board becomes satisfiable (a workSpot-null / board-empty race at the check), and `DowntimeWork`
has **no job site** (`NO_JOBSITE`) and does not re-seek - so the leavers sit on Downtime/IDLE while a
satisfiable sword job sits on the board. The ask never fires not because of `inJobSite`, but because
**they are off `seeking_work` by the time work exists.** This supersedes the (a)/(b) fork.
Fix loci: (1) extend the seek job's patience so it outlives board-fill (bump `withMaxTickLimit(1000)`);
(2) make `DowntimeWork` re-seek (`changeJobForVisitorFromBoard`) when the board has a satisfiable job for
that root; (3) hold the leaver on `seeking_work` as a last resort. (1) and (2) are the real candidates; (3) band-aid.

## STATUS (2026-10-07, run-18) — a separate, earlier gate is now fixed; the work-site issue remains
Two distinct soft-lock mechanisms were tangled in this thread. This doc
tracks the **flaky `NO_JOBSITE` work-site resolution** (`getRoomsMatching`).
A *separate*, earlier blocker — the Phase-1 kickoff gate (`job_board` /
`store_room` / `town_gate` / `wooden_sword` stalling `ACTIVE` because the room
recipes registered before the tutorial issued the quests) — is now fixed by
`TownQuests.backfillSatisfiedQuests` (per-tick re-evaluation of open ROOM/ITEM
quests against current town state; see `town/entity/COMPLEX.md`).

- **Verified (run-18):** all 12 onboarding quests complete (raw→kitchen→cooked
  chain), cook + hunter both staffed, `NO_FORWARD_PROGRESS` no longer fires.
- **Still open:** the `NO_JOBSITE` work-site resolution this doc chases — in
  run-18 the cook bounces `NO_SUPPLIES → IDLE → NO_JOBSITE` once it clears the
  gate. The `getRoomsMatching` flaky factor below is the live suspect; the
  Phase-1 fix unblocked, did not fix, this one.

