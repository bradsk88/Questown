---
title: 'Curated quest paths: a shared onboarding trunk, then data-defined paths (first: Outfitter)'
status: draft (rulings recorded; phases not started)
origin: r16 #14 design chat, dev rulings 2026-10-24. Supersedes
  2026-10-24-001-feat-post-onboarding-job-change-alternation-plan.md
---

# Curated quest paths

## The rulings (dev, 2026-10-24)

1. **Direction.** "The game CAN support randomized quest batches ... the engine supports it, it
   just needs to be tuned. For preparing for v1 release, I'd rather just focus on curated
   experiences." Switch toward a **tree** of curated experiences: everyone plays onboarding,
   then chooses one or more curated quest paths. Build the first one together; more later.
2. **Fork point: after the miner step.** The shared trunk is hunter/gatherer, empty rooms,
   cook (+ storeroom upgrade), miner. The **top-need batch is cut**
   (`tryPhase2_75_topNeedUnlock`). The crafter steps (`tryPhase4_crafter`) and the
   farmer/baker/crafter + small kitchen step (`tryPhase5_supplyChain`) leave the trunk; their
   content may return inside a path. `TutorialComplete` fires at the fork.
3. **Choosing a path: a starter batch per path.** At the fork the quest list shows each path's
   opening batch; completing one opens the rest of that path. The others stay open, so a player
   can take several.
4. **Random garden: fallback after a path.** When the player has finished the paths they took,
   the procedural garden takes over. #16 (the garden stops after a no-upgrade roll) is therefore
   a real fix, not urgent.
5. **First path: Outfitter.** Origin: the "Outfitters" achievement idea
   (`src/notes_archive/2026-01-01.txt`: "Have your villagers generate: food, armor, and a sword
   one time") and `docs/project-qa.md` / `project-qa-2.md` ("town as an outfitter that supports
   adventuring", "add the outfitter jobs" on the path to v1). Scope: wood + stone + **iron**.
6. **Shape: parallel branches.**
   ```
   Start (at the fork): crafter job change + crafting room + sticks
   ├─ Armor:   armory + leather armor (crafter branch, hunter's leather)
   ├─ Weapons: wooden sword (crafter lvl 7, smithy) -> stone sword (lvl 12)
   └─ Iron:    deep miner + smeltery -> raw iron -> smelter ingot branch -> iron ingots
   Finale (all three branches done): iron sword + iron armor (lvl 17) -> "Outfitters" achievement
   ```
7. **Ingots: a new smelter branch** (e.g. `smelter/iron_ingot`): raw iron + coal in a furnace,
   in the smeltery, reusing the cook's furnace job shape (insert, fuel, extract).
8. **Iron supply: "deeper" mining.** The three miner tiers (short / half-day / full-day trips)
   are recast as depth: going deeper brings rarer loot. **A loot table per depth**: deeper means
   less cobblestone and coal, more iron ore, and the rare ores only at depth. **New job ids**
   (not only display names), so old saves need a rename-on-load step.
9. **Iron armor: at level 17**, matching the blacksmith's iron tier. **Correction (same day):**
   iron armor jobs already exist (see Facts); the work is adding the level-17 gate to the iron
   tier of the armorer expansion, not new jobs. Told to the dev in the chat.
10. **XP: an optional `experience` value per job JSON**, used instead of the formula when
    present. Set it on the cook and baker jobs first (they earn +1 today), then on path jobs
    where pacing matters.
11. **Path format: data files, and onboarding moves onto them too** (one system).
12. **Rewards: an arrival on some batches**, chosen per batch in the path file, with the r16
    #11/#17 bed + storeroom provisioning. BOPs stay level-up-only (`CONTEXT.md`: no BOP quest
    reward).
13. **The cook batch asks for a cook skill (2026-10-25).** A new quest type, `SKILL_UNLOCK`:
    unlock any skill on the cook's skill tree. It teaches skill unlocking. It sits beside Cooked
    Food x8 to make that quest easier (dev, after first putting it in the miner batch). Shipped
    in code (`TownQuests.addQuestsForJobChangeAndFood`, `town/quests/COMPLEX.md` "SKILL_UNLOCK").
    The path data format (11) will need to express this quest type.

## Outfitter first cut (dev rulings 2026-10-26, after r22)

The dev chose to design the Outfitter content before the engine, so the engine fits real content.

14. **First cut = Start + Armor + Weapons (wood -> stone).** The Iron branch (depth miners,
    per-depth loot, the smelter ingot job, the old-id rename) and the iron finale come in a second
    pass.
15. **Lower the crafter level gates: wood 3, stone 5, iron 8, gold 11, diamond 14** (were 7 / 12 /
    17 / 22 / 27, `available_at_level` in the `blacksmith_*.json` files). Crafter XP stays on the
    formula for now; no crafter XP has been logged yet, so measure it in the first run. Ruling 9's
    "iron armor at 17" becomes 8 to match.
