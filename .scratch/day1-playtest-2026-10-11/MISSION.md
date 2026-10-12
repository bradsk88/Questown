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

## Session state (2026-10-13) — backlog

Committed today: `ec235d0c` (econ-tab fallback retirement + town-wide
unlock filter + Fullness log guard), `75e8b081` (job-board craft at the
flag + starting-timber seed). Verification: 58-scenario autotest suite
green-with-knowns (no new failures vs the 2026-10-10 baseline;
crafter/leather_boots + one harvest_wheat variant improved), unit suite
1160/0/50.

**User instruction (2026-10-13):** stamp C#2 decisions + design, build a
strong backlog, launch a playtest environment; the user plays while the
agent works the queue.

**Work queue (ordered):**
1. **C#2 free-food card + red dot** — design settled in
   `free-food-economics-card.md` (modify the `villager_food` unmet-need
   card, claim affordance + tab red dot, `ClaimFreeFoodMessage` →
   `TownFlagBlockEntity.claimBonusFood`, 20 carrots to registered
   container / inventory fallback, remove dirt-on-flag trigger, re-point
   the chicken-arc hint copy).
2. **D#2 empty room as early quest** (`empty-room-as-early-quest.md`) —
   solo batch between campfire+mat and basics; walls + registered door;
   chicken path auto-completes.
3. **GUI quick wins** (batch or one commit each): `paged-menus-scroll-to-page`,
   `stats-page-show-didnt-eat-didnt-sleep`, `stats-page-show-level-right-aligned`,
   `skills-tab-hidden-until-level-2`, `flag-villager-leaver-return-progress-bar`,
   `job-board-no-requests-bubble`.
4. **"Created Air" welcome-mat message** (`welcome-mat-craft-chat-says-created-air`,
   reopened) — probe off the econ branch; re-add a targeted probe, find, fix.
5. **needs-info cluster** (8 tickets) — blocked on user observations;
   summarize for the user at closeout.
6. **ready-for-human cluster** (5 tickets) — user calls;
   `flag-incomplete-quests-indicator` shares the tab red-dot machinery
   with C#2 (build the mechanism in C#2, extend when the user rules).

**Verify live during the user's playtest (current build):** job-board
craft recipe (flag menu row 3), starting-timber seed (logs + 2 trees
around a new flag), econ tab shows only the food need on day one.
After the playtest: restart the server on the next build so C#2 is live
(world preserved; tell the user when the restart is ready).

## Walkthroughs settled (2026-10-13, all letters done: A B C D E F G)

Decisions stamped in the tickets; ordered implementation queue:
1. **C#2** free-food claim on the `villager_food` unmet-need card + econ
   tab red dot; `ClaimFreeFoodMessage` → `claimBonusFood` (20 carrots to
   registered container, inventory fallback, clear chat message); remove
   dirt-on-flag trigger; re-point chicken-arc hint copy.
2. **E2** tick-delayed broadcast queue on the flag for ALL broadcast
   messages (~5s / 100 ticks spacing; a debounce — spread messages
   unaffected, bunched ones spaced).
3. **E1** job-change roll at BOP-spend (blessed or pre-rolled random
   stored; morning keeps priority: blessed > tutorial
   `overnightJobOverride` > pre-roll; dawn roll only as fallback), quest
   completes at spend. Open: find where `JOB_CHANGE` currently completes;
   persist pending root across save/reload.
4. **G1+G2** one bubble system (`BubbleRenderer.renderIconBubbleAtBlock` /
   `NeedBubbleClientEvents` pattern): "!" over the flag while quests
   incomplete + "!" over the job board while zero work requests;
   foundation for `docs/todo/flag-speech-bubble.md`.
5. **G3** leaver return progress bar on the villagers-menu leaver row;
   payload carries the absolute **return tick**, client derives the
   fraction per frame (ticks down live, no push channel).
6. **F1** vanilla filled map (no custom decoration; chunk-readable zoom)
   at flag placement; check the chicken path's flag award to avoid
   double-grant.
7. **F2** day-2 quest "post your first work request" (any request
   completes it; small reward) + job-board **auto-request toggle**
   (re-scoped from `auto-request-root-initial-request`; checkbox in the
   board UI, default ON, flag-NBT persisted).
8. **D#2** empty room as early quest (solo batch between campfire+mat and
   basics; walls + registered door; chicken path auto-completes).

Then: GUI quick wins (`paged-menus-scroll-to-page`,
`stats-page-show-didnt-eat-didnt-sleep`,
`stats-page-show-level-right-aligned`, `skills-tab-hidden-until-level-2`,
`job-board-no-requests-bubble` is G2 above), "Created Air" re-probe.
needs-info cluster (8) awaits user observations; ready-for-human leftovers
(`flag-map-when-lost` now settled by F1,
`change-job-quest-completes-on-bop-consumption` settled by E1,
`quest-complete-chat-blast-pacing` settled by E2) flip to implemented on
ship.

## Walkthrough letter → ticket map (persisted 2026-10-13)

