---
title: Add `_qtdev inspect` as a player-facing stand-in for right-clicking
status: needs-triage
created: 2026-09-08
priority: p1
closed: 2026-09-08
---

## Context

A brand-new player has no model of Questown's internals. They see a chicken point
at the flag, right-click the flag, and open its menu — that menu is the only
entry to the game. Until the chicken reveals the flag is interactable, the player
does not know the flag exists, so they cannot see "the chicken's arc" and "the
quest board" as separate things and decide between them.

This session, an agent driving a held village run reached past the chicken for
internal knowledge — the `_qtdev help` `status` line (the arc's internal
condition), the whole-beat map, and the quest board via `_qtdev quests` — and read
that as the next step. That is a cheatsheet behavior: it gives the agent a vantage
a new player does not have. The agent also kept acting on a dead player (empty
inventory, unreachable by `tp`/`give`), which is a setup problem, not an
in-run problem.

The fix that keeps the agent on the new-player side is a single command that
stands in for **looking**, not for **acting**, and that returns the same view a
player gets from right-clicking — nothing behind it.

## The command

`_qtdev inspect <target>` — a read-only stand-in for right-clicking a thing,
returning what a player would see. It is the "look closer" step; the action
(placement, right-click) stays a separate command (`clickwand`, `click`, or the
dev placing a block).

### `inspect <chicken>`

Narrates the current beat **as a player perceives the chicken's behavior**,
dynamically from state — not the `ChickenBeatState` enum, not a `status` line, not
a "condition met / not met" boolean.

Example (current beat is "claim the flag with the wand"):

```
It looks at your town wand, then back to the flag base. [Use town wand on flag base]
```

The text responds to state: the same beat reads differently if the player holds
the wand vs not, because the chicken's behavior differs. If a beat ever fails to
respond to state (prints the same line regardless of whether the condition is met),
that is a bug to fix when encountered, not something `inspect` special-cases.

Ceiling: the chicken's behavior and the interaction it points to. No internal
state name, no condition boolean, no "why the arc is stuck" explanation.

### `inspect <flag>`

Stands in for **opening the flag menu** — the visual menu a player gets by
right-clicking the flag:

```
Villagers: [ {villager 1 details}, {villager 2 details} ]
Quests: Active
...
```

Sub-commands are the menu's **tabs** (the five `FlagTabs` defines, in order:
Villagers, Quests, Economics, Block of Progress, Crafting):

`_qtdev inspect <flag> <tab>` where `<tab>` is one of `villagers`, `quests`,
`economy`, `blocks`, `crafting`.

The tabs carry static content read from the flag, not a live re-derivation. The
client-to-server packet `OpenFlagMenuMessage` is just `{flagPos, type,
showBlockOfProgressTab}`; the tab content the server renders
(`TownFlagMenus.showUI`) is the flag's data. `inspect <flag> <tab>` mirrors what
each tab's payload carries (`FlagMenus.fromNetwork` / `writeAndLink`):

| tab        | static content the tab shows                                        |
|------------|---------------------------------------------------------------------|
| quests     | the flag's `Collection<UIQuest>` + a `QuestScreenSummary` (2 booleans) |
| villagers  | per-villager `StatusPacket` (job id, status text, texture) + job-journal items, plus `morningSpawnPending` |
| economy    | the `FlagInfo` only (no per-tab payload beyond it)                  |
| blocks     | `blocksOfProgress` count + `FlagInfo` (`bopFull` red-dot)           |
| crafting   | the `FlagInfo` only                                                 |

So `inspect <flag>` returns the flag's static shape (which tabs are shown, the
quests list, the villagers, the block-of-progress count), and `inspect <flag>
<tab>` returns that tab's static content. No live re-computation: a new player
opening the menu does not re-derive anything, neither does `inspect`.

| tab          | shown when              | red-dot (notification)       |
|--------------|-------------------------|------------------------------|
| villagers    | `showVillagersTab`      | never                        |
| quests       | `showQuestsTab`         | `hasIncompleteQuests`        |
| economy      | `showEconTab`           | never                        |
| blocks       | `showBlockOfProgressTab`| `bopFull`                    |
| crafting     | always (no guard)       | never                        |

A tab a player could not open (not shown) should not appear in `inspect`; a tab
with a red-dot is the one the game marks as needing attention.

The tabs replace the current direct commands (`_qtdev quests` /
`InspectQuestsCommand`, `_qtdev villagers` / `InspectVillagerCommand`). Those
direct commands are the cheat version — they reach the quest board and the
villager list without going through the menu the chicken opens. Drop them so
`inspect <flag>` is the only path, in order.

## Acceptance criteria

- `_qtdev inspect <chicken>` returns the current beat phrased as the chicken's
  observed behavior (e.g. "It looks at your town wand, then back to the flag base.
  [Use town wand on flag base]"), dynamically from the flag's live state. It
  never prints the `ChickenBeatState` enum, a `status`/condition line, or the
  arc map.
- The same beat returns different text for different player state (holds wand vs
  not), so the chicken's "behavior" tracks state. A beat that ignores state is a
  bug.
- `_qtdev inspect <flag>` returns the flag menu: villagers (with per-villager
  detail), quests active/inactive, and the tabs the menu has.
- `_qtdev inspect <flag> <tab>` returns one tab, where `<tab>` is one of
  `villagers`, `quests`, `economy`, `blocks`, `crafting` — the five tabs `FlagTabs`
  defines, in that order. A tab the flag's `FlagInfo` would not show is not
  offered (matching `FlagTabs.computeTabDecisions`), and a tab with a red-dot
  (quests when `hasIncompleteQuests`, blocks when `bopFull`) is marked as such.
- `_qtdev quests` / `InspectQuestsCommand` and `_qtdev villagers` /
  `InspectVillagerCommand` are removed; the only path to the quest/villager info
  is `inspect <flag>` and its tabs.
- `inspect` is read-only: it changes no state. The action (placement, right-click,
  registering a room) stays a separate command.
- `_qtdev help`'s `how` line points the agent to `inspect` ("right-click the flag
  — or `_qtdev inspect <flag>` to see what it wants") instead of pre-stating the
  next action, so the agent is led to look closer rather than told the answer.

## Notes

- The new-player framing: `inspect` may show what the right-click shows (the
  interaction prompt, and a missing-item message the game itself displays), but
  not the gate behind it (the state enum, the boolean, the quest list, the
  16-block nearest-player scan).
- Deadness is solved in sandbox setup, not in-run: setup sets the world to
  peaceful (difficulty peaceful, world-level, not per-player, so a death cannot
  revert it) before the run starts. A new player in a peaceful world just would
  not get killed. The agent therefore never has to wonder whether the player it
  emulates is alive. (This is a separate change from `inspect`, but the same
  principle: the agent's failures were a dead player, not a missing game fact.)
- `inspect` should not report liveness; liveness is a gate on whether the agent
  can interact at all, handled by peaceful setup, not by the "look closer" command.
