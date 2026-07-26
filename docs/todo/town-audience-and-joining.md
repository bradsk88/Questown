Town Audience (and, later, Joining a Town)
============================================

Priority stance (2026-07-26): **single-player is the priority; co-op only has to
not break.** This doc is therefore scoped to "stop the silent dead-ends", not to
full multiplayer support. The future idea it leaves room for is letting a player
**join** a town.

The problem
-----------

There is no notion of *which players a town's notifications are for*. The same
subsystem is wrong in both directions at once:

**Too narrow — a co-op partner's tutorial silently dies.**
`VisitorTrigger:31`, `RoomTrigger:31` and `TutorialTrigger:31` each fire on
`level.getNearestPlayer(...)`, i.e. exactly one player. Journal (Patchouli) pages
unlock *from advancements*, and per `onboarding-player-experience.md` the journal
is the tutorial's sequencer. So whoever happens to be standing closer gets the
page and the other player's journal stays locked forever, with no message
explaining why. This is the one that qualifies as "broken" rather than merely
untidy — it is a silent dead-end in the onboarding path.

**Too broad — unrelated towns spam each other.**
`TownMessages.broadcastMessage:41` and `broadcastTutorialToast:53` iterate
`level.getServer().getPlayerList().getPlayers()` — every player on the server,
with no distance, dimension, or town-ownership filter. Three towns on a server
means everyone receives everyone's "A new Store Room was constructed at [x, z]"
and everyone's tutorial toasts.

Minimal fix (does not require the joining feature)
--------------------------------------------------

Introduce one function — the **town audience** for a flag — and route both
directions through it:

1. Implement it as *players within a radius of the originating town flag*.
   `TOWN_TICK_RADIUS` is the obvious candidate bound, and it is already the
   radius the town uses to decide what it cares about.
2. Point `TownMessages.broadcastMessage` / `broadcastTutorialToast` at the
   audience instead of the whole player list.
3. Fire the three advancement triggers for **every** player in the audience
   rather than the nearest one.

In single-player these are all no-ops (one player, standing at their town), which
is why this is safe to do under a single-player-first stance.

Later: joining a town
---------------------

When players can join a town, **town audience** becomes *membership* rather than
proximity, and the call sites above do not change — only the implementation
behind the seam does. That is the reason to name the concept now even though the
current implementation is just a distance check.

Membership would also settle the chicken-arc ambiguity recorded in `CONTEXT.md`
("player" = nearest-to-flag on the render path vs the clicker on the hint path),
which is deliberately not worth chasing before then.
