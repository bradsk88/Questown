Old Journal -- Onboarding Audit & Proposal
===========================================


PART 1: CURRENT STATE
=====================

What exists today
-----------------

The Old Journal has two categories:

  1. Journal Entries -- 11 sequential diary pages, gated by advancements
  2. Lessons -- 2 entries: Farms, Villager Needs


Journal Entries -- the guided walkthrough
-----------------------------------------

  #   Entry            Gate                  Teaches
  --  ---------------  --------------------  ------------------------------------------------
   1  Welcome          root (approach flag)  The flag exists; light a campfire nearby at night
   2  Getting Started  first_visitor         A visitor arrived; they need a room and a tool
   3  Rooms            wand_get              Town Wand; rooms need 4 walls + door; click door
   4  Job Board        first_room            Place a sign in a room to create a Job Board
   5  Requests         first_job_board       How to request items from the board
   6  Town Gate        first_job_request     Welcome Mat; gatherers need it to leave town
   7  Storage          first_welcome_mat     Chest in a room = storeroom
   8  Supplies         first_store_room      Villagers eat; put food in store room
   9  Monitoring       first_leave_to_gather Right-click flag to open management menu
  10  Growing          first_open_flag_menu  More villagers via quests; quests tab in flag
  11  Skills           first_job_done        Villagers gain experience; new quests unlock


Lessons -- reference pages
--------------------------

  Entry           Gate                Teaches
  --------------  ------------------  -----------------------------------------
  Farms           first_farm_quest    Fence + gate = farm; use wand to register
  Villager Needs  first_unmet_needs   Economics tab; stock the store room


What's missing
--------------

The walkthrough gets the player from "what is this flag?" to "my first villager
did a thing" in about 30 minutes. After that, the journal goes silent. The
player has learned the CONTROLS (wand, flag menu, job board) but nothing about
the ENGINE:

  Room recipes -- The player doesn't know rooms can be upgraded or that block
  combinations matter. They've built exactly one room and one storeroom.

  The quest loop -- Entry 10 mentions quests exist but doesn't explain the loop
  (quest > build/gather > complete > new quest > progression). The player
  doesn't know quests ARE the progression system.

  Supply chains -- No mention that jobs feed into each other (miner > smelter >
  blacksmith > everyone else). The player has no mental model for why they'd
  want more than one job type.

  Warp -- The defining feature of the mod (offline production) is never
  explained. The player might leave town and come back confused about where
  items came from, or worse, never leave and miss it entirely.

  Scaling storage -- The player built one chest room. They don't know storage
  tiers exist or that running out of storage halts production.

  Villager needs -- Entry 8 says "give food." The economics tab lesson triggers
  on first_unmet_needs but the player may not connect cause and effect (villager
  idle > check needs > stock items).

  The flag as a hub -- The flag does a lot (convert items, open menus, view
  quests, check economics). Entry 9 covers "click to open menu" but the flag's
  item-conversion role is scattered across entries and easy to forget.

  Multiple towns / town radius -- Not covered, but may not need to be for
  onboarding.


========================================================================


PART 2: PROPOSED COMPLETE ONBOARDING
====================================

Design principles
-----------------

  1. Teach systems, not jobs. Questown is an engine. The jobs are content that
     plugs into the engine. The journal should teach the engine; individual jobs
     are discovered through quests.

  2. Gate on player actions, not job completion. A lesson about supply chains
     should appear when the player has enough infrastructure to benefit from it,
     not when a specific job finishes.

  3. Two-phase structure. The existing Journal Entries (diary voice) walk you
     through setup. The Lessons (discovered notes) teach you the engine. Keep
     both voices.

  4. No lesson should require reading another lesson first. Each is
     self-contained reference material.

  5. Let quests do the teaching for specific jobs. If the player gets a quest to
     "build a kitchen," the quest itself tells them what blocks to place. The
     journal doesn't need a page for every job -- it needs to teach the player
     how to read quests and act on them.


Proposed structure
------------------

CATEGORY 1: JOURNAL ENTRIES (unchanged)

  Keep entries 1-11 exactly as they are. They're a good guided tutorial.


CATEGORY 2: LESSONS

  Reworked from job-specific pages to system-focused reference material.


........................................................................

