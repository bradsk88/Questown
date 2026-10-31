---
title: 'Scale track: one always-on path beside the capability paths, the only source of villagers'
status: draft for review (not built). Captured 2026-10-26 from the Outfitter judge-call chat.
origin: Outfitter agent playtest 2026-10-26, finding #14 (the town starves past the fork), dev
  design chat the same day. Builds on 2026-10-24-002-feat-curated-quest-paths-plan.md.
---

# Scale track

## The idea (dev, 2026-10-26)

1. **Two tracks.** The quest board runs two kinds of path side by side.
   - **Capability paths** (the onboarding trunk, the Outfitter, later paths): job changes, skill
     unlocks, work rooms. They never bring a villager.
   - **The scale track**, beside them: the town's capacity to hold people, meaning bedrooms,
     chests and storage, and food from the **hunter and cook only**. It is the **only** source of
     new villagers.
2. **The scale track's food is the hunter and the cook, and stops there** (dev: "I wouldn't want
   it to grow to include farmers, fishers, etc because I'd want those to be their own paths"). Its
   food quests use those two job roots' upgrades and skills: the half-day/full-day hunting trips
   (finding #25), cook skills, raw meat and cooked food. Farmer, fisher, baker and other food
   jobs are capability paths of their own.
3. **Backport to onboarding.** The onboarding trunk gets a scale track beside it too ("Maybe the
   entire onboarding path has a scale path beside it").

Why: finding #14. Past the fork, arrivals outpace food: Outfitter Start and Weapons II each
bring a villager (`"arrival": true`), the miner batch brings villager 4, and nothing asks for
food first. If every arrival is the reward of a scale batch whose quests are that villager's bed,
storage and food, arrivals can't outrun the town's ability to feed them.

## What exists today (checked 2026-10-26 at `a68ec12a`)

- **Arrivals ride on capability batches.** `TownFlagTutorialAdapter.makeReward` gives villager 2
  to `PHASE_HUNTER_GATHERER` and villager 3 to `PHASE_JOB_CHANGE_FOOD` (`arrivalWithQuestBatch()`),
  villager 4 to `PHASE_MINER` and path batches marked `"arrival": true` to `PHASE_PATH_ARRIVAL`
  (`arrivalOnly()`), and `PHASE_NEW_JOB_ROOM` gets `defaultQuestCompletionRewards`, which can
  spawn a visitor.
- **Scale rules already exist, attached to arrivals.**
  - "A batch that brings a villager carries the bed that villager will sleep in" (CONTEXT.md
    "Onboarding arrival").
  - `TownQuests.provisionArrival` / `ArrivalProvisions` adds a bedroom quest per missing bed and a
    storage quest to any batch whose reward brings a villager.

  The scale track is these rules given their own track.
- **The procedural garden** (`defaultQuestCompletionRewards` and the visitor batches) is the
  fallback once the taken paths finish (curated-paths plan ruling 4). It also brings villagers.
- **Paths are data files** (`data/questown/questown_paths/`, `QuestPaths`, `QuestPathParser`;
  a batch's `"arrival"` flag is read at `QuestPathParser.java:33`). Every path's starter posts at
  the fork, and a path is "taken" once its starter is complete. No path is posted unprompted.
- **Onboarding is hand-coded Java** (`TownQuests.addTutorialBatches`, the `tryPhase2_*` steps),
  not a path file yet. The curated-paths plan already intends moving it onto the path format.

## Vocabulary -> code

| Term | Today | Proposed |
|---|---|---|
| Capability path | a curated path (`QuestPath`) | unchanged; `"arrival"` removed from its batches |
| Scale track | none (rules ride on arrival batches: `provisionArrival`) | an always-on path (`"auto": true`) |
| Scale batch | none | a batch whose reward is one arrival and whose quests are that villager's needs |
| Arrival | a reward on onboarding, path and garden batches | a scale-batch reward only |

## Open decisions

1. **The interlock.** A capability batch's job change needs a free villager, and only the scale
   track brings one, so a capability batch can stall with nobody to change. Proposal: the scale
   track paces itself on demand. Its next batch opens when the capability side's open job changes
   outnumber the town's free villagers, which also tells the player "your town needs to grow"
   exactly when it does. Alternatives: a fixed cadence, or one scale batch per capability batch.
2. **It never ends.** Paths are finite and scaling isn't. Is the scale track a hand-written list
   that ends in a template (bed + storage + food for villager N, sizes growing with N), or does it
   take over the arrival half of the procedural garden outright? Either way the garden must stop
   bringing villagers, or rule 1 breaks.
3. **Always on.** The scale track can't be optional, so the path format needs a flag (proposal:
   `"auto": true`, posted at its trigger without being taken) and the ladder must not hold the
   garden on it (today `tryCuratedPaths` holds the garden until every *taken* path is finished).
4. **Where it starts.** It could start at world start (the onboarding backport) or at the fork
   (Outfitter-only first cut). Proposal: build it at the fork first, then backport.
5. **What a scale batch asks for.** Proposal, per arrival: the beds the town will be short of
   (today's `provisionArrival` arithmetic), a storage quest, and a food quest from the hunter or
   cook (raw meat ×N or cooked food ×N, sized to the roster). Later batches teach the hunter and
   cook upgrades: "Unlock half-day hunting" (the B3 idea from #14), a cook skill.
6. **The onboarding backport splits the hand-coded phases.**
   - Capability: the hunter, cook and miner job changes, and the empty-rooms and work-room beats.
   - Scale: the bedrooms (including r22's newcomer bedroom), the kitchen, raw and cooked food,
     the storeroom upgrade, and villagers 2-4.

   The fixed arrival order (gate -> 1, hunter-gatherer -> 2, cook -> 3, miner -> 4) has to
   survive or be re-ruled.
7. **What happens to today's rulings.**
   - Villager 4 from the miner batch (ruled 2026-10-26 morning) moves to a scale batch.
   - The Outfitter's `"arrival": true` on Start and Weapons II comes off.
   - #14's B3 (a hunter-upgrade branch) becomes scale quests, not an Outfitter branch.
8. **Two starters at the fork.** If the scale track is always on, the fork shows the Outfitter's
   Start beside the first scale batch. How does the player tell them apart (for example, a "Grow your town" title on scale batches)?
9. **Tests.** `paths/outfitter`, `paths/at_fork` and `village/growth` assume arrivals on
   capability batches. Each needs its roster checks re-pointed at scale batches.

## Phases (proposed order, after the open decisions are ruled)

1. Path-format flag for an always-on path, plus a ladder rule that doesn't hold the garden on it.
   Unit tests in `TownQuestsCuratedPathsTest`.
2. A scale path file starting at the fork. Remove `"arrival"` from the Outfitter. Stop the garden
   from bringing villagers while the scale track runs. Re-point `paths/outfitter`.
3. Pacing (decision 1) and the template batch (decision 2).
4. Onboarding backport (decision 6): move the trunk's scale beats into the scale track, then
   re-point `village/growth`.

## Related, ruled the same day (independent of this plan)

These hold under either model and are built separately:

- Finding #25: half-day trips roll 5 times; full-day trips roll 12 times into 6 slots, and a
  requested item replaces a random non-requested one.
- #10: a skill unlock posts its first request while the board's auto-request toggle is on.
- #11: armor tiers are gated at leather 3, iron 8, gold 11, diamond 14.
- #20: below a skill's level, its card is blank and the tooltip gives the level.
- #21: leather's weight is raised in the `hunter_sword` tables.
