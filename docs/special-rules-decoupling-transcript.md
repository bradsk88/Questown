# Special Rules Decoupling Discussion

A conversation about decoupling Special Rules from Minecraft code to
support time warp simulation and testing.

---

## Setting the Context

**Developer A:** I've reviewed the Special Rules system. What I'm seeing is a
`JobPhaseModifier` abstract class that serves as the extension point, a registry
(`SpecialRulesRegistry`), and about 20+ concrete implementations. The coupling to
Minecraft is pervasive - the event records like `BeforeExtractEvent` contain
`ServerLevel`, `BlockPos`, `ItemStack` directly. The implementations like
`HarvestCropSpecialRule` call Minecraft APIs for block manipulation.

Before we dive into solutions, let me understand your goals better. When you say
"decouple from Minecraft code," what's the end state you're envisioning? Are we
talking:

1. **Testability** - Being able to unit test rule logic without spinning up Minecraft?
2. **Portability** - Making these rules work on a hypothetical non-Minecraft platform?
3. **Clean architecture** - Separating "what" from "how" even if Minecraft is always the target?

Which of these is driving this refactor?

**Developer B:** I mentioned my reasoning in project-qa. Did you see that yet?

**Developer A:** *(reviews project-qa.md)*

Got it. The context is clear now. The core driver is the **Warp system**. You want
realtime and warp to share 99% of their logic, differing only in where they read/write.
Special Rules are a blocker because they're deeply coupled to live Minecraft - pulling
from container slots, adjusting blockstates, checking if trees can grow at positions.

For Warp to simulate these rules, it needs to run the same logic against an in-memory
representation of the world state, not live `ServerLevel` and `BlockPos`.

So the goal isn't "remove Minecraft" - it's **abstract the world interface** so rules
can operate against either:
- The real Minecraft world (realtime)
- A simulated world snapshot (warp)

Looking at what rules actually do, I see a few categories:

1. **Block state queries** - "Is this a full-grown crop?", "Can a tree grow here?"
2. **Block state mutations** - "Set crop age to 0", "Till this dirt to farmland"
3. **Container interactions** - "Put item in slot 0", "Take item from slot 2"
4. **Entity/pose manipulation** - "Make villager lie down"
5. **Loot table evaluation** - "What drops from this crop?"

Each of these needs a Minecraft implementation and a simulated implementation.

Before I propose a pattern - how much of the world state do you actually need to
track for Warp? Is it just "what's in containers" and "what blocks exist at job sites"?
Or do you need to track full block states across the whole town?

---

## Scoping the Problem

**Developer B:** "What's in containers" is good. "What blocks" is probably also
important, but we can take a single look at the town at the beginning of time travel
because the time travel is expected to fully run and complete within a couple of
ticks - meaning any "real" world changes are unlikely to happen (not impossible, but
"unlikely" is acceptable for us).

One of the more interesting examples of where this becomes challenging is "wheat".
Because wheat grows at a semi-random pace during real gameplay, but during time travel,
we would want to faithfully simulate real growth and probably even apply the
state-of-growth back to the world state after the time warp. Does that make sense?

**Developer A:** Makes total sense. So the warp isn't read-only - it's a full
simulation that produces a **delta** to apply back to the world. The wheat example
is perfect because it shows both directions:

1. **Read**: Snapshot wheat age at warp start (say, age 3)
2. **Simulate**: During warp, farmer "harvests" when age hits 7, resets to 0, plants
   again, grows to age 4 by warp end
3. **Write**: Apply age 4 back to the real block, put harvested wheat in real container

This means the abstraction needs to be **read-write** with deferred writes that get
applied after warp completes.

Here's where it gets architecturally interesting. Two approaches I see:

**Approach A: World Interface**
Create an interface like `IWorldAccess` that both `ServerLevel` and `WarpWorldState`
implement. Rules operate against this interface.

```java
interface IWorldAccess {
    int getCropAge(BlockPos pos);
    void setCropAge(BlockPos pos, int age);
    ItemStack extractFromContainer(BlockPos pos, int slot);
    // etc.
}
```

**Approach B: Command Pattern**
Rules don't execute mutations directly. They return "intents" or "commands" that
describe what should happen. A separate executor interprets these for realtime vs warp.

```java
// Rule returns:
new SetCropAgeCommand(pos, 0)
new ExtractItemCommand(pos, slot)

// Executor applies to real world or buffers for warp
```

Approach A is simpler but requires duplicating a lot of Minecraft logic in the warp
implementation. Approach B is more work upfront but gives you a clean audit trail of
what happened during warp.

