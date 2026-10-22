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
