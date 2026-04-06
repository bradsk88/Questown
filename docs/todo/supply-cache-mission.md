Supply Cache Mission (Tutorial Exploration Beat)
=================================================

Replace the wooden sword item quest in Phase 1 with a map-driven
exploration quest. The gatherer's first expedition brings back a
treasure map pointing to a Questown-generated chest outside town
containing an iron sword.

Solves:
  - Eliminates the misleading "toss near the flag" item quest mechanic
    (flavor text has been fixed, but the underlying quest type still
    requires putting items in containers — not intuitive)
  - Introduces an exploration beat early in the tutorial instead of
    waiting until Phase 7
  - Upgrades the reward from a trivial wooden sword to a meaningful
    iron sword — predictably achievable because the map guarantees it
  - Teaches that the gatherer produces useful things

Implementation requires:
  - Map item generation (treasure map pointing to a specific chest)
  - Chest placement outside town (Questown-generated structure)
  - Gatherer loot table modification (tutorial-only map drop)
  - New quest type or quest detection for "retrieve item from map chest"

The map mechanic would be tutorial-only. Future use could tie into the
planned post-office feature (see docs/features/post-office.md).

Source: docs/onboarding-player-experience.md, Critique #6
