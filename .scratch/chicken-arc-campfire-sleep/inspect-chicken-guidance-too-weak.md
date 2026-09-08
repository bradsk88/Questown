---
title: _qtdev inspect chicken gives observable-only guidance (not the action), so the agent is routed away from the actionable text and into a code-derived substitute
status: ready-for-agent
created: 2026-09-08
priority: p2
updated: 2026-09-08
closed: 2026-09-08
---

## Resolution (2026-09-08)

`_qtdev inspect chicken` now prints the actionable guidance (need/how) from
`AgentBeatGuidance` - the same source as `_qtdev help`, so the two paths cannot
drift - plus a concrete `where:` (the block the chicken pecks at, or "register
the room first" when no room is registered) and a plain `gap:` note for the
beats that need a live player (villager-UI, flag-UI, seeds). This is the fix
direction the issue asked for: the command the agent is routed to gives the
action, not only the observable behavior. The `AgentBeatGuidanceTest` was
deleted - the guidance is the test (a human reads it), not a wording a unit
test should pin (the test had codified the job-board-for-sign bug as
correct). See hold-run-command-set-gaps.md for the remaining command-set gap.

# The guidance the agent is routed to is weaker than the guidance that exists

Observed while driving a held `village/growth` run. When a beat couldn't be
driven, the agent went looking at code and placed a *substitute* action (a job
board block instead of a sign). Part of the reason is that the guidance the
agent is routed to is deliberately weaker than the guidance that already
exists in the codebase, and that weakness is what pushed it into the
substitute.

## Two text paths, the agent is routed to the weaker one

There are two guidance texts in the code:

- `QtDevInspectCommand.chickenBehavior` — used by `_qtdev inspect chicken`.
  Deliberately narrates only the **observable** behavior a new player sees, not
  the action. For the sign beat it prints:
  > "The chicken looks into the room and pecks. [Put a sign in the room]"
  The skill (`questown-held-run`) routes the agent to this command and tells it
  *not* to use `_qtdev help`.

- `AgentBeatGuidance.forBeat` — used by `_qtdev help` (the cheat). It has a
  richer `need`/`how`/`teaches` triple. For the sign beat:
  > need: "Put a sign in the registered room."
  > how: "Place a sign inside the registered room; it becomes a job board."

So the actionable text ("place a sign") already exists — but it's in the
command the agent is told not to use. The command it *is* told to use
(`_qtdev inspect chicken`) only says "put a sign in the room," the
observable-only line, which is ambiguous enough that the agent didn't know it
was the *action* and went looking at code instead.

## Even the richer text is unactionable at the moment it's needed

`AgentBeatGuidance` for the sign beat says "place a sign **inside the
registered room**." But when the agent hit the sign beat, there was **no
registered room** (only a bare door had been registered, no recipe-matched
room). The text assumed a registered room already existed, which was false, so
even the cheat text was unactionable at that moment.

## The correct model: point at the concrete location

The right guidance is the one the user described: the chicken should lead the
player to a **specific nearby room** and say "put it *here*" — not "put a sign
in *the* registered room," which requires the player to already know which room
is registered and that it isn't. The wall/door beats already do this ("the gap
the chicken pecks at"); the sign/chest/pressure-plate beats don't, and they
should.

When no room is registered yet, the guidance should say "register the room
first" (the chicken's peck is the signal for *where*, and the room isn't
detected until a door is registered), not silently assume one exists.

## Why this caused the job-board substitute

The observable-only text for the sign beat ("put a sign in the room") did not
make the *action* (place a sign) obvious enough, and the beat didn't advance,
so the agent filled the gap by reading code (`convertItemInHand`
SIGNS->JOB_BOARD_BLOCK, the autotest placing a job board, the recipe needing a
sign-input) and used `_qtdev place_board` to drop a job board block. The
richer `AgentBeatGuidance` text at least says "place a sign" — the real
action — which would have stopped the agent from placing a job board. The
observable-only vantage is what let the substitute through.

## Direction

- Reconcile the two guidance paths: the command the agent is *routed to*
  (`_qtdev inspect chicken`, or a dedicated beat-guidance command) should carry
  the **action** (place a sign / open a menu / right-click the campfire), not
  only the observable behavior. The skill's routing to an observable-only
  vantage is the gap.
- The action guidance should name the **concrete location** the chicken points
  at (the room/door/gap), like the wall/door beats already do, and say
  "register the room first" when none is registered yet, instead of assuming a
  registered room exists.
- The observable-only vantage has a place (it's the new-player's view), but it
  should not be the *only* thing the agent is routed to, and it should not be
  ambiguous enough to push the agent into a code-derived substitute.

## Notes

- Verified: `_qtdev inspect chicken` printed
  "The chicken looks into the room and pecks. [Put a sign in the room]"
  (from `QtDevInspectCommand.chickenBehavior`, not `AgentBeatGuidance`).
- Verified: `AgentBeatGuidance` is only used by `QtDevHelpCommand`, which the
  skill tells the agent not to use.
- Related: the same "console can't do the real action" cause is in
  `.scratch/chicken-arc-campfire-sleep/hold-run-command-set-gaps.md`; this is
  the *guidance* side of that gap (the guidance the agent is routed to is too
  weak), not the command-set side.
- Log: /tmp/village-server.log (2026-09-08).
