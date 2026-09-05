---
status: accepted
---

# Gold (blessed) Blocks of Progress guarantee a root job; the flag stores real stacks

**Context.** Blocks of Progress used to be fully fungible: a villager earning a BOP deposited
it, and the flag held a bare `int` count, because nothing about a BOP ever mattered past "how
many". Spending one on a root job change always picked a **random** root. Players wanted
composition control, and the codebase already had a dormant hook for it: `BlockOfProgress.bless`,
the `menu.unlock_root.insert_bop_blessed` confirm text, and a `isBlessed` check in the job-change
menu — none of it wired to anything (the bless writer had no callers).

**Decision.**

- A deposited BOP is **blessed with the root job that earned it**
  (`TownFlagBlockEntity` blesses the stack in the `hasBlockOfProgress` hand-off; NBT: `blessed`
  plus a `JobID` tag). The item renders as **Gold Block of Progress** with secondary tooltip
  text "Guaranteed to change job to \<root\>".
- The flag stores **real stacks** (`bopStacks`, FIFO) instead of a count, so bless NBT survives
  the round trip earn → deposit → collect. Serialization keeps a legacy `count` migration path.
- Spending a gold BOP on a root job change **forces that root**, applied at `handleMorning`
  **before** the overnight quest override (a spend is the newer, more explicit intent).
  Guarantees persist (`guaranteed_roots`) so a BOP spent a night before a save/quit still lands.
- The flag **sparkles cyan** while it holds any BOP (`DustParticleOptions`, was green
  `HAPPY_VILLAGER`); a full flag keeps the red spinning cube warning.

**Consequences.** Root changes are now a menu of "random" (plain BOP from tutorial/test grants)
versus "guaranteed" (earned BOPs). The 64-slot cap, the `flag/bop_boundary_63` and
`flag/bop_full_64` autotest scenarios, and the latch-clearing semantics are unchanged; the two
scenarios run green against the stack-backed storage.
