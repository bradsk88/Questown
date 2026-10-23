---
title: 'Audit: is every piece of dev playtest feedback addressed?'
status: ready-for-agent
created: 2026-10-22
---

The dev asked (2026-10-22): "Do you have the list of feedback I've given in previous playtests?
Have they all been addressed?" No single list existed. ~150 `.scratch/**/*.md` tickets quote the
dev's chat; 71 carry a done-like status, 78 do not (`open-tickets-2026-10-22.txt`, one per line:
`<status> <path>`). Status labels are known to be stale, so each must be checked, not trusted.

## Working ledger (fill in as you go; one row per ticket)

| ticket | verdict (done / open-agent / open-dev / wontfix?) | evidence (commit, code line, live check) | question for dev (if any) |
|---|---|---|---|
| day1/board-requests-briefly-missing-cooked-foods | open (needs-info) | no repro, no commit; ADR-0007 filter intended | — |
| day1/change-job-quest-targets-specific-villager | open-dev (partial done) | ruling D covers onboarding | post-onboarding: name a townie? |
| day1/empty-room-quest-door-icon-and-shorter-tooltip | done | QuestsScreen.java:540 OAK_DOOR; 78-char flavor | — |
| day1/expected-guaranteed-cook-got-arborist | done | onboardingJobChangeGuarantee + 6 unit tests; cook in r3/r5/r7 | — |
| day1/seeking-work-leaver-sees-empty-board-room | open-agent | no fix for empty seeker room scan; needs repro | — |
| 10-15/campfire-quest-copy | done | en_us.json:554,558 (bbbdf88f) | — |
| 10-15/change-job-broadcast-uuid-instead-of-name | done | SimpleVillagerHandle.java:734 chatName (bbbdf88f, f76442cc) | — |
| 10-15/collecting-supplies-stall | open-agent (mitigated) | watchdog shipped; desync root cause open | — |
| 10-15/hunter-food-volume-and-invisible-loot | open-dev | taste: hunt yield, loot visibility | yes |
| 10-15/job-changed-broadcast-twice | done | bbbdf88f runMorning isMorning; BroadcastPacer a73a1401 | — |
| 10-15/room-created-double-announcement | done | bbbdf88f TownRooms.roomAdded | — |
| 10-15/stone-sword-quest-flavor | done | TownQuests.java:1423 | — |
| 10-15/villager-arrival-batch-missing-bedroom | done | cook batch carries BEDROOM->BIG_BEDROOM upgrade (dev rule 10-18) | — |
| agent-10-22/findings | partial | fixed items committed; BOP icon size fixed (ab5acc10, 9fb76332; live r8 2026-10-22: flag BOP tab, villager BOP screen, flag tab icon) | 'No quests found' (queued #3) |
| agent-10-22/known-gather-results-ignore-loot-prefix | open-dev | economy design | 3 options |
| cook-soft-lock/DIAGNOSIS + STATE | done | STATE.md FIXED 2026-10-06 (3 commits) | — |
| day1/auto-request-root-initial-request | done | boardAutoRequest toggle + idempotent requestWork | — |
| day1/campfire-sleep-feels-close-enough | done | CampfireSleepHandler.java:63 player-side bed; CampfireSleepSideTest | — |
| day1/change-job-quest-completes-on-bop-consumption | done | markJobChangeCompleteAtSpend + TownQuestsJobChangeSpendTest | — |
| day1/cook-status-says-nowhere-to-work-when-blocked-on-fuel | open-dev | surfacing done; coin-flip selection (2147a1e7) | cook-scoring-tie A+B? |
| day1/econ-tab-top-blocker-missing-coal | done | f1b5967c smelterFuelBlocker -> Blocked(coals) | — |
| day1/empty-room-as-early-quest | done | TownQuests.addEmptyRoomBatch:162 | — |
| day1/flag-crafting-ui-tooltips-and-border | done | FlagCraftingScreen.java:65,233; seen live 10-22 | — |

## Question queue (ask one at a time, recommendation first; record the answer here)

1. **#26 relocation keeps jobs** — asked 2026-10-22 ~16:50. **Answer: full journal snapshot** (save each
   absorbed townie's whole journal — job + status + carried items — on the flag tile, keyed by UUID; respawn
   restores it). Implemented as `AbsorbedJournals` (fallback: the townie's `NBT_TOWN_STATE` entry, for flags
   that went dormant before the fix).
