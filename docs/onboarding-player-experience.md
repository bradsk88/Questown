Questown Player Onboarding — Experience Analysis
==================================================

This document describes the player's typical onboarding experience, walks
through two illustrative scenarios, and offers critique and suggestions.


========================================================================


SECTION 1: THE TYPICAL EXPERIENCE
===================================

A new player has crafted or found a Town Flag block. They have no prior
knowledge of Questown's systems. Here is what they encounter, step by step.


Step 1: Placing the Flag and Finding the Journal
--------------------------------------------------
The player places the flag in their base or a clearing. When they walk
within 10 blocks, the `ApproachTownTrigger.FirstVisit` advancement fires.
Two things happen:

  1. A chat message appears: "You find an old journal on the ground."
  2. A Patchouli book ("Old Journal") is added to the player's inventory.

The journal's first entry ("Welcome!") is immediately readable. It's
written in-character as a letter from a departing settler:

  "Dear traveler, You have stumbled upon the place I once called home.
   My people and I are leaving now and will not return. But I don't
   believe you will be alone for long."

It includes a multiblock diagram showing the flag with a campfire, and
the instruction: "Just light a campfire by the flag and wait until
morning."

The flag menu also shows a campfire quest. Between the journal and the
quest, the player has two channels telling them the same thing.

First impression: The journal gives the experience a narrative frame —
this isn't just a mod mechanic, it's a story about inheriting a town.

Visitor spawning: When the campfire quest completes, `MCDelayedReward`
adds the `SpawnVisitorReward` to the flag's `morningRewards` queue. The
reward only fires when `AbstractTownFlagTicker.handleIfNewMorning()`
detects `Signals.MORNING` (dayTime < 6000 ticks). The visitor spawns on
the next in-game morning — the journal's "wait until morning" is
mechanically accurate.


Step 2: First Visitor and the Journal Walkthrough
---------------------------------------------------
On the next in-game morning after the campfire quest completes, a
villager spawns. A toast notification appears: "A visitor has arrived."
A chat message says new quests are available.

The player sees four new quests in the flag menu:
  - Build a town gate (welcome mat)
  - Build a job board (sign in a room)
  - Build a store room (chest in a room)
  - Wooden sword (item quest)

These quests are presented simultaneously, but the journal is designed to
walk the player through them in a specific order. Each journal entry
unlocks when the player completes a step, guiding them to the next one:

  Entry 2 (unlocks: first_visitor) → "Make a wand" (stick on flag)
  Entry 3 (unlocks: wand_get)      → "Register a room" (wand on door)
  Entry 4 (unlocks: first_room)    → "Build a job board" (place a sign
                                      in a registered room — auto-converts
                                      to a job_board_block)
  Entry 5 (unlocks: first_job_board) → "Make a request" (use the board)
  Entry 6 (unlocks: first_job_request) → "Build a town gate" (pressure
                                          plate on flag → welcome mat,
                                          with multiblock diagram)
  Entry 7 (unlocks: first_welcome_mat) → "Build storage" (chest in room)
  Entry 8 (unlocks: first_store_room) → "Supply food" (put food in chest)

The journal is the sequencer. Quests show everything needed; the journal
tells the player what to do next. Each entry includes multiblock diagrams
and explicit instructions. A player who follows the journal will complete
the batch in order without getting stuck.

Item quest note: The wooden sword quest requires putting the sword in a
container (chest, furnace, crafting table) in any registered room — not
specifically the store room. A player could complete it as soon as they
register their first room and place a chest in it. However, neither the
journal nor the quest text tells the player to do this. The journal
doesn't mention putting items in containers until entry 7 (store room),
and the flavor text says "toss it near the town flag" — which describes
a mechanic that doesn't exist (see Critique #1). The constraint isn't
mechanical (the system allows it early) but informational (nothing
guides the player to try it).


Step 3: Building Rooms and Registering Doors
---------------------------------------------
Following the journal's sequence, the player makes a wand (entry 2),
builds a room (entry 3), registers a door, then places a vanilla sign
in the room — the sign auto-converts to a job board block (entry 4).

