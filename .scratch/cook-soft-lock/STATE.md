# Cook soft-lock — investigation STATE

> **STATUS: FIXED 2026-10-06.** Three-part fix committed on `1.20.6-neoforge-wip`:
> 1. `effb43d6` DowntimeState — rest is a reward for work-since-rest (rule b) + idle-rest escape.
> 2. `060a457c` TownQuests — same-root fallback for JOB_CHANGE quests (cook quest now matches).
> 3. `d9fcad5c` Scenario — wait for the gate room (special_quest.town_gate) alongside board + store rooms
>    before spawning villagers; the gate-room shell is a registered room, not a loose mat.
>
> **Run-25 verification (2026-10-06)**: the chain now **completes** — raw-meat LOOT quest COMPLETED,
> cook JOB_CHANGE quest COMPLETED (the `cook/simple_furnace_food` → `cook/mutton` same-root match),
> kitchen QUEST COMPLETED, `villager_food` LOOT COMPLETED, `MILESTONE: cook_unlocked` on day=1.
>
> **Root cause chain (all three fixes needed for the chain to complete):**
> - **A. Downtime gate** — `effb43d6` fixes the cascade firing the downtime gate before the
>   cycler for fresh/never-worked townies (run-19: all 4 cascade entries branched to DowntimeWork,
>   `reached cycler` 0×). Rule b (work-since-rest) + idle-rest escape.
> - **B. Cook quest never closes** — `060a457c`. `cook/simple_furnace_food` is expanded by
>   `ResourceJobLoader` into per-item sub-jobs (`cook/mutton`, …) that never `equals` the source
>   `JobID`; now matches by same-root (exact match keeps precedence for crafter/hunter source).
> - **C. Gate room not a registered room shell** — `d9fcad5c`. The leaver jobs anchor to
>   `special_quest.town_gate`; a loose mat outside a registered room shell produces
>   `getJobSites()=0` (run-6–11: leavers bounce COLLECTING_SUPPLIES/NO_JOBSITE, no food;
>   the whole chain soft-locks → NO_FORWARD_PROGRESS after ~15000 days). Now the scenario builds
>   the gate room before the board/store wait, so it is a real registered room when the leaver
>   loops start.
>
> ---

> **Single source of truth for current state.** `DIAGNOSIS.md` (810 lines) is the *history*
> archive — tiers, corrections, session findings. New sessions read **this** first.
>
> **Discipline (dev-set 2026-10-06):** do NOT bias toward a false sense of confidence.
> Every `CERTAIN` must carry a file:line **and** a log-verification (file + line count/timestamp
> window in which it was confirmed). Every `ASSUMED` must name (a) the exact code:line it rests
> on, and (b) the **specific probe/test that would close it** — not "it's probably X." A
> finding "confirmed in one seed" is not "certain" if the current seed's probe didn't capture
> it; it must be re-probe'd on the current seed before it earns "certain."

---

## Mission

**The `village/self_sufficiency` held autotest must run the full food chain to cooked-food
steady state, end-to-end, with no `NO_FORWARD_PROGRESS` lock and no manually-seeded raw food.**
Winning = a *townie* produces raw meat → the raw-food LOOT quest closes → the cook staffs →
cooked food produces. The visible "cook bounces fuel↔extract / NO_JOBSITE" was a *symptom of a
later seed shape*; the raw LOOT gate is the *gate*.

Success bar (measurable):
- `villager_simple_furnace_food_raw` (raw-meat LOOT quest) shows `COMPLETED` in the
  executor's `[jcscan]` line.
- `cookUnlocked=true`.
- `NO_FORWARD_PROGRESS` absent across the 5-day watch.
- 5/5 seeds green in the batch sweep.

Non-goals (dev-set): `always_consider`; manually seeding raw food; widening the `canFit`
window as a fix.

---

### Chain (each gate has a code-verified name)

1. **Gate: raw-meat LOOT quest.** `TownQuests.java:1092` `MCQuest.loot(...,
   VILLAGER_SIMPLE_FURNACE_FOOD_RAW.location(), 1)`. Completed on *a townie extracting a
   member of the tag* via `processLootQuests(extracted)` (TownQuests:1555–1585) — matched by
   `Ingredients.asIngredient(tag).test(stack)`. NOT by stock.
2. **Tag members** (`data/questown/tags/items/villager_simple_furnace_food_raw.json`):
   rabbit, porkchop, **mutton**, chicken, beef, potato. *All meat, except potato.*
3. **Producer needed: a HUNTER** (meat) or a GATHERER (potato). In this scenario the `gather`
   job seeds `wheat_seeds` (bread chain) — *not* a tag member — so the raw-meat LOOT gate can
   only be closed by a **hunter** (meat).
