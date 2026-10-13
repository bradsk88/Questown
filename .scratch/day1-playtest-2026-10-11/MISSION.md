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

## Session state (2026-10-13, evening) — queue progress

User was out for lunch; agent worked the "next up" queue headless.

**Shipped this session (all unit-green, in-game confirmation pending):**
- `e8178b5a` **E1** BOP-spend job-change quest: root pre-rolled at spend
  (blessed > override > pre-roll > re-roll), quest completes at the spend
  moment, morning applies the roll. `TownQuestsJobChangeSpendTest` (9 tests).
- `9d0acd65` **F2** day-2 quest + board auto-request toggle:
  `FIRST_WORK_REQUEST` quest posted from the basics-batch completion reward
  (moment board + store land); completes on the flag's first `requestWork`;
  1-BOP reward. `boardAutoRequest` flag (default ON, NBT-persisted,
  `[x] Auto-post new requests` row in the board menu,
  `ToggleBoardAutoRequestMessage`, `requestWork` now idempotent via
  `contains` guard). `TownWorkHandleRequestWorkTest` (4 tests).
- `e1c48fb0` **D#2** empty-room early quest: `empty_room.json`
  zero-ingredient room recipe matches any room; onboarding chain re-gated
  (gate → empty-room batch → basics batch); chicken path auto-completes.
  `EmptyRoomRecipeTest` (3 tests).
- `e7e8787d` **messages-print-twice**: the legacy double
  `roomRecipeCreated` call in `registerBlockAsRoom` now broadcasts once
  (`broadcast` flag; meta-room call silent, `broadcastBlockRoomCreated`
  stays the line); craft buttons self-deactivate before sending
  (`btn.active = false` — 1.20.6 has no `setActive`) so a fast double-click
  can't double-send `FlagCraftMessage`.
- `dd70d7e8` **villager-jobs-tooltip-shows-all-jobs**: the status-icon
  same-root job list now filters through the town-wide `isUnlocked(JobID)`
  gate (request-board semantics; same as the day-1 econ-tab fix).

Full unit suite 1182/0/51skipped after each commit; importCheck +
strangler-check clean at each commit.

**Scanned and deliberately deferred (needs a live/held session):**
- **seeking-work-leaver-sees-empty-board-room** (the "gatherer-stall" item):
  static read done this session — the WHY dump's `spots={} rooms={} job_blocks={}`
  means the seeker's *room set* was empty (upstream of the state check); the
  board recipe (`questown:job_board_inputs` tag) + `getRoomsFor` path should
  have populated it (the `gather` job saw the gate room minutes earlier and
  re-claimed fine at 19:24). This is a live-state question — needs
  instrumented room-population logging in a held run, per the ticket's own
  "Notes". Same category as the fuel/coal decision-layer item.
- **fuel/coal decision layer** (cook-status + econ-tab-top-blocker): a
  decision-layer change in `RoomsWithWorkableStatefulBlocks` semantics
  ("blocked on input" vs "no workable block"); high-risk without held-run
  verification. Left as-is from the prior session's ruling.
- **skills-tab-hidden-until-level-2**: needs the user's layer→jobs ruling
  ("confirm the exact layer→jobs mapping with the data" — and which jobs).
- **paged-menus-scroll-to-page**: wiring verified complete this session
  (`NeoMouseScrollDispatch` registered on the Neo bus at FMLClientSetup;
  `QuestsScreen` implements `ScrollableScreen`) — in-game verification only.