When the room scan detects a valid room from a registered door, multiple
forms of feedback fire:

  - Happy villager particles spawn at the room location (every time)
  - Chat message: "A new [Room Type] was constructed at location [x, z]"
    (every time — names the specific room type, e.g. "Store Room")
  - Advancement toast (first time per room type): "Four walls (and a
    roof?)", "What I Want", "Stocking Up", "Welcome!", etc.
  - Journal page unlock (first time per room type)

Room destruction also has feedback: smoke particles + chat message.
Room resizing triggers happy villager particles + chat message.

The success path is well-covered. The gap is the failure case:
  - If the player registers a door on an invalid room (missing wall,
    open-air structure), the door stays registered but no room is ever
    detected. There is no failure message. (Dead door detection code
    exists in `TownRoomsMap.dropDeadDoors()` but is commented out.)
  - The wand click itself has no immediate feedback — confirmation
    arrives later when the room scan ticks.

Key friction: Success feedback is strong (particles, chat, toasts).
The remaining gap is failure feedback — nothing tells the player their
room was NOT detected.


Step 4: Jobs and Growth (Phases 1.5–3)
---------------------------------------
With the kickoff quests done and a second villager arriving, the tutorial
introduces job changes, bedrooms, and food. The player learns to:
  - Open a villager's menu and change their job
  - Build a bedroom (bed in a registered room)
  - Supply food items via the store room

This section flows relatively well — each quest is concrete and the flavor
text explains the "what" and "why." The 3 BOP grant at this stage gives
the player currency to spend, though the notification that BOPs were
granted is subtle (no explicit chat message in current implementation).

Key friction: The BOP grant is easy to miss. The player may not realize
they have currency to spend until they stumble into the skill tree.


Step 5: The Crafter and Skill Tree (Phase 4)
---------------------------------------------
The tutorial asks the player to assign a crafter and build a crafting room,
then specialize the crafter into bowl crafting. This teaches the skill tree
and BOP spending.

This is the deepest teaching phase — the player navigates the skill tree
UI, spends BOPs twice (once for root job, once for child job), and sees
that jobs have upgrade paths.

Key friction: The skill tree UI is new and unfamiliar. The player must
find the Skills tab in the villager menu, locate the crafter job, and
figure out how to spend a BOP on it. If they don't have enough BOPs,
they're stuck with no clear path to earning more.


Step 6: Supply Chains and Storage (Phases 5–6)
------------------------------------------------
The tutorial requires farmer + baker + crafter running simultaneously,
plus a kitchen room. Then a storage upgrade quest arrives.

This is where the mod's core loop clicks: jobs feed each other, storage
is the bottleneck, and the player's role shifts from "do everything
yourself" to "manage the village economy."

Key friction: The CONCURRENT_JOBS requirement can be confusing — the
player may not understand why swapping a single villager between jobs
doesn't count. The flavor text helps but the concept is subtle.


Step 7: The World Beyond (Phase 7)
------------------------------------
The tutorial sends the player to find exotic wood from a distant biome.
This provokes them to leave town, which triggers warp on return — the
key "aha moment" where the village produces while the player explores.

Key friction: The warp payoff depends on the warp summary message, which
is not yet implemented. Without it, the player returns to find items in
chests but may not connect this to "the village worked while I was away."


Step 8: Post-Tutorial
----------------------
Procedural quests take over. These are generated by a needs-driven
algorithm: `AbstractQuestGarden.doGrow()` checks what rooms the village
needs (via `EconomicsHandle.getAggregatedRooms()`) and generates ROOM
quests to fill gaps. If no rooms are needed, it picks from all available
room recipes.

Procedural quests have no flavor text — they show a room icon and type
but no guidance like "why" or "how." The transition from tutorial to
procedural quests happens silently. There is no "tutorial complete"
message; the generic "New quests are available!" chat message is the
same one used throughout the tutorial. The `TutorialComplete` advancement
fires earlier (at Phase 4 start), not at the actual end of the tutorial.

Key friction: The player goes from quests with flavor text explaining
each step to quests with no context. The shift is invisible — there's no
signal that the tutorial is over and the player should now self-direct.


========================================================================


SECTION 2: SCENARIOS
=====================