4. **Hunter adopt: `hunter:sword` from the board (the cycler, TFBE:866–940).** The townie must be
   adopted from the board (`changeJobForVisitorFromBoard` → `nextForVillager`) into a real hunter
   job. **RUN-19: this was never reached — the cascade's downtime gate (TFBE:834) fired first, and
   the cycler was 0× across all 8 days.**
5. **Cook staff** (raw LOOT closes → cook jobChange unlock → cook produces cooked food).

The chain's *gate* is #1; its *upstream blocker* is #4 (hunter adopt). The visible
"NO_JOBSITE" symptoms live in the gatherer / cook and are **downstream** of #4 in this run.

---

## CERTAIN (code + log-verified, with refs)

### The meat-LOOT gate and who must produce it

| Fact | Ref |
|---|---|
| The raw-meat LOOT gate wants `{rabbit, porkchop, mutton, chicken, beef, potato}` | `data/questown/tags/items/villager_simple_furnace_food_raw.json` |
| It completes **on production (extract)** of a tag member, not on stock | `TownQuests.java:1555–1585` `processLootQuests`; `TownQuests.java:1092` (quest def) |
| The cook (`cookUnlocked`) is gated on this LOOT quest | `jcscan` line in run-19 log + `TownQuests.addQuestsForJobChangeAndFood` |
| The scenario's `gather` job seeds `wheat_seeds` (bread), not a tag member — so it does **not** close the gate | `VillageSelfSufficiencyExecutor.assignJobs` (work-requests `mutton` + `wheat_seeds` + `#questown:villager_food`) |
| So a **hunter** (meat) is the required producer of the gate item | inference: tag ∩ {hunter-producible} = meat (rabbit/porkchop/mutton/chicken/beef); a **gatherer** could *also* close it via potato |

**run-19 log verification:**
- `EXTRACTING_PRODUCT` status appears **0×** across the whole run — *no* townie entered the
  extraction/production state in run-19 (grep count 0 in `knot-run19.log`).
- `processLootQuests` log (`LOOTTRACE`) — the `[LOOTTRACE]` calls **do not appear** in
  run-19's log (grep count 0), *or* they fired with `extracted=null` (the null-check guard at
  `TownQuests:1547` logs a line and returns). **Both are consistent with "no tag-member
  extraction ever happened in run-19."** (A non-null extract that didn't match would still log
  a `LOOTTRACE` line — that count is 0 → no match-path extract occurred.)
- `cookUnlocked=false farmerUnlocked=false` at the final `milestones:` line.

### The confirmed blocker: cascade's downtime gate fires before the cycler (run-19, 4/4 entries)

**The cascade `changeJobForVisitorFromBoard` (TFBE:802) is entered exactly 4× in run-19, and all 4
exits are `→ BRANCH downtime`. `reached cycler` is 0× across the entire run. The cycler (gate 6,
TFBE:866–940) is therefore never reached, for any townie, for all 8 days.**

| Fact | Ref |
|---|---|
| The cascade's gate order is fixed: damaged→BOP→**downtime**→deposit→diner→**cycler** | `TownFlagBlockEntity.java:816–818` (damaged), `822–830` (BOP), `834–843` (downtime), `848–849` (deposit), `855–862` (diner), `866–950` (cycler) |
| The **downtime** gate is `isReadyForDowntime(ownerVUID, tick) && !DowntimeWork.matches(currentJob)` → `DowntimeWork` | `TownFlagBlockEntity.java:834–842` |
| `isReadyForDowntime` returns **true for a fresh townie** that has never registered a downtime: `mostRecent = getOrDefault(mostRecentDowntimeTick, from, -maxTicksBeforeDowntime)`; `(currentTick - (-max)) >= max` ⇒ true | `SimpleVillagerHandle.java:705–706` |
| The `[DEV-DIAG:ask]…BRANCH downtime (isReady=true)` probe fired **2× for `da6d40ed` + 2× for `6eeeefa4`** — **all 4 at 04:38:59** (run start) | run-19 log, grep `DEV-DIAG:ask.*BRANCH downtime` |
| `[DEV-DIAG:ask]…reached cycler` fires **0×** in the whole run (8 days) — the cycler (the job pick) is never reached | run-19 log, grep `DEV-DIAG:ask.*reached cycler` |
| No townie in run-19 ever took the **real** hunter job (`hunter:sword/*`) — 0× `Job changed to …hunter:sword*`; the `[jobcyc]`/cycler lines (TFBE:886 `nextForVillager` + `:888–939` dump) fire 0× | run-19 log, grep `Job changed to JobID{rootId='hunter', jobId='sword` → 0×; grep `\[jobcyc\]` → 0× |
| `handleMorning` ran **406×** yet the cycler was still 0×: so the "clear ready for downtime" path (SVH:638 `registerMostRecentDowntime`) did **not** unblock the stuck hunters | `SimpleVillagerHandle.java:638`; run-19 log `handleMorning` count 406 |
| `da6d40ed` real sequence: `gatherer:gather`(04:38:39) → `gatherer:seeking_work`×7(04:38:40) → **`gatherer:downtime`(04:38:59, the 2 BRANCH-downtime entries)** → **ChangeJobReward re-root to `hunter:seeking_work`**(04:39:41) → **stuck `hunter:seeking_work`** (final) | run-19 log, uuid `da6d40ed-...` |
| `6eeeefa4`: re-rooted to hunter at run start; **2 BRANCH-downtime entries at 04:38:59**; final `hunter:downtime` | run-19 log, uuid `6eeeefa4-...` |

