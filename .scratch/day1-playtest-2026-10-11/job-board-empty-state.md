---
title: Job board menu needs an empty state explaining requests
status: ready-for-agent
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
