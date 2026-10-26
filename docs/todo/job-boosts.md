# Job boosts (future feature)

Status: idea, not designed. Captured 2026-10-25.

## Idea

Each job tree gets its own set of unlockable **boosts**: passive upgrades that make that
job better at what it already does. Boosts are tiered, so a later tier strengthens an
earlier one.

Example, miner:

- Tier 1: +10% chance to bring back the **requested** item rather than a random roll
- Tier 2: +20%
- (etc.)

## UI placement

Draw the boosts **to the left of the job tree's root node**, on the same screen where
jobs are unlocked. The tree grows to the right from the root, so the space to the left is
free, and putting the boosts there links them to the whole job family rather than to one
node.

## Seams to look at when this is designed (entry points, not verified designs)

- Leaver products come from a `biome_loot` roll (`result.type: biome_loot`, `attempts: N`
  in the job JSON, e.g. `miner_half_day.json`), not from the request. The request picks
  which job runs. See `CONTEXT.md` "Leaver job" and `jobs/gatherer/COMPLEX.md`.
- A "chance to bring back the requested item" boost looks like the existing **loot
  guarantee** prepend (`AbstractWorldInteraction.withLootGuarantees`;
  `jobs/declarative/COMPLEX.md` "Loot guarantees"). A boost would be a probabilistic,
  per-town version of that prepend instead of a one-shot flag.
- "Job root" vs "starter job" naming: the boost set belongs to a **job root**
  (`JobID.rootId()`, the cycling pool), not to a single starter job. See `CONTEXT.md`.

## Open questions

- Cost: what unlocks a boost? Blocks of progress (like job unlocks), quests, or job
  proficiency/usage?
- Scope: is a boost town-wide per job root, or per villager?
- Which non-leaver jobs get boosts, and what do theirs look like (speed, extra output,
  lower input cost)?
- How many tiers, and do tiers stack additively?
