---
title: A villager "dines" at the flag with zero food in the town, and stays "working" forever
status: needs-info
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 22:29:09:

> A villager is at the flag, dining. But our town literally had no items
> anywhere, not anything in the villger's invenotyr. And thery're standing
> theri "working" infinitely.

A villager stands at the flag showing the "dining" status even though the town
has no food (nothing in any container, nothing in the villager's inventory), and
it remains stuck in "working" indefinitely — it cannot complete a meal it has no
food for.

## Acceptance criteria

- A villager cannot enter/hold the dining status without actual food to eat.
- A townie with no food and no available work does not spin in "working"
  forever — it idle/needs-state correctly.

## Notes

This is a status-soft-lock: the dining job's state machine claims "dining" with
no food. Load the `questown-status-why` skill to dump the decision that assigns
the dining/working status (the LZCD circuit inputs), not the status name. Check
the dining job's workability gate — it should require food in reach before
entering the "dining" state. The "working infinitely" is the same soft-lock
stuck at a state with no completion path.
