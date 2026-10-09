---
name: questown-item-producers
description: >-
  Answers "which jobs produce / need item X" and "which jobs live in room Y"
  by reading the job data via scripts/job-producers.py. TRIGGER when asked for
  a top-need → producer batch, who produces or *needs* an item (output vs
  input/tool side), which jobs live in a room (the "build the base room" beat),
  or whether a mislabeled unmet-need key has a real producer. SKIP guessing or
  grepping item names in Java — the data files are the source of truth.
---

# questown-item-producers

Answers "which jobs produce / need item X" and "which jobs live in room Y" by
reading the job data — the correct, verified route for any top-need → producer
question (do **not** guess, do not grep item names in Java).

## TRIGGER

When asked or needing to know:
- Who produces item X (coal, meat, planks…) for a top-need → producer batch
- Who *needs* an item as ingredient or tool (input side)
- What jobs live in a room (the "build the base room" beat pairing)
- Whether a mislabeled unmet-need key has a real producer

## COMMAND (run from repo root)

```
python3 scripts/job-producers.py <item-substring>      # coal, sword, wheat
python3 scripts/job-producers.py --room <room-substr>  # mineshaft, block_room
python3 scripts/job-producers.py --list
```

Output is labelled **PRODUCES** vs **consumes/needs** (input side) — and for
tools it says so (`TOOLS = held, usually durable — NOT the product`).

## JOB SCHEMA (the fields that matter — verified 2026-10-03 against
baker_bread, blacksmith_wood_axe, crafter_planks, hunter_unmapped_sword_full, miner_short)

| Concept | Field(s) |
|---|---|
| **OUTPUT side** (what the job produces) | `initial_request` (the job's product ask) + `result` |
| result kinds | `type: item` → `result.item`; `type: biome_loot` → **every entry** of `data/questown/loot_tables/jobs/<prefix>/*.json` (that union is the output space); `type: crafting_table` → `result.fallback` |
| **INPUT side** | `work_states[].ingredients` (consumed, in order), `work_states[].tools` (held, usually durable) |
| **BASE ROOM** (the room the producer job must be built) | `room` + `block` |
| Unlock tree | `parent` chain, `priority` (higher wins), `unlocks_title` |

**The engine's own mapping** (for the "top need → producer" batch):
`TownQuests.producerRootsForItem` inverts `ServerJobsRegistry.getResults`
(→ per work: `work.results`) — i.e. exactly the OUTPUT side above. Anything
the script reports as "no job PRODUCES X" is also null to the batch.

## Verified findings (2026-10-03, this data)

- **Swords have NO job producer** — the only job references are the
  `hunter/sword*` family, where `#questown:swords` is a **tool** (held/durable),
  and `initial_request` is `minecraft:mutton` (biome_loot result). The sword
  channel in data is the **player-crafted ITEM quest** (wooden → stone),
  satisfied by `SupplySwordCommand` in held runs (`_qtdev supply_sword`).
- **Coal's producer** = `miner/coal` (and half/full-day), base room
  `questown:block_room/block.questown.mineshaft`, tool `#questown:pickaxes`.
  This is the "unlock job + build its base room" pair for coal.
- `baker/bread` consumes `#minecraft:coals` (input side) — a coal *consumer*,
  which is how coal demand registers before any miner exists.

## Traps (all hit this session)

- Grep-ing an item name in the **tools** field does not mean the job
  *produces* it (hunter/sword is the canonical false positive).
- "hunter/sword" ≠ a job that makes swords; the sword names the **tool the
  hunter carries**. The hunter's output is meat (biome_loot union).
- `biome_loot` jobs' output space = the **union of biome tables**, not a
  single item — `initial_request` is one representative.
- Item quests (sword) are the player-supplied channel, not job output —
  don't conflate them with `producerRootsForItem` (which only sees job output).
- Don't read a producer relationship from Java by grepping item ids; the
  data files are the source of truth.
- `producerRootsForItem` (runtime) is **discovery-knowledge-gated** (`biomeDesc` -> `allKnownGatherItemsFn`) — right for a job's *execution* path, wrong as a *quest* resolver: the unlock-quest lookup is **omniscient** (adjudicated 2026-10-09) — static job capability (`initialRequest` declared product; the full loot-table union is the parked follow-up) ∩ the not-yet-unlocked pool. Do not gate quest resolution on knowledge state.