Given your goal of 99% shared logic between realtime and warp - which pattern feels
right to you? Or is there a third option I'm missing?

---

## The Two-Tier System

**Developer B:** Let's talk it through a bit more before we start solutioning. Here's
something else I've been considering: It's important for the core jobs of Questown to
be 1) testable to protect against regressions and 2) warpable to provide gameplay
benefits to the players. Our interface, which will provide a lot of very job-specific
functions like "get crop growth" will aide in that goal.

But remember that the vision for this mod is for other mod-builders to integrate with
it. As has been my experience, integrating directly against Minecraft is the easiest
way to add a new special rule that works, so we might want to leave that door open -
maybe by allowing modders to register EITHER new warpable/testable special rules OR
Minecraft-integrated special rules and making it clear that the latter will not
benefit from warp and that they will need to submit a request to the main Questown
project if they need functions that are not available via the QT world-analog. What
do you think?

**Developer A:** That's a really pragmatic take, and honestly it solves one of the
biggest problems with abstraction layers - they become barriers to adoption.

If I'm a modder adding, say, a "Thaumcraft Essentia Extractor" job, I don't want to
wait for Questown to add `getEssentiaLevel()` to some interface. I just want to call
the Thaumcraft API directly and get my thing working.

So you're proposing a **tiered system**:

**Tier 1: "Questown-native" rules**
- Use the abstracted world interface
- Fully testable, fully warpable
- Core Questown jobs live here
- Limited to functions QT provides

**Tier 2: "Minecraft-native" rules**
- Direct Minecraft/mod API access
- Work in realtime only
- Skipped (or gracefully degraded) during warp
- Zero friction for modders

And critically - there's a **promotion path**. If a modder's rule becomes popular or
they need warp support, they can submit a request: "Hey, I need `getEssentiaLevel()`
in the QT world interface." You add it, they migrate their rule to Tier 1.

A few follow-up questions on the mechanics:

1. **Warp behavior for Tier 2 rules**: When warp encounters a job with Minecraft-native
   rules, does it skip the rule entirely? Skip the whole job? Or mark the job as
   "cannot simulate" and pause that villager's progress until realtime resumes?

2. **Mixed rules**: Can a single job have both Tier 1 and Tier 2 rules? If so, warp
   would run the Tier 1 rules and skip Tier 2?

3. **Degradation transparency**: Should modders explicitly declare "this is
   Minecraft-native" or should the system infer it based on which interface they
   implement?

---

## Clarifying the Mechanics

**Developer B:**
1. I think, presently, the job finishes in a sort of in-between state, where (using
   farmers as an example) the farmer's hoe might get worn down, but the actual pulling
   of drops from the crop and changing of crop growth level does not happen. Another
   example would be plant seeds, I think the seeds get consumed during warp, but they
   don't get PLANTED because that's a special rule and I think the warper does not
   apply special rules.

2. We probably should not allow mixing. If we detect a job with mixed rules, we should
   probably log an error during server start-up and during warp so that no one is
   confused when it doesn't work properly.

3. It should be inferred.

**Developer A:** That inconsistent state is exactly the kind of bug that's hard to
catch without integration tests - the farmer "worked" but the world doesn't reflect it.
Seeds vanish into the void.

So to summarize the behavioral model:

**Tier 1 jobs (all rules are QT-native):**
- Warp simulates fully
- World state gets written back after warp
- Integration testable

**Tier 2 jobs (any rule is Minecraft-native):**
- Warp skips the job entirely (or logs a warning and holds the villager in a "waiting"
  state)
- Only progresses in realtime
- Modder accepts this tradeoff for flexibility

**Mixed detection:**
- At server startup: scan all registered jobs, check their rules, log errors for any
  mixing
- During warp: if a job would be simulated but has Minecraft-native rules, log warning
  and skip

For inference, I'm thinking the class hierarchy makes this natural:

```
JobPhaseModifier (existing base - becomes "Minecraft-native" tier)
    |
    +-- WarpableJobPhaseModifier (new base - "QT-native" tier)
            |
            +-- HarvestCropSpecialRule
            +-- TillWorkspotSpecialRule
            +-- etc.
```

Modders extending `JobPhaseModifier` directly get Tier 2. Modders extending
`WarpableJobPhaseModifier` get Tier 1 but must use the QT world interface.

---

## Designing QTWorldAccess

**Developer A:** Now here's the big architectural question: **What does the QT world
interface actually look like?**

Looking at what your current Tier-1 candidates need:
- Crop age read/write
- Block type checks (is this farmland? is this a composter?)
- Container slot read/write
- Loot table evaluation
- Block removal/placement

