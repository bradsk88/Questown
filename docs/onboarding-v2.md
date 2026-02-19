Onboarding v2 Proposal
======================

v1 had two attempts: 12 job-focused lesson files (one per job), then a
systems-only doc that ignored the curated quest flow. Both missed the mark.

The real onboarding should mirror what the game already does. The tutorial
quest flow (campfire > kickoff > hunter > food job > room building) is a
curated experience. The journal should reinforce and extend it, not
duplicate or contradict it.

New concepts to cover that v1 missed entirely:
  - Blocks of Progress (BOP) are the progression currency
  - Job unlocking uses a parent-child skill tree
  - The crafter is a keystone job that gates fishing station, bowls, shears,
    fishing rods, etc.
  - Some blocks (fishing station) are ONLY obtainable from crafter villagers


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
  earn these as they work -- check the flag menu's <rendered item: BOP> tab."

  Why here: The player just got their first job change quest. They need to
  understand the skill tree NOW, before they encounter a locked job and get
  confused.


Entry 13: "The Crafter"

  Gate:     NEW advancement "tutorial_complete" (tutorial returns DONE)

  Content:  "The crafter is special. They make things nobody else can --
  bowls, shears, fishing rods, and even fishing stations. Without a crafter,
  your village can't grow past basic gathering. Prioritize unlocking crafter
  jobs on the skill tree."

<Human Feedback for Claude>: There's a slight confusion here because PLAYERS 
can create some of those items. So this raises an eyebrow for the reader. We
would do better to focus on their value as an automator and for unlocking the
items that truly CANT be made by a player.

  Why here: After the tutorial, the player is about to encounter procedural
  quests. The crafter is the single most important job to understand because
  it gates other jobs' supplies.

<Human Feedback for Claude> This would also be a time to point the player to 
JEI recipe page for whatever crafter-built block is required for the next job.
(We have not yet built the recipe page for JEI, but it should show - for example
"fishing station: crafted by questown crafter from fishing rods" [visually]).

Entry 14: "Supply Chains"

  Gate:     NEW advancement "second_job_type" (two different root jobs active)
            Fallback: tutorial_complete

  Content:  "Your villagers feed each other's work. A miner digs ore. A
  smelter processes it. A blacksmith forges tools. A crafter makes handles.
  The store room is the hub -- everyone deposits and picks up from there.
  If someone is idle, check whether their supplier is keeping up."

<Human Feedback for Claude> This is a place where we should be curated. We should
request the player to create two very specific jobs which very clearly feed from
one to the other. I also think there should be a quest or achievement later on 
in the game that rewards the player for creating chains of certain lengths.

Entry 15: "While You Were Away"

  Gate:     NEW advancement "first_warp" (warp simulation runs on player
            return)
            Fallback: tutorial_complete

  Content:  "Your village works while you're gone. Travel far away, explore,
  adventure. When you come back, check the store room -- your villagers kept
  producing. Make sure they have supplies and storage before you leave."

<Human Feedback for Claude> "first_warp" should have a base threshold for minimum
time away from town. Maybe even minimum distance.

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

<Human Feedback for Claude> Probably a good time to mention villagers' inability to stack?


Lesson: The Town Wand (sortnum 5)

  Gate:     wand_get

  Content:  Click door = register room. Click gate = register farm. Click
  special block = register block room. "If a villager isn't using a room,
  you probably forgot to register it."

<Human Feedback for Claude> Could possibly be powered by the same logic that 
finds campfires, but run fairly rarely to avoid performance hit that we were
trying to avoid by adding door registration in the first place.  "Found 
unregistered door -> Trigger this journal entry".


Lesson: The Flag (sortnum 6)

  Gate:     first_open_flag_menu

  Content:  Right-click to open menu (villagers, quests, economics, BOP
  tabs). Also converts items (stick > wand, pressure plate > welcome mat).

<Human Feedback for Claude> The whole "right-click on flag" thing is prone 
to mistakes. We should probably just add a crafting tab to the flag UI (
and a JEI recipe page to support it)

Lesson: Blocks of Progress (sortnum 7)

  Gate:     first_job_quest

  Content:  "Villagers earn Blocks of Progress as they work. Collect them
  from the BOP tab in the flag menu. Spend them to unlock new jobs on a
  villager's skill tree. Choose wisely early on -- they're scarce at first."

<Human Feedback for Claude> Something to consider: Give the player a bunch of 
BOPs as a reward for some early-game milestone. Quests and journal entries that
drive them to spend the BOPs and realize their value.

Lesson: The Crafter's Workshop (sortnum 8)

  Gate:     tutorial_complete (or first_job_done fallback)

  Content:  "Some items can only be made by crafter villagers: bowls, shears,
  fishing rods, ladders, paper, and fishing stations. The crafter skill tree
  branches from sticks into specialized tools. Unlock fishing_rod before you
  can unlock fishing_station."

  Note: No crafting recipe page needed -- it's just a crafting table in a
  room. Show the crafter skill tree progression as a text list instead.