Scenario A: The Builder (Journal Reader)
------------------------------------------
Alex is a builder-type player. They love constructing elaborate bases and
aesthetically pleasing villages. They place the flag in a pre-built town
square.

  1. Places flag. Approaches it, receives the old journal. Reads it —
     Alex appreciates the narrative framing ("dear traveler..."). Sees
     the campfire diagram, lights a campfire. The journal says "wait
     until morning" — Alex waits, and a visitor appears the next
     morning. Good so far.

  2. Journal entry 2 unlocks ("Getting Started"). Alex reads it, learns
     the stick-to-wand trick, makes a wand. Entry 3 unlocks ("Rooms")
     with a room diagram. Alex follows along, builds a room, registers
     the door.

  3. Entry 4 unlocks ("Job Board"). The journal says to place a sign in
     a registered room. Alex places a vanilla oak sign in the room. It
     auto-converts to a job board block. Alex makes a request on the
     board. Entry 5 and 6 unlock. Entry 6 ("Town Gate") shows the
     welcome mat diagram with pressure plate instructions.

     Alex follows the instructions but is annoyed that the welcome mat
     doesn't match their build aesthetic. Places it anyway. Entry 7
     unlocks ("Storage"). Alex builds a store room.

  4. Alex now has a store room with a chest. The sword quest says "toss
     it near the flag" — Alex dropped a wooden sword on the ground
     earlier. It despawned. Alex crafts another, and since the store
     room now exists, tries putting it in the chest. The quest
     completes. Alex could have done this earlier (any chest in any
     registered room works) but had no reason to try — the flavor text
     pointed elsewhere. All kickoff quests done.

  5. Progresses through remaining phases without major issues. The room-
     building quests align well with Alex's playstyle. The exotic wood
     quest gives Alex a reason to explore biomes for building materials
     they'd want anyway.

  Pain points for Alex:
    - The journal-guided flow works well — Alex completed everything in
      order without confusion on the core mechanics
    - The sword quest flavor text caused a wasted item (minor annoyance)
    - Welcome mat is aesthetically displeasing (minor frustration)
    - Room registration feedback is good: happy villager particles, chat
      messages naming the room type, and advancement toasts. But if Alex
      builds a room with a missing wall or forgets to register the door,
      there's no negative feedback — just silence


