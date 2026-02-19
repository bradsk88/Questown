Onboarding v4 Proposal — Quest-Driven Extended Tutorial
========================================================

v3 incorporated all v2 feedback but remained fundamentally passive: journal
entries tell the player about systems. The codebase exploration reveals that the
quest system is the real teaching tool -- it already drives players to build rooms,
change jobs, and gather items through tutorial phases 0-3. The journal should be
backup reference, not the primary teacher.

Core philosophy shift: Extend the tutorial quest system past Phase 3 with new
quest-driven phases that MAKE the player interact with BOP, the crafter, supply
chains, storage, and warp. Journal entries unlock alongside quests as reference
material.

New feedback from v3 review:
  - Provoke the player to leave town (enables warp discovery)
  - Quest system should drive storage upgrades (not just journal advice)
  - Concrete "spend BOP" quests needed -- specifically: unlock the crafter
  - Villagers can't stack items (confirmed for both realtime and warp paths)


========================================================================


SECTION 1: THE PLAYER JOURNEY TODAY
====================================

Phase 0: Flag + Campfire
------------------------
  Learns:     The flag exists, campfire attracts visitors
  Doesn't:    What the flag menu does, what the wand is for

Phase 1: Kickoff (town gate, job board, store room, iron sword)
---------------------------------------------------------------
  Learns:     Welcome mat, job board, storage, item quests
  Doesn't:    Room recipes, why storage matters at scale

Phase 1.5: Hunter/Gatherer (mutton, job change, bedroom)
---------------------------------------------------------
  Learns:     Job changes exist, villagers can leave town, bedrooms
  Doesn't:    What BOP is, how the skill tree works

Phase 2: Food Production (apples, random food-producing job change)
-------------------------------------------------------------------
  Learns:     Multiple job types, food supply matters
  Doesn't:    Supply chains, how jobs feed each other

Phase 3: Build Room for Second Job
-----------------------------------
  Learns:     Jobs need specific rooms
  Doesn't:    Room upgrades, what blocks matter inside rooms

Post-Tutorial: Procedural Quests Take Over
-------------------------------------------
  Learns:     (nothing new -- no guidance)
  Doesn't:    Warp, BOP, skill tree, crafter dependency chain,
              storage scaling, room upgrades, self-sufficiency


========================================================================


SECTION 2: EXTENDED TUTORIAL -- QUEST-DRIVEN PHASES
=====================================================

This replaces v3's "diary entries" section as the primary teaching track.
Instead of journal entries gated by advancements, we define new tutorial quest
phases that continue after Tutorial.DONE. Each phase teaches exactly one core
concept through player action. Journal entries unlock alongside as reference.

Existing tutorial phases 0-3 are unchanged.


Phase 4: The Crafter & BOP
---------------------------
  Gate:       Tutorial.DONE (phase 3 complete)
  Teaches:    Skill tree navigation, BOP spending
  Reward:     Phase 3 completion auto-deposits 3 BOPs to flag

  Batch A -- Assign the crafter:

    Quest (JOB_CHANGE): "Assign a villager to the Crafter job"
      Target: crafter/stick (root job, parent: null, costs 1 BOP to change)
      Flavor: "Your village needs a crafter to automate production.
      Open a villager's skill tree and spend a Block of Progress to
      change their job."

    Quest (ROOM): "Build a Crafting Room"
      Target: Place a crafting table in a registered room
      Flavor: "The crafter needs a workshop. Place a crafting table
      in a room and register the door with your wand."

  Batch B -- Deepen the skill tree (fires after crafter is working):

    Quest (JOB_CHANGE): "Specialize your crafter in Bowl Crafting"
      Target: crafter/bowl (child job, parent: crafter/stick, costs 1 BOP)
      Flavor: "Unlock Bowl Crafting on the skill tree. It costs another
      Block of Progress -- check the flag's BOP tab if you need more."

  Journal unlocks: Entry 12 (Skill Tree), Entry 13 (The Crafter)
  Advancement: tutorial_complete

  Design notes:
    Changing a villager's root job costs 1 BOP. Unlocking a child job costs
    another 1 BOP. So the player spends 2 of their 3 granted BOPs in this
    phase (1 for crafter/stick root change, 1 for crafter/bowl unlock),
    leaving 1 spare. This teaches BOP spending twice: first for job changes,
    then for skill tree branching.

    Bowl is a good target because soup cooks need bowls, demonstrating
    "crafter supplies other jobs."

    The player must navigate the skill tree UI, find crafter/bowl, spend a
    BOP to unlock it, then assign the villager. This teaches the full BOP
    economy loop: earn > collect > spend > assign.