- **A** — job board (`job-board-should-be-roomblock`, `job-board-organic-introduction`, `job-board-empty-state`, `job-board-no-requests-bubble`)
- **B** — bedroom/sleep (`bedroom-quests-under-posted`, `two-villagers-after-first-sleep`, `flag-nbt-no-town-state-reset`)
- **C** — economics (`C#1` `econ-tab-shows-locked-job-items`, `C#2` `free-food-economics-card` + `free-food-tab-red-dot`, `econ-tab-satisfied-need-lingers`)
- **D** — onboarding (`D#1` auto-request, `D#2` `empty-room-as-early-quest`, `D#3` `onboarding-starting-trees-or-logs`)
- **E** — reward timing (`change-job-quest-completes-on-bop-consumption`, `quest-complete-chat-blast-pacing`)
- **F** — onboarding completeness (`flag-map-when-lost`, `job-board-organic-introduction`)
- **G** — notification/UX polish (`flag-incomplete-quests-indicator`, `job-board-no-requests-bubble`, `flag-villager-leaver-return-progress-bar`)

## 2026-10-12 afternoon live-playtest batch (chat 17:32-17:48, filed 2026-10-13)

All chat observations captured. **Fixed same-day** (commits `6865917a`,
`9c5fb728`): free-food claim card + red dot (C#2 shipped), board placement
completes the JOB_BOARD quest, mat + board quest text points at the crafting
tab, chicken hint re-pointed.

**New tickets filed:**
- `flag-crafting-ui-tooltips-and-border` (ready-for-agent) — input/output/button
  tooltips; crafting-menu border matches the rest of the flag UI.
- `messages-print-twice` (ready-for-agent) — "Created X" chat + recipe_created
  broadcast each fire twice per event; root-cause the double-firing, not just
  the E2 debounce dedupe.
- `campfire-sleep-feels-close-enough` (ready-for-agent, low) — vanilla
  bed-range rejection feels wrong next to the campfire.
- `job-board-quest-card-shows-sign`, `job-board-placement-orientation`
  (ready-for-agent / ready-for-human) — see the tickets.

**Appended to existing tickets:** `empty-room-as-early-quest` (user looked for
it on day 1 and didn't see it), `job-board-empty-state` (REOPENED — empty state
should guide to the top requestable need, not the vague "maybe this will do
something soon"), `onboarding-starting-trees-or-logs` (user accepted the seed;
optional one-line explanation), `welcome-mat-craft-chat-says-created-air`
(user confirmed the Craft button prints it; `[craft-give]` probe armed in
`FlagCraftMessage.giveAndNotify` — needs the rebuilt server + one more click).

## Session state (2026-10-13 lunch) — autonomous work

**User instruction (persisted):** "I'm stopping to have some lunch. Try to
complete as many of my TODOs as you can do autonomously. When I get back,
I'll want to play-test on a brand new server so I can feel out the
improvements." → work the queue solo; leave the tree committed + a
fresh-from-zero launch ready.

- Old session torn down 2026-10-13 (client 150700, server, fifo writer).
  No user chat on server-8 (`grep 'Not Secure' /tmp/dev-server-8.log` = 0
  lines) — nothing left to ticket from that session.
  **G1/G2 tickets NOT flipped**: user will re-verify the "!" bubbles in
  the fresh playtest; flip on their confirmation.
- Evening playtest world preserved at
  `/tmp/playtest-world-2026-10-12-evening` (19M; the gatherer-stall world).
  Stale-world artifact noted: on player join the deserializer spams
  `Tried to load invalid item: 'No key id in MapLike[{}]'` (~6/visitor
  entity) — pre-existing contaminated NBT, expected to be absent on a
  fresh world.
- Worktrees `/tmp/bubbles` + `/tmp/c2` removed; main tree at `9f1fb5c1`
  is the only working tree. Gradle is FREE in the main tree (user away).
- Plan: G3 leaver-return bar → the 8 new playtest tickets (cheap fixes
  first, gatherer-stall investigation with the saved logs) → E1 → F1 →
  F2 → D#2, as far as gets done cleanly. On the user's return: `rm -rf
  run/world run-client/saves` + server-first launch (`dev-server-9.log` /
  `dev-client-8.log`).
- **G3 SHIPPED `2bc27b85`** — leaver-return progress bar (LeaverReturn /
  StatusPacket fields / MultiStatusScreen.renderLeaverReturnBar; gui-lint
  check deferred to the user's first menu open).
- **new-cook HAS NO COOK WORKS SHIPPED (this session)** — root cause:
  the simple-furnace template's expanded jobs were not `unlocked_with_root_job`
  (the 2026-10-15 ruling had mutton onboarding-only), so a manual/staffed cook
  got extract + fuel only. Fix: `starter_recipes` JSON array on the template
  (`["mutton"]`) → loader ORs it into the expanded jobs' flag → every
  staffing path gets the starter recipe via `getDefaultWork`. The dead
  `ChangeJobReward.onboarding` flag deleted. `CookStarterRecipeLoaderTest`
  added (registry-bound half @Disabled with reason). Full suite 1166/0/51skipped,
  importCheck + strangler green. Cook-skill-unlock ticket amended; CONTEXT.md
  starter-job entry updated.
- **F1 flag-map SHIPPED (this session)** — `TownFlagBlock.setPlacedBy`
  (player placements only) → `TownFlagBlockEntity.grantStarterMap`: flag-centered
  zoom-1 filled map, biome-preview pre-filled, no decoration, into inventory
  at flag placement. Chicken sunset chest is now axe-only (the chest map was
  the double-grant the F1 decision warned about). helperchicken COMPLEX.md
  re-pointed. Full suite 1166/0/51skipped.
- **F2 DOCUMENTED, not implemented (this session)** — day-2 "post first
  request" quest + board auto-request toggle is a full feature + GUI surface
  (board-menu checkbox + flag NBT + network), and the day-2 batch timing
  needs a live server to feel out. Key correction found while scoping:
  `ChangeJobReward.getApplier` ALREADY auto-posts the root's
  `initial_request` (unconditionally, job-change path) — so the toggle is
  mostly flag-NBT + checkbox + gating that existing post + idempotency.
  Notes landed in both F2 tickets (`job-board-organic-introduction`,
  `auto-request-root-initial-request`).
- **Session wrap decision:** the remaining queue items (F2, E1, D#2 = full
  features; fuel/coal blind spot = high-risk decision-layer work that needs a
  held run to verify; gatherer-stall = needs the saved-world log re-read) are
  not economical to finish headless this session. Left the tree committed +
  green and the fresh-from-zero launch ready.
- **Next session, in order:** F2 (now scoped, notes above) → E1 BOP-spend
  job-change roll → fuel/coal decision layer (held-run verified) → D#2
  empty-room early quest → gatherer-stall log investigation (saved world at
  `/tmp/playtest-world-2026-10-12-evening`).

## Session close (night of 2026-10-12) — queue state

**Shipped this evening:** `6865917a` C#2 free-food claim card + red dot (dirt
trigger retired); `9c5fb728` board placement completes JOB_BOARD quest +
crafting-tab quest text; `30b0edb4` E2 broadcast pacing (5s queue + dedupe).
All tickets from both playtest rounds filed/updated (`f542afa8`,
`7fee604a`). Full unit suite green on each commit.

**Outstanding, in order:** G3 leaver
return bar (return-tick in payload), E1 (roll at BOP-spend; find where
JOB_CHANGE currently completes), F1 vanilla filled map at flag placement,
F2 day-2 "post first request" quest + board auto-request toggle,
D#2 empty-room early quest, then the evening tickets
(`free-food-economics-card` CTA-lingers follow-up,
`villager-jobs-tooltip-shows-all-jobs`, `messages-print-twice`,
`flag-crafting-ui-tooltips-and-border`, `job-board-quest-card-shows-sign`,
`job-board-empty-state` reopen, `campfire-sleep-feels-close-enough`).

**Shipped next round:** `ebf63af5` "Created Air" fix (capture the item name
before `add()` consumes the stack — `ItemStack.getItem()` falls back to AIR on
an empty stack; user confirmed fixed in-game); `9f1fb5c1` G1+G2 "!" bubbles
(flag with incomplete quests / job board with zero work requests — state on
the flag update tag, `AttentionBubbles` + `NeedBubbleFocus` third holder,
`BubbleRenderer.renderTextureBubbleAtBlock` + `attention.png`; full suite
green in an isolated worktree). **In-game verification DONE 2026-10-12
19:31:** server+client restarted on the bubble build (server `/tmp/dev-server-8.log`
booted 19:29:56, client `/tmp/dev-client-7.log`, Dev joined 19:31:27) — user is
playing on it now.

**Evening playtest captured (2026-10-12 19:04-19:25, 13 chat lines → 8
new tickets in this folder):** `change-job-quest-targets-specific-villager`,
`cook-kitchen-batch-should-include-storage-upgrade`,
`expected-guaranteed-cook-got-arborist`,
`cook-status-says-nowhere-to-work-when-blocked-on-fuel`,
`board-requests-briefly-missing-cooked-foods`, `new-cook-has-no-cook-works`,
`seeking-work-leaver-sees-empty-board-room` (the gatherer stall — full log
evidence chain in the ticket: seeker's workable-block scan `spots={} rooms={}
job_blocks={}` for ~15 min while the board was registered with requests; the
gatherer worked fine on `gather` before 19:07 and again from 19:22:55),
`econ-tab-top-blocker-missing-coal`.

**Open question for the user (Created Air):** RESOLVED — root cause was the
post-consumption `ItemStack.getItem()` → AIR fallback, not registry/holder
corruption; fixed in `ebf63af5`, user-confirmed in chat.

**Environment:** dev server + client killed for the night; world preserved in
`run/world`; FIFO wiring still in `build.gradle` (gated on
`-Dquestown.tp.fifo`) + writer process may be alive. `/tmp/c2` worktree is
stale — main-tree HEAD is the truth; copy changed files in, build there while
the user's session is up.
