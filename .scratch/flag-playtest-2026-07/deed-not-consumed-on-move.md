---
title: Relocation deed is not consumed after a successful move
status: needs-triage
created: 2026-07-14
priority: p1
---

## Context

Found during the 2026-07-14 in-game playtest of town relocation (#199, ADR-0009).
Flow: flag menu → "Begin moving" → deed dropped → right-clicked the deed on fresh
ground → the town relocated successfully. But the **deed remained in the player's
hand / inventory** afterward. A leftover deed is a duplication hazard (place it
again → another flag / phantom town).

Memory/design note says `RelocationDeedItem.useOn` "consumes only on OK", so either
the empty-town move returned a non-OK/partial result while still moving the flag, or
consumption regressed. The playtest town had **no villagers and no buildings**, which
may be the trigger (an empty roster/rooms edge case in `TownRelocation.place`).

## Acceptance criteria

- After a successful relocation, the deed stack is consumed (count decremented).
- On a failed/aborted move, the deed is retained (unchanged).
- Covered by an autotest that asserts deed count 1→0 across a successful `place`.

## Notes

- Reproduce: empty town (bare flag, no villagers/rooms), "Begin moving", place deed.
- Check `RelocationDeedItem.useOn` return-value handling + what `place` returns for an
  empty town.
