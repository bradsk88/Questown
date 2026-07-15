# Questown Documentation Index

## Architecture & Decisions

- **[special-rules-decoupling.md](special-rules-decoupling.md)** — Plan for decoupling special rules from Minecraft code to support time warp simulation and testing. Lists jobs using special rules and proposes QTWorldAccess methods.
- **[special-rules-decoupling-transcript.md](special-rules-decoupling-transcript.md)** — Conversation transcript from the decoupling discussion.
- **[decision-declare-and-collect-global-rules.md](decision-declare-and-collect-global-rules.md)** — Global rules are declared locally by jobs but collected and executed globally during warp.
- **[decision-proportional-deltas.md](decision-proportional-deltas.md)** — Use proportional time deltas rather than assuming fixed-frequency ticking in warp hooks.
- **[decision-qttoolaction-closed-enum.md](decision-qttoolaction-closed-enum.md)** — QTToolAction is a compile-time-safe closed enum limited to vanilla tool actions.
- **[decision-silent-world-access.md](decision-silent-world-access.md)** — Warp uses a silent world access variant that suppresses sound effects while keeping other interactions real.
- **[sequence-diagrams.md](sequence-diagrams.md)** — Mermaid sequence diagrams for complex systems.
- **[project-qa.md](project-qa.md)** — AI-conducted interview documenting unspoken design decisions.

## Resolved bugs (verify before beta cut)

- **[bugs/resolved/eating-dine-at-time-fullness.md](bugs/resolved/eating-dine-at-time-fullness.md)** — `eating/dine_at_time` villager starved to -16%: `tryGiveItems` skipped `postExtractHook` (and thus `HUNGER_FILL`) for effect/knowledge results. Verify with the `eating` autotest category.
- **[bugs/resolved/cook-warp-lower-yield-than-realtime.md](bugs/resolved/cook-warp-lower-yield-than-realtime.md)** — Cook warp produced ~56% of realtime cooked-beef yield. Verify with `/_qtdev test cook 10000 destroy`.
- **[bugs/resolved/warp-advancer-tool-only-off-by-one.md](bugs/resolved/warp-advancer-tool-only-off-by-one.md)** — Advancer filled 5/6 inventory slots for tool-only jobs (gatherer×2, hunter, miner). Verify with `/_qtdev test` per job.
- **[bugs/resolved/quest-flavor-test-cutoff.md](bugs/resolved/quest-flavor-test-cutoff.md)** — Wooden-sword quest flavor text overflowed the quest card and overlapped icons. Verify by opening the quests UI in-game.
- **[bugs/resolved/chicken-sunset-chest-bit.md](bugs/resolved/chicken-sunset-chest-bit.md)** — `F2_chest_spawn_after_campfire` sunset chest never spawned: chest target took the player's Y (off the floor) and the arrival radius was tighter than the pathfinder's stopping accuracy. Verify with the chicken autotest track.

## Conventions / background

- **[conventions/realtime-job-cycling.md](conventions/realtime-job-cycling.md)** — How villagers cycle through jobs in real-time using shuffled selection (and the "cycle drag" players sometimes observe with cook).

## Solutions

`docs/solutions/` — documented solutions to past problems (bugs, conventions, design patterns, best practices), organized by category with YAML frontmatter (`module`, `tags`, `problem_type`, `component`, `severity`). Relevant when implementing or debugging in documented areas.

- **[solutions/conventions/prefer-compat-util-renderutil-wrappers.md](solutions/conventions/prefer-compat-util-renderutil-wrappers.md)** — Before calling a raw Minecraft API for a common integration (text split, components/styling, tooltips text, sounds, item names), scan `mc/Compat`, `mc/Util`, `gui/RenderUtil` first and reuse the wrapper (e.g. `Compat.splitText` not `font.split`, `Compat.translatableStyled` not `.withStyle`). Add one to `Compat` if none fits.
- **[solutions/conventions/gui-layout-flow-and-linter.md](solutions/conventions/gui-layout-flow-and-linter.md)** — Building screens: use flow layout (compute each block's Y from the real wrapped-text height above it, never hardcode), give wrapped text its full width, prefer tooltips for descriptions. Verify with the dev-only `gui-lint` oracle — open the screen and confirm `[gui-lint] <Screen> — OK, no layout violations`. Read this before touching any `gui/` screen.
- **[solutions/conventions/setblockandupdate-side-effect-bypass-2026-04-24.md](solutions/conventions/setblockandupdate-side-effect-bypass-2026-04-24.md)** — `Level.setBlockAndUpdate` bypasses item-use side-effects (welcome-mat registration, sign→job-board conversion, 2-high door upper half). When test harness code places these blocks, mirror the side-effect manually or place the target block directly.
- **[solutions/design-patterns/cross-scenario-entity-pollution-two-layer-fix-2026-04-24.md](solutions/design-patterns/cross-scenario-entity-pollution-two-layer-fix-2026-04-24.md)** — Sequential autotest scenarios sharing one `ServerLevel` need both a wide `discard()`-based teardown sweep AND an `ownerFlagPos` filter on read-side queries to prevent cross-scenario entity pollution. Either layer alone is insufficient.
- **[solutions/best-practices/verify-brigadier-arg-order-and-flag-bit-semantics-2026-04-24.md](solutions/best-practices/verify-brigadier-arg-order-and-flag-bit-semantics-2026-04-24.md)** — Verify `RunCommand` strings against the actual Brigadier `register()` chain (left-to-right traversal) before authoring blueprints. Verify flag-bit semantics against the production handler, not the bit name — bits can be repurposed as gates.

## TODO / Future Work

- **[todo/farmer-cook-code-redundancy.md](todo/farmer-cook-code-redundancy.md)** — ~~Identify and consolidate overlapping code between farmer and cook jobs for accessing world blocks.~~ RESOLVED by QTWorldAccess abstraction.
- **[todo/special_rule_result.md](todo/special_rule_result.md)** — Support special result values in job JSON instead of `minecraft:air` for jobs fully dependent on special rules.
- **[todo/test-command-improvement.md](todo/test-command-improvement.md)** — ~~Use existing flag position as origin for `/qt test` command instead of player position.~~ DONE