Scenario B: The Explorer (Journal Skipper)
--------------------------------------------
Sam is an explorer-type player. They spend most of their time traveling,
mining, and fighting. They want the village to run itself while they're
away. They place the flag near a cave entrance.

  1. Places flag. Approaches it, receives the old journal. Glances at it
     briefly — "Dear traveler..." — skims past the campfire diagram.
     Already has a campfire nearby for cooking. The campfire quest
     completes, but nothing visible happens yet. Sam goes mining.
     Returns the next morning to find a visitor has appeared.

  2. Sam ignores the journal and goes straight to the flag menu. Sees
     four quests. Reads the flavor text:
       - "Build a fence gate entrance for your town."
       - "Place a lectern in a room and register the door."
       - "Place a chest in a room and register the door."
       - "Craft a wooden sword and toss it near the town flag."

     Sam starts with the sword — seems easiest. Crafts one, drops it
     near the flag. Nothing happens. Tries again. The sword sits on the
     ground, then despawns. Sam is confused.

  3. Sam tries the room quests. Builds dirt huts with doors. Doesn't
     know about the wand — quest text says "register the door" but
     doesn't explain how. Sam notices "with your wand" phrasing in the
     store room quest, doesn't have a wand.

     Sam re-opens the journal, finds entry 2 (wand instructions). Makes
     a wand. Now Sam has re-engaged with the journal. Registers doors.

  4. Sam reads the job board quest: "Place a lectern." Finds a village,
     gets a lectern, places it in a room. Does this complete the quest?
     (This depends on whether the room recipe system accepts a lectern —
     the journal says "sign" but the quest says "lectern.")

     If the lectern doesn't work, Sam is stuck again. Opens the journal,
     finds entry 4 says "sign block." Tries a sign. It converts to a
     job board. The quest completes. Sam is frustrated by the
     contradiction — the quest and journal disagree.

  5. Sam now follows the journal more closely, having been burned twice
     by the quest text. Completes the town gate and store room via the
     journal's sequence. Puts the wooden sword in the store room chest
     (having learned that "toss near the flag" doesn't work). All
     kickoff quests done.

  6. Phase 1.5: Sam changes a villager to hunter. This resonates — the
     hunter leaves town, which mirrors Sam's own playstyle. Sam starts
     to see the vision.

  7. Phase 4 (crafter): Sam finds the skill tree interesting but is
     impatient with the BOP economy. Wants to unlock everything fast.
     Spends both available BOPs immediately. Now has no BOPs and can't
     unlock the baker for Phase 5. Must wait for villagers to earn more.

     Sam doesn't know how villagers earn BOPs or how long it takes. Goes
     exploring. Returns. Still no BOPs. Goes exploring again. Returns.
     One BOP available. Unlocks baker. Continues.

  8. Phase 7 (exotic wood): This is Sam's favorite quest. They were
     already itching to explore. The biome-specific wood gives direction
     to the exploration. Sam travels 500+ blocks, finds jungle, returns
     with jungle logs.

     On return, the village has produced items via warp. Sam checks the
     store room and finds bread, tools, bowls. The "village runs itself"
     promise is delivered. Sam is hooked.

  Pain points for Sam:
    - The quest flavor text actively misled Sam twice: "toss near the
      flag" (doesn't work) and "place a lectern" (wrong block)
    - Sam's frustration was partly self-inflicted (skipping the journal)
      but the quest text shouldn't contradict the journal
    - Early phases feel slow — too much building, not enough exploring
    - BOP earning rate is opaque
    - The warp payoff (Phase 7) is the hook, but it comes after 6 phases
      of building tutorials that don't match Sam's playstyle


========================================================================


SECTION 3: CRITIQUE AND SUGGESTIONS
=====================================


1. Misleading Item Quest Flavor Text
--------------------------------------

Problem: Four item quest flavor texts say "toss it near the town flag":
  - "Craft a wooden sword and toss it near the town flag."
  - "Hunt a sheep and toss the mutton near the flag."
  - "Upgrade your villager's weapon. Craft a stone sword and toss it
     near the flag."
  - "Gather apples and toss them near the flag."

But item quests are fulfilled by `TownContainers.getAllStacks()`, which
scans containers (chests, furnaces, crafting tables) inside registered
rooms. Dropping items on the ground near the flag does nothing — the
items will despawn.

This is the most concrete onboarding bug: the instructions tell the
player to do something that doesn't work.

The sword quest can technically be completed as soon as any registered
room has a container with a sword in it — it doesn't require the store
room specifically. But the player has no reason to try this: the journal
doesn't mention putting items in containers until entry 7 (store room),
and the flavor text says "toss" which implies dropping on the ground.
The result is that most players will either waste the item by dropping
it or delay the quest until they happen to have a store room.

Fix: Update the flavor text to match the actual mechanic. For example:
  - "Craft a wooden sword and place it in your store room chest. Your
     villager will need it."

This also reinforces the store room's purpose — "this is where you
deposit items for your village." The phrasing naturally implies the
store room should be built first.


2. Job Board Quest Flavor Text Contradiction
----------------------------------------------

Problem: The Phase 1 job board quest says "Place a lectern in a room
and register the door." But the actual mechanic has two valid paths:

  a) Place a vanilla sign in a registered room — it auto-converts to a
     job_board_block via `TownWorldInteraction.swapJobBoardSign()`.
     This is what the journal teaches (entry 4): "Place a blank, wooden
     sign block in a registered room to convert it into a JOB BOARD."
     The journal's multiblock diagram shows `minecraft:oak_sign`.

  b) Right-click the flag with a sign (any `ItemTags.SIGNS`) to get a
     `job_board_block` item, then place it manually. This is the flag
     conversion path in `TownFlagBlock`, but no documentation teaches it.

The quest text references a lectern, which is neither path. A player who
follows the quest text will place a lectern in a room; a player who
follows the journal will place a sign. These are different blocks. If
the room recipe system doesn't recognize a lectern as a job board
ingredient, the quest-following player is stuck.

Fix: Update the quest flavor text to match the journal:
  - "Place a wooden sign in a registered room. It will become a Job
     Board — this is how villagers find work."


3. Room Registration Feedback — Notes
---------------------------------------

Success feedback is already strong. When the room scan detects a valid
room, the player gets:
  - Happy villager particles at the room (every time, not just first)
  - Chat message naming the room type: "A new Store Room was constructed
    at location [x, z]" (every time)
  - Advancement toast (first time per room type)
  - Journal page unlock (first time per room type)

