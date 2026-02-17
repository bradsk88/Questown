[DO NOT MODIFY THIS FILE]

[Follow-up interview covering the work done on the 1.19.2-0.0.11-decup branch.
See also: project-qa.md for foundational project context.]

# Questown Project Q&A — Part 2: The Decoupling Branch

This document captures decisions, lessons, and direction from the work done on
the `1.19.2-0.0.11-decup` branch (63 commits, 615 files changed, ~39K
insertions). The branch spans Warp Revival, QTWorldAccess decoupling,
warp-interleaved hooks, farmer rule integration tests, and extensive bug fixing.

---

## Warp Priority

**Q: This branch represents a massive push on the Warp system — "Warp Revival",
"Warp Redux", and ultimately confirming it works for Crafter:Bowl. In the
original Q&A you described warp as "crucial because I don't want to trap players
in a town." Now that you've actually gotten it working for multiple job types,
has your view of warp's importance changed at all?**

A: No, it's still the same priority.

---

## QTWorldAccess Motivation

**Q: The `QTWorldAccess` abstraction was a big architectural move — replacing
`ServerLevel` with a clean interface across all 7 event records and 6 hook
classes. What specifically triggered this refactoring?**

A: It was motivated by the goal of writing unit tests. While we still continue
to lean heavily on in-game testing, the vision involves a test suite that
renders in-game testing a rare requirement in the future.

---

## Escape Hatch Strategy

**Q: You ended up with a phased approach to the decoupling — farmer rules fully
migrated first, while 8 non-farmer rules still use the `asServerLevel()` escape
hatch. Was this strategy planned from the start, or did it emerge during
development?**

A: It emerged as a useful method for making gradual progress on the
reintroduction of warping. It allows us to stop adding support for new jobs
while still keeping the mod able to compile, run, and play.

---

## Warp-Interleaved Hooks: Design Intent

**Q: The warp-interleaved hooks system addresses global effects like crop growth
that need to happen during warp even though they aren't tied to a specific
villager's job. Do you envision other global effects beyond crop growth that
would use this pattern?**

A: The idea is: if there's a villager who can "tend to" a block or entity, then
we should progress it via the warp. If there's no villager to tend to it, then
it's fine that it does not progress during warp.

---

## Warp/Realtime Convergence

**Q: During this branch's development, you hit several bugs where warp and
realtime behavior diverged — the `degradeTool` bug destroying food items, state
pollution between jobs, cook warp state clobbering. In the original Q&A you
described an ideal where warp shares 99% of its logic with realtime. After this
experience, do you still believe that's achievable?**

A: Yes. The core of the job should use the same code regardless of realtime or
warp. The addition of the interleaved warp effect for special rules represents a
departure but a necessary one that should be used sparingly.

---

## Test Confidence

**Q: The `TestWorldAccess` and `FarmerRuleTest` infrastructure represents the
first real integration tests for special rules — 12 tests covering all 5 farmer
rules, running against an in-memory world with no Minecraft dependency. How
confident are you that these tests catch the kinds of bugs that previously
required in-game testing?**

A: Given the amount of in-game testing done on this branch, not very confident
yet that the unit tests are covering the important logic. But the belief is that
we will get there.

---

## Abstraction Philosophy

**Q: You consolidated logic into the `DeclarativeJobTicker` package with an
explicit preference for concrete types. At the same time, you introduced
`QTWorldAccess` — which is an abstraction. How do you reconcile these two
philosophies?**

A: **Concrete types for logic, abstractions for access.**

---

## Wrapping Minecraft Types

**Q: The `QTToolAction` enum was introduced to avoid a runtime bug where Forge
`ToolAction` string names didn't match the constant field names. Do you see this
pattern — wrapping Minecraft/Forge types in Questown-owned enums — as something
that should expand to other areas?**

A: Only when necessary. Decoupling from Minecraft concepts is important.

---

## Global Rules and Villager Presence

**Q: The warp-interleaved hooks are declared as `"global"` rules in job JSON
files, collected from active villagers, and deduplicated. This means crop growth
during warp only happens if at least one farmer is assigned. Was that a
deliberate design choice — tying world effects to villager presence?**

A: Yes. It's the idea that villagers "tend to" things while the player is gone.
There's no expectation to add a "true global" effect at the moment, but maybe
there will be a reason someday.

---

## In-Game Debugging

**Q: Beyond the unit test goal, have you thought about better tooling for
in-game debugging — like a debug overlay, an in-game inspector for villager
state, or structured logging?**

A: The best method of adding transparency hasn't been decided yet. When it was
just manual development, the debugger was used to trace through things like
warp, utilizing unit tests to run scenarios whenever possible. Now that agents
are being used, debug logs seem useful but may not be the answer. Once the warp
work is done and v1 preparation begins, some refactoring to make the code
itself easier to intuit around is planned.

---

## Documentation Shift

**Q: This branch introduced substantial documentation — `project-qa.md`,
`sequence-diagrams.md`, the decoupling doc, decision records, bug writeups. This
is a big shift from the original Q&A where "no significant documentation
currently exists." What prompted the change?**

A: The primary purpose of these docs is to provide agents with the context they
need to be helpful. The codebase is quite complex, so agents tend to burn
through tokens in the process of crawling around trying to understand.

---

## Working with AI Agents

**Q: You've been working heavily with AI agents throughout this branch. What has
surprised you most about the experience?**

A: The most surprising thing is how unintuitive some of the code is. Before
working with agents, the codebase felt big but also self-documenting. Working
with agents has revealed a lot of dark corners where a lot of mental context is
required to understand why things are the way they are.

---

## Codebase Complexity Trend

**Q: In the original Q&A, you identified "too many abstractions" as a top source
of complexity. Net-net, do you feel the codebase is simpler or more complex than
when the branch started?**

A: Hopefully simpler. The abstractions added are thin and simple, and the
consolidation has made the logic behave more like a single unit, rather than a
cobbled-together collection of sub-units. The plan is to continue in this
direction going forward.

---

## Preventing Warp/Realtime Divergence

**Q: The `degradeTool` bug — where non-damageable items were destroyed during
warp because the warp path reimplemented MC mechanics differently — is a
textbook divergence bug. What's the strategy for preventing this class of bug?**

A: Integration testing and consolidation of logic.

---

## Path to V1

**Q: With the warp system now functional for several job types and the
decoupling infrastructure in place, what's the realistic path to v1?**

A: Next steps are:
1. Make sure time warp works for the majority of existing jobs
2. Add the outfitter jobs (armorer, blacksmith)
3. Play-test the onboarding tutorial
4. Target Pam's HarvestCraft as one of the first cross-mod integrations

---

## Retrospective

**Q: Looking back at the entire arc of work on this branch, what's the one thing
you'd do differently if you were starting over? And what are you most satisfied
with?**

A: The in-game test command was a big help. It should have been added sooner.

---

See Also: project-qa.md, sequence-diagrams.md, special-rules-decoupling.md
