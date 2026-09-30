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

## Driving a run (vs. observing it)

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
