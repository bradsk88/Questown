[DO NOT MODIFY THIS FILE]

[I asked an AI model to interview me about this project so that 
some "unspoken" design decisions would be centralized somewhere]

# Questown Project Q&A

This document captures key information about the Questown mod to help reduce
ambiguity and onboard contributors.

---

## Core Vision

**Q: What is Questown at its core?**

A: Questown is a **village/town building mod** for Minecraft. The main gameplay
experience focuses on building and managing NPC settlements.

---

## Project State

**Q: What is the current state of the mod?**

A: The mod is in **early development**. Core systems are still being built.

---

## Platform

**Q: What Minecraft version and mod loader does Questown target?**

A: **Forge 1.19.x**

---

## Core Systems

**Q: What are the main technical systems in the mod?**

A: The key systems are:
- **NPC/Villager AI** - Behavior and decision making
- **Job/Profession system** - Villager roles and work assignments
- **Declarative job definitions** - Jobs defined in JSON/config files, not code
- **Special Rules** - Hook-based functions and first-party rule modifiers
- **Time travel system (Warp)** - Offline progression simulation

---

## Minecraft-Agnostic Design

To make this mod work in the absence of minecraft (for example, unit tests and 
in-memory simulation during time travel), the "gold standard" in the project
is a complete decoupling from minecraft code wherever possible.

As mentioned, much of the incremental development that got us to where we are
was done against running minecraft code. So, we ended up integrating directly
against minecraft sometimes. But, this should be considered "tech debt" that
we should attempt to move away from.

---

## "Special Rules" system

**Q: What are "Special Rules" and how do they work?**

A: Special Rules take two different forms:

1. **Hook-based functions** - These can be registered with the Questown mod
   and allow hooks to be used for interaction with the real Minecraft game
   world. This registry is meant to be used by other mods for making their
   game/mod concepts appear within the Questown experience.

2. **First-party rules** - These are built directly into Questown and sometimes
   work the same as the hook rules, but sometimes modify core logic when they
   are declared on a job's definition.

---

## Time Travel / Warp System

**Q: What is the time travel system and what purpose does it serve?**

A: The time travel system (also called "Warp") aims to simulate the realistic
creation and consumption of resources in town when players have left for a long
time. By simulating the progress, we do not need to keep the town chunk loaded
and waste CPU cycles on villager navigation AI, etc.

**Q: How does the Warp system determine what to simulate?**

A: It identifies "important ticks" for each job definition, then simulates the
key world interactions that would have happened on those ticks if the villager
had run in real-time.

---

## Key Entities

**Q: What are the main entities/concepts a developer needs to understand?**

A: The three core entities are:
- **Town/Settlement** - The central organizational unit
- **Villager/Resident** - Custom NPCs that live and work (separate from vanilla
  Minecraft villagers)
- **Job/Work** - Tasks that villagers perform

---

## Town Management

**Q: How is a Town created?**

A: Town blocks *should* spawn naturally in the world (though this is currently
broken since the upgrade from 1.18 to 1.19). There is also a "creative mode"
command that can be used to place a flag.

**Q: How is town/villager state persisted?**

A: Town and villager data is stored in **block entity NBT** data.

---

## Jobs

**Q: How do Jobs relate to Villagers?**

A: Jobs are **auto-assigned** by the system based on town needs.

**Q: What is the typical workflow for adding a new job type?**

A: **Create a JSON file only**. Jobs are defined declaratively in JSON
configuration files, not in Java code.

---

## Architecture Challenges

**Q: What are the biggest sources of confusion or complexity in the codebase?**

A: Several issues contribute to complexity:

1. **Too many abstractions** - Hard to trace code flow

2. **Unclear responsibilities** - Classes and systems have overlapping duties

3. **Testing gap** - The game was built with real-time testing as the primary
   mechanism. For complex logic, classes and static functions were extracted
   and fully decoupled from Minecraft to allow them to easily run out-of-game.
   This led to a pattern where classes depend on interfaces that (not
   intentionally) can be provided by multiple implementations—often fake
   implementations in the context of test suites. This resulted in a project
   that "seems to be" well-tested, but it's a classic example of **100 unit
   tests, 0 integration tests**. Due to the complexity of the mod, we have
   reached a point where small changes can cause cascading bugs that are not
   caught because we don't have integration tests.

---

## Current Priorities

**Q: What are the current development priorities?**