Room destruction and resizing also have feedback (smoke/happy particles
+ chat messages). The success path during the tutorial is well-covered.

The remaining gap is failure feedback — see TODO/dead-door-detection.md.


4. BOP Economy Transparency
-----------------------------

Problem: The player receives BOPs at Phase 1.5 with no clear notification.
They spend BOPs in Phase 4 but have no visibility into how BOPs are earned,
how many they have, or when the next one will arrive.

The v4 proposal addresses notification (flag particles, tab preselection)
but doesn't address the earning-rate opacity.

Suggestion: In addition to the v4 notification improvements:
  - The BOP tab in the flag menu should show a progress indicator:
    "Next Block of Progress: 60% (from Hunter working)" or similar.
    Even a simple progress bar per active villager would help.
  - When a BOP is earned, a chat message: "Your Hunter earned a Block
    of Progress! Check the flag menu to collect it."

The BOP economy is the mod's core progression currency. Making it opaque
forces players to wait passively, which is the opposite of the mod's
"village runs itself while you explore" pitch.


5. Quest List vs. Journal Ordering
------------------------------------

Problem: The Phase 1 quest list adds the town gate quest first, which
likely renders it first in the quests UI. But the journal expects the
town gate to be completed last — entry 6, after job board, request, and
all intermediate steps.

A player who reads the quest list top-to-bottom and starts with the
first quest will attempt the town gate before the journal has taught
the welcome mat mechanic (entry 6 is locked behind 3 prerequisites).

Suggestion: Reorder the quest batch to match the journal's expected
completion order. The journal sequences: room registration → job board →
request → town gate → store room. The quest list should present them in
a compatible order, or at minimum not lead with the town gate.


6. Supply Cache Mission (Early Exploration Beat)
--------------------------------------------------

Problem: Phases 1–3 are heavily building-focused. Explorer-type players
must build 4–5 rooms before they reach gameplay that matches their
interests (Phase 7). Meanwhile, the item quests (wooden sword, mutton)
use a misleading "toss near the flag" mechanic and offer easy-to-craft
items that don't push the player to engage with the world.

Suggestion — Supply cache mission: Replace the wooden sword item quest
with a map-driven exploration quest. The flow:

  1. The gatherer's first expedition brings back a treasure map as part
     of their loot (tutorial-only behavior, not a regular gatherer drop).
  2. The map points to a Questown-generated chest somewhere outside town
     containing an iron sword (or similar upgrade).
  3. The quest becomes: "Your gatherer found a map! Retrieve the iron
     sword from the cache."

This solves multiple problems at once:
  - Eliminates the misleading "toss" item quest mechanic entirely
  - Introduces an exploration beat early in the tutorial (Phase 1.5–2)
    instead of waiting until Phase 7
  - Upgrades the reward from a trivial wooden sword to a meaningful iron
    sword — predictably achievable because the map guarantees it
  - Teaches that the gatherer produces useful things (builds investment
    in the village-runs-itself loop)
  - Creates a natural escalation toward Phase 7's longer expedition

The map mechanic would be tutorial-only. Future use could tie into the
planned post-office feature (see docs/features/post-office.md) where
villagers deliver messages and maps to the player.

Caveat: This requires new mechanics (map item generation, chest
placement, gatherer loot table modification) which is higher effort
than the text-only fixes in critiques #1–2. But it replaces the weakest
part of the tutorial with something that serves all playstyles.


7. Post-Tutorial Transition
-----------------------------

Problem: After Phase 7, procedural quests begin. These are needs-driven
ROOM quests generated by `AbstractQuestGarden.doGrow()` based on what
rooms the village lacks. They have no flavor text, no "how-to" guidance,
and no indication that the tutorial is over. The same generic "New quests
are available!" message is used throughout. The player may not realize
they've transitioned from guided to self-directed play.

The `TutorialComplete` advancement fires at Phase 4 start — not at the
actual end of the tutorial. There is no advancement or message at the
true end (Phase 7 completion).

Suggestion: At the end of Phase 7, fire a distinct advancement toast:
  "Your village is thriving! New quests will appear as your village
  grows." This signals the transition.

The first 2–3 procedural quests could include brief flavor text:
  - "Your village needs a [room type]. Build one to keep production
    flowing."

This would fade the tutorial out gradually rather than cutting it off.


