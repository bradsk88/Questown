---
title: A quest displays its raw i18n key instead of a translated string
status: needs-triage
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:32:39 (`run/client2.log`):

> Also, the quest is showing the raw i18n key, not a proper string.

## Notes

Bug: at least one quest renders its raw `questown:...` translation key in
the UI instead of the localized string. Identify which quest (the batch
in play at that moment: hunter-gatherer / phase-2.5 kitchen era), find
the missing or mistyped key in the lang files, and check whether other
quests share the same gap.