A:
1. Fix the Warp system - Get time simulation working correctly
2. Add integration tests - Improve test coverage to catch cascading bugs
3. Fix town spawning - Restore natural town generation (broken since 1.18 to
   1.19 upgrade)
4. Improve onboarding experience - Make it more easily understood and
   satisfying

---

## Gotchas

**Q: What are common mistakes when working on this codebase?**

A: Test assumptions can be misleading—tests may pass but real behavior can be
broken due to the unit test / fake implementation architecture.

---

## Documentation

**Q: Is there external documentation available?**

A: No significant documentation currently exists. This Q&A document is intended
to help fill that gap.

---

## Conventions

**Q: Are there naming conventions or code patterns to follow?**

A: No strict rules are enforced. Standard Java/Forge conventions are generally
followed.

---

## Deep Dive: Architecture Decisions

**Q: The `TownFlagBlockEntity` is the central hub for nearly everything—villager
management, job queues, quest generation, room detection, network sync. There's
even a comment in the code: "TRY NOT TO ADD MORE FUNCTIONALITY TO THIS ENTITY".
Was this "God object" design intentional from the start, or did it evolve that
way? And if you could redesign it today, would you split it up or keep it
centralized?**

A: Yes, the design is intentional. By having the "entire game" exist within a
single block, we only need to store one "game file" which we write to the NBT
of that block. It essentially acts as a "game within a game", and allows
Questown to easily run outside of Minecraft (e.g., in test suites).

Eventually, I would like to move villager ticks into the town flag as well,
because I believe that would make the time warp mechanic easier to test and
maintain—making villagers just a simple visual representation of the underlying
game state (and a bit of navigation logic, which is still just visual
dressing).

**Q: The `DeclarativeJobTicker` package-info explicitly states a preference for
concrete types over abstractions and integration tests over unit test mocks.
Combined with the "100 unit tests, 0 integration tests" problem—what specific
experiences or bugs led you to this anti-abstraction stance? Was there a
particular moment where the fake implementations in tests masked a real bug?**

A: The "easiest" way to confirm that new additions to the game were working was
by playing the game myself. Given the long load time required to boot the game
over and over for debugging, and the effort required to set up scenarios
in-game, this became a clear bottleneck for making progress and adding new
features and functionality.

Since early game testers spent a lot of time "in town", it was also easier to
add new functionality to the realtime ticker ONLY, leaving the warper
unimplemented for a long time—which is a shame because it's a major component
for making the game feel real and provide benefits for the players.

A suite of integration tests (even ones that failed for the warp case) would
have allowed us to make the warper work via TDD rather than requiring even MORE
in-game testing. And then changes made to enable the warper often broke
realtime logic, where integration tests would have (once again) protected us
from regressions.

**Q: Realtime and warp logic often broke each other. Looking at the codebase,
they share some code paths but diverge in others (e.g., `DeclarativeJobTicker`
vs `ProductionTimeWarper`). What's your ideal end-state for the relationship
between realtime and warp? Should they share a single simulation engine with
different "tick sources", or remain fundamentally separate implementations that
just need to stay in sync?**

A: In my ideal world, the time warp would share 99% of its logic with the
realtime ticker. The only difference between the two would be where they "read"
and "write" from. The realtime ticker reads from the live game world; the time
warper reads from and updates the stored game state, ultimately writing it back
to the town after the warp. Thereafter, the realtime version would read that
state and update itself to match, allowing it to "continue where the warp left
off".

**Q: The job system has a concept of villager "preferences"
(`TownVillagers.getPreferredWork`, acceptance thresholds like
`MIN_JOB_ACCEPTANCE` and `PREFERRED_JOB_ACCEPTANCE`). How do you envision
villager personality/preferences evolving? Are they meant to be static traits
assigned at spawn, or should villagers develop preferences over time based on
what they've done?**

A: At the moment, I don't have any vision for villagers having their own
preferences. Instead, I expect MOD DEVELOPERS to state a preference via the
JSON files. This was inspired by the farmer, where they should prefer
harvesting crops because that opens up opportunities to plant seeds and
maximizes the number of simultaneously-growing crops.

As an aside, the word "preferred" might have been victim to some "smearing" in
this project. In the case of `PREFERRED_JOB_ACCEPTANCE`, that is referring to
the fact that we "prefer" for villagers to choose work that has been requested
via the job board. But if enough time passes without any requested work being
done, we will allow the villager to "make up their own mind" and start working
on something else that it seems like they can finish based on the current state
of town inventory.

