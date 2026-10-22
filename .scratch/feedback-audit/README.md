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
| agent-10-22/findings | partial | fixed items committed; judge items open | BOP icon size; 'No quests found' |
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
| day1/job-board-placement-orientation | open-agent | corrected table needs a south/east placement check | — |
| day1/paged-menus-scroll-to-page | open-agent → fixing | dispatch listened to InputEvent (no-screen only); now ScreenEvent.MouseScrolled.Pre | — |
| day1/messages-print-twice | done | TFBE.roomRecipeCreated broadcast flag:1096; FlagCraftingScreen:148; BroadcastPacer | — |
| day1/skills-tab-hidden-until-level-2 | done | d942eed8 available_at_level | — |
| day1/skills-tab-icons-stacked | open-agent (UI batch) | verify in client | — |
| day1/stats-page-show-didnt-eat-didnt-sleep | open-agent (UI batch) | renderMood commented out | — |
| day1/stats-page-show-level-right-aligned | open-agent (UI batch) | no level drawn | — |
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
| 10-15/villagers-climb-walls-over-doors | open-agent | fix reverted 10-17 | — |
| 10-17/away-summary-says-0-days | done | AwayDuration + test | — |
| 10-17/bop-tab-focus-and-dot-clears-on-withdraw | done | FlagTabs + BopTabAttentionTest | — |
| 10-17/change-job-in-morning-message-stutters | done | en_us.json:35 | — |
| 10-17/diner-carrot-take-put-loop | done | 10-17 r5 fix; eating 5/5 | — |
| 10-17/double-new-quest-announcement | done | day1 tripwire PASS | — |
| 10-17/economics-tooltip-too-wide | done | TownEconomicsScreen.java:114; live 10-22 | — |
| 10-17/first-arrival-text-telegraphs-villagers | done | en_us.json:556 | — |
| 10-17/hunter-collecting-supplies-after-hunt | open-agent | e2e check owed | — |
| 10-17/hunter-stuck-after-root-change-stale-mat-state | done | loot/gatherer_becomes_hunter | — |
| 10-17/job-board-empty-state-needs-blank-line | done | WorkScreen.java:178 | — |
| 10-17/missed-morning-while-away | done | day1 check PASS 10-22 | — |
| 10-17/villager-ui-opens-on-change-job-when-dotted | done | VillagerInitialTabTest | — |
| 10-17/villagers-tab-red-dot-until-first-open | done | TownFlagMenus.java:57 + test | — |
| 10-20/food-producers-list-includes-leavers | → task (narrow count) | dev answer Q5 | answered |
| 10-21/hunters-stuck-collecting-after-sleep | open-agent | unreproduced | — |
| village-self-sufficiency/SUMMARY | done (archive) | July summary | — |
