---
title: A quest displays its raw i18n key instead of a translated string
status: resolved
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:32:39 (`run/client2.log`):

> Also, the quest is showing the raw i18n key, not a proper string.

Same quest card as 17:32:24 (see `job-unlock-asks-baker-not-hunter.md`):

> The first job unlock quest is asking for "baker".

## Notes

The raw string on the JOB_UNLOCK quest card. Candidates: the title
(`questown.menu.quests.job_unlock` = "Unlock job: %s" with the bare
`jobId`, e.g. "extract"), the tooltip (`menu.questown.quests.job_unlock_1`
with the bare `rootId`, e.g. "baker"), or the flavor text (bare root ids
inlined). Verify which the player saw, then name the jobs properly
(i18n job names, not bare ids).

## Triage notes

Fixed in 530c8923: JOB_UNLOCK/JOB_CHANGE cards use jobs.<root> lang keys (UIQuest) and block-room recipes fall through to the block's own lang key (RoomRecipes.getName). RoomRecipesNameTest green.