**Q: The quest system pre-generates batches with weighted item rarity. Quests
can be room recipes, item collection, upgrades, or job changes. What's the
intended player experience with quests? Are they meant to guide progression,
provide optional side objectives, or something else?**

A: Quests were one of the first concepts that inspired this game, but since
then more focus has gone into the job progression part. It was inspired by the
game Dragon Quest Builders, where you find an empty magical abandoned town and
people who join the village ask you to build specific buildings, which advances
the game plot.

Originally, I thought this would be a cool way to give the player a clear
progression path, but it's still a bit under-developed. I even envisioned a
system where, after achieving "enough" quests, the town would be considered
"done" and the player would be transported to another empty town somewhere far
away where they could start again—but that's still a ways off.

**Q: Room detection integrates with an external `RoomRecipes` library. What's
your relationship to the RoomRecipes library—is it a separate project you
maintain? And how important is "room building" to the core gameplay loop versus
just being a requirement gate for jobs?**

A: I am the architect of RoomRecipes as well. Detecting rooms in Minecraft
seemed like a separate problem space, and also felt like it might be useful to
other mod developers. So, early on, I decided to break it off as its own
library. I feel like this was the right choice, and has allowed its logic to be
solidified in isolation.

Remembering the original vision of "quests" as part of Questown, I envisioned
that villagers would ask for very specific rooms, like "a bedroom with a chest
and a flower pot" (which might have a fun recipe name like "Cute Bedroom"). We
just haven't got there yet. Room requests (quests) would become more elaborate
as the town became bigger and more populated.

**Q: The network layer uses Forge's SimpleChannel with many message types. How
well does multiplayer work currently? Can multiple players interact with the
same town simultaneously, and how do you handle conflicts (e.g., two players
trying to assign the same villager to different jobs)?**

A: Multiplayer works fine. Job assignment is a "last wins" situation. If two
players opened the villager UI and chose a job, whoever clicked last would get
the real choice. This is fine because the game announces quite noisily when
jobs change.

**Q: The Special Rules system has hook-based functions for mod integration and
first-party rules that modify core logic. Have any third-party mods actually
integrated with Questown via the hooks system yet? And what's the most complex
or surprising thing a Special Rule currently does?**

A: Not yet. But the pattern of "how" this would be done has been demonstrated
via `VanillaSpecialRules`.

`check_tree_plantable` is an interesting rule. It essentially simulates world
gen for each plot of dirt in a farm to decide if a tree could grow there, and
that's how a villager decides where to plant tree saplings.

**Q: The villager AI uses Minecraft's brain system with memory modules,
activities, and behaviors. Was integrating with vanilla's brain system worth it
compared to rolling your own AI? And given your vision of villagers as "visual
dressing", how much of the brain system would you keep versus simplify?**

A: It's tough to say at this point. It was probably the right choice because
learning how to implement villager navigation would have added a huge chapter
of learning that would have blocked the "fun parts" of development. It works
fine, but does require some patching here and there.

**Q: There's economic tracking in the town. What role does "economy" play in
the intended gameplay? Is there a resource scarcity/abundance model, or is it
more about tracking what the town has produced? Any plans for trading between
towns or with the player?**

A: For now, the "economy" is intended to help new players understand "why isn't
my villager doing their job?". They can look at the economics screen and see
what items are needed but not available.

I also thought it would be cool if players could eventually see "your village
produced these results today" to showcase the value of maintaining a village.

I also thought this might be useful data for generating random events, like a
"trader" arriving who just happens to have the stuff you need, or "trade
quests" where a valuable prize can be gained by trading 100 of an item when you
*usually* produce 80 in a day—requiring the player to consciously assign 
villagers or optimize routes, etc.

**Q: You mentioned "improve onboarding experience" as a current priority. What
does ideal onboarding look like? Should players discover towns organically, be
guided to them, or create their own? And what's the "aha moment" you want new
players to experience?**

A: Players should discover towns organically (although starting in creative
mode is totally valid). Currently, when a player approaches a town flag for the
first time, they are given an "old journal" which instructs them through most
of the key early-game concepts. I used Patchouli and a set of achievements for
this.

I think there are probably a handful of "aha moments" depending on the player's
playstyle. For example:

- **Adventurer playstyle**: The "aha" might come when they respawn in their
  village after dying and find that the cook has prepared food for them and 
  the armorer and blacksmith have a fresh suit of armor and a sword ready 
  for them.

