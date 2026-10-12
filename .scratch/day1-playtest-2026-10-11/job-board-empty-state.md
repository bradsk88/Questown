---
title: Job board menu needs an empty state explaining requests
status: ready-for-human
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:34:02 (`run/client2.log`):

> When a player opens the job board menu and it's empty, there should be
> an empty state briefly explaining what requests are and how to add them.

## Notes

Empty-state copy for the job-board screen: what a work request is + how
the player adds one. Pair with the "!" bubble idea
(`job-board-no-requests-bubble.md`) so the empty state is discoverable.

## Triage notes

Implemented (uncommitted batch 2): WorkScreen draws two wrapped lines when work.isEmpty(). Lang keys job_board.empty.1/2.

## Update (2026-10-11 evening playtest — copy needs changing)

Chat 22:12:07: "Empty state should probably just teast. Right now it says
'click the plus' but since we have no villagers, there is nothing you can add.
It reads like a bug. It should probably just say 'Maybe this will do something
soon...'" So the current empty-state copy (job_board.empty.1/2) tells the
player to click the "+" even when there are no villagers to add — that reads
like a bug. Change the empty state to a teaser, not an action prompt: e.g.
"Maybe this will do something soon..." (keep it to a short one- or two-liner;
no CTA when there's nothing to add).

## Fix (2026-10-11 evening)

Changed `job_board.empty.2` from the CTA ("Press the + button to request an
item - a townie will make or find it.") to the soft teaser "Maybe this will do
something soon...". Kept `job_board.empty.1` ("No work requests yet.") — it's
accurate and not the part that read like a bug. Lang-only change, no code
layout change. Build-verified; trivial to confirm visually.

## 2026-10-12 reopened: empty state should guide to the top requestable need

> Job board says "maybe this will do something soon". But I have a villager,
> and I can request wheat seeds. So it should be guiding me to do that instead.
> (chat 17:48:24)

The shipped two-line empty state (`job_board.empty.1/2`, "Maybe this will do
something soon...") is generic. When the town has actual unmet needs a
villager could fill (wheat seeds here), the board should point at posting the
top request instead of the vague line. Design: empty-state text (or a button)
offers the top unmet-need from the flag's unmet-need list; keep the generic
two lines only when no need exists at all. The `#questown:villager_food`
free-food claim card (free-food-economics-card.md) is the precedent for
riding an unmet-need entry in the UI.

## Implementation (2026-10-13)

- `TownFlagBlockEntity.topUnmetNeedName()`: refreshes the economics aggregate,
  takes the max-`timesNeeded` entry, resolves it to a player-readable display name
  via `Ingredients.fromString` + `Ingredients.getName` (handles both item keys and
  tag keys like `#minecraft:coals`).
- `TownWorkHandle.openMenuRequested`: when `requestedResults` is empty, the top
  need name is written as a trailing `writeUtf` slot in the container NBT (empty
  string otherwise); `ForClientSide` reads it.
- `WorkScreen` empty state: with a top need, line 2 becomes
  `job_board.empty.top_need` ("A townie in town needs %s. Use the + button to
  request it - a townie will make or find it."); without one, the generic
  `job_board.empty.2` teaser remains.
- `TownWorkContainerTopNeedTest`: Utf-slot round trip + constructor null/empty
  contracts. The full ForClientSide round trip needs the work-results write, which
  pushes Ingredients through Neo's item-registry ID codec — refused for a
  registry Neo has not marked synced (vanilla's BuiltInRegistries.ITEM is never
  marked synced outside a running mod-load), so the full path is covered by the
  held playtest instead.
