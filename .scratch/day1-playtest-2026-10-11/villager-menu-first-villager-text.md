---
title: Re-phrase the villager menu's "and one more in the morning" for the first villager
status: needs-triage
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:15:18 (`run/client2.log`):

> The flag's villagers menu says "and one more in the morning", but that
> text is only good if we have already got a villager. It should be
> re-phrased for the first villager.

## Notes

Copy fix: the pending-arrival line assumes an existing townie. Needs a
first-villager variant (e.g. "a villager will arrive in the morning").