Lesson 1: Farms
  Status:   KEEP (already exists)
  Gate:     first_farm_quest
  Sortnum:  1

  Content: How to build a farm. This one is correct -- farms are a unique room
  type (fences + gate, not walls + door) so they deserve a dedicated page.


........................................................................

Lesson 2: Villager Needs
  Status:   KEEP (move to Lessons category)
  Gate:     first_unmet_needs
  Sortnum:  2

  Currently in "Journal Entries" category -- should move to "Lessons" since it's
  reference material, not a diary entry.

  Content: Economics tab; keeping the store room stocked.


........................................................................

Lesson 3: Room Recipes
  Status:   NEW
  Gate:     first_job_done
  Sortnum:  3

  Page 1: "Rooms aren't just walls and a door. What you put inside matters. A
  room with a furnace becomes a kitchen. A room with a crafting table becomes a
  workshop. The town reads the recipe of blocks in each room and assigns it a
  purpose."

  Page 2: "You don't need to memorize room recipes. The quest system will tell
  you exactly what to build. But if you're curious, experiment -- try adding
  chests, lights, or work blocks to an existing room and see what it becomes."

  Why this matters: This is the single most important concept after "how to
  register a room." Every job, every upgrade, every progression step flows from
  room recipes.


........................................................................

Lesson 4: The Quest Loop
  Status:   NEW
  Gate:     first_job_done
  Sortnum:  4

  Page 1: "The quests tab in the flag menu is your to-do list. Each quest asks
  you to build a room, gather materials, or upgrade something. Complete a quest
  and new ones appear -- that's how your village grows."

  Page 2: "Quests are the main way to unlock new jobs, attract new villagers,
  and upgrade rooms. If you're ever stuck, check the quests tab. If there are no
  quests, your villagers may need to finish their current work first."

  Why this matters: Many players don't realize quests ARE the progression
  system. They think the job board is the main interface.


........................................................................

Lesson 5: Supply Chains
  Status:   NEW
  Gate:     second_job_type  [NOT YET IMPLEMENTED -- needs advancement for
            "player has more than one type of job running"]
  Sortnum:  5

  Page 1: "Your villagers don't work in isolation. A miner digs up raw ore. A
  smelter processes it into ingots. A blacksmith forges the ingots into tools.
  A crafter makes handles and bowls. Each job feeds the next."

  Page 2: "The store room is the hub. Villagers deposit what they make and pick
  up what they need. If a blacksmith is idle, check whether your smelter is
  keeping up. If nobody has tools, you might need a crafter."

  Why this matters: This is the "aha" moment where the mod clicks. Without it,
  players build isolated rooms and wonder why nothing works together.


........................................................................

Lesson 6: Storage Matters
  Status:   NEW
  Gate:     first_store_room
  Sortnum:  6

  Page 1: "Storage rooms grow with your village. More chests in a room means a
  bigger storeroom:

     1 chest  -- Small Storeroom
     2 chests -- Storeroom
     3 chests -- Big Storeroom
     6 chests -- Huge Storeroom
    12 chests -- Storage Facility
    24 chests -- Warehouse"

  Page 2: "When storage fills up, production stops. Villagers can't deposit
  items if there's no room. Upgrade your storage before you need it -- it's the
  cheapest upgrade and the most important."

  Why this matters: Storage bottlenecks are the #1 cause of "my village stopped
  working" confusion.


........................................................................

Lesson 7: While You Were Away
  Status:   NEW
  Gate:     first_warp  [NOT YET IMPLEMENTED -- triggered when the player
            returns to town after being away and warp runs]
  Sortnum:  7

  Page 1: "Your village doesn't stop when you leave. When you travel far away
  and come back, your villagers will have kept working. Check the store room --
  you might find items they produced while you were gone."

  Page 2: "The longer you're away, the more they accomplish (up to a limit).
  Make sure they have enough supplies and storage before you leave, or they'll
  run out and sit idle."

  Why this matters: Warp is the mod's signature feature and it's completely
  invisible if nobody explains it. Players who don't know about it miss the
  entire point of setting up supply chains.


........................................................................