> **Inference (honest, not a certainty):** the cascade is only entered 4× (at run start) and then the
> stuck hunters stop re-entering it. *Why* they stop re-entering (they rest → re-seek → but never
> reach the cascade's cycler again) is **one remaining open item**. What is certain: when they did
> enter, the downtime gate (3) fired before the cycler (6); and after those 4 entries, the cycler was
> never reached, ever. That is a confirmed fact about the *outcome*, independent of the re-entry
> trigger.

**run-19 log verification:**
- The *only* seeker-branch probes captured for `da6d40ed`/`6eeeefa4` in run-19 were at
  **04:38:40–04:38:50, as `gatherer:seeking_work`** (before the hunter re-root at 04:39:40)
  and all show `hasBOP=false preselectedEmpty=true` (3–5 samples each) then
  `registeredUnmet=true journalStatus=NO_WORK_POSSIBLE` (04:38:50, 1 sample each).
- **No post-re-root (04:39:40→) hunter-phase seeker-branch probes appear in the run-19 log** for these
  townies — i.e. the current seed's probe did NOT capture the hunter-sword adopt decision.

> **Honesty note (dev rule):** the *gatherer-phase* probes (`hasBOP=false`) are NOT the same
> as the *hunter-phase* probes (the adopt decision for `hunter:sword`). Do not carry the
> gatherer-phase `hasBOP=false` over as "the hunter's BOP is false." The current seed's probe
> did not capture the hunter-phase decision. (The 2026-10-04 seed DID capture it — see
> `knot-run-capture-bop` log, §"HUNT RESULT (2026-10-05)"; that is the proven seed for the
> BOP/canFit question, but per the honesty rule it must be re-captured on the current seed
> before it is declared certain *here*.)
>
> **Reconciled (2026-10-06, run-19):** the `DEV-DIAG:seeker-branch` probe (top of
> `getComputeStatusOverrideForSpecialJobs`, `:143`) fired in the gatherer phase (04:38:40–50) and
> did **not** fire after the re-root (04:39:40→). **But** the cascade *was* entered 4×
> (`DEV-DIAG:ask…BRANCH downtime`, 04:38:59) — all for the *gatherer-phase* seekers, before the
> re-root. After the re-root the hunters are `hunter:seeking_work` and **re-enter neither the
> override nor the cascade** (0 more `DEV-DIAG:ask` lines). That re-entry stop is U6. So the root
> is **not** "BOP-gated" (U4 refuted: it was entered, `hasBOP=false` at gate 2) and **not** "canFit
> REJECT" (U2 ruled out: cycler 0×) — it is (a) the **downtime gate (3) before the cycler (6)**
> (U1 confirmed) *and* (b) the **seeker's board-ask stops re-firing after the initial 4 entries**
> (U6, `AbstractWorldInteraction:480`).

---

## ASSUMED / UNCERTAIN — each with the probe that would close it

> One line per item. "Open" = still true as of 2026-10-06. **The named probe is what would
> close it.** Items marked RESOLVED / RULED-OUT carry the run-19 log verification (N× + window).

| # | Assumption / claim | Status (run-19) | Log-verification / code ref |
|---|---|---|---|
| U1 | The gate that blocks the stuck hunters is the **downtime gate (3) firing before the cycler (6)** | **RESOLVED — CONFIRMED** | `TownFlagBlockEntity:834–843` (downtime gate, order before `:866` cycler) + `SimpleVillagerHandle:705` (isReady default -max ⇒ true for fresh). Log-verified: `DEV-DIAG:ask…BRANCH downtime (isReady=true)` ×2 for `da6d40ed` + ×2 for `6eeeefa4`, **all at 04:38:59**; `reached cycler` ×0; `jobcyc` ×0. |
| U2 | **`canFit` / `canSatisfy` REJECT the sword adopt** | **RULED-OUT for run-19** (a different seed's seed) | `canFit`/`canSatisfy` are *inside* the cycler (TFBE:874–939), which the run never reached (`reached cycler` ×0). The 2026-10-04 seed's `490× REJECT@canFit` is a *different* run (`knot-run-capture-bop`); per the honesty rule it is not "certain here". |
| U3 | **`registeredUnmet` latch prevents re-evaluation once the seeker saw an empty board** | **Open (unrelated to the confirmed gate)** | `WorkSeekerJob:158–164`; `TownPossibleWork:538`. Still unverified against the current seed in the window after 04:38:54; the confirmed gate (downtime-before-cycler) makes this secondary, not the root. |
| U4 | **The `changeJobForVisitorFromBoard` cascade is never entered for the stuck hunter in run-19** (i.e. 155-branch skipped) | **REFUTED** | `DEV-DIAG:ask…BRANCH downtime` fired 4× (the cascade WAS entered), at `TownFlagBlockEntity:839`. It was not BOP-gated (gate 2 at :822–830 skipped; probe `hasBOP=false`). It was entered and parked at gate 3 (downtime). |
| U5 | **`withMaxTickLimit(1000)` patience elapses before the board populates** | **Open (unrelated to the confirmed gate)** | `WorkSeekerJob:74`; `JobLogic:230`. The 4 cascade entries (the seek job's path) all branched to downtime (not the seek-timeout); so this is *the* gate, not *a* timeout. The seek-timeout (→ DowntimeWork directly, not via the cascade) is a *separate* mechanism that I have not yet verified. |
| U6 | **Why the stuck hunters stop re-entering the cascade after the initial 4 entries** (rest → re-seek → never reach the cycler again) | **Open — the new primary item** | The seeker's board-ask is `WorkSeekerJob.tryExtractProduct` (`:115–132`) → `changeJobForVisitorFromBoard`, gated at `AbstractWorldInteraction:480` on `isEntityClose && action >= maxState && workLeft()==0`. After the 4 `BRANCH downtime` entries (04:38:59), `tryExtractProduct` stops firing (grep `DEV-DIAG:ask` = exactly 4, all 04:38:59). **To close:** a probe at `AbstractWorldInteraction:480` capturing `isEntityClose`, `action >= maxState`, `workLeft()==0`, and whether `tryExtractProduct` is called — for the stuck hunter in a run.

**Status summary (as of 2026-10-06, run-19):** PRIMARY ROOT CAUSE **confirmed** = the `changeJobForVisitorFromBoard` cascade's **downtime gate (TFBE:834) fires before the cycler (TFBE:866)**; `isReadyForDowntime` true for fresh/re-rooted (SVH:705 default `-max`); **4 cascade entries (04:38:59) all → downtime; cycler 0× across 8 days; `EXTRACTING_PRODUCT=0`; the raw-meat LOOT gate never closed → `NO_FORWARD_PROGRESS` lock at day 8.** (U1 RESOLVED · U4 REFUTED · U2 RULED-OUT.) Open sub-mechanisms: **U6** (re-entry mechanism, the `AbstractWorldInteraction:480` seek-loop gate — *next probe*), U5 (the seek-timeout, a separate path), U3 (`registeredUnmet` latch, secondary). **No code fix before U6 is confirmed.** |

### What is *not* a reasonable "next step" (already ruled out for this run or dev-set non-goal)

- `canFit` / `canSatisfy` gating — **not the root cause in run-19** (the cycler where they live was
  never reached, 0×). The 2026-10-04 seed had `490× REJECT@canFit` (a different seed); it is not
  established here.
- `withMaxTickLimit(1000)` patience — **not the root in run-19** (the 4 cascade entries hit the
  downtime gate, not the seek-timeout; whether the seek-timeout is a *separate* failure path is U5/U6).
- Manually seeding raw food / `always_consider` — **non-goal** (dev-set).
- "The board is empty" — refuted by the 2026-10-04 seed (`[DEV-DIAG:knot] board=[sword,…] ×1664`)
  and by `DEV-DIAG:rooms jobs=7 CHEST:[store_room] …` in run-19 (the board's rooms/chests resolve);
  the 04:38:40–50 *window* is separate from the confirmed gate.

---

## Decision the dev must make (resolved 2026-10-06)

**Resolved: Option A ("downtime shouldn't fire for a fresh townie"), implemented as a state-model fix.**
The design (per dev, 2026-10-06):

> "Villagers should prioritize rest over work (if they are tired - i.e. they have not rested for N
> ticks)." Plus: a night's sleep = a rest, so the morning is "fresh."

**The bug (run-19 confirmed):** a townie that has not worked (has not produced) and has not rested
should not be gated into `DowntimeWork` before getting a real job. The pre-fix code treated "no
rest record" as "rested long ago" (default `-max` ⇒ `(currentTick - (-max)) >= max` always true), so
a fresh townie was immediately ready to rest. In run-19, da6d40ed was fresh (initialized at 04:38:38,
"Initialized morning rewards for new flag"), 6eeeefa4 was just re-rooted to hunter, and both were stuck
in `seeking_work` (no `EXTRACTING_PRODUCT` for either before 04:38:59). The cascade's downtime gate
(`TownFlagBlockEntity:834`) fired for them before the cycler did.

**The fix (state model):** the state is moved onto a small **pure, Forge-free record**
`DowntimeState` (`ca.bradj.questown.town.entity.DowntimeState`), so the "rest is a reward, not a
default" rule and its transitions are owned by the value instead of scattered in
`SimpleVillagerHandle`. `SimpleVillagerHandle` now holds one `Map<UUID, DowntimeState> downtime`
(replacing the prior trio of `mostRecentDowntimeTick` + `restedUuids` + `workDoneUuids` — the latter
two were pure presence-flags, now folded in):

```
DowntimeState(long n, Long lastRest, Long lastWork)
    fresh(n)                 -> (n, null,  null)      // never rested, never worked: work first
    rest(tick)               -> (n, tick,  lastWork)  // a rest happened; the last-work tick becomes stale
    work(tick)               -> (n, lastRest, tick)   // adopted into a real work job at tick
    isReadyForRest(now)      -> lastRest != null && lastWork > lastRest && now - lastRest >= n
    isReadyForIdleRest(now)  -> lastRest != null && now - lastRest >= n
```

The dev chose **rule (b)** — "rest is a reward for work *since the last rest*" (self-clearing) — plus an
explicit **idle-rest escape**. Two readiness rules, both owned by the record:

- **Primary** (`isReadyForDowntime` → `DowntimeState.isReadyForRest`): work was done **after** the last
  rest (`lastWork > lastRest`), and that rest is at least `n` ticks old. A fresh / never-worked townie
  has `lastWork == null` ⇒ **never ready before it works first** (the run-19 shape). This gates the
  "rest now" cascade branch at TFBE:834.
- **Idle-rest escape** (`isReadyForDowntimeIfIdle` → `DowntimeState.isReadyForIdleRest`): the rest is
  `n` ticks old *without* requiring work-since-rest. A townie whose last rest is old but who has rested
  again and has not worked since (primary is therefore never ready for them) can still rest when there
  is **truly no work** — so they do not sit idle forever. This fires at the **end of the cascade**
  (`changeJobForVisitorFromBoard`, the last gate before `return false`), i.e. *after* both
  `possibleWork.nextForVillager(...)` and `fallbackSelector.tryFallback(...)` came back empty: the
  board has already proven there is no job to hand out.

Rerouted writers (unchanged in *where*): exit-DowntimeWork (SVH:308) and NOON/morning (SVH:647) call
`markRested` → `.rest(tick)`; the cycler-adopt (TFBE:946) calls `markWorked(uuid, tick)` → `.work(tick)`.
**`markWorked` now takes the tick** (so "work" is a *when*, not a set-once flag; it must be after the
last rest for the primary rule to fire). The legacy name `registerMostRecentDowntime` is retained and
delegates to `markRested`. Net: a rested-then-idle townie is only ever parked in DowntimeWork by the
idle-rest escape, never by the primary rule.

**Not-yet-wired follow-ups (not blocking):**
1. *Sleep-wake:* an explicit `markRested` on the `VillagerSleepModule` `stopSleeping` path. "A night's
   sleep = a rest" is currently covered only by the daily NOON handler (`handleMorning`, SVH:647); a
   mid-day nap (e.g. a damaged townie) does not mark rested — tolerable because a sleeping/damaged
   townie is handled by the separate damaged→rest gate (TFBE:816) that fires before the cycler. Add
   `markRested` on the sleep-wake path to cover the mid-day case too.
2. *Re-root carryover:* a townie re-rooted (gatherer→hunter) keeps its `lastRest` / `lastWork`, so a
   townie that worked under the old root can be primary-ready for a rest before doing the new job. To
   treat a re-root as a fresh job (`DowntimeState.fresh(n)`), the reset must run **before** the cycler's
   `markWorked` (TFBE calls `markWorked` *before* `changeJob.accept`), or after the job-change settles.
   Deliberate, not-yet-done. Pre-existing behaviour (the prior `workDoneUuids` latch had the same
   carryover).

**Tests (Forge-free, no `TestDelegator` fakes):**
- `DowntimeStateTest` — 6 cases, all PASS: `fresh_isNotReadyForRest`,
  `restedButNoWork_isNotReadyForRest_butIsReadyForIdleRest`,
  `workedBeforeThisRest_isNotEarnedyetThatTheyWorkedAgain`, `workedSinceLastRest_isReadyAfterRestIsOldEnough`,
  `restedAfterWorkThenIdle_isReadyForIdleRestNotRest`, `transitionsAreValueBased_andOldStateUnchanged`.
- `SimpleVillagerHandleTest` — 5 primary + 2 idle-rest, all PASS: `isReadyForDowntime_*` (fresh;
  afterEnoughTicksSinceWorkedAndRested; justRested; noWorkDoneNeverReady) +
  `registerMostRecentDowntime_delegatesToTheSameStateAsMarkRested` + `isReadyForDowntimeIfIdle_*`
  (fresh; restedButNeverWorked). `markWorked` now takes a tick (tests pass `90L` / `now+10L` / `50L` /
  `10L`, each after the corresponding rest tick, so the primary rule's `lastWork > lastRest` gate holds).

**Pre-existing failures (not caused by this change):** the `scheduleJobRootChange_*` tests fail with
`ExceptionInInitializerError` / `NoClassDefFoundError` on `DinerRawFoodWork` — a Forge-registry-bound
class that cannot be unit-tested (the same class's `completionListener_firesAddExperience_onJobCompletion`
is `@Disabled`). These fail on this session's baseline (`4e668661`) as well. 1 SKIPPED (the `@Disabled`
test).


---

## Probe inventory (already in, strip on close-out)

| File | Probe | Fires on |
|---|---|---|
| `jobs/JobLogic.java` | `DEV-DIAG:loop` (seeker + leaver state, `%200` throttle) | every job loop for a leaver (root=gatherer/hunter) |
| `jobs/declarative/WorkSeekerJob.java` | `DEV-DIAG:seeker-branch` (decision inputs, `%200`) | `specialJobOverride` for `isSeekingWork` townies — **fires on the gatherer phase in run-19, does NOT fire on the hunter phase** |
| `jobs/declarative/AbstractWorldInteraction.java` | `DEV-DIAG:ask` (cycler-reached) | `tryExtractProduct` entry |
| `town/entity/TownFlagBlockEntity.java` | `DEV-DIAG:ask BRANCH …` (gate cascade) | `changeJobForVisitorFromBoard` gates |
| `jobs/declarative/DeclarativeJobTicker.java` | `DEV-DIAG:cook` (NO_JOBSITE discriminator) | cook NO_JOBSITE |
| `town/rooms/TownRoomsMap.java` | `DEV-DIAG:kitchen` (join breakdown) | `getRoomsMatching(kitchen_small)` |

*All `ENABLE_DEV_COMMANDS`-gated. Strip in the commit that closes the issue. DIAGNOSIS.md
"Probe-strip pairing" is the commit-boundary rule.*

## 2026-10-17 run-22 findings

**Original "no meat" bug — RESOLVED.** Evidence (run-22, ENABLE_DEV_COMMANDS=1, /tick rate 200):
- `DEV-DIAG:extract` fires: `jobs/hunter_sword` → chicken, porkchop (loot roll IS reached)
- `food_raw` LOOT quest → COMPLETED (was ACTIVE/stuck in runs 20–21)
- `villager_food` LOOT quest → COMPLETED
- `cookUnlocked=true` milestone reached
- Cook in roster: `cook/mutton` — working (GOING_TO_JOB → COLLECTING_SUPPLIES with mutton in hand)

**NEW deeper issue — the real lock (run-22 and all runs after DowntimeState fix):**

Chain:
1. `TownQuests.java:1128`: `MCQuest.jobChange(batchUUID, null, JobID("cook", "simple_furnace_food"))`
2. `processJobChanges` (TownQuests.java:1496): `v.getValue().equals(wantedJobId)` — exact match
3. `ResourceJobLoader.makeCookJobs`: expands `cook_simple_furnace_food.json` into per-item sub-jobs from
   `VILLAGER_SIMPLE_FURNACE_FOOD_RAW` tag: `cook/mutton`, `cook/beef`, `cook/chicken`,
   `cook/porkchop`, `cook/rabbit`, `cook/potato`
4. `cook/simple_furnace_food` itself is NEVER registered — it is the source/parent JSON ID only
5. `JobID{cook, mutton} != JobID{cook, simple_furnace_food}` → quest unmatchable → ACTIVE forever
6. Lock fires at 3 consecutive days unchanged: `cooking quest ACTIVE, other 11 quests COMPLETED`

**Fix (not yet applied):**
- (a) `processJobChanges`: add `sameRoot(wantedJobId)` in addition to exact `equals` match
- (b) Change quest definition to a real registered sub-job ID (less clean, one-off)
- (c) Register `cook/simple_furnace_food` in the job system as a virtual job (most invasive)

Recommended: **(a)** — the quest's intent is "a cook exists", and `sameRoot('cook')` is the correct
semantics. The `cook/simple_furnace_food` ID is a design artifact of the job-expansion mechanism.

---

## How to update this doc (for future sessions)

1. Read top-to-bottom before touching the investigation.
2. Add **new certainty** by: (a) naming the probe that closed it, (b) citing file:line for
   the code, (c) giving the log-verification (file + N occurrences + timestamp window). Do
   **not** mark "certain" a fact "seen once in another seed" without re-capturing it in the
   current seed (dev-honesty rule).
3. **Assumed** items move to `CERTAIN` only when their named probe has fired and was
   captured in a run log; otherwise they stay in `ASSUMED` with the probe still named.
4. When a probe fires and *refutes* an assumption, mark it `RESOLVED (date, seed, N×)` and
   leave it in the table — do not delete, the record matters.
5. **Do not implement a code fix until the `ASSUMED` item it depends on is `CERTAIN` or
   `RESOLVED`.** The failure mode is a silent no-op that looks like progress but isn't.

## Round 2 — parallel-only confirmation (2026-10-06 evening)

Gate: serial runs out of scope (user) → N parallel standalone servers from one captured
gradle command. Tooling: `run/psweep.sh` (gitignored; recipe lives in
`references/questown-dev-launch.md` "Parallel confirmation sweep"). Launches 3-28
collected ~50% pass / ~50% stall; stall signature is **uniform**: 2× gatherer +
hunter stuck `COLLECTING_SUPPLIES`, **zero LOOTTRACE** (extraction never fires),
day counter advances, raw-meat LOOT quest stays ACTIVE.

### Flakes found and separated (probe chain, all throttled warns, see markers)
1. **Fix (in tree, to commit):** `TownRoomsHandle.getMatches` now unions the
   welcome-mat meta room when no room-map entry carries `special_quest.town_gate`.
   Verified effect: in a world that previously showed `siteRooms=[]`, the selector
   now sees `mats=1 rooms=1` (probe `[PROBE:matRooms]`). This closes the
   "gate room invisible to the selector" class.
2. **Remaining flake (UNFIXED — the exact gap, isolated by probes):** the leaver's
   `cleanRooms()` returns **0 needed predicates** in stalled worlds
   (`[PROBE:checkFn] neededPredicates=0` 10/10) vs neededPredicates=2 in passing
   worlds. `cleanFns` is empty because
   `roomsNeedingIngredientsOrTools` maps **no room to state 0** and (vs passing)
   lacks state 1: stalled = `{0=[]} passing = {0=[], 1=[]}`
   (`[PROBE:cleanRooms] EMPTY state2rooms=…`). Recomputed every tick
   (DeclarativeJobTicker:193 `computeRoomsNeedingInput(dependencies, work)`), so it
   is not a stale cache — in stalled worlds the gate room's job-site/state data is
   simply not there (job-site registration or `work::getJobBlockState` layer).
   Next suspect files: `RoomsStatusLogic.roomsWithState`, `DeclarativeJobTickerDependencies.getJobSites()`,
   the job-block work-status registration at furnish/quest-complete.
3. Give-back latch (`[PROBE:giveback]`) is **not** the blocker: `wtu=null result=true`
   in every world (stuck incl.) — the latch self-heals; it only explains the
   job-cycling while supplies can't be taken.

### Live artifacts (2026-10-06 ~midnight)
- psweep-26 dir=/tmp/psweep-26 port=25586 pid=61111 — PASSING (cook=1, loot=12)
- psweep-27 dir=/tmp/psweep-27 port=25587 pid=61377 — STALLED (the repro; console: `bash run/psweep.sh feed 27 "…"`; `/data get block`, `/_qtdev inspect villagers|quests 0 64 0` all work)
- psweep-28 dir=/tmp/psweep-28 port=25588 pid=61652 — STALLED
- Probes in tree (remove with final fix): ProductionJob `[PROBE:suppliesNull]` +
  `[PROBE:checkFn]` + `suppliesNullProbes/checkFnProbeN`, RealtimeWorldInteraction
  `[PROBE:giveback]` + `giveBackProbes`, TownRoomsHandle `[PROBE:matRooms]` +
  `gateMetaRoomProbes`, DeclarativeJob `[PROBE:cleanRooms]` + `cleanRoomsProbeN`,
  ProductionTimeWarper `PROBE` probe (never fires — wrong path; remove first).

## Round 3 (same session, "Do it now")

New probes (all throttled): `[PROBE:state0]` (RoomsStatusLogic.compute — jobRooms/matches/
rwwcbd/qty), `[PROBE:jobSites]` (DeclarativeJobTickerDependencies.getJobSites — fires only
when a GATE job sees 0 sites), `[PROBE:gateQuery]` (TownRoomsHandle.getRoomsMatching
TOWN_GATE branch — rooms + raw mats list), `[PROBE:rniot]` (DeclarativeJobTicker, fired
when a GATE job's inner map is all-empty; also prints canClaim/state of 3 contained blocks).

Findings (worlds 35-46):
1. The "0 sites / flagMats=0" window is a **pre-seed timing** (fires 01:39:04-10, mat seeds
   01:39:15, mats=1 from 01:39:16) — NOT a second flag object (identities match: one BE ID
   per world across both probe contexts) and NOT oscillation (the systematic per-context
   split in world-42 was actually the two throttles catching different windows).
2. **After** the seed, job sites DO resolve (gateQuery rooms=1, matRooms=1) — even in stalled
   worlds (42/45/46) — yet they stall. So the container-visibility fix is in effect and the
   remaining gate is `RoomsStatusLogic.roomsWithState`:
   `state` entry requires `getJobBlockState(bp) != null && processingState()==state`
   (null state is EXCLUDED) reading the **per-owner** work-status handle
   (leaver rules = [PRIORITIZE_EXTRACTION, NULLIFY_EXCESS_RESULTS] → NO SHARED_WORK_STATUS →
   `town.getWorkStatusHandle(ownerUUID)`; `ownerUUID` is passed in by the job requestor via
   `WorksBehaviour.getJobFunc.apply(UUID owner)`).
3. So the stall = **no work-state record of processingState 0 for (owner, matPos) in that
   leaver's per-owner store, and nothing bootstraps it**: roomsWithState needs the record,
   cleanFns needs roomsWithState, supplies need cleanFns, and the record is normally written
   by the first insert/work at the mat. Passing worlds have the record; stalled worlds don't
   (50/50). Next code targets: `AbstractWorkStatusStore` write path (claim/insert/
   `setWorkLeftAtFreshState`), `tryGiveItems` insert path, and why ~half the worlds never
   record state 0 for the leaver at the mat (owner-identity or initial-claim ordering).
4. Live stalled world: psweep-45 dir=/tmp/psweep-45 port=25605 (console via
   `bash run/psweep.sh feed 45 "…"`); 46 similar; 44 passing. All carry the probe set.

## Round 3b (same session, world batch 50-64)

Probe rounds on 50-64 (58/64 passing, 47-49 mixed, 59-61+57 ALL stalled one batch):
- `[PROBE:ownerStore]` (DeclarativeJobTickerDependencies.getWorkStatusHandle — fires
  throttled, prints room/owner/store identity + that store's gate-mat State).
- `[PROBE:matInit]` (TownWorkStatusStore factory — the mat block IS registry-eligible;
  `init=true` everywhere; `State.fresh()` = state 0 confirmed).
- `[PROBE:scanRoom]` + `[PROBE:scanPos]` (AbstractWorkStatusStore.scanRoom — mat room
  scanned by multiple stores; per-position containsKey/def).
- Established: leavers run ONE per-owner store each (jobHandles map); the per-tick driver is
  TownFlagTicker.updateWorkStatuses (behind isFlagTick = gameTime % FLAG_TICK_INTERVAL).
  `scanRoom` is the seeder for new rooms (registerNewRooms → scanRoom → factory → put).
- THE GATE (world 63 stalled, 58/64 passing): the stalled leaver's own store has
  gateMatState=null PERSISTENTLY while the same-position mat record exists (state 0→2
  advancing) in at least one other leaver's store in the passing worlds. I.e., the
  per-owner store of the stalled gatherer never gets the mat's state-0 record, while
  sibling leaver stores do. All three owners in 63 had gate stores; only one
  (5bb/17c/1bc…) held null.
- Open question (the next read, not a new probe): why does `registerNewRooms` +
  `scanRoom` seed a mat record in some owner stores but not others — same flag tick loop,
  same `allRooms` (which provably contains the mat room: gateQuery rooms=1 post-seed).
  Suspects, in order: (1) `scanRoom`'s containsKey/airCheck branch removing it (air =
  false — mat present); (2) the per-store room set never getting the mat room (rooms Set
  identity vs value-equals on RoomRecipeMatch meta objects — every-tick new instances?);
  (3) factory returning null for that store (init=true says no); (4) store created before
  the mat AND `updateWorkStatuses` throwing before `.tick` in that world (no log evidence
  of a throw in 63). Files: AbstractWorkStatusStore (tick/registerNewRooms/scanRoom,
  lines ~150-280), TownFlagBlockEntity.getRealWorkStatusHandle (1826-1850),
  TownFlagTicker.updateWorkStatuses (90-97), AbstractTownFlagTicker.tickInner (36-95).
- World 64 = passing with full probe set (see its log); no world live-stalled right now
  (59-61 killed? no — 59/60/61 are the ALL-STALLED batch; confirm `bash run/psweep.sh status`).
