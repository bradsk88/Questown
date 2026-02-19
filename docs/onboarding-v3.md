Onboarding v3 Proposal
======================

v2 established a solid structure (gap analysis, journal content, UI elements) but
inline feedback revealed several philosophy shifts. This revision folds all of that
feedback in. v2 is preserved for history.

Key shifts from v2:
  - Crafter reframed as automator + gateway to exclusive blocks (not "makes things
    nobody else can" -- players CAN craft bowls, shears, rods at a vanilla table)
  - Supply chains taught via curated Farmer > Baker quest, not abstract examples
  - BOP notification via flag visual change, not chat spam
  - Chapter milestones as MC achievements, not chat messages
  - Journal notification dot dropped (over-engineering for Patchouli)
  - Tab indicators ("new info" red dot) proposed as general UI system
  - Flag UI crafting tab proposed to replace error-prone right-click conversion
  - Storage lesson mentions villagers can't stack items
  - first_warp advancement requires minimum time threshold
  - Toasts already exist -- improve them rather than claiming they're silent


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


SECTION 2: THE PROPOSED PLAYER JOURNEY
=======================================

Extend the curated experience past phase 3 via two tracks:

  TRACK A: Journal content (Patchouli book)
    Diary entries for "aha" moments (supply chains, warp, BOP)
    Lesson pages for reference material (storage, wand, flag, rooms)

  TRACK B: New UI/gameplay elements
    Enhancements that teach through the game itself, not just the book


------------------------------------------------------------------------
Journal: New Diary Entries (sequential, advancement-gated)
------------------------------------------------------------------------

Entry 12: "The Skill Tree"

  Gate:     first_job_quest (already exists -- fires when job change quest
            appears)

  Content:  "Each villager has a skill tree. Open their menu and check the
  Skills tab. Locked jobs need a Block of Progress to unlock. Your villagers
  earn these as they work -- check the flag menu's BOP tab."

  Why here: The player just got their first job change quest. They need to
  understand the skill tree NOW, before they encounter a locked job and get
  confused.


Entry 13: "The Crafter"

  Gate:     NEW advancement "tutorial_complete" (tutorial returns DONE)

  Content:  "You can craft bowls, shears, and fishing rods yourself at a
  crafting table. But your crafter does it for you at scale --
  freeing you to explore while your village keeps producing. More
  importantly, the crafter makes things you CAN'T: fishing stations are
  only obtainable from a crafter villager. Prioritize unlocking crafter
  jobs on the skill tree."

  Future: JEI recipe page showing crafter-exclusive items (e.g.
  "fishing station: crafted by questown crafter from fishing rods"). Not
  yet built, but should be referenced here once available.

  Why here: After the tutorial, the player is about to encounter procedural
  quests. The crafter is the single most important job to understand because
  it gates other jobs' supplies.


Entry 14: "Supply Chains"

  Gate:     NEW advancement "second_job_type" (two different root jobs active)
            Fallback: tutorial_complete

  Content:  "Your villagers feed each other's work. Try this: set up a
  farmer, then unlock and build a baker. Watch the baker turn your farmer's
  wheat into bread automatically. The store room is the hub -- everyone
  deposits and picks up from there. If someone is idle, check whether
  their supplier is keeping up."

  Future: Achievement milestones for building longer chains (3-job chain,
  4-job chain, etc.).

  Why here: With two job types active, the player can observe the
  relationship directly. Farmer > Baker is concrete and immediately
  verifiable.


Entry 15: "While You Were Away"

  Gate:     NEW advancement "first_warp" (warp simulation runs on player
            return)
            Fallback: tutorial_complete

  Content:  "Your village works while you're gone. Travel far away, explore,
  adventure. When you come back, check the store room -- your villagers kept
  producing. Make sure they have supplies and storage before you leave."

  Note: The first_warp advancement should require a minimum time threshold
  (e.g. 1+ MC day away from town). Possibly also a minimum distance from the
  town flag. This prevents the advancement from firing on brief absences that
  produce nothing meaningful.
  <Human Feedback for Claude> We may also want to include something in the 
onboarding which *provokes* the player to leave town. Right now it is *likely*
the sword quest, but they can - in theory - achieve it without leaving town.


------------------------------------------------------------------------
Journal: New Lesson Pages (reference, browse-to-find)
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

  Important: "Villagers can't stack items. Each item takes one slot. This
  means storage fills up faster than you'd expect -- plan for more chests
  than you think you need."

<Human Feedback for Claude> Rther than telling the player to plan for this,
the quest generation system should likely be a good driver of storage room 
adds and upgrades.

Lesson: The Town Wand (sortnum 5)

  Gate:     wand_get

  Content:  Click door = register room. Click gate = register farm. Click
  special block = register block room. "If a villager isn't using a room,
  you probably forgot to register it."

  Future: Rare scan for unregistered doors (similar to campfire detection
  logic, but run infrequently to avoid the performance hit that motivated
  door registration in the first place). Detection of an unregistered door
  would trigger this journal entry automatically, supplementing or replacing
  the wand_get gate.


Lesson: The Flag (sortnum 6)

  Gate:     first_open_flag_menu

  Content:  Right-click to open menu (villagers, quests, economics, BOP
  tabs). Also converts items (stick > wand, pressure plate > welcome mat).

  Note: Right-click conversion is error-prone (misclicks open the menu
  instead, or vice versa). Future improvement: add a crafting tab to the
  flag UI with proper slots, plus a JEI recipe page showing flag
  conversions. Current lesson documents right-click as-is until the
  crafting tab is built.


Lesson: Blocks of Progress (sortnum 7)

  Gate:     first_job_quest

  Content:  "Villagers earn Blocks of Progress as they work. Collect them
  from the BOP tab in the flag menu. Spend them to unlock new jobs on a
  villager's skill tree. Choose wisely early on -- they're scarce at first."

  Early reward idea: Give the player a batch of BOPs at an early milestone
  (e.g. tutorial_complete advancement). A journal entry + quest should drive
  them to spend and discover the value of BOPs firsthand. This teaches the
  BOP economy through action rather than explanation.

<Human Feedback for Claude> Let's come up with some ideas for the "spend BOP" 
quests NOW and incorporae them inot our plan.


Lesson: The Crafter's Workshop (sortnum 8)

  Gate:     tutorial_complete (or first_job_done fallback)

  Content:  "The crafter automates crafting at scale. Bowls, shears,
  fishing rods, ladders, paper -- your crafter makes them so you don't
  have to. And some items are crafter-exclusive: fishing stations can
  ONLY be made by a crafter villager. The crafter skill tree branches
  from sticks into specialized tools. Unlock fishing_rod before you
  can unlock fishing_station."

  Future: JEI recipe page for crafter products. Show what the crafter
  can make and what materials are needed. Especially important for
  crafter-exclusive items.


Lesson: Room Upgrades (sortnum 9)

  Gate:     NEW "first_room_upgrade" or first_job_done fallback

  Content:  "Add blocks to upgrade rooms. Don't demolish -- just add. Quests
  tell you what to build next."

  Note: This lesson should be accompanied by a room upgrade quest that
  teaches by doing. The quest fires alongside or shortly after this entry
  unlocks, giving the player immediate practice.


========================================================================


SECTION 3: PROPOSED NEW UI/GAMEPLAY ELEMENTS
=============================================

A. ADVANCEMENT TOASTS [LOW COST]

  Toasts already fire for Questown advancements. Proposal: improve them.
  Better icon (journal book), clearer text ("Check your journal!" or
  "New lesson available!"). Make the toast feel like a notification worth
  clicking on.

  Implementation: Update "display" objects in advancement JSONs (icon,
  title, description fields).

  Impact: Player learns the journal has new content. Passive discovery.


B. QUEST FLAVOR TEXT [MEDIUM COST]

  Add translatable description to quest UI rendering. Instead of just icons:
  "Build a kitchen so your cook can prepare food."
  "Your blacksmith needs a forge. Place a light source in the smithy."

  Implementation: Add "description" key to quest generation, render in
  QuestsScreen card layout.

  Impact: Every quest becomes a micro-lesson. The biggest teaching surface.


C. WARP SUMMARY [MEDIUM COST]

  When player returns after warp, show production summary in chat:
  "While you were away (2 days): 14 bread, 6 ingots, 3 tools produced."

  Implementation: Collect item deltas during warp loop, send chat message
  on completion.

  Impact: Teaches warp by showing it. Player immediately understands
  offline production.

  Future: Chat message is fine for v1. Long-term, move to a less spammy
  integration -- flag UI tab showing last warp summary, or a dedicated
  summary screen on return.


D. BOP NOTIFICATION [MEDIUM COST]

  No chat message. Instead, a visual change to the town flag:

  1. Float BOP particles around the flag when uncollected BOPs are
     available. Reuse the BOP-above-villager-head visuals that already
     exist.
  2. First time the player opens the flag after a BOP is earned:
     preselect the BOP tab automatically.
  3. NEW advancement "first_bop_view" fires when the player views the
     BOP tab for the first time. Use this to gate journal entries that
     reference BOP spending.
  4. After first_bop_view fires, stop preselecting the BOP tab on
     subsequent opens.

  Implementation: Flag block entity tracks uncollected BOP count for
  particle rendering. Flag UI checks first_bop_view advancement for
  tab preselection logic.

  Impact: Player discovers BOP organically through visual curiosity,
  not chat noise.


E. IDLE VILLAGER INDICATOR [MEDIUM COST]

  Particle effect or overhead icon when a villager is idle due to missing
  supplies. Like vanilla villager "angry" particles but for "needs items."

  Implementation: Check villager state each tick, spawn particles.

  Impact: Connects "villager standing around" to "check economics tab."


F. POST-TUTORIAL CHAPTER MILESTONES [LOW COST]

  After tutorial ends, milestone advancements mark progression:
  "Chapter 2: Feeding the Village", "Chapter 3: Tools of the Trade"

  Implementation: MC advancement system with display objects. Trigger
  on quest batch thresholds (e.g. 5 procedural quests completed, 10
  completed, etc.).

  Impact: Gives progression arc feeling past the tutorial cliff. Uses
  the same achievement toast system players already understand.


G. TAB INDICATORS ("NEW INFO" DOT) [MEDIUM COST]

  Red dot/indicator on UI tabs when new information is available. General
  system, not specific to any one screen. Examples:
    - Skill tree tab: dot when new jobs are unlockable
    - BOP tab: dot when uncollected BOPs exist
    - Economics tab: dot when villager is idle/starving

  Implementation: Per-tab dirty flag system. Each tab tracks whether
  the player has viewed it since last state change.

  Impact: Teaches players to explore tabs without chat messages. Players
  learn to look for the red dot across all Questown UIs.


H. FLAG UI CRAFTING TAB [MEDIUM COST]

  Replace right-click item conversion with a proper crafting tab in the
  flag menu. Slots for input items, output preview, craft button.
  Conversions: stick > wand, pressure plate > welcome mat, etc.

  Implementation: New tab in flag UI. JEI recipe page showing all flag
  conversions.

  Impact: Eliminates misclick frustration. JEI integration means players
  can discover conversions through normal recipe browsing.


========================================================================


SECTION 4: NEW ADVANCEMENTS NEEDED
====================================

  ID                  Trigger                                 Used by
  ------------------  --------------------------------------  ------------------
  tutorial_complete   Tutorial phase returns DONE             Diary 13, lesson 8
  second_job_type     Two different root job types active     Diary 14
  first_warp          Player returns after warp runs          Diary 15
                      (min time threshold: 1+ MC day)
                      (possible min distance from flag)
  first_room_upgrade  Room recipe changes to higher tier      Lesson 9
  first_bop_view      Player opens BOP tab for first time     BOP notification D

All can fallback to first_job_done if advancement impl is deferred.


========================================================================


SECTION 5: IMPLEMENTATION TIERS
=================================

Tier 1 -- Patchouli only, no Java:

  Write diary entries 12-15
  Write lesson pages (room recipes, storage, wand, flag, BOP, crafter,
    upgrades)
  Move lesson_needs.json to lessons category
  Add sortnum to lesson_farms (already done)

Tier 2 -- Small Java, high impact:

  Advancement toast improvements (update display in advancement JSONs)
  Post-tutorial chapter milestones (new advancements with display)
  New advancements (tutorial_complete, second_job_type, first_warp,
    first_room_upgrade, first_bop_view)

Tier 3 -- Medium Java, high impact:

  Quest flavor text
  Warp summary chat message
  BOP notification (flag particle visuals + tab preselection)
  Tab indicators ("new info" red dot system)
  Flag UI crafting tab + JEI recipe page
  Idle villager indicators

Tier 4 -- Future / exploratory:

  Unregistered door detection (rare scan for wand lesson trigger)
  Crafter JEI recipe page
  Warp summary screen (upgrade from chat message)
  Supply chain length achievements


========================================================================


SECTION 6: FILE ACTIONS
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

CREATE (document):
  docs/onboarding-v3.md                (this file)


========================================================================


VERIFICATION CHECKLIST
=======================

  [x] Every feedback point from v2 is addressed in v3 text
  [x] Crafter framing distinguishes automation vs exclusives
  [x] Supply chain entry uses Farmer > Baker specifically
  [x] BOP notification uses visual flag change, not chat
  [x] Journal notification dot removed entirely
  [x] Chapter milestones use MC achievements, not chat
  [x] Tab indicators proposed as general UI system
  [x] Flag crafting tab proposed to replace right-click conversion
  [x] Storage lesson mentions villagers can't stack
  [x] first_warp has minimum time threshold
  [x] Toasts acknowledged as existing, proposal improves them
  [x] Warp summary has future note about less spammy integration
  [x] Early BOP reward idea included
  [x] Room upgrade quest note included
  [x] Wand lesson has unregistered door detection future note
  [x] Plain text formatting (no markdown rendering needed)