8. Quest Hint Indicator
------------------------

Problem: At multiple points during onboarding, the player's next action
is unclear. They must open the flag menu, navigate to the quests tab,
read the quest text, and figure out the next step. If they forget which
quest they're working on, they must re-open the menu.

Two approaches (not mutually exclusive):

  Option A — Boss bar: A persistent on-screen text indicator showing the
  current active quest name. Common in modpacks (FTB Quests, Better
  Questing). Implementation via the vanilla boss bar API (lightweight,
  server-controlled). Must include a permanent disable toggle in the
  flag UI — some players will find it intrusive.

  Option B — Flag speech bubble: Render a floating "speech bubble" above
  the flag showing the most relevant item icon for the current quest.
  For example, early in the tutorial it might show a town wand icon,
  drawing the player's attention to the flag and hinting at what to do
  next. This is less intrusive than a boss bar — it's diegetic (part of
  the world, not HUD overlay) and only visible when the player is near
  the flag.

Option B fits the mod's narrative framing better (the flag is the town's
center, not a quest tracker). It also naturally fades as the player
moves away from town, avoiding HUD clutter during exploration.

9. Room Type Discoverability (Post-Tutorial)
----------------------------------------------

Room type feedback during the tutorial is already handled: the chat
message on room detection names the specific room type (e.g. "A new
Store Room was constructed"), and the journal teaches which blocks create
which rooms via multiblock diagrams.

The gap is post-tutorial: procedural quests ask the player to build room
types they haven't seen before (e.g. "Build a Smelter Room") but provide
no flavor text explaining which blocks are needed. The journal's "Room
Recipes" lesson page (v4 proposal) covers this but requires the player
to find it.

Suggestion: Add brief flavor text to procedural room quests naming the
key block:
  - "Build a Smelter Room — place a blast furnace in an enclosed room
    and register the door."

This extends the tutorial's teaching pattern into the procedural phase.


========================================================================


SECTION 4: STATUS
===================

  DONE:
    #1  Fix item quest flavor text ("toss" → "place in store room")
    #2  Fix job board quest flavor text ("lectern" → sign mechanic)
    #5  Reorder quest batch to match journal sequence
    #4  BOP earning chat notification (message when BOP earned)
    #7  Chat message at tutorial end (first procedural batch)
    #7  Flavor text on first 3 procedural batches
    #3  Room registration feedback documented (success feedback exists;
        failure feedback → TODO/dead-door-detection.md)

  TODO FILES (future work):
    TODO/supply-cache-mission.md     — replace item quests with map-
                                       driven exploration (#6)
    TODO/flag-speech-bubble.md       — diegetic quest hint on flag (#8)
    TODO/bop-progress-indicator.md   — progress bar in flag UI (#4)
    TODO/dead-door-detection.md      — failure feedback for invalid
                                       room registration (#3)
    TODO/procedural-quest-room-recipes.md — key block names in
                                       procedural quest flavor text (#9)

  ALREADY PLANNED (from v4):
    Flag UI crafting tab (v4 Section 4G)
    BOP flag particles and tab preselection (v4 Section 4C)
    Tab indicators / red dots (v4 Section 4E)


========================================================================


SECTION 5: SUMMARY
====================

The onboarding system has a well-designed two-layer architecture: quests
present what the player needs to build, and the journal sequences them
into a step-by-step walkthrough with multiblock diagrams. A player who
follows the journal will complete the Phase 1 batch in order without
getting stuck on any mechanic — wand, room registration, job board, and
welcome mat are all taught in sequence with visual diagrams.

The following issues have been addressed:

  1. Item quest flavor text fixed: "toss near the flag" → "place in
     store room chest" (4 strings updated)
  2. Job board quest flavor text fixed: "lectern" → sign mechanic
  3. Quest list reordered to match journal sequence (job board first,
     town gate after store room)
  4. BOP earning chat notification added (broadcasts when deposited)
  5. Tutorial-end chat message added (first procedural batch completion)
  6. First 3 procedural batches now include flavor text naming the
     needed room type

Remaining work is tracked in TODO/ files. The largest remaining
opportunity is the supply cache mission (TODO/supply-cache-mission.md)
— replacing item quests with a map-driven exploration mechanic that
would introduce an early exploration beat and teach players that
villagers produce useful things.