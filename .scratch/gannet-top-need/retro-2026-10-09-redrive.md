# Retro — 2026-10-09, gannet top-need mission, re-drive session
Session: `2026-10-09T04-29-31-890Z` (Questown). Scope: the run-15 re-drive and
the diagnosis chain that followed (this is the **second** retro of the mission
lineage; the first covered decisions 2+3 + the retro-process fixes already in
AGENTS.md — this one covers the delta).

## Stumbles (ranked by cost)

### 1. Negative claims from partial log evidence — 4 broken narratives
- Claimed the `kitchen_small` blueprint "was seating 32 coal in this run's
  kitchen"; corrected by the user ("never asked for kitchens to be stocked")
  and then by me: the run's kitchens were **recipe-driven and pure**
  (`recipes/kitchen_small.json` = `[furnace]`; the 32-coal `cookBlueprint`
  supplies are the `test:*` room-test fixtures, asserted there).
- Claimed "zero furnaces in the world" from a grep pattern
  (`coal_block|lit_furnace`) that missed plain `furnace` — the run's log said
  `built room ... [Block{minecraft:furnace}]` two lines away.
- Repeatedly claimed the batch chain "parked for want of a quest request" and
  planned an executor nudge; the reward code (`TownFlagTutorialAdapter.makeReward`,
  `MCDelayedReward→addMorningReward`, `AddBatchOfQuestsForVisitorReward`) shows
  onboarding is designed self-chaining (user: "should be a chain. There should
  be no need to request"). The chain died of an **NPE**, invisible to any
  "why didn't X happen" grep.
- Framed "cook produced 59 with no coal" as a suspected free-cooking bug; the
  log showed the fuel gate firing (cook/fuel skipped in a second) and the
  output was quest-supply *extraction* (LOOTTRACE).
**Pattern:** after the first correct evidence-gathering round, I stopped
verifying and started asserting from memory. The user's domain model
out-forecast my narrative three consecutive times.

### 2. Completeness steering (the highest-signal class)
- The "quest request" concept itself: I planned a whole executor nudge before
  reading the reward composition of a closed tutorial batch. The missing step:
  **before concluding "the mechanism never fired," read what the close path
  claims (rewards/appliers) and grep the log for the claim path's failure
  (exceptions, "not ready when requested", `InMorning`, `upgrade`)**. The user's
  nudge ("What is a quest request?") is the spec of that step.
  Task type: "why didn't X happen?" held-run diagnosis → belongs in the
  `questown-held-run` skill as a gate.
- Frequency: this is the mission's core loop; every re-run that "stops
  short" costs a diagnosis round without this gate.

### 3. Near-miss caught (guard held — keep)
- The `ls` quoting failure on `DetectorWithLevel.class` (read as "class
  missing") — caught by `find` the same turn. Pattern is fine.

### 4. Environment friction
- Held-run teardown: `kill <writer-pid>` cascaded SIGTERM to the issuing
  shell (exit 143) and the orphan `sleep 999999` survived. Kill the *writer's
  children* (`pkill -P <pid>`) or accept the cascade and re-verify state in a
  fresh call. Low cost; recorded so the 143 is recognized as teardown, not
  failure.

## Applied (this session)
1. **AGENTS.md** — "Negative claims need a verified source, not a passing
   grep": the anti-false-negative rule + the exact `run 15` incident pair
   (lit_furnace pattern; log-lines vs world state).
2. **AGENTS.md** (Preferences) — domain-assert triage: when the user asserts a
   domain fact during a diagnosis, read that layer before continuing to
   explain the opposite (gannet: 3/3 user assertions held; my competing
   narratives broke one layer down).
3. **`.pi/skills/questown-held-run/SKILL.md`** — new gate in the observe/learn
   section: the "why didn't X happen?" checklist (close-path rewards → claim
   failure lines → exception dump **before** proposing mechanism changes).

## Deliberately not done
- A new skill for "why didn't the chain/event fire" — the existing
  `questown-held-run` skill owns that task type; the gate goes there instead.
- No recommendation to make `getUpgradeRecipe`'s upgrade-vs-chain branch
  prefer the chain during a tutorial — that is the dev's design call
  (presented, unanswered); the NPE fix is sufficient to unblock the chain.

## Session disposition
Work in progress (NPE seam fix in tree, unverified) → **apply recommendations,
commit, then continue the mission loop** (verify TDD, re-drive, iterate to the
coal→miner beat). User asleep and explicitly asked for the loop to keep going.
