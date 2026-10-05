---
name: questown-behavior-verify
description: >-
  Verifies a Questown behavior (navigation, jobs, movement) on a live server when a unit test already settles the question. TRIGGER when asked to "watch" a Questown behavior, run a before/after, or "see" a villager/townie do something live. SKIP when a unit test or the existing autotest suite already proves it. The box is headless, so the evidence for a *behavior* comes from the server log (server-side state, not pixels) — not because there is no window: the game's own window **is** capturable from inside via the client hook, and the `tools/porttest` oracle (goldens in `tools/porttest/goldens/` are the proof) is the right tool for *GUI pixel parity*.
---

# Questown behavior verification (no window)

The box is headless: **external** screen tools fail (`grim` captures 0 bytes, `xdotool search --name Minecraft` finds nothing), so you cannot screenshot from outside. But the game's own window **is** capturable from inside — the client hook (`QtDiagClient.shot` → vanilla `Screenshot.grab`) writes `diags/<name>.png`, which is what the `tools/porttest` oracle uses, and it works headless (the golden bank `tools/porttest/goldens/1.19.2/` is six real PNGs, captured this box 2026-09-30). So: **GUI pixel parity → the `tools/porttest` oracle** (see `tools/porttest/PORTTEST.md`); **behavior** → the server log, because the state you're measuring is server-side (entity positions, needs, room state), not pixels. See `references/questown-dev-launch.md` for the launch plumbing. For the full held-run sequence (server-first launch order, 30s observation cadence, chat/observe channels, the console command set) load the `questown-held-run` skill; this skill is for verifying a specific behavior on a live world, not driving the town through onboarding.

**The user wants the client run, and a held server is the verification path — never ask, offer, or defer it.**
When a held `runServer` autotest or play-test needs a verification pass, just run
`runClient` (add `-Pautojoin` for a live view) and hand control back. Do not ask
the user whether they want it run, do not offer it as a choice, and do not
"watch a client" the run when a unit test or the existing autotest suite already
settles the question (that is the only skip). Do not decline the run because the
box has no window — the client still renders and a user can drive a
reproduction; the automated evidence comes from the server log, and that is what
the run produces.

## The recipe

1. **Prove the objective is loggable.** The autotest harness already writes
   results to `QT.FLAG_LOGGER` via `PlayerTestOutput`/`LogTestOutput`. A
   scenario's `customAssertion` (or `TestResultChecker`) can read live state
   (entity positions, blockstates, needs) and log it. If the thing you want to
   see is not expressable as an assertion, add one — it becomes the objective
   read.

2. **Add a scenario to `TestBlueprintRegistry`.** Model it on an existing
   entry (e.g. `organizerFetchBlueprint`). Give it a `customAssertion` that
   logs the outcome you care about (the before/after signal).

3. **Run it from a console source.** `_qtdev test` cannot be driven from the
   FIFO — it calls `getPlayerOrException()`, which throws on a console source,
   so it silently does nothing. Two ways to run from a headless box:
   - The `AutoTestRunner` hold path: `runServer -Dquestown.autotest=true
     -Dquestown.autotest.hold=true -Dquestown.autotest.only=<scenario>`. It
     runs the scenario and keeps the world alive, logging results.
   - Or register a console command that builds the same `TestExecutor` from a
     console source (origin from a flag scan) with `LogTestOutput`, and send it
     through the FIFO.

4. **Read the server log**, not the client. Grep for the scenario's
   `QT.FLAG_LOGGER` output (e.g. `[nav] RESULT: ...`). That is the evidence.

## Traps

- **Baseline runs: stash only your change's files.** A before/after comparison
  needs a baseline run on the pre-change code, but a full `git stash` also
  reverts unrelated tracked edits (this tree often carries them) that
  untracked in-progress files depend on — the baseline then fails to compile
  with `cannot find symbol` in files you did not touch, and the run is wasted.
  Stash explicitly: `git stash push -- <only the files your change touched>`.