Phase 5: Supply Chain
---------------------
  Gate:       crafter/bowl unlocked
  Teaches:    Jobs feed each other, farmer > baker wheat > bread chain

  Quests:

    Quest (CONCURRENT_JOBS): "Have a Farmer, Baker, and Crafter working"
      Target: farmer + baker/bread + crafter/stick active simultaneously
      Flavor: "A thriving village needs many hands. Make sure your farmer,
      baker, and crafter are all working at the same time."
      Note: Farmer may already exist from phase 2. Baker and crafter root
      changes each cost 1 BOP. The player's remaining BOP from the phase 3
      grant (1 spare) covers the baker. If the player has no spare BOP,
      they must wait for villagers to earn more -- reinforcing the economy.

    Quest (ROOM): "Build a Kitchen"
      Target: Place a furnace in a registered room
      Flavor: "Your baker needs a kitchen. Place a furnace in a room
      and register the door."

  Journal unlocks: Entry 14 (Supply Chains)
  Advancement: second_job_type

  Design notes:
    CONCURRENT_JOBS prevents fast-swapping a single villager through jobs
    to rush the tutorial. The player must maintain all three jobs at once,
    which forces them to experience the supply chain (farmer grows wheat,
    baker bakes bread, crafter makes tools) as sustained parallel production.

    Root job changes cost 1 BOP each. If the player already has a farmer
    from phase 2, they only need to pay for baker (1 BOP). Baker is the
    key teaching beat -- it creates a visible farmer > baker wheat > bread
    chain.


Phase 6: Growing Pains
-----------------------
  Gate:       baker producing bread
  Teaches:    Storage pressure, room upgrades

  Quests:

    Quest (ROOM): "Upgrade your store room"
      Target: Add a second chest (store_room_medium)
      Flavor: "Storage fills fast. Upgrade before production stops."

  Journal unlocks: Lesson: Storage Tiers
  Advancement: first_room_upgrade

  Design notes:
    This quest fires when production is flowing, so storage pressure is real.
    The quest arrives at the moment the player needs it -- not as abstract
    advice in a journal entry.


Phase 7: The World Beyond
--------------------------
  Gate:       storage upgraded
  Teaches:    The village runs itself, warp exists

  Quests:

    Quest (ITEM): "Find [exotic wood type] for the Flagpole"
      Target: An exotic wood/log type that only generates in a biome
      different from the town's. The nearby-biome scan already exists,
      so use it to pick a wood type the player must travel to find.
      Candidates: dark oak log, jungle log, acacia log, mangrove log --
      whichever is foreign to the town's biome.
      Flavor: "Your village deserves a proper flagpole. Bring back
      [exotic wood] from the lands beyond."

  Journal unlocks: Entry 15 (While You Were Away) -- gated on first_warp
  Advancement: first_warp (requires min 1 MC day away + return)

  Design notes:
    Lore: The village's flag is just a block on the ground. The exotic wood
    quest gives narrative purpose to exploration -- "build a real flagpole."
    This opens a future quest line: a flag-maker crafting job that turns
    exotic wood + dyes into decorative flags/banners for the town. The
    flagpole becomes a visible monument to the player's first expedition.

    The quest provokes the player to leave. The biome-foreign wood means
    they must genuinely explore (not just mine under town). The warp fires
    on return. The warp summary (chat message) makes the payoff visible.
    Journal entry 15 contextualizes what happened.

    Item choice: Use the existing nearby-biome scan to find a biome the
    player hasn't visited, then pick a wood type native to that biome.
    Guarantees 200+ blocks of travel. Wood is a natural fit for "flagpole"
    lore and is recognizable to all Minecraft players.

    New Behaviour: Rick-clikcin on the flagpole with exotic wood in hand causes
    two fence posts to be added atop the flag base.


After Phase 7, procedural quest generation takes over (existing system).


========================================================================


SECTION 3: JOURNAL ENTRIES (REFERENCE TRACK)
=============================================

Same entries as v3, reframed as reference material that unlocks alongside
quest phases. The player has already DONE the thing; the journal explains WHY.


------------------------------------------------------------------------
Diary Entries (sequential, advancement-gated)
------------------------------------------------------------------------

Entry 12: "The Skill Tree"

  Gate:     first_job_quest (already exists)
  Phase:    Unlocks during Phase 4

  Content:  "Each villager has a skill tree. Open their menu and check the
  Skills tab. Locked jobs need a Block of Progress to unlock. Your villagers
  earn these as they work -- check the flag menu's BOP tab."


