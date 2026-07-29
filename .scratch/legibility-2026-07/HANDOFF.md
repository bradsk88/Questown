---
title: Session handoff — legibility pass slice 1 (need bubbles) shipped; what's next
status: ready-for-agent
created: 2026-07-29
priority: p1
---

# Handoff

The perf thread from [[../perf-2026-07/HANDOFF]] is **finished and committed**. The active thread
is now the **"nothing in this mod is silent" legibility pass**, whose first slice (need bubbles)
landed. Everything below is about continuing that pass.

**Next task: pick up the legibility list at item 2 — see §3.**
**Blocker to consider fixing first: the autotest flake in §5. It is degrading every change's signal.**

## 1. What landed this session (7 commits, 2 unpushed)

```
4cf7e52f feat(townies): need bubbles, starting with "can't reach"   <- unpushed
ee153430 fix(villagers): scale injury healing by the flag tick interval  <- unpushed
83efc333 fix(config): re-default the flag tick interval via a V2 key
2c2cf80b fix(config): give EconomicRecordsDepth its own key
c649c3c2 docs: threat-as-pressure ADR, CONTEXT terms, and the open issue files
4a07c0ac perf(flag): stop rebuilding every Work per block position
1f066b04 perf(flag): profile the flag tick and throttle the town container scan
```

`git push` has not been run for the last two (no credentials in the agent environment — Brad runs
`! git push`). **Check `git status -sb` before assuming.**

### Perf (closed)

Flag-tick p99 **14.7ms → 2.7ms**, `updateWorkStatuses` **5314 → 138us**. Cause was
`Works.values()` rebuilding whole `Work` objects per block position plus one cook job per cookable
item; fixed by memoizing the suppliers and caching `shouldInitializeWithState` by
**(BlockState, air-above)**. A real timer-decay bug was fixed alongside. Full write-up in
`.scratch/perf-2026-07/`. Remaining, unclosed: `possibleWork` still spikes to ~4.6ms (the fix is
hoisting `Containers.get` out of its per-state loop) and `updateStoredData` maxes ~6.6ms.

### Config (closed, but note the consequence)

`ECONOMIC_RECORDS_DEPTH` was registered under the key `"FlagTickInterval"`, so **100 was written
into every world** — the dev-config divergence earlier sessions blamed on a hand edit was this bug.
Both keys are now distinct, and the flag key was renamed **`FlagTickIntervalV2`** so existing
worlds re-default to 10 instead of staying pinned at a value nobody chose. **Every existing world
therefore now ticks work statuses 10x more often than it used to.** That is intended and the perf
work is what makes it affordable, but it is a live behaviour change, so watch for it in playtests.

### Need bubbles (slice 1 of the legibility pass)

ADR-0011's first consumer, `CANT_REACH`. Server marks the need; client picks **one** townie nearest
the crosshair and draws the bubble; walking within 8 blocks and looking squarely spells it out on
the action bar. Design notes live in `src/main/java/ca/bradj/questown/mobs/visitor/COMPLEX.md` and
the **Need bubble** entry in `CONTEXT.md`.

## 2. Decisions made this session (do not re-litigate)

- **The give-up is keyed on failure, not elapsed travel.** ADR-0011 says to shorten
  `WanderGiveUpTicks` 2000 → ~200. Do not: it is the behaviour's *max duration* and `stop()` erases
  `WALK_TARGET`, so shortening it abandons townies mid-journey on healthy long walks. The shipped
  trigger is vanilla's `CANT_REACH_WALK_TARGET_SINCE` (>200 ticks) or 3 failed unstick shoves.
  **ADR-0011's text is stale on this point.**
- **A townie that gave up keeps retrying.** No backoff, no silent switch to other work. Brad's
  call; recorded in CONTEXT's Need bubble entry with "adding a retry backoff (considered and
  declined)". The eventual polish is expressive idle behaviour — `docs/todo/recreation-poses.md`.
- **The icon is the vanilla barrier item**, not authored art, so the slice did not block on a PNG.
  Replaceable any time via `TownieNeed.icon()`.
- **`HelperChickenBubbleLayer` stays in the chicken package for now.** It gained a generic
  `renderIconBubbleFor` and now has a second, non-chicken consumer. Move it to a neutral home when
  the third (dead doors) lands — 11 files reference that package, so the move is not free.
- **Config comments stay player-facing.** Brad: *"Players don't care about 'the old interval'."*
  Rename rationale goes in COMPLEX.md/commit messages, never in the toml.

## 3. The legibility list — where to resume

Agreed sequencing (from ADR-0011 + the perf handoff), with slice 1 done:

1. ~~`WANDER_GIVEUP_TICKS`~~ — **done**, differently than proposed (see §2).
2. **Deed-consumption dupe bug** — `.scratch/flag-playtest-2026-07/deed-not-consumed-on-move.md`
   (**p1**, the only p1 left in that folder). Start here.