Lesson 8: Room Upgrades
  Status:   NEW
  Gate:     first_room_upgrade  [NOT YET IMPLEMENTED -- triggered when a room
            recipe changes to a higher-tier version]
  Sortnum:  8

  Page 1: "Rooms can be upgraded. Add furniture, lights, or extra work blocks
  to transform a basic room into something better. A crafting room with extra
  tables and lights becomes a workshop. A kitchen with a chest becomes a
  stockable kitchen."

  Page 2: "You don't need to memorize upgrades. Quests will tell you what to
  build next. Just know that when a quest asks you to 'upgrade' a room, it
  means adding specific blocks to an existing room -- not tearing it down and
  rebuilding."

  Why this matters: Players often demolish rooms instead of upgrading them.
  This saves frustration.


........................................................................

Lesson 9: The Town Wand
  Status:   NEW
  Gate:     wand_get
  Sortnum:  9

  Page 1: "The Town Wand is your most important tool. Use it to:
    - Click a door       > register a room
    - Click a fence gate > register a farm or gated area
    - Click a special block > register a block room"

  Page 2: "Rooms must be registered to count. If a villager isn't using a room
  you built, you probably forgot to register it. The wand also shows which town
  it belongs to -- hover over it in your inventory."

  Why this matters: "I built the room but nothing happened" is the second most
  common confusion. The existing entry 3 covers this but it's buried in the
  tutorial flow and easy to forget.


........................................................................

Lesson 10: The Flag
  Status:   NEW
  Gate:     first_open_flag_menu
  Sortnum:  10

  Page 1: "The flag is your town's command center. Right-click it to:
    - View and manage villagers
    - Check the quests tab
    - See the economics tab (what items are needed)
    - Monitor villager happiness and status"

  Page 2: "The flag also converts items. Right-click it while holding:
    - A stick            > Town Wand
    - A pressure plate   > Welcome Mat
  Some special items can only be obtained this way."

  Why this matters: The flag's dual role (menu + item converter) is taught
  across multiple diary entries. A single reference page consolidates it.


========================================================================


SUMMARY
=======

What changed from the job-focused approach
------------------------------------------

  Job-focused (old)                      Engine-focused (new)
  -------------------------------------  ------------------------------------
  12 entries, one per job/room type      10 entries teaching systems
  Player learns "furnace = kitchen"      Player learns "blocks define purpose"
  No mention of warp                     Dedicated lesson on offline production
  No mention of quest loop               Dedicated lesson on progression
  No mention of supply chains            Dedicated lesson on how jobs connect
  Storage as a flat list                 Storage as a production bottleneck
  Room upgrades as examples              Room upgrades as a concept
  Job-specific crafting recipes          No recipes needed -- quests handle it


New advancements needed
-----------------------

  ID                   Trigger                                    Status
  -------------------  -----------------------------------------  ---------------
  first_warp           Player returns after warp runs              NOT IMPLEMENTED
  first_room_upgrade   Room recipe changes to higher-tier version  NOT IMPLEMENTED
  second_job_type      Two different job types active at once      NOT IMPLEMENTED

  If these advancements are too costly to implement right now, all three lessons
  could temporarily gate on first_job_done instead. The content still makes
  sense at that point in the player's journey -- it's just less precisely timed.


Files to create/modify
----------------------

  Action          File                   Notes
  --------------  ---------------------  ----------------------------------------
  Keep            lesson_farms.json      Add sortnum: 1 (already done)
  Move category   lesson_needs.json      Change category entries > lessons, sn: 2
  Create          lesson_rooms.json      Room recipes concept
  Create          lesson_quests.json     The quest loop
  Create          lesson_supply.json     Supply chains
  Create          lesson_storage.json    Storage tiers + bottleneck warning
  Create          lesson_warp.json       Offline production
  Create          lesson_upgrades.json   Room upgrades
  Create          lesson_wand.json       Town Wand reference
  Create          lesson_flag.json       Flag as hub
  Delete          lesson_cooking.json    Replaced by systems approach
  Delete          lesson_baking.json     Replaced by systems approach
  Delete          lesson_soup.json       Replaced by systems approach
  Delete          lesson_crafting.json   Replaced by systems approach
  Delete          lesson_blacksmith.json Replaced by systems approach
  Delete          lesson_armorer.json    Replaced by systems approach
  Delete          lesson_smelting.json   Replaced by systems approach
  Delete          lesson_mining.json     Replaced by systems approach
  Delete          lesson_fishing.json    Replaced by systems approach