Entry 13: "The Crafter"

  Gate:     tutorial_complete
  Phase:    Unlocks during Phase 4

  Content:  "You can craft bowls, shears, and fishing rods yourself at a
  crafting table. But your crafter does it for you at scale -- freeing you
  to explore while your village keeps producing. More importantly, the
  crafter makes things you CAN'T: fishing stations are only obtainable from
  a crafter villager. Prioritize unlocking crafter jobs on the skill tree."

  Future: JEI recipe page showing crafter-exclusive items (e.g.
  "fishing station: crafted by questown crafter from fishing rods"). Not
  yet built, but should be referenced here once available.


Entry 14: "Supply Chains"

  Gate:     second_job_type
  Phase:    Unlocks during Phase 5

  Content:  "Your villagers feed each other's work. Try this: set up a
  farmer, then unlock and build a baker. Watch the baker turn your farmer's
  wheat into bread automatically. The store room is the hub -- everyone
  deposits and picks up from there. If someone is idle, check whether their
  supplier is keeping up."

  Future: Achievement milestones for building longer chains (3-job chain,
  4-job chain, etc.).


Entry 15: "While You Were Away"

  Gate:     first_warp (min 1+ MC day away from town)
  Phase:    Unlocks after Phase 7 return

  Content:  "Your village ran itself while you were gone. Travel far away,
  explore, adventure. When you come back, check the store room -- your
  villagers kept producing. Make sure they have supplies and storage before
  you leave."


------------------------------------------------------------------------
Lesson Pages (reference, browse-to-find)
------------------------------------------------------------------------

Lesson: Room Recipes (sortnum 3)

  Gate:     first_job_done

  Content:  What you put inside a room defines its purpose. Furnace =
  kitchen. Crafting table = crafting room. Quests tell you what to build --
  but you can experiment by adding blocks to see what a room becomes.


Lesson: Storage Tiers (sortnum 4)

  Gate:     first_store_room

  Content:  Storage progression table (1 chest > warehouse). "When storage
  fills up, production stops. Upgrade before you need it."

  "Villagers can't stack items. Each item takes one slot. This means storage
  fills up faster than you'd expect -- plan for more chests than you think
  you need."

  Note: The quest system drives storage upgrades (Phase 6). This lesson
  is reference for players who want to understand why.


Lesson: The Town Wand (sortnum 5)

  Gate:     wand_get

  Content:  Click door = register room. Click gate = register farm. Click
  special block = register block room. "If a villager isn't using a room,
  you probably forgot to register it."

  Future: Rare scan for unregistered doors (similar to campfire detection
  logic, but run infrequently to avoid performance hit).


Lesson: The Flag (sortnum 6)

  Gate:     first_open_flag_menu

  Content:  Right-click to open menu (villagers, quests, economics, BOP
  tabs). Also converts items (stick > wand, pressure plate > welcome mat).

  Note: Right-click conversion is error-prone. Future: add a crafting tab
  to the flag UI with proper slots, plus a JEI recipe page.


Lesson: Blocks of Progress (sortnum 7)

  Gate:     first_job_quest

  Content:  "You spent a BOP to unlock Bowl Crafting. As your villagers
  work, they'll earn more. Use them to branch deeper into each skill tree.
  Choose wisely early on -- they're scarce at first."

  Note: Reframed around what the player just did in Phase 4, not abstract
  explanation.


Lesson: The Crafter's Workshop (sortnum 8)

  Gate:     tutorial_complete

  Content:  "The crafter automates crafting at scale. Bowls, shears,
  fishing rods, ladders, paper -- your crafter makes them so you don't
  have to. And some items are crafter-exclusive: fishing stations can
  ONLY be made by a crafter villager. The crafter skill tree branches
  from sticks into specialized tools. Unlock fishing_rod before you
  can unlock fishing_station."

  Future: JEI recipe page for crafter products.


Lesson: Room Upgrades (sortnum 9)

  Gate:     first_room_upgrade

  Content:  "Add blocks to upgrade rooms. Don't demolish -- just add. Quests
  tell you what to build next."


========================================================================


SECTION 4: UI/GAMEPLAY ELEMENTS
================================

Carried from v3, reprioritized for the quest-driven approach.


A. QUEST FLAVOR TEXT [HIGH PRIORITY]

  Critical for the extended tutorial. Each quest needs context text:
    "Your village needs a crafter to automate production."
    "The baker turns wheat into bread -- set up a supply chain."
    "Storage fills fast. Upgrade before production stops."

  Implementation: Add "description" key to quest generation, render in
  QuestsScreen card layout.

  Impact: Every quest becomes a micro-lesson. The biggest teaching surface.