<Human Feedback for Claude> See prev notes about crafters.

Lesson: Room Upgrades (sortnum 9)

  Gate:     NEW "first_room_upgrade" or first_job_done fallback

  Content:  "Add blocks to upgrade rooms. Don't demolish -- just add. Quests
  tell you what to build next."

<Human Feedback for Claude> Should probably be accompanied by a room upgrade 
quest.


========================================================================


SECTION 3: PROPOSED NEW UI/GAMEPLAY ELEMENTS
=============================================

A. ADVANCEMENT TOASTS [LOW COST]

  Wire MC's built-in toast system to Questown advancements. Currently
  advancements fire silently. Show "New journal entry!" with book icon.

  Implementation: Add "display" objects to advancement JSONs (MC renders
  toasts automatically for advancements with display info).

  Impact: Player learns the journal has new content. Passive discovery.

<Human Feedback for Claude> They're not currently silent. But if we could 
make them better, I'm down.

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

<Human Feedback for Claude> This is probably fine for a v1 release. I'd 
like to do something less spammy and more integration eventually though.


D. BOP NOTIFICATION [LOW COST]

  Chat message when a new BOP is earned: "A Block of Progress was earned!
  Check the flag menu to collect it."

  Implementation: Hook into BOP deposit logic, send chat message.

  Impact: Player learns BOP exists without having to discover the tab.

<Human Feedback for Claude> Instead of a chat messae, I think it would be cool 
to visually change the appearance of the flag (maybe BOPs floating around it -
we already float BOPs above villager heads when they have them) and when the 
user clicks on the flag, preselect the BOP UI the first time. Maybe an achievement
for first view of BOP screen would help with gating journal entries and prevenitng
re-opening flag UI to BOP after first time.

E. IDLE VILLAGER INDICATOR [MEDIUM COST]

  Particle effect or overhead icon when a villager is idle due to missing
  supplies. Like vanilla villager "angry" particles but for "needs items."

  Implementation: Check villager state each tick, spawn particles.

  Impact: Connects "villager standing around" to "check economics tab."


F. POST-TUTORIAL CHAPTER TITLES [LOW COST]

  After tutorial ends, first few procedural batches get a chat message:
  "Chapter 2: Feeding the Village", "Chapter 3: Tools of the Trade"

  Implementation: Counter on quest batch generation, keyed chat messages.

  Impact: Gives progression arc feeling past the tutorial cliff.

<Human Feedback for Claude> Maybe achievement instead of chat message?

G. JOURNAL NOTIFICATION DOT [MEDIUM-HIGH COST]

  Badge on journal item when new entry unlocks.

  Implementation: Client-side item rendering overlay or NBT flag.

  Impact: Player knows to check the book without being told.

<Human Feedback for Claude> We use patchouli. Should avoid over-engineering.


H. SKILL TREE HINT ON FIRST JOB CHANGE [LOW COST]

  When player completes their first job change quest, send a chat message:
  "Tip: Open a villager's menu and check the Skills tab to see what jobs
  they can learn."

  Implementation: One-time chat message in job change reward logic.

  Impact: Directly teaches the skill tree exists at the moment it matters.

<Human Feedback for Claude> I've been meaning to add little red "hey check out this tab" 
visual indicators to these UIs when new info is avialable. Maybe now is the time.


========================================================================


SECTION 4: NEW ADVANCEMENTS NEEDED
====================================

  ID                  Trigger                                 Used by
  ------------------  --------------------------------------  ------------------
  tutorial_complete   Tutorial phase returns DONE             Diary 13, lesson 8
  second_job_type     Two different root job types active     Diary 14
  first_warp          Player returns after warp runs          Diary 15
  first_room_upgrade  Room recipe changes to higher tier      Lesson 9

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

  Advancement toasts (add display to advancement JSONs)
  Skill tree hint chat message
  BOP notification chat message
  Post-tutorial chapter titles

Tier 3 -- Medium Java, very high impact:

  Quest flavor text
  Warp summary message
  Idle villager indicators
  New advancements (tutorial_complete, second_job_type, first_warp,
    first_room_upgrade)

Tier 4 -- Nice to have:

  Journal notification dot


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
  docs/onboarding-v2.md                (this file)


========================================================================


VERIFICATION CHECKLIST
=======================

  [x] Every gap from the "doesn't learn" column has a proposed solution
  [x] BOP + skill tree + crafter chain covered in both diary and lesson
  [x] No lesson duplicates what quests already teach
      (quests say WHAT to build; journal says WHY)
  [ ] ./gradlew compileJava after file changes
  [x] Plain text formatting (no markdown rendering needed)
