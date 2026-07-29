---
title: Relocation deed is not consumed after a successful move
status: wontfix
created: 2026-07-14
resolved: 2026-07-29
priority: p1
---

## RESOLVED 2026-07-29 — the cause was creative mode, and the fix already shipped

**Cause.** Creative, not the empty-town edge case. `ServerPlayerGameMode.useItemOn` wraps the call in

```java
int i = stack.getCount();
InteractionResult r = stack.useOn(ctx);
stack.setCount(i);      // creative only — hands the used item straight back
```

so the deed's `deed.shrink(1)` was undone the moment `useOn` returned. Nothing about the empty
roster or rooms was involved; `place` returned OK and the move worked, exactly as reported.

**Fix.** Already in the tree: commit `42a6cc95` (2026-07-22) replaced the shrink with an explicit
`setItemInHand(hand, ItemStack.EMPTY)`, which the creative restore cannot undo because it mutates a
stack that is no longer in the slot. That commit was about collecting the deed from the flag and
fixed this in passing, which is why this file stayed open.

**What was missing, and is now added.**

- The sibling path — the far-fixtures confirmation screen (`RelocationChoiceMessage`) — still used
  `shrink(1)`. It is not wrapped by the game mode, so it was not broken, but it is now on the same
  `RelocationDeedItem.consumeFrom` idiom rather than a second one that only happens to work.
- A test. The existing relocate scenarios call `TownRelocation.place` directly, so no test touched
  the item layer at all. New autotest **`flag/deed_consumed_on_place`** drives the real vanilla path
  (`ServerPlayerGameMode.useItemOn` → `RelocationDeedItem.useOn`) with a **creative** Forge
  FakePlayer and asserts deed 1 → 0, plus that nothing was dropped on the ground instead.

**Acceptance criteria, verified.** Fix in tree → `1/1 passed`. Control run with `consumeFrom`
reverted to `shrink(1)` → `0/1`, failing on exactly `5 deed-gone-after` with the log line
`useItemOn(...) -> CONSUME, deeds held 1 -> 1` — i.e. the test reproduces the original playtest
symptom on demand.

Not covered: the confirm-screen path's own consumption, which needs a town with fixtures out of
tick radius plus a client round-trip. Its consumption call is now shared with the covered path.

## Triage notes

Marked `wontfix` to mean **done, kept as historical record** — the canonical five statuses in
`docs/agents/triage-labels.md` have no "resolved" state, and `wontfix` is the only one documented as
"kept as a historical record". The `resolved:` date in the frontmatter is what actually distinguishes
this from a genuine "will not be actioned". That vocabulary gap is worth closing.

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