Do you see this as one big interface? Or domain-specific interfaces like `CropAccess`,
`ContainerAccess`, `BlockAccess`? And should it use Minecraft types (like `ItemStack`)
or pure QT types?

**Developer B:** Let's see if we can rule any of those out as problems. For example,
"gatherer" works with the current warper implementation AND uses loot tables to
generate their loot.

**Developer A:** Good call - let's not solve problems that don't exist.

So loot tables already work in warp context. That makes sense actually - loot tables
are data-driven and Minecraft's loot table API probably doesn't require a live world
to evaluate, just a context you can construct.

Let me think about what that implies for the others:

- **Loot table evaluation** - Already works (gatherer proves it)
- **Container slot read/write** - Does warp currently interact with real containers
  at all, or does it work purely against the villager's internal "held items" / town
  inventory state?
- **Block type checks** - These feel like they'd be snapshot-able at warp start.
  "Is position X,Y,Z farmland?" doesn't change during a 2-tick warp.
- **Crop age read/write** - This is the wheat problem you mentioned. Needs simulation.
- **Block removal/placement** - Like planting seeds or harvesting. Needs to be deferred
  and applied post-warp.

What else already works in warp that I might be assuming is broken? Does the "consume
ingredients from inventory" and "produce results to inventory" loop work, and it's
just the special rule hooks that are skipped?

**Developer B:** Yes, I think you're right about containers. We maintain in-memory
lists which represent "held" and "town" storage. So that one is likely already solved.

Also, there's an "is job block" check that gets handled by the warper via delegation
to a class that CAN interact with minecraft (or, in the case of unit tests, is mocked)
but there's still an interesting problem there in that special rules can supplement or
replace this check (e.g. tree planter simulates tree growth to decide if a block of
dirt is REALLY a valid place to work).

Crop age needs simulation, I agree. But there's probably some less-specific approach
we can take like "get numerical value from block state, max value, and rate of natural
change" - granted, that last one might not be as readily available as we would like
because I think it's a "random chance of growth" rather than a rate, but we could even
get that random chance and run it ourselves.

Thinking about that more, there's probably no problem with ACTUALLY writing blockstate
to the world during warp (maybe thousands of times in a single game tick) as long as
we delegate to someone who encapsulates that away from the warper.

Block removal and placement is less about actual block manipulation and more about
using items on blocks (seeds on dirt and hoes on wheat) and having the world react
properly to that item-use. We should think very hard about the best way to solve this.

Consume ingredients and produce results both work well.

**Developer A:** This is sharpening nicely. Let me recap what's actually solved vs.
what needs work:

**Already working:**
- Loot tables
- Container/inventory interaction (in-memory)
- Consume ingredients / produce results
- "Is job block" check (delegated)

**Needs abstraction:**
1. **Block state queries** - You're right that a generic approach is better. Something
   like `getBlockStateProperty(pos, propertyName)` returning an int, plus metadata
   about max value and growth probability. Avoids crop-specific methods.

2. **Special rules that augment job block checks** - Tree planter simulating growth.
   The rule itself contains the coupling.

3. **Item-on-block interactions** - The interesting one.

On that third point - you said there's probably no problem with actually writing
blockstate during warp. That's a different framing than I was assuming.

If we *can* touch the real world during warp, then the problem isn't "how do we
simulate item use" - it's "how do we encapsulate world access so the warper doesn't
directly depend on Minecraft types."

So the pattern might be:

```
Warper -> QTWorldAccess (interface) -> MinecraftWorldAccessImpl -> ServerLevel
```

The warper only knows about `QTWorldAccess`. The Minecraft impl does the real
mutations. For tests, you swap in a mock or in-memory impl.

The "use item on block" abstraction could be something like:

```java
interface QTWorldAccess {
    UseItemResult useItemOnBlock(ItemStack item, BlockPos pos);
}
```

Where `UseItemResult` captures what happened - block changed, item consumed, etc. -
in QT terms.

Does that align with your thinking? Or were you heading somewhere different with
"delegate to someone who encapsulates it"?

**Developer B:** I can be convinced.

---

## Next Steps

Developer B requested a document outlining all jobs using special rules with proposed
`QTWorldAccess` methods. This analysis was captured in `special-rules-decoupling.md`.

Key decisions made:
1. Use a two-tier system (QT-native vs MC-native rules)
2. No mixing of tiers within a single job
3. Tier detection is inferred from class hierarchy
4. Start with `ItemStack` type, use `<POS>` generic for positions
5. Immediate writes during warp are acceptable
6. Partial warp completion is considered "success"