B. WARP SUMMARY [HIGH PRIORITY]

  Chat message on return from warp:
  "While you were away (2 days): 14 bread, 6 ingots, 3 tools produced."

  This is the payoff for Phase 7. Must exist before the warp quest makes
  sense.

  Implementation: Collect item deltas during warp loop, send chat message
  on completion.

  Future: Move to a less spammy integration -- flag UI tab showing last
  warp summary, or a dedicated summary screen on return.


C. BOP NOTIFICATION [MEDIUM]

  Flag visual change + first-time BOP tab preselection:

  1. Float BOP particles around the flag when uncollected BOPs are
     available. Reuse the BOP-above-villager-head visuals.
  2. First time the player opens the flag after a BOP is earned:
     preselect the BOP tab automatically.
  3. Advancement "first_bop_view" fires when the player views the
     BOP tab for the first time.
  4. After first_bop_view fires, stop preselecting the BOP tab.

  Implementation: Flag block entity tracks uncollected BOP count for
  particle rendering. Flag UI checks first_bop_view advancement for
  tab preselection logic.


D. ADVANCEMENT TOAST IMPROVEMENTS [LOW]

  Already work. Improve icon + text. Better icon (journal book), clearer
  text ("Check your journal!" or "New lesson available!").

  Implementation: Update "display" objects in advancement JSONs.


E. TAB INDICATORS [MEDIUM]

  Red "new info" dot on UI tabs. General system, not specific to one screen:
    - Skill tree tab: dot when new jobs are unlockable
    - BOP tab: dot when uncollected BOPs exist
    - Economics tab: dot when villager is idle/starving

  Especially valuable for the BOP tab after Phase 4 deposits BOPs.

  Implementation: Per-tab dirty flag system. Each tab tracks whether the
  player has viewed it since last state change.


F. POST-TUTORIAL CHAPTER MILESTONES [LOW]

  MC achievements for progression markers:
  "Chapter 2: Feeding the Village", "Chapter 3: Tools of the Trade"

  Implementation: MC advancement system with display objects. Trigger
  on quest batch thresholds.


G. FLAG UI CRAFTING TAB [MEDIUM]

  Replace right-click item conversion with a proper crafting tab in the
  flag menu. Slots for input items, output preview, craft button.
  Conversions: stick > wand, pressure plate > welcome mat, etc.

  Implementation: New tab in flag UI. JEI recipe page showing all flag
  conversions.


H. IDLE VILLAGER INDICATOR [MEDIUM]

  Particle effect or overhead icon when a villager is idle due to missing
  supplies. Like vanilla villager "angry" particles but for "needs items."

  Implementation: Check villager state each tick, spawn particles.


Removed from v3: Journal notification dot (over-engineering for Patchouli).


========================================================================


SECTION 5: NEW ADVANCEMENTS
=============================

  ID                  Trigger                                 Used by
  ------------------  --------------------------------------  ----------------
  tutorial_complete   Tutorial.DONE fires                     Phase 4 gate
  second_job_type     Two different root job types active      Phase 5 reward
  first_warp          Return after 1+ MC day away              Phase 7 / E15
  first_room_upgrade  Room recipe changes to higher tier       Lesson 9
  first_bop_view      Player opens BOP tab first time          BOP notification
  first_bop_spend     Player spends first BOP                  Phase 5 tracking

All can fallback to first_job_done if advancement impl is deferred.


========================================================================


SECTION 6: NEW QUEST MECHANICS NEEDED
=======================================

The extended tutorial uses existing quest types (ROOM, ITEM, JOB_CHANGE) plus
one new type (CONCURRENT_JOBS), and needs a few new capabilities:


1. BOP Grant as Quest Reward

  Phase 3 completion grants 3 BOPs. Needs a new reward type (or manual
  deposit via existing BOPDepositorWork pattern).

  Notification problem: The player must know this happened. Options:
    a) Special-case: gatherer delivers a "letter" item explaining the
       reward. One-off for onboarding but feels organic.
    b) First-class "letters" system: villagers can deliver messages to
       the player (see docs/features/post-office for future design).
       Overkill for now, but the BOP grant is a good first use case.
    c) Advancement toast + BOP flag particles (Section 4C) firing
       simultaneously. Lower cost, still noticeable.

  Recommended: Option (c) for v1, option (b) as a future system.


2. Conditional Quest Skipping

  Phase 5 should skip the farmer quest if a farmer already exists.
  TownQuests already does this pattern (checks for existing job types
  before adding quests).


3. Quest Flavor Text

  New "description" field in quest rendering. Needed for all extended
  tutorial quests. See Section 4A.


