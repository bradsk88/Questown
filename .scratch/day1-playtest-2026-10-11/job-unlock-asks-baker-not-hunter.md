---
title: First job-unlock quest asks for baker; player expected hunter
status: needs-triage
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:32:24 (`run/client2.log`):

> The first job unlock quest is asking for "baker". Shouldkn't it be
> asking for hunter?

## Notes

The player had just taught a hunter (sword quests) and expected the
demand quest to unlock/extend the hunter. The producer-root choice is
`TownQuests.chooseProducerRoot` (sorted-order first candidate that is
not the stuck root and not already unlocked). Verify against the
run's log what the stuck root + missing item were at that moment and
whether baker is the *correct* answer or the choice needs a
demand-weighted order. Pairs with `demand-quest-constantly-replaced.md`
and `quest-shows-raw-i18n-key.md` (same quest card).