**Next session, in order:** held-run verification of the five new features
(E1/F2/D#2/messages-twice/jobs-tooltip) → instrumented seeking-work room
investigation (the `spots={} rooms={}` live-state question) → fuel/coal
decision layer (held-run verified) → the small UI batch
(roomblock-quests-custom-place-tooltip, job-board-quest-card-shows-sign,
job-board-empty-state, flag-crafting-ui-tooltips-and-border,
campfire-sleep-feels-close-enough, skills-tab pending the user's layer
ruling) → fresh playtest: `rm -rf run/world run-client/saves` +
server-first launch.

## Session state (2026-10-13, night) — Q2 resolved

**Skills-tab level gate shipped** (user ruling: per-job `available_at_level`
in the job JSON; no separate layer system): commit `d942eed8`.
- `Work.availableAtLevel` (default 2), `ResourceJobLoader` parse,
  `ServerJobsRegistry.getAvailableAtLevel` (pre-startup guard).
- 25 blacksmith tier files carry the values: wood 7, stone 12, iron 17,
  golden 22, diamond 27. Nothing else does.
- `TownVillagerUIsHandle.showUI` filters the unlockable set by the townie's
  level; `showSkillsTab` rides `VillagerMenusPayload` + every tabbed
  villager menu; `VillagerTabs.forMenu` gates the SKILLS screen fn (plus
  the per-menu `getEnabledTabs` conditional - double gate is deliberate).
- `UnlockJobMessage` re-checks server-side (stale client / direct packet).
- Tests: `JobAvailableAtLevelDataTest` (data oracle, 25 files + exclusivity),
  `WorkAvailableAtLevelTest` (default + chain). Full suite 1192/0/51skipped.

The "skills-tab pending the user's layer ruling" line above is resolved.
Remaining queue: held-run verification of the six new features
(E1/F2/D#2/messages-twice/jobs-tooltip/onboarding-guarantee + this gate) →
instrumented seeking-work room investigation → fuel/coal decision layer →
small UI batch → fresh playtest.

## Session state (2026-10-13, night 2) — Q3 resolved as (b)

**Smelter fuel surfacing shipped** (user ruling: "I'd try b first for sure" —
surviving layer only, `RoomsWithWorkableStatefulBlocks` untouched): commit
`f1b5967c`.
- Unlit `AbstractFurnaceBlock` in the base room of a `furnace_smelt_warp`
  job whose room check failed → `JobFacts.blockedOnLabel =
  #minecraft:coals` → `GateReason.Blocked` → item store: UNMET_ITEM bubble
  names coal + econ tab lists it. Lit smelter not misnamed.
- `SmelterFuelGateReasonTest`; full suite 1195/0/52skipped.
- In-game check owed with the rest: a cook blocked on coal shows the bubble
  + econ entry; refueling clears it.

Remaining queue: held-run verification of the seven new features
(E1/F2/D#2/messages-twice/jobs-tooltip/onboarding-guarantee/skills-gate +
this fuel surfacing) → instrumented seeking-work room investigation →
(option a decision-layer rewrite of the fuel semantics, if (b) proves
insufficient) → small UI batch → fresh playtest.

## Session state (2026-10-13, night 3) — small UI batch: 5 tickets implemented

All five small-UI tickets implemented and green (full suite
**1203 tests / 0 failures / 52 skipped** — up from 1195). All tickets
flipped to `ready-for-human` with implementation notes; visual
confirmation pending the held run:

1. **roomblock-quests-custom-place-tooltip** — `QuestsScreen.renderCardTooltip`
   ROOM case: `TOWN_GATE` → `menu.quests.roomblock.welcome_mat`; `JOB_BOARD`/
   `RoomBlock.isBlockRoom` → `menu.quests.roomblock.town` with
   `RoomRecipes.getName(wantedId)`; campfire branch unchanged.
2. **job-board-quest-card-shows-sign** — `UIQuest.fromLevel` overrides the
   `JOB_BOARD` rMap entry with a single-ingredient recipe of
   `JOB_BOARD_BLOCK` (`buildKeepingLast()` for the deliberate duplicate); card
   now shows the board item, not the blueprint's sign ingredient.
3. **flag-crafting-ui-tooltips-and-border** — border switched to
   `JEI.getRecipeGuiBackground()` (shared flag-UI nine-nine); 1.20.6 gives free
   tooltips only to container slots, so `renderRecipeRow` shows
   `Compat.getItemName(stack)` when the cursor is over the 16x16 input/output
   icon boxes.
4. **campfire-sleep-feels-close-enough** — root cause in 1.20.6 bytecode:
   `ServerPlayer.startSleepInBed` → `bedInRange` → `isReachableBedBlock`
   rejects a bed >3 blocks (x/z) / >2 (y) from the player. The old
   `findSafeSleepPosition` picked the first safe direction in iteration order
   (always NORTH); the bed on the far side was 4-6 blocks away →
   `TOO_FAR_AWAY` from an adjacent position. Now candidates are scored by
   `playerPos.distanceToSqr(head)` and the closest safe side wins (bed on the
   player's side of the fire); the 2-arg overload (test seam
   `placeTempBedForTest`) keeps first-safe-side. `CampfireSleepSideTest` (4
   tests, mocked open-air level).
5. **job-board-empty-state** — `TownFlagBlockEntity.topUnmetNeedName()` (economics
   aggregate, max timesNeeded, resolved through `Ingredients.fromString` /
   `getName` so tag keys read as "Coal"); written as a trailing `writeUtf` slot
   from `TownWorkHandle.openMenuRequested` when the board is empty;
   `WorkScreen`'s empty state renders `job_board.empty.top_need` instead of the
   generic teaser when the name is present. Testable portion only
   (`TownWorkContainerTopNeedTest`: Utf slot + constructor contract) — the full
   ForClientSide round trip pushes Ingredients through Neo's registry ID codec,
   which refuses a registry Neo has not marked synced outside a running
   mod-load; held playtest covers the rest.

Next: prep the commit (COMPLEX.md entries written under
`gui/COMPLEX.md` "Day-1 playtest batch 3") → held-run verification of the
accumulated feature list → instrumented seeking-work room investigation.

## Session state (2026-10-13, night 4) — held-run verification done

Agent drove two sequential `village/growth` agentDrive holds at 200 t/s
(`/tmp/village-server.log`, `/tmp/village-server2.log`), every beat via FIFO
dev commands (`setblock` / `_qtdev place_mat|place_door|registerroom|place_board|
build_room|place_block|board_request|change_job|clickwand|clickheld|inspect`).
Full results table: `.scratch/day1-playtest-2026-10-11/held-run-checklist.md`
"Results" section.

- **PASS live:** E1 BOP job-change (3/3 pre-rolls applied overnight), F2
  first-work-request (server side), D#2 empty-room quest (both runs), 9d
  campfire-sleep side selection (instrumented with a temporary DEV-DIAG on the
  candidate loop — closest-safe-side confirmed, north cells blocked by flag /
  chicken scaffolding; DIAG removed afterwards, tree clean).
- **PARTIAL:** row 8 fuel/coal — UNMET surfacing live with real item names
  (Food → #c:mushrooms); the exact "needs coal" branch needs a cook-ROOT
  townie; three BOP rolls gave soup_cook/soup_cook/farmer, BOP pool exhausted.
  Branch is unit-verified. Village left running — new arrivals roll fresh roots.
- **PENDING visual (client-side, needs the user's eyes):** rows 2-UI, 5, 7,
  9a/9b/9c/9e.
- **NOT COVERED live:** row 6 onboarding-cook-guarantee (no onboarding
  JOB_CHANGE quest was open in either run).
- New notes: `WARN Recipe weight is 0: questown:empty_room` (cosmetic, consider
  ticket); `facing=n` rejected by 1.20.6 `setblock` (use `facing=north`);
  `place_block` arg parser rejects namespaced ids and `Loader.blockFromId`
  missed `questown:soup_pot_small` (vanilla `setblock` worked); 6.6 s
  "Can't keep up" spike around room detection at 200 t/s (note, not regression).

## Session state (2026-10-14) — board-quest live bug found + fixed; user playtesting

User back from lunch; drove a fresh playtest (server-11 → 12 → 13 across
restarts + a machine reboot). Commits:
- `f05e7c85` welcome-mat card tooltip copy (+ importCheck fix).
- `af2c557f` empty-room card: oak-door icon + halved flavor.
- `12a28a0d` **tooltip-precedence fix**: TOWN_GATE/EMPTY_ROOM two-line
  tooltips moved ABOVE the flavor-text check in `renderCardTooltip` (the
  posted quest's flavor shadowed the ROOM branch — dead-branch stumble,
  retro'd).
- `a60e959f` job-board quest flavor: dash + tail cut.
- `74710677` user's generic `job_board.empty.top_need` copy (dropped %s);
  dead arg removed from the WorkScreen call.
- `91aa5ce4` **board-quest stall**: a board placed OUTSIDE any
  door-registered room never completed the JOB_BOARD quest (wanted id
  `questown:job_board` vs the synthetic `block_room/...` registration key;
  event-path top match ties on cost and wins by stream order). Fixed via a
  JOB_BOARD branch in `TownRoomsHandle.getRoomsMatching` (per-tick backfill
  is the completion route now). `JobBoardRecipeDataTest` pins the data
  wiring. town/entity/COMPLEX.md "on the spot" section rewritten (stale).
  **Live-verified 00:36**: user re-joined, backfill completed the board
  quest and posted the F2 day-2 quest.
- `c4934342` silenced the false-positive "Recipe weight is 0" warning for
  the zero-ingredient `empty_room` recipe.

**Retro (2026-10-14):** dead-branch tooltip (covered by gui/COMPLEX.md),
hard-reboot = no save-on-shutdown + no world-freshness check (new
"Hard machine reboot" section in `references/questown-dev-launch.md` +
pointer in the playtest-watch skill). Trivial copy-edit commits: no
COMPLEX.md entry needed (deliberate).

**New tickets (needs-triage):** `ghost-empty-item-entities.md` (6 item
entities with empty NBT load every boot — pre-existing since server-12),
`sleep-module-entity-load-before-town-init.md` (`claimBed` at entity load
throws "Town has not been initialized" — pre-existing; the entity may be
dropped silently).

**World state (server-13, live):** onboarding complete through basics
batch; F2 "post your first work request" quest open; board at 23,69,47
(registered); the old 24,69,50 board was destroyed by the user; chicken arc
forfeited (WAITING_FOR_STICK); 1 townie (gatherer). Pending-visual items
unchanged: F2 checkbox row, jobs tooltip, skills-tab absence, 9a/9b/9c/9e.

**Still open (unchanged):** seeking-work instrumented held run;
playtest-sentinel (ready-for-agent, awaiting green-light); the two
needs-triage user calls (village-is-thriving, load-time reward noise).

## Day close (2026-10-14 late night) — comprehensive retro + autonomous sleep-module fix

- `2b3c8e13` **townie-drop fix**: `TownVillagerSleepModule` deferred bed claims.
  Entity load out-runs the flag's initializer; `claimBed` threw
  "Town has not been initialized" and vanilla dropped the loading entity -
  one townie/visitor lost per restart (the 00:46 gatherer vanished under
  `bdaf231f`, reappeared as `2f33acf3` after the next restart). Test:
  `TownVillagerSleepModuleTest`. Ticket:
  `sleep-module-entity-load-before-town-init.md` (fixed-2026-10-14).
- `0b7bb00f` hard-reboot docs; `d6a43a9f` load-path audit; `c7e821b8`
  empty_room weight-0 doc (town/quests/COMPLEX.md).
- **Comprehensive retro applied:** AGENTS.md API-drift += `Component` has no
  `withStyle(Style)` (use `Compat.translatableStyled`); user-level AGENTS.md
  += one-question-at-a-time preference. Both prior retro items (dead-branch
  tooltip, hard reboot) already codified - not duplicated.
- **Full autotest suite running unattended** (`/tmp/autosuite-1.log`, started
  00:53 on a clean world). As of 01:11: 18/57 scenarios, 2 scenario-FAILs -
  both in the 2026-10-10 known-fail set (`farmer:harvest_wheat [WARP]`,
  `organizer:fetch`); `crafter:leather_boots` (a baseline known-fail) now PASSES.
- **World backups:** `/tmp/playtest-world-2026-10-14` (16M, pre-suite) +
  `~/playtest-world-2026-10-14` (persistent - /tmp dies on reboot).
- **Tomorrow's first move:** restore the user's world before any user-facing
  server launch: `rm -rf run/world && cp -r ~/playtest-world-2026-10-14
  run/world` (the suite server will save its own world over run/world on
  shutdown). Server will then run the townie-drop-fixed build.
- **Still open:** pending-visual items (F2 checkbox row, jobs tooltip,
  skills-tab absence, 9a/9b/9c/9e); two user calls (village-is-thriving 3
  options, load-time reward noise); seeking-work instrumented held run
  (world now backed up - safe); playtest-sentinel green-light;
  ghost-empty-item-entities (6 empty-NBT shells every boot - pre-existing).