2. **10-21 #17 double meal** — answer: a diner whose hunger is acceptable goes **back to work, or relaxes
   (downtime) at night**, instead of the raw-food timeout fallback.
3. **#10/#29 "Unlock job: X" top-need quest** — answer: **remove the quest** from the top-need batch.
4. **Cook coin flip** (cook-scoring-tie) — answer: **A+B** (band always-consider jobs under the preferred
   band + score-aware selection, shuffle only exact ties).
5. **Known gather results / loot prefix** — answer: **narrow the player-facing count only** (producer
   count / list counts jobs whose own loot table has the item); loot engine untouched.
6. **#15 crafter twice** — answer: **`crafter/ladder` should have a crafter parent** (fix the data).
7. **UI/copy batch (#4, #6, #13/#16, #17 toast, #28)** — answer: **fix them**. Also: research pre-2025 UI
   design best practices and lock down the principles of what a "Questown UI" feels like; make whatever
   improvements are warranted (dev playtests tonight).
8. **First-work-request quest** — answer: **player's own request only**. New request: let players request
   **"food"** (and likewise **"swords"**, etc.: category requests) on the board; cooks' starting subset
   shouldn't matter to the player.
9. **Hunter yield** — answer: likely a **warp bug**; JSON defines the counts. (Investigate the warp path.)
10. **Post-onboarding job-change quests** — answer: **drop them**; post-onboarding goes back to room quests
    only. Future: opt-in themed quest chains; golden BOPs (education) will make job-change asks reasonable.
11. **BOP icon** — answer: **scale up in GUI** (full slot size), in-world unchanged.

### How the answers were applied (for the dev to veto)

- **#10 "drop job-change quests post-onboarding"** — read as "drop the quests that rely on the random
  BOP roll". Those are the two JOB_UNLOCK sources: the top-need "Unlock job: X" quest and the stuck-townie
  unlock quest. Both are gone (b/c commit "Quests: post-onboarding asks are room quests only"). Phases 2.7
  (miner) and 4/5 (crafter, bowl, concurrent jobs) are **kept**: they are onboarding-flagged, so their
  open JOB_CHANGE fills the BOP-spend guarantee slot and the spend is not random. Say so if those should
  go too.

| day1/flag-incomplete-quests-indicator | done | AttentionBubbles + attention.png | — |
| day1/job-board-no-requests-bubble | done | attentionNoWorkRequests | — |
| day1/flag-map-when-lost | done | grantStarterMap:1059 | — |
| day1/free-food-economics-card | done (+1 open-agent) | claim card shipped; CTA-lingers follow-up open (TownEconomicsScreen.java:214) | — |
| day1/free-food-tab-red-dot | done | FlagTabs.java:158 | — |
| day1/job-board-empty-state | done | WorkScreen.java:175 top_need | — |
| day1/job-board-organic-introduction | done | FIRST_WORK_REQUEST quest + toggle | — |
| day1/job-board-quest-card-shows-sign | done | UIQuest.java:148 | — |
| day1/new-cook-has-no-cook-works | done | starter_recipes mutton | — |
| day1/roomblock-quests-custom-place-tooltip | done | QuestsScreen.java:335 | — |
| day1/job-board-placement-orientation | done | 5ecdc017 live in all four directions (r8) | — |
| day1/paged-menus-scroll-to-page | done | 6204f39c ScreenEvent.MouseScrolled.Pre; live scroll paging (r8) | — |
| day1/messages-print-twice | done | TFBE.roomRecipeCreated broadcast flag:1096; FlagCraftingScreen:148; BroadcastPacer | — |
| day1/skills-tab-hidden-until-level-2 | done | d942eed8 available_at_level | — |
| day1/skills-tab-icons-stacked | done | live r8 2026-10-22: root -> boots -> column of 5 jobs, each at its own position; sibling spacing matches the 1.19.2 golden (frames abut). Judge: a 5-job column runs past the pane top/bottom | — |
| day1/stats-page-show-didnt-eat-didnt-sleep | done | 81adb774 mood effects line; live sleepless effect shown (r8) | — |
| day1/stats-page-show-level-right-aligned | done | 81adb774 "Lvl N"; live (r8) | — |
| day1/two-villagers-after-first-sleep | done | day1 one-arrival-per-day PASS | — |
| day1/villager-card-opens-management-only-screen | done | showInventoryTab 10-14 | — |
| day1/villager-dining-with-zero-food-stuck | done | 9c059c46 DiningStages.next | — |
| day1/villager-asks-for-unlocked-job-tool | open-dev | 'why' names unattainable tool | queue |
| day1/villager-jobs-tooltip-shows-all-jobs | done | TownVillagerUIs.java:220 isUnlocked | — |
| day1/villager-menu-needs-job-board-during-evening-idle | open (needs-info) | no repro | — |
| gannet/retro-2026-10-09-redrive | done (archive) | retro doc | — |
| 10-14/cook-status-blocker-surfacing-10-14 | → task A+B | coin flip | answered (A+B) |
| 10-14/diner-tooltip-i18n-key | done | JobTooltips fallback | — |
| 10-14/empty-room-quest-loses-after-completion | done | QuestBatch.markRecipeAsLost:276 | — |
| 10-14/job-board-empty-copy-name-the-blocker | done | en_us.json:447 | — |
| 10-14/job-board-quest-mat-format-tooltip | done | QuestsScreen.java:292-301 | — |
| 10-14/onboarding-miner-after-cook | done | tryPhase2_7_miner + test | — |
| 10-14/use-1.20-villager-skins | done | TownieSkins + test | — |
| 10-14/villager-display-names | done | displayNamesForAll + test | — |
| 10-15/first-work-request-card-text-overflow | done | fixed 10-15; ellipsis helper -> UI batch | — |
| 10-15/job-board-break-drops-sign | done | RoomBlock.getOrphanDropItem:82 only | — |
| 10-15/loot-quest-food-vs-raw-food | done | UIQuest.lootNameKey | — |
| 10-15/loot-quest-named-raw-food | done | superseded | — |
| 10-15/quest-progress-missing-space | done | UIQuest.java:257,266 | — |
| 10-15/villagers-climb-walls-over-doors | needs-info | vanilla door costs since 10-17; no recurrence in .scratch/ since; a fix needs a positive wall-top penalty + fixture | — |
| 10-17/away-summary-says-0-days | done | AwayDuration + test | — |
| 10-17/bop-tab-focus-and-dot-clears-on-withdraw | done | FlagTabs + BopTabAttentionTest | — |
| 10-17/change-job-in-morning-message-stutters | done | en_us.json:35 | — |
| 10-17/diner-carrot-take-put-loop | done | 10-17 r5 fix; eating 5/5 | — |
| 10-17/double-new-quest-announcement | done | day1 tripwire PASS | — |
| 10-17/economics-tooltip-too-wide | done | TownEconomicsScreen.java:114; live 10-22 | — |
| 10-17/first-arrival-text-telegraphs-villagers | done | en_us.json:556 | — |
| 10-17/hunter-collecting-supplies-after-hunt | done | e2e: sleep_mid_trip extracts at ~9.8k and ~11.5k ticks from job start, 0 max-ticks fallbacks (2026-10-22) | — |
| 10-17/hunter-stuck-after-root-change-stale-mat-state | done | loot/gatherer_becomes_hunter | — |
| 10-17/job-board-empty-state-needs-blank-line | done | WorkScreen.java:178 | — |
| 10-17/missed-morning-while-away | done | day1 check PASS 10-22 | — |
| 10-17/villager-ui-opens-on-change-job-when-dotted | done | VillagerInitialTabTest | — |
| 10-17/villagers-tab-red-dot-until-first-open | done | TownFlagMenus.java:57 + test | — |
| 10-20/food-producers-list-includes-leavers | → task (narrow count) | dev answer Q5 | answered |
| 10-21/hunters-stuck-collecting-after-sleep | open-agent | unreproduced | — |
| village-self-sufficiency/SUMMARY | done (archive) | July summary | — |

### Queued for the dev's return (asked while away = blocks the session, so held here)

1. **Cook coin flip, A+B failed.** Implemented as chosen; the cook autotests went 6/6 -> 2/6 (warp
   cooked nothing; realtime never extracted). cook/fuel and cook/extract are the furnace's real stages,
   not no-ops. Reverted. Proposed instead: score an always-consider job 1.0 when its slot precondition
   holds now (fuel: furnace lacks fuel; extract: furnace has output) and banded otherwise, with the
   warp list keeping all of them. Detail: `.scratch/cook-scoring-tie/always-consider-noop-tie-mechanism.md`.
   Question: try that, or leave the flip?
   **Dev answer (2026-10-23):** neither. `always_consider` is a gate bypass (introduced 9e533826, "ignore
   the supply gate"). Make `TownPossibleWork.gateFacts` read the job's `block.has_item_in_slot_initially`
   (`SlotPrecondition`) against the room's job blocks; remove `always_consider` from cook/baker fuel+extract;
   the warp list must keep slot jobs whose precondition can become true mid-warp. Commit only when the cook autotests are 6/6 again. Stock/fetcher jobs keep it for now.
   **Done 18a43a23** (cook 7/7, baker 1/1, suite 92/94; the two stock jobs still tie a ready food job).
2. **Wake, cancel and deed re-issue have no player UI.** Shutdown starts from the flag's relocate
   button (`BeginTownRelocationMessage` -> `beginTownShutdown`). `wakeInPlace`, `reissueDeed` and
   `cancelTownShutdown` are called only from `FlagShutdownCommand` (`/qt flag <pos> shutdown
   [wake|reissue|cancel]`) and the autotests. Right-clicking a dormant flag only collects a waiting
   deed (`collectDeed`). ADR-0009 calls wake and re-issue the loss-protection paths. So a player who
   loses the deed, or who clicks relocate by mistake, has no way back without commands.
   Recommendation: right-clicking a dormant flag with no deed waiting opens a two-button confirm
   ("Wake the town" / "Get the deed back"), and the flag menu shows "Cancel" during the ritual.
   Question: build that now, or park it with relocation?
   **Dev answer (2026-10-23):** build it now, as recommended (dormant-flag confirm with Wake / Get the deed back; Cancel in the flag menu during the ritual).
   **Done 87dfc36d** (verified live through the real right-click; gui-lint OK).
3. **Miner "No quests found" batch re-asks satisfied quests.** agent-10-22 findings:100. With every
   quest done, `TownFlagTicker.addQuests` posted a miner batch (Mineshaft, Miner job, pickaxes,
   swords, coal); all but coal were already satisfied, so backfill closed them in the same second
   and chat repeated "Quest completed: Mineshaft" three times in 3 minutes. Recommendation: build
   the fallback batch only from quests not already satisfied (no backfill chat for those).
   Question: OK, or should the fallback batch stop entirely now that post-onboarding is rooms only?
   **Dev answer (2026-10-23):** stop the fallback batch entirely (post-onboarding is rooms only).
   **Done cb51a173.**
4. **"I need a fishing rod to work" for a job the townie hasn't unlocked**
   (`day1-playtest-2026-10-11/villager-asks-for-unlocked-job-tool.md`). The status line names the
   gate item of a candidate job the townie can't hold yet. Recommendation: name only items for
   jobs the townie can hold (unlock != workability). Question: OK?
   **Dev answer (2026-10-23):** OK, only name items for jobs the townie can hold.
   **Done ff65cd00.**
5. **Restart mid-shutdown was broken; fixed (2ff42b88).** No question, just a heads-up: shutdown,
   then a restart before dormant, used to leave the flag "needs a moment to start up" forever.

6. **The helper chicken's storeroom chest blocks the room's doorway.** `ChickenRoomShellPlan` drops it
   at `door.relative(inward, 1)` in a 2x2 interior (4x4 shell incl. walls; the javadoc says 3x3), so
   villagers climb onto the chest to reach the room. r9 reproduced the mechanism with a furnace and
   a chest inside doors (cook stalled 13 min on top of the furnace; moved -> six extracts in 50 s).
   Detail: `.scratch/agent-playtest-2026-10-22-r9/findings.md` #13. Recommendation: put the chest in
   the far interior corner (2-line change; the chicken-arc autotests would need a re-run). Question:
   OK, or would you rather the shell grow to the documented 3x3 interior?
   **Dev answer (2026-10-23):** chest in the far interior corner; fix the javadoc; re-run chicken-arc.
   **Done 24d3968b** (chicken F1-F4 pass in the full suite).
7. **Working villagers show the missing-texture checkerboard as their status icon.** `StatusArt` has no
   art for work states (`state:N`), WAITING_FOR_TIMED_STATE or EXTRACTING_PRODUCT, so it returns
   `textures/error.png` (same in 1.19.2). Detail: r9 findings #14. Recommendation: map them to the
   unused `gathering.png` / `returned_success.png` until per-job art exists. Question: OK, or do you
   want new art?
   **Dev answer (2026-10-23):** new art (dedicated icons for work / waiting / extracting). The dev draws them; the agent wires StatusArt to three new texture paths with placeholders until the PNGs land.
   **Done 28619e09** (placeholders: menu/gatherer/working.png, waiting.png, extracting.png; replace the files).
8. **"Begin moving this town" now takes two clicks (3c1b3d1a).** Heads-up, no question: it packed the
   town on one click with no in-game undo. Easy to revert (one method) if you disagree.

### Suite and live checks, 2026-10-22 evening

- Full sharded autotest suite after the day's fixes: 90/93. Fails were `chicken-arc:skip_chicken_command`
  (known red), `nav/roof_gatherer` and `flag/floating_furniture_over_half` (both pass alone, known
  load flakes). Then `flag/shutdown_keeps_job_board` added (49dc0a9a).
- Found live: every town shutdown removed the town's job boards and welcome mats (sub-block
  watchdog heartbeat skipped while parked). Fixed 49dc0a9a.
- Found live: a restart mid-shutdown left the flag "needs a moment to start up" forever. Fixed 2ff42b88.
- Second full suite at 3af9cadd (after the relocate confirm, checkbox, campfire copy): 91/94. Fails:
  `chicken-arc:skip_chicken_command` (known red), `nav/roof_gatherer` and `organizer/fetch [warp]`
  (both pass alone, known flakes). The new `flag/shutdown_keeps_job_board` passed in the suite.
- Third full suite at 964033e4 (after the stats-listener fix): 92/94. Fails: `chicken-arc:skip_chicken_command`
  (known red) and `farmer/harvest_wheat` (warp half: "Town flag failed to initialize (timeout after
  200 ticks)" under shard load). Alone, farmer ran 8/9 with `[proficiency_parity]` failing, the
  scenario's known rotating flake (AGENTS.md known-failures list).