3. **Craft-button feedback + disable** — `craft-buttons-no-feedback.md`,
   `craft-buttons-not-disabled-without-item.md`.
4. **Crafting-tab layout** — `crafting-tab-layout-broken.md`. Note: the GUI work has a convention
   (`docs/solutions/conventions/gui-layout-flow-and-linter.md`) and a dev-only `gui-lint` oracle —
   open the screen in a dev client and confirm `[gui-lint] <Screen> — OK`. There is also an
   uncommitted L2 widget-linter PoC mentioned in the `gui-layout-validation` note.
5. **Dead-door failure message** — the second need-bubble consumer. ADR-0011 explicitly **rejects**
   `dropDeadDoors()`/deregistration: diagnosis only, never undo the player's action.
6. **Third bubble consumer: unmet item.** `NoMCEconomics.unmetNeedsRecord` already holds
   `UnmetNeed(tick, villager, request)` per villager UUID — a ready-made source. `WithReason` prose
   is developer-facing and must be rephrased before being shown verbatim.

Adding a consumer is now cheap: add an enum constant to `TownieNeed`, give it an icon, add one
lang key (`message.questown.townie_need.<lowercase_name>`), and call `setNeed` from wherever the
condition is detected.

## 4. Verification state

- **JUnit: 869 tests, 1 failure** — `ChickenArcHintsTest.todo_modRegisteredBubbleStatesAreCoveredByThisSuite`.
  Pre-existing and deliberate (the "make it fail so untestable code is visible" convention). Do not
  treat it as a regression; do not "fix" it by deleting the assertion.
- **Autotest: 57/58**, the single failure being the flake in §5. It was 58/58 earlier in the day on
  the same code.
- Need-bubble visuals were confirmed in a dev client by Brad ("Players will definitely understand
  'what's up'"). The cone/hint thresholds are only verifiable in-game; `keepsBubble` is unit-tested.

## 5. The blocker: `organizer/fetch [warp]` is eating the suite's signal

`.scratch/autotest-suite/organizer-fetch-warp-fails-only-in-full-suite.md` has the full table. The
short version: **identical code both passed and failed** across runs, so it is nondeterministic,
not merely order-dependent as originally filed. Today: green in the morning, then the last three
consecutive runs red — including a control run with the change under test **removed**.

That shape suggests **accumulating** arena residue (`run/world` persists between suite runs), not a
per-run coin flip. **First experiment: wipe `run/world`, run the suite twice, and see whether run 1
is reliably green and run 2 reliably red.** Note the failing variant is `[warp]`, which runs a
realtime prelude and then warps — so the cause could be in either half, and "warp does not run
entity AI" is NOT sufficient to exonerate a movement-related change (I made that mistake).

Cost so far: three extra full-suite runs (~1 hour) spent purely proving unrelated changes innocent.

## 6. Process gotchas (cost real time; don't repeat)

- **Commits are blocked by a pre-commit hook** until the `prep-commit` procedure runs: spawn
  `complex-docs` on the **staged** diff, write/stage the co-located `COMPLEX.md`, then
  `WORKFLOW_MD_CHECKED=$(./scripts/gen-commit-token) git commit ...` **in one Bash call** (an
  earlier `export` does not reach the hook). Token expires on the half hour.
- `complex-docs` earns its keep — it caught a real bug in review (`failedUnsticks` persisting
  across targets, which would make a townie abandon its *next* target instantly). Answer its
  unresolved_questions rather than waving them through.
- **`git stash` does not stash untracked files.** Stashing to build a control tree left the new
  `TownieNeed`/`NeedBubbleFocus` files behind, which then failed to compile against the reverted
  entity. Move new files aside manually, or use `-u`.
- **Do not run `./gradlew compileJava` while a `runServer` autotest is in flight** — it rewrites
  `build/classes` under the running server and invalidates the result.
- `runServer` exiting **1 after `RESULT: N/N passed`** is the self-halting server, not a failure.
  `runClient` exiting 1 is Brad closing the window — not a crash, do not investigate or relaunch.
- Never run two `runServer`s at once (`session.lock`).

## 7. Open questions for Brad (asked, not answered)

- Should `FlagTickIntervalV2`'s shipped default stay **10** now that every existing world will
  adopt it? (Perf makes 10 affordable; 100 is what players were actually running.)
- `BlockClaimsTickLimit` is **dead config** — `Claim.ticked()` decrements `ticksLeft` and nothing
  reads it, so claims never expire. Make it real, or delete it?
  See `.scratch/perf-2026-07/block-claims-tick-limit-is-dead-config.md`.
- Injury heals by **two routes in different units** — `tickDamage` (TICK_FACTOR-scaled) and the
  sleep-wake listener (raw game ticks), so a night's sleep grants a tenth of what the same duration
  of continuous healing does. Tuning or an omitted factor?