16. **Path "make X" quests count production (LOOT)**, the villagers' own output, like the miner's
    Coal x3, not chest contents.
17. **Skill quests name a skill.** A new form of SKILL_UNLOCK that completes only for the named job
    (e.g. `crafter/wooden_sword`); today's form accepts any skill on a root's tree.
18. **Batch layout:**
    ```
    START (posted at the fork)
      - Crafting room
      - Job change: crafter
      - Your crafter makes 8 sticks              (LOOT)
      opens -> ARMOR, WEAPONS I
    ARMOR
      - Armory (blacksmith's table + 4 chests)
      - Your hunters bring 4 leather             (LOOT)
      - Unlock Leather Boots                     (named skill)
      - Your crafter makes leather boots         (LOOT)
    WEAPONS I
      - Smithy (blacksmith's table + light)
      - Unlock Wooden Shovel, then Wooden Sword  (named skills)
      - Your crafter makes a wooden sword        (LOOT)
      opens -> WEAPONS II
    WEAPONS II
      - Unlock Stone Shovel, then Stone Sword    (named skills)
      - Your crafter makes a stone sword         (LOOT; the miner already brings cobblestone)
    ```
19. **Arrivals: Start and Weapons II** each bring a villager (with the bed + storeroom
    provisioning); Armor and Weapons I bring none.
20. **Fork cut confirmed** (ruling 2): `tryPhase2_75_topNeedUnlock`, `tryPhase4_crafter` and
    `tryPhase5_supplyChain` leave the trunk; only the path's Start batch appears after the miner.

21. **Build: path files first, onboarding later.** The path data format, loader and engine come
    with Outfitter as the first file; onboarding stays in Java and hands off at the fork. Porting
    the trunk onto the format (phase 3) waits until the dev has played the path.

### Engine sketch (draft for review, not built)

One file per path, `data/questown/questown_paths/<path>.json`:

```json
{
  "id": "questown:outfitter",
  "batches": [
    {"id": "start", "opens_after": [], "arrival": true, "quests": [
      {"room": "questown:crafting_room"},
      {"job_change": "crafter/stick"},
      {"loot": "minecraft:stick", "count": 8}]},
    {"id": "armor", "opens_after": ["start"], "quests": [
      {"room": "questown:armory"},
      {"loot": "minecraft:leather", "count": 4},
      {"skill": "crafter/leather_boots"},
      {"loot": "minecraft:leather_boots", "count": 1}]},
    {"id": "weapons_1", "opens_after": ["start"], "quests": [
      {"room": "questown:smithy"},
      {"skill": "crafter/wooden_shovel"},
      {"skill": "crafter/wooden_sword"},
      {"loot": "minecraft:wooden_sword", "count": 1}]},
    {"id": "weapons_2", "opens_after": ["weapons_1"], "arrival": true, "quests": [
      {"skill": "crafter/stone_shovel"},
      {"skill": "crafter/stone_sword"},
      {"loot": "minecraft:stone_sword", "count": 1}]}
  ]
}
```

- Loader: a data reload listener like `ResourceJobLoader`; a pure validator test over every path
  file (ids unique, `opens_after` names a batch in the same file, no cycles, at least one starter,
  every job id and room id resolves).
- Engine (in `TownQuests`, after the trunk reports done): post each path's starter batches
  (`opens_after: []`) once; post any batch whose `opens_after` batches are all complete. Each posted
  `MCQuestBatch` carries its path and batch id (NBT), which is the record of what has been posted.
- "Taken" = a path whose starter batch is complete. The garden runs once every taken path has no
  batch left to open; untaken starters stay in the quest list beside it (ruling 3).
22. **A path's JOB_CHANGE gets onboarding's treatment** (dev 2026-10-26): the BOP spend is
    guaranteed to give the named root and applies at the spend, like the onboarding trunk.
23. **A batch opening shows only the new cards**: the standard new-quests notice, no extra chat
    line, toast or lesson.

Facts behind these (read 2026-10-26 @ 299f6404): the sword jobs chain behind their shovels
(`crafter/wooden_shovel` -> `crafter/wooden_sword`, `crafter/stone_shovel` -> `crafter/stone_sword`,
all room `questown:smithy`); the leather armor templates take 1 `minecraft:leather` per piece and
have no level gate (`armorer_*.json`, room `questown:armory`); `smithy` = light source +
`questown:blacksmiths_table`, `armory` = blacksmith's table + 4 `#c:chests`, both blocks have
crafting recipes; crafter jobs declare no `experience`. Observed XP across this session's logs:
hunter:sword, miner:coal, gatherer:gather +200 each, no crafter line at all.

## Facts this rests on (checked 2026-10-24 in the worktree `qt-wt-work`)

- The garden only runs once onboarding reports DONE: `TownQuests.tick` calls
  `addTutorialBatches` first and returns on APPLIED/SKIPPED.
