---
title: "Verify Brigadier arg order and flag-bit semantics against source, not intuition"
date: 2026-04-24
category: docs/solutions/best-practices/
module: autotest blueprints / command authoring
problem_type: best_practice
component: testing_framework
severity: high
applies_when:
  - Authoring a RunCommand string in an autotest blueprint
  - Reading a Brigadier command registration to reconstruct its syntax
  - Asserting on a flag-bit value whose name may not match its production role
tags: [brigadier, command-tree, autotest, flag-bits, arg-order, semantics]
---

# Verify Brigadier arg order and flag-bit semantics against source, not intuition

## Context

Two bugs appeared in the same autotest blueprint (`skip_chicken_command`) and were
fixed together in commit `85eb26da`. Both stem from the same root habit: reading
a name or a familiar pattern and assuming its meaning rather than verifying against
the production source.

**Bug 1 — wrong command string.** The blueprint sent:

```
/qt flag place_above 0 63 0 skip-chicken
```

The author assumed `verb → args` ordering (literal action word, then coordinates).
Brigadier registered the tree in a different order, so the parser rejected the
command at runtime and the scenario received a parser error instead of executing.

**Bug 2 — wrong assertion value.** The blueprint asserted
`chicken-ever-spawned=false` after placing the flag, reasoning: "no chicken
actually spawned, so the 'ever spawned' bit should be false." But the production
handler sets the bit to `true` to gate future spawn attempts — the bit is
repurposed as a "chicken-ineligible" flag, not a literal record of whether a
chicken physically appeared.

Both bugs produced silent-looking failures: the scenarios ran to completion but
asserted on wrong outcomes.

## Guidance

### Rule 1 — Read the Brigadier `register()` chain end-to-end before writing a command string

Brigadier parses left-to-right in the exact order arguments and literals appear
in the `.then()` chain. Verb-then-coordinates is intuitive but is not the only
valid ordering; the tree decides.

The actual registration in
`src/main/java/ca/bradj/questown/commands/FlagCommand.java`:

```java
src.register(
    Commands.literal("qt").then(       // 1. "qt"
        subCmd                          // 2. "flag"
            .requires(AddExperienceCommand::isCreative)
            .then(posArg                // 3. <BlockPos pos>
                .then(subSubCmd         // 4. "place_above"
                    .executes(css -> setBlock(...))
                    .then(Commands.literal("skip-chicken").executes(...))  // 5. optional
                )
            )
    )
);
```

Tree path: `qt → flag → <pos> → place_above → [skip-chicken]`

Correct command: `/qt flag 0 63 0 place_above skip-chicken`

Wrong command (rejected at parse time): `/qt flag place_above 0 63 0 skip-chicken`

**Procedure:** When writing any `RunCommand` value in a blueprint, open the
relevant `*Command.java` file, locate `register()`, and read every `.then()` in
nesting order. Write the command string as you traverse the tree, left-to-right,
outermost-to-innermost.

### Rule 2 — Verify flag-bit semantics against the production handler, not the bit name

Bit names are written at definition time. If the bit is later repurposed its name
may no longer reflect what a `true` or `false` value means in practice.

The production handler in `FlagCommand.java`:

```java
private static void markCommandPlacedFlagAsChickenIneligible(
        CommandSourceStack source,
        BlockPos flagPos
) {
    if (!(source.getLevel().getBlockEntity(flagPos) instanceof TownFlagBlockEntity flag)) {
        return;
    }
    flag.setChickenEverSpawned(true);      // sets true, not false
    flag.setChickenRotationDetected(true);
    CompoundTag tag = Compat.getBlockStoredTagData(flag);
    flag.writeTownData(tag);
    flag.setChanged();
}
```

The comment above the method explains the repurposing:

> Command-placed flags skip the helper-chicken arc entirely: only worldgen-placed
> flags satisfy the arc's scaffolding assumptions. The BE's onLoad already ran
> initializeFreshFlag(false) which sets the chicken bits to their defaults via
> each InitPair's onFlagPlace consumer. Set our bits AFTER that runs, so the
> command's intent survives the InitPair defaults.

`setChickenEverSpawned(true)` does not mean "a chicken actually appeared"; it
means "treat this flag as having exhausted its chicken eligibility." The bit is
a gate, not a ledger.

**Procedure:** Before asserting any flag-bit value in a blueprint, find the
handler that runs after the command and read what value it writes and why. Never
derive an expected value solely from the field name.

## Why This Matters

Both bugs produce failures that look like environment or timing problems rather
than authoring errors: the command silently fails or the assertion fires on a
value that seems wrong. Debugging time compounds because the failure is in the
blueprint, not in production code.

The autotest suite reached `41/41 passed` only after both fixes were applied
together — a single blueprint with two independent authoring mistakes.

## When to Apply

- Any time you write a `RunCommand` string in a blueprint or test scenario.
- Any time you assert on a named flag bit, NBT tag, or block-entity property
  whose meaning might differ from what its accessor name implies.
- When reviewing blueprint PRs that introduce new `/qt` or `/_qtdev` commands.

## Examples

### Reading the tree (annotated)

```
Commands.literal("qt")           -> token 1: "qt"
  .then(Commands.literal("flag") -> token 2: "flag"
    .then(posArg                 -> token 3: <x y z>
      .then(Commands.literal("place_above")  -> token 4: "place_above"
        .then(Commands.literal("skip-chicken"))  -> token 5 (optional)
      )
    )
  )
```

Read nesting depth (outermost first) to reconstruct the command string:
`qt flag <x y z> place_above [skip-chicken]`

### Blueprint before (both bugs present)

```java
new RunCommand("/qt flag place_above 0 63 0 skip-chicken", 10),
// ...
.expectation(ChickenArcExpectation.builder()
        .chickenSpawned(false)
        .flagBits(Map.of("chicken-ever-spawned", false))   // wrong: handler sets true
        .build())
```

### Blueprint after (both bugs fixed)

```java
new RunCommand("/qt flag 0 63 0 place_above skip-chicken", 10),
// ...
.expectation(ChickenArcExpectation.builder()
        .chickenSpawned(false)
        .flagBits(Map.of("chicken-ever-spawned", true))    // correct
        .build())
```

## Related

- `src/main/java/ca/bradj/questown/commands/FlagCommand.java` — canonical Brigadier registration
- `src/main/java/ca/bradj/questown/commands/test/ChickenArcBlueprintRegistry.java` — `skipChickenCommand` scenario
- Commit `85eb26da` — fix that resolved both bugs
- `docs/bugs/warp-ignores-night.md` — another example of a named concept (warp/sleeping) whose implementation diverges from its surface name
- Parent plan: `docs/plans/2026-04-23-001-feat-chicken-arc-agent-automation-plan.md`