- **Builder playstyle**: The "aha" might come when an "explorer" villager
  discovers a source of rare wood (that does not actually exist in the real
  game world, but which the gatherers can "go get" when they disappear for
  their gathering runs).

**Q: How do you balance the abstracted gathering (virtual resources, loot
tables) against Minecraft's core loop of players manually exploring and mining?
Is the intent that villages handle the "grind" so players can focus on
building/adventuring?**

A: It's sort of intended to be a "trade-off". Players can choose to build a
village *instead of* searching for resources and crafting. But they are still
required to put in the effort of managing their people and building the
buildings (villagers cannot—and will never—build).

The idea is that it's similar effort to the typical resource gathering but
offers a change of gameplay.

**Q: If a modder wanted to add a completely new job type today, what's the
process? Is it purely JSON, or are there cases where Java code is still
required? And are there job concepts you've wanted to express but couldn't fit
into the current JSON schema?**

A: The real differentiator is the job block. If the job block is some
non-interactable block, then a fully JSON declaration of the ingredients,
tools, work, time, and result would be completely sufficient.

Where it gets interesting is when villager job blocks are blocks that real
players could also interact with (think furnaces, patches of dirt where things
can be planted, crops that can be harvested). For those interesting cases, the
modder would probably specify "air" as the result and add a special rule that
runs during the "post extract" hook—maybe pulling items from a specific slot on
the container block, or adjusting its blockstate in a specific way.

This is one of the reasons why implementing time warp (and integration tests)
for jobs that have special rules is so tricky—they tend to be very coupled to
real Minecraft objects and concepts.

Another example is a special rule that runs after insertion. Questown jobs
don't "actually" insert items into blocks; they just maintain an internal state
that remembers which items were "inserted" by the villager (which get
consumed/removed from the game). But for example, in the instance of the "cook"
job, they use a special rule which uses the "after insert item" hook to
ACTUALLY put the inserted item into a specific slot on a real furnace block.
This means that a cook can insert an item into a furnace and a PLAYER could
take it out, assuming they beat the cook to the furnace—this is intentional.

A key gameplay (and "lore") concept is that **villagers CANNOT stack items in
containers**. This creates an incentive to create additional storage rooms or
participate in the town's item management.

One ambitious early idea was the addition of an "organizer" job. Villagers with
this job would have a unique leveling mechanic where they would be able to
stack up to two items, and maybe even unlock bigger stacks as they leveled up.
They would also be able to be "asked" to put specific items in specific
chests—this was implemented at one time by introducing the "clipboard" item,
where players could request items that would be stored on the NBT of the
clipboard. Placing the clipboard in a chest would cause organizers to see that
chest as a "job site", which needed the "ingredient" of whatever was stored on
the clipboard. This job made extensive use of many `JobPhaseModifier` hooks
(special rules) to achieve this. I'm not sure if it still works though, because
I moved on to other jobs which seemed more relevant to early-game onboarding.

**Q: You've mentioned several features that are broken or incomplete: town
spawning since 1.18->1.19, the organizer job, the multi-town progression
vision. What's the single most important thing to fix or finish before you'd
consider the mod "playable" for a first public release? And what would you cut
entirely if you had to ship tomorrow?**

A: Town spawning is a must. I would actually say we're pretty close to a v1
release. Branch `1.19.2` is really quite stable but it simply doesn't have
working time warp—which I feel is crucial because I don't want to "trap"
players in a town in order to see the benefits.

I also think having a strong suite of integration tests would build a lot of 
confidence in the release.

Beyond these things, adding some basic jobs like the aforementioned "armorer"
for armor and "blacksmith" for weapons. If the town could act as an "outfitter"
that supports adventuring, I think that would be a solid base to release on.

**Q: Looking back, what's the one architectural decision you're proudest of?
And conversely, if you could go back and change one thing from the beginning,
what would it be?**

A: Declarative Jobs were a pretty substantial breakthrough. They opened up a
clear pathway for Questown to be a hugely integrated mod enhanced by dozens of
other mods very easily. Time warp was an exciting "aha" moment, realizing that
I could "simulate" village progress rather than needing to keep a chunk loaded.

If I could go back and change one thing, I probably would have tried to nail
down the onboarding experience as the top priority, even before time warp. I
think I could have had a v1 released a long time ago if I had done that—it's
just not very fun to build.

See Also: sequence-diagrams.md