- Scenario-name matching is on the scenario name only; chicken-arc names have
  no `chicken/` prefix (`F3_build_room_to_welcome_mat`, not
  `chicken/F3_build_room_to_welcome_mat`). A 0-match run prints the full
  `available scenarios` list — a cheap way to get exact names.

- `setblock`/`/give` resolve **item** ids, not block ids. Questown blocks have
  a different item id (flag block `questown:cobblestone_flag_base`, not
  `questown:cobblestone_town_flag`). Place mod blocks via the `_qtdev`/`qt`
  harness, not vanilla `setblock`.
- `getblockstate` is unsupported in this build — read block/entity state via the
  assertion's level access, not a console command.
- A held server is a `java` process; match with `pgrep -af '[r]unServer'` or
  `pgrep -af forgeserveruserdev`, not `pgrep -af 'runServer'`.
- **Hunting is off-town; no animals in the world are ever needed.** A leaver
  (hunter/gatherer/miner/fisher) is sent to an imaginary off-town location
  and the "work" is a biome **loot-table roll**, not entity collection — a
  town with zero animals still hunts and brings back meat (CONTEXT.md
  "leaver job"). Do not `summon` prey to make a hunt happen, do not wait
  for the townie to "find" animals, and do not read a world without mobs as
  a blocker. The evidence of a hunt is the townie returning with carried
  meat / the inventory delta, not watching it interact with an entity. A
  leaver with nothing to bring back is missing **known loot / biome
  knowledge**, not missing mobs.

## When a unit test is enough

If the behavior is a pure function of inputs (like a walk-target computation),
a unit test that fails on the old code and passes on the new is stronger
evidence than a live run and is faster. Reserve this skill for behavior that
only settles on a live world (interaction timing, pathfinding end-state,
real-time effects).

## Scenario selection: which scenario proves which behavior

Do not pick a scenario because it runs autonomously or looks convenient. The
scenario name is the wrong selector; the behavior you want to prove is.
The full table (every scenario, its time model, what it staffs, run mode) and
the three-"warp" / TPS breakdown live in
`src/main/java/ca/bradj/questown/commands/test/AUTOTESTS.md`; the reasoning in
`.../commands/test/COMPLEX.md`. The two picks to never mix up:

- **Cook / leaver / food timing / anything that only ticks in realtime** ->
  `village/self_sufficiency`. This runs **realtime** and must be **held** with a
  player near the flag (flag ticks need a player). This is where the cooking and
  food-lock behavior lives.