4. Biome-Foreign Item Quest

  Phase 7 needs an ITEM quest for a block from a different biome than
  the town's. Implementation options:
    a) Maintain a small map of biome > foreign-block
    b) Pick from a universal set of blocks rare in most biomes
       (packed ice, red sand, jungle sapling, etc.)


5. JOB_CHANGE for Child Jobs

  Phase 4 uses JOB_CHANGE for crafter/bowl (a child job). The quest
  system needs to handle the case where the target job is locked -- the
  player must unlock it via the skill tree (spending BOP) before the
  quest can complete.

  Verify that existing JOB_CHANGE quest completion logic already handles
  this (player can't assign a locked job, so the quest stays active until
  they unlock + assign).


6. CONCURRENT_JOBS Quest Type (Anti-Fast-Swap)

  Problem: Players could fast-swap a single villager through multiple jobs
  to complete JOB_CHANGE quests sequentially, bypassing the intended lesson
  of running multiple jobs simultaneously.

  Solution: New quest type CONCURRENT_JOBS -- "Have [job A], [job B], and
  [job C] active at the same time." Completion requires all listed jobs to
  exist simultaneously across different villagers.

  Usage in tutorial:
    Phase 5 could use CONCURRENT_JOBS instead of individual JOB_CHANGE
    quests: "Have a Farmer, Baker, and Crafter working at the same time."
    This forces the player to maintain all three jobs, teaching the supply
    chain through sustained parallel production rather than one-off swaps.

  Implementation: Check active villager job set on each tick/event.
  Complete when the set contains all required job IDs.


========================================================================


SECTION 7: IMPLEMENTATION TIERS
=================================

Tier 1 -- Patchouli only, no Java:

  Write all journal entries and lesson pages (same files as v3 Section 6)

Tier 2 -- Extended tutorial quests (core Java):

  New tutorial phases 4-7 in TownQuests.addTutorialBatches()
  BOP grant reward type
  New advancements (tutorial_complete, second_job_type, first_warp,
    first_bop_spend, first_room_upgrade, first_bop_view)
  Quest flavor text system

Tier 3 -- UI enhancements:

  Warp summary chat message
  BOP notification (flag particles + tab preselection)
  Tab indicators (red dot system)
  Flag UI crafting tab + JEI
  Idle villager indicators

Tier 4 -- Polish:

  Advancement toast improvements
  Post-tutorial chapter milestones
  Unregistered door detection
  Crafter JEI recipe page
  Supply chain length achievements


========================================================================


SECTION 8: FILE ACTIONS
=========================

KEEP (already modified):
  src/.../entries/lesson_farms.json     (sortnum: 1 already added)

EDIT (move category):
  src/.../entries/lesson_needs.json     change category entries > lessons

CREATE (new Patchouli entries -- Tier 1):
  src/.../entries/012-skill-tree.json
  src/.../entries/013-the-crafter.json
  src/.../entries/014-supply-chains.json
  src/.../entries/015-while-you-were-away.json
  src/.../entries/lesson_room_recipes.json
  src/.../entries/lesson_storage.json
  src/.../entries/lesson_wand.json
  src/.../entries/lesson_flag.json
  src/.../entries/lesson_bop.json
  src/.../entries/lesson_crafter.json
  src/.../entries/lesson_upgrades.json

EDIT (Java -- Tier 2):
  TownQuests.java                      phases 4-7

CREATE (Java -- Tier 2):
  New reward type for BOP grant

CREATE (document):
  docs/onboarding-v4.md                (this file)


========================================================================


VERIFICATION CHECKLIST
=======================

  [x] Every phase teaches exactly one core concept through action
  [x] BOP spending taught twice: root job change (1 BOP) + child unlock (1 BOP)
  [x] Root job changes correctly cost 1 BOP (not free)
  [x] Player provoked to leave town via flagpole exotic wood quest
  [x] Exploration quest has narrative purpose (flagpole lore)
  [x] Storage upgrade is quest-driven, not just journal advice
  [x] Journal entries complement quests (reference for what player just did)
  [x] All v3 feedback points still addressed (crafter framing, stacking, etc.)
  [x] CONCURRENT_JOBS prevents fast-swapping through tutorial
  [x] BOP grant notification addressed (toast + particles for v1, letters future)
  [x] Crafter framing distinguishes automation vs exclusives
  [x] Supply chain entry uses Farmer > Baker specifically
  [x] BOP notification uses visual flag change, not chat
  [x] Storage lesson mentions villagers can't stack
  [x] first_warp has minimum time threshold
  [x] Toasts acknowledged as existing, proposal improves them
  [x] Plain text formatting throughout
