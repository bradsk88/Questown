# Mission — 2026-10-11 day-1 playtest feedback (autonomous)

**User instruction (persisted for compaction survival):** "I'm leaving for a few
hours now. Your mission is to address those feedbacks fully. Keep working
autonomously." + "I'm not going to be here to answer questions, and we're on
nova, so we're billed by the minute. Try not to ask me any questions."

→ **Do not ask the user anything.** Decide, implement, verify, commit. Every
open question gets a defensible default decision recorded in the commit/issue
notes instead of a question. Billed by the minute: be economical — batch
investigation, no speculative re-reads, verify with the cheapest sufficient
oracle (unit test > build > held run).

## Scope

All `needs-triage` todos in `.scratch/day1-playtest-2026-10-11/` (21 items,
two playtest batches, 17:02–17:41, no-chicken day-1 path). "Address fully" = implement the fix (or a documented, committed design
decision for the big design items) + flip the shipped issue to the repo's
terminal convention: `status: wontfix` + `closed:`/`resolved:` dates + a
`## Resolved` note (wontfix here means "no further action", same as the
bug-1 issue in `.scratch/mat-gated-arrival/`). Genuinely deferred items
keep `needs-triage` with a `## Deferred` note.

## Priority order (decided 2026-10-11)

1. **Bugs first** (mechanical, root-causable):
   `welcome-mat-craft-chat-says-created-air`, `menu-crafted-welcome-mat-missing-nbt`
   (likely shared root: the menu-craft path), `quest-shows-raw-i18n-key`,
   `campfire-sleep-hint-unclear` (truncated text), `villager-menu-first-villager-text`
   (copy: `gui.status.villager_arriving_morning` at MultiStatusScreen.java:84),
   `crosshair-renders-over-flag-menu`, `demand-quest-constantly-replaced`
   (retarget-in-place UX; bug-1 fix 8f670d10 shipped but the complaint stands).
2. **Flag-menu UI gating/wiring** (moderate): hide villager tab pre-arrival,
   hide economics tab pre-onboarding (gate: first villager arrives — the
   later 17:16 message raised "onboarding done" as an alternative; DECISION:
   use "first villager has arrived" — simpler state, matches 17:11's plain
   ask), quest-click → crafting tab (hardcoded), status-icon tooltips,
   free-food economics card + red dot (the card can subsume the red dot: a
   visible card IS the indicator; DECISION: implement the card, skip the
   separate red dot, record the call), BOP-deposit reason line, chat-blast
   pacing.
3. **Design items** (bigger): `flag-map-when-lost` (reward a map / flag
   locator on day 1 without chicken), `job-board-no-requests-bubble`,
   `job-board-empty-state`, `flag-incomplete-quests-indicator`,
   `empty-room-as-early-quest`, `onboarding-starting-trees-or-logs`.
   Implement the cheap concrete parts (empty-state copy, indicator
   skeleton) even when the full design needs a plan; record what's deferred
   and why in the issue.

## State at start

- Playtest session killed 17:4x (server 9370, wrapper 9054; client gone).
  Gradle is FREE. `run/server11.log` + `run/client2.log` are the evidence
  logs (durable; keep citing them).
- Baseline `./gradlew build -x test` running in `/tmp/baseline-build.log`.
- Commits so far today: `8f670d10` (bug-1 fix), `7493f906` (bug-2
  misdiagnosis docs), `c7f692ee` (retro: known-dumps.py + AUTOTESTS),
  `9c4c50b6` + `3af8ed06` (the two todo batches).
- 1.20.6 branch: bare `./gradlew` works (gradle.properties pins JDK 21);
  do NOT use the skill's JDK-17 JAVA_HOME override (1.19.2-vintage).
- Prep-commit gate: stage explicit paths only (AGENTS.md/CONTEXT.md are
  stale foreign changes — never stage); token form
  `T=$(./scripts/gen-commit-token) && WORKFLOW_MD_CHECKED="$T" git commit -m …`.
- GUI screens: follow `docs/solutions/conventions/gui-layout-flow-and-linter.md`;
  1.20.6 GUI pose rule: guiGraphics.pose() only, no global modelview mixing;
  blit 32×32 art with the 10-arg Util.blit.
- Known-red unit tests: `todo_…` set — gate on `build -x test` + targeted
  filtered test XMLs, never a full `:test` green.

## Still owed after the feedback work (if time)

Held `village/growth` re-run to roster ≥ 5 (JOB_UNLOCK beat via
`_qtdev change_job`), then verdict-record close-out. Lower priority than
the feedback itself.

## Session state (2026-10-12)

- Econ-tab fix (C#1) **implemented, option b** (retire `FallbackSelector` +
  town-wide-unlock filter on `registerUnmetNeeds`), per user decision.
  Unit suite green (1160 tests, 0 failures, run 2026-10-12 03:10).
- Held `village/self_sufficiency` verification run live: `/tmp/fix-verify.log`,
  flag `0 64 0`, 200 t/s, probe soft, client-free. Pids in `run/hold-*.pid`
  (server=87312, re-captured). Day-2 evidence so far: 0 probe hits, gatherers
  only on base `gather`, econ tab = `#questown:swords` only.
- Retro applied this session: AGENTS.md `git log -S` bullet (local, do-not-
  commit list), two new `questown-playtest-watch` traps (qtdev grep marker,
  Fullness spam filter).
- Fullness no-op log guard: `SimpleVillagerHandle.fillHunger` logs only on
  actual change (was spamming `from 2500 to 2500` every handle tick).
- Playtest world saved at `/tmp/playtest-world-2026-10-13` (the bug's world).
- Autotest suite (57 scenarios) still to run after the held run locks.
- `:test` is now fully green (todo_… de-noised, 2e0e51f1) — the "Known-red
  unit tests" line above is stale.