- `village/growth` **warps the clock with `time set`** and, per COMPLEX.md,
  "structurally cannot exercise leaver/timed-work behavior (which only ticks in
  realtime). A warp skips exactly the behavior it is meant to watch." It also
  does not staff a cook (food comes from the town's own jobs, never seeded).
  So `village/growth` is the wrong scenario to prove a cook/food behavior, even
  though it runs without a client.
- **`warps` are not tick-rate boosts — they skip real ticks and simulate.** The
  harness's `warp` (`time set`, `warpAmount`, default 24000, injected by
  `build.gradle`) fast-forwards the day/night clock. The mod's
  `TimeWarpCommand` / `flag.warpTime(ticks)` / `MCTownState.warp` path
  **skips the intervening real ticks and computes the end state** (evaluates
  each job's "important ticks" in one analytic pass); it does NOT run those
  ticks faster. A "Warp took N ms" log line is the *computation* time, not N
  accelerated ticks. Because it bypasses the tick loop, it **skips** the
  behavior that only ticks in realtime, so it cannot substitute for a realtime
  proof. Warps skip real ticks; they do not speed them up. **Speeding a realtime
  proof is a separate lever, and it EXISTS on the 1.20.6 branch** (20 TPS is only
  the vanilla *default*, not a cap): feed `/tick rate <float>` (1.0–10000.0)
  through the FIFO console after boot, `/tick query` to read back, `/tick freeze`
  / `unfreeze` to pause/resume. Safe sweet spot on this box is 200 t/s. **1.19.2
  (Questown-119) has no such lever — TPS is fixed at 20 there.** Warp is still the
  skip-and-simulate end-state tool behind the single-job tests (`cook`/`farmer`).
  Sweep + "upper limit is measured, not set":
  `.../commands/test/AUTOTESTS.md` ("TPS"); the feed command + per-run default
  live in `questown-held-run`.
- **Before launching a "prove phase X live" run, pin the seed vs the gate AND the batch's room
  requirements.** If the behavior is a phase with a roster/state threshold (e.g. the cook phase
  `PHASE_JOB_CHANGE_FOOD` opens only at **roster ≥ 3**), confirm the chosen seed starts at/above
  that threshold BEFORE spending a launch: `grep VILLAGER_COUNT` (or the seed's roster) in the
  scenario's executor, and `grep villagerCount\|tryPhase2_5`
  in `TownQuests` for the gate. A 2-villager seed proves nothing about a roster-3
  phase. Caught this way it is a one-line seed change; re-derived as a live run it
  costs a launch + a multi-day watch and still ends at the same threshold.
  Then read the phase's *batch* (`addQuestsFor...`): for every job it staffs, the job JSON
  (`data/questown/questown_jobs/<root>_<work>.json`) carries `room` and `block` requirements
  the town must be able to build. If the batch has no quest that creates that room and the
  scenario's seed/room-builder can't supply one, the staffed townie loops
  `seeking_work`↔`downtime` forever (no selectable work), its JOB_CHANGE quest never closes,
  and the batch reward never fires — and no amount of watch-cadence finds it, because the
  town "looks alive." The cook phase's live run (2026-10, egret) spent ~15 min of real ticks
  plus deep extract-pipeline code reads on that loop: every `cook/*` job requires
  `room: questown:kitchen_small`, the 2.5 batch has no kitchen quest (it only appears in
  phase 5, gated behind 2.5), and the diagnosis was two greps (`cook_simple_furnace_food.json`
  `room` field + `grep kitchen_small TownQuests.java`).
- If the scenario is stuck at roster 0 with "no villagers and no open quests"
across several observe windows, that is a scenario limitation, not transient.
  Stop and reassess the scenario choice rather than keep polling.

## Driving a run (vs. observing it)

**Find the stuck *stage* before reasoning about its cause.** In a multi-stage pipeline
(hunt → extract → cook → serve), a lock is held by exactly one stage. Identify it first by
**terminal state**, not by activity volume: the stuck stage is the one whose quest is `ACTIVE`
and **never reaches `COMPLETED`** (`grep -c 'COMPLETED:<quest>' = 0` over the run), *not* the
one with the most `REJECT@canFit` / `seeking_work` / re-select loop lines. A high activity count
at an (upstream) stage that is already `COMPLETED` is a *symptom* (a lates-day `canFit` cap, a
thin job set), not the cause — the lock can be held downstream of it. This session (2026-10-04,
cook-soft-lock) I chased a SOFT run's 2,036× `REJECT@canFit` and blamed the hunter's `canFit`
window, but the hunter stage (raw LOOT `villager_simple_furnace_food_raw`) was already
**COMPLETED** (620/768) — the actually-stuck stage was the **cook** (`cook_qt_simple_furnace_food`
`COMPLETED=0`, `NO_JOBSITE`/`NO_SPACE`), matching the egret note below (cook phase requires the
`kitchen_small` room the 2.5 batch doesn't provide). One `COMPLETED-count` grep per stage finds
the stuck stage and skips the upstream detour.

**A staffed townie idling across 2+ cycles: first read is its job JSON's
`room`/`block`/`tools` requirements vs the town's registered rooms and held items — never
the loot/extract pipeline.** Idle-ness of a townie that *has* an open quest is a missing
world prerequisite (a room nobody can build, a tool nobody has), and the fix is the
missing quest/room — not the pipeline. The extract pipeline only looks broken when the
item flow is visibly wrong (carrying without depositing, `cannot deposit` lines); with no
such line, the work was never selected.

**A re-select loop (`Job changed to X [from X]` storming on every `cooldown_ticks`,
visible as a `Can't keep up` wall only at high tick rate) is the same read: the job set
the townie was staffed with, not the per-tick cost.** Cost is the symptom at the rate
you happen to be running; at 20 t/s it is amortized away, at 150-200 t/s it becomes
the whole budget, and a "rate ceiling" you measure there is really the cost of a thin
job set the feature does not give the cook. Read staffing (what jobs are unlocked for
that job, and what they can make with what is in the world) before concluding anything
about the tick rate (5-day run, 2026-10-15: cook on `extract` with
`cooldown_ticks: 50` re-selected `extract → extract` ~every 50 ticks; fix
shipped 2026-10-16: `extract`+`fuel` unlocked for any new cook via the
`unlocked_with_root_job` JSON flag, plus `cook/mutton` granted to the
onboarding cook — see `.scratch/cook-skill-unlock/`).

— but a re-select loop is **not always a staffing problem**; it can be **supply
starvation** (the townie *has* the jobs, none workable — no item, no site, can't
deposit). The fastest discriminator, before any job-JSON read, is the townie's
**ProductionStatus** via `_qtdev inspect villagers <pos>`: `NO_SUPPLIES` (has the
job, lacks the item) / `NO_JOBSITE` (no workable block at all) / `NO_SPACE`
(can't deposit). It says supply-vs-staffing in one command. (This session, 2026-10:
the cook loop *looked* like a job-count problem, but `inspect` showed `NO_JOBSITE`
for `extract` and `NO_SUPPLIES` for `fuel` — starved of coal + a loaded furnace,
not short of jobs; the `unlocked_with_root_job` fix was valid but orthogonal to
the storm.)

**When it IS the staffing set, enumerate the staffing PATHS before editing.**
A cook is staffed by ≥2 routes (`register` on spawn, `ChangeJobReward` on
job-change — the self_sufficiency cook takes this one), and the onboarding
identity is *not* a path but a **quest event** (owner adoption in
`processJobChanges`). A per-root grant belongs on the universal path; an
onboarding-only grant belongs on the quest event. Putting the onboarding grant
in `ChangeJobReward` leaks it to every job change (the 2026-10-16 near-miss).
Check `grep -n 'changeJobForVillager\|register(\|processJobChanges'` before
assuming one path.

**The observation standard.** The two modes have different rules. Verification
(this skill's main job) may read code freely: code says what to *measure*. A
driven run is the opposite: the agent plays as a new player and may use only
what the game surfaces (the log, the `inspect` tabs, the chicken, broadcasts,
chat) - code and docs never say what to *do next*, and skill docs record what
a command does and how it fails, never recipes, mechanics, or strategy the game
is meant to teach. When a driven run stalls on knowledge the game does not
surface, the fix belongs in the game or a dev command's output, not in a doc.

This skill is about *verifying* a behavior, not *driving* a town through onboarding.
When the task is to drive a held run by console (place blocks, register rooms,
supply items, flip the clock), do not pre-plan a sequence of actions. Read the
latest `[village]` KNOWN/UNCLEAR line and act only on what it says; one action,
then read the result, then decide the next. Do not infer the next step from
reading Java (e.g. the reward a tutorial phase "should" hand out) — a guessed
outcome reported as fact is the failure mode. The next step is whatever the town
reports, not what the code is expected to do.

**Read the chat channel, not only the `[village]` observe lines.** The town
speaks in chat: `TownMessages.broadcastMessage` sends the player-facing signal
("Quest completed: X", "New visitors will arrive after all players sleep",
"New quests are available! Right-click the town flag") to every connected player.
The observe lines are a *separate*, staler channel that does not carry these.
Each 30s window, read both: the server log's `Broadcasting message` lines
(`grep -aE 'Broadcasting message' <serverlog>` — proof the town sent it) and the
client log's `[CHAT]` lines (`grep -aE '\\[CHAT\\]' <clientlog>` — what the user
sees). The chat tells the *next action* (e.g. "right-click the flag", "after
all players sleep"). But the broadcast is **not proof a quest completed** — it
fires on room re-creation, not on completion; verify completion against the
done list / `qt quests` (a `recipe_created` broadcast can appear while the quest
is still open). Read the chat to know what to do next; verify completion against
the done list.