- Blacksmith jobs are crafter-root branches (`blacksmith_*.json` ids are `crafter/<tier>_<tool>`,
  `unlocks_title: blacksmith`), each sword behind its shovel: `stick -> wooden_shovel (7) ->
  wooden_sword (7)`, `wooden_shovel -> stone_shovel (12) -> stone_sword (12)`,
  `stone_shovel -> iron_shovel (17) -> iron_sword (17)`. Level gates are per townie
  (`Work.availableAtLevel`, `jobs/COMPLEX.md` "available_at_level").
- **Corrected 2026-10-24:** the four `armorer_{boots,helmet,leggings,chestplate}.json` files are
  templates. `ResourceJobLoader.makeArmorerJobs` expands each per material in
  `#questown:armor_materials` (leather, iron ingot, gold ingot, diamond) into `crafter/<material>_
  <piece>` jobs (16 in all), room `questown:armory`. Parents (`deriveArmorerParent`): leather boots
  <- `crafter/stick`, each later piece <- the previous piece of its material, and each non-leather
  material's boots <- `crafter/leather_boots`. So armor is a **crafter** branch, like the
  blacksmith tiers, and iron armor jobs already exist. The expansion chains no
  `available_at_level`. Runtime evidence: the suite's `crafter/leather_boots` autotest passes.
  (`scripts/job-producers.py` reads the raw templates and misses all of this.)
- No job makes iron ingots. `smelter/process_ore` turns 1 iron ore into 2 raw iron (needs a
  pickaxe, room `questown:smeltery`).
- Miner loot (`loot_tables/jobs/miner/default.json`, total weight 182): cobblestone 100, coal
  50, iron nugget 10, terracotta 10, iron ore 5, copper ore 2, redstone/gold ore/emerald
  ore/lapis ore/diamond 1 each. All three miner tiers roll this same table (2 / 4 / 6 attempts,
  2000 / 4000 / 6000-tick trips).
- Hunter loot (`hunter_sword/default.json`, total 130): leather 10.
- XP per completion (`DeclarativeJob.getExperienceEarned`): work units x action duration +
  max(time / 10, 1). Every cook and baker job declares `"work": 0.1`, which
  `ResourceJobLoader` reads with `getAsInt()` -> 0, so each completion earns 1. Logs: 267
  `[Experience] +1 from cook`, 213 `+200 from hunter`, 45 `+200 from miner`.
- XP curve (`Config` Leveling): 1000 to reach level 2, x1.2 per level. Cumulative to level 7
  ~9,930; 12 ~32,150; 17 ~87,440. Estimated per-item XP (formula, not seen in a log): stick
  ~250, sword/armor piece ~1,000.
- `scripts/job-producers.py` missed the miner loot table (reported "no job PRODUCES
  iron_ore"). Don't trust its negatives for loot-table items.
- No job-id migration mechanism exists (searched `src/main/java` for legacy/alias/migrate job
  id patterns; none found).

## Phases (proposed order)

1. **XP field** (ruling 10): `experience` in job JSON -> `ResourceJobLoader` -> `Work` ->
   `DeclarativeJob.getExperienceEarned`. Pure unit pin. Set cook/baker values. Small,
   independent, unblocks cook leveling now.
2. **Path data format + engine** (rulings 3, 11, 12): schema (path id, batches, quests, open
   conditions, reward flag), loader, a pure validator test over every path file, the engine in
   `TownQuests` (which batches are open, starter batches at the fork, fallback to the garden
   when the taken paths are done).
3. **Onboarding onto the format** (ruling 11): port the trunk (hunter/gatherer -> miner) to a
   path file, keeping the existing onboarding unit tests green; cut top-need, crafter and the
   farmer/baker/crafter step from the trunk (ruling 2).
4. **New job content**: smelter ingot branch (7), depth miners with new ids, per-depth loot
   tables and the old-id rename on load (8), the level-17 gate on the iron armor expansion (9).
   Also done this session: `crafter/plate` (dev ruling, Dinner Plates had no source).
5. **Outfitter path file** (5, 6) + the "Outfitters" advancement.
6. **#16 fix** (garden after a no-upgrade roll), since the garden is the post-path fallback (4).
7. **Verification**: `village/growth` retargeted at the trunk + Outfitter path; a held playtest.

## Open decisions

1. XP values for the cook/baker jobs (proposal: match the hunter's rate per tick of effort).
2. Depth loot numbers (draft: full-day trip iron ore ~20% of rolls, about 2-3 ingots per trip
   after smelting, so a full iron set takes about 10 trips).
3. New depth miner ids and display names.
4. Quest counts per batch (sticks, leather, swords, ingots).
5. What `village/growth` checks once the garden is a fallback.
6. ~~The `hunger_fill_half` on the half-day miner trip that the full-day trip lacks.~~ **By
   design** (dev 2026-10-24): one food ration lasts a medium trip, not a full one. Recorded in
   `CONTEXT.md` "Leaver job" (Trip rations); keep it when recasting the tiers as depth.
