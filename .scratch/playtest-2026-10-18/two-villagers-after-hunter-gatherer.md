---
title: 'Two villagers arrived the morning after raw food + bedroom; should be one'
status: resolved
origin: 2026-10-18 playtest (dev-server-12) — user, in Claude chat
---

"Looks like I got two new villagers afer completing the raw meat quest and sleeping. Should hav
eonly been one." Dev ruling: "Basics should not spawn a villager."

Cause: the basics batch (PHASE_KICKOFF) and the hunter-gatherer batch both queued a morning
arrival; the basics one waited on raw food (inside the hunter-gatherer batch), so both landed
together (22:01:12, a13c1942 + c50d73a4). Fix + follow-ons in `town/entity/COMPLEX.md`
"Onboarding arrivals": basics brings no villager; hunter-gatherer -> villager 2; the cook batch
opens at two villagers and brings villager 3 (fixed arrival, not the defaultQuestCompletionRewards
coin flip). Also fixed: the hunter-gatherer reward used different ids for the arrival and its
quest batch.

Pinned: day1 `one-arrival-per-day[day N]` (red on pre-fix: two on day 3),
`TutorialPhaseGateTest.whenHunterDoneAndTwoVillagers_jobChangeAndFoodAdded`.
