# Questown Documentation Index

## Design principles

- **[design-principles.md](design-principles.md)** — Standing design rules: unlock ≠ workability (`NO_SUPPLIES`/`NO_JOBSITE`/`NO_SPACE` are distinct), quests are division-of-labor pedagogy (coal = player supplies, mutton = town produces), the self-sufficiency test seeds player-inputs and must not read a missing-player-input as a bug, and onboarding communicates roles + trims aids that don't change the player's next action. Read before diagnosing townie stalls or before adding onboarding beats.

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
- **[bugs/room-scan-server-stall.md](bugs/room-scan-server-stall.md)** — "waiting for server" stall was a villager wander retry loop, not room detection: a town with no wanderable room re-ran a full interior scan per stuck villager per tick. Fixed by deferring the retry and logging the failure reason once. Verify with a play session in a town missing a valid wander room.

## Diagnosed bugs (fix not yet applied)

- **[bugs/campfire-sleep-multiplayer-softlock.md](bugs/campfire-sleep-multiplayer-softlock.md)** — Two players sleeping via campfire-wand never reach morning: vanilla skips night only when *every* player holds `sleepCounter >= 100` simultaneously, and re-clicking the campfire resets a player's counter. Root-caused from 1.19.2 bytecode; fix direction noted (no-op a re-click while already sleeping).

## Conventions / background

- **[conventions/realtime-job-cycling.md](conventions/realtime-job-cycling.md)** — How villagers cycle through jobs in real-time using shuffled selection (and the "cycle drag" players sometimes observe with cook).

## Build environment & version migration

- **`../AGENTS.md` ("Build environment" section)** — the JDK-17 Gradle pin, `jar tf` (not `unzip`) to locate classes in a jar, the javap caveats, and the compile-loop migration discipline. Read before running `./gradlew` or porting the mod to a new MC version.
- **skill `questown-mc-version-migration`** — the ordered loop to migrate Questown + RoomRecipes to a new MC version (probe deps -> RoomRecipes -> publish -> Questown -> compile loop) and how the strangler/`Compat` seam keeps the change centralised.

## Solutions

`docs/solutions/` — documented solutions to past problems (bugs, conventions, design patterns, best practices), organized by category with YAML frontmatter (`module`, `tags`, `problem_type`, `component`, `severity`). Relevant when implementing or debugging in documented areas.

- **[solutions/conventions/prefer-compat-util-renderutil-wrappers.md](solutions/conventions/prefer-compat-util-renderutil-wrappers.md)** — Before calling a raw Minecraft API for a common integration (text split, components/styling, tooltips text, sounds, item names), scan `mc/Compat`, `mc/Util`, `gui/RenderUtil` first and reuse the wrapper (e.g. `Compat.splitText` not `font.split`, `Compat.translatableStyled` not `.withStyle`). Add one to `Compat` if none fits.
- **[solutions/conventions/gui-layout-flow-and-linter.md](solutions/conventions/gui-layout-flow-and-linter.md)** — Building screens: use flow layout (compute each block's Y from the real wrapped-text height above it, never hardcode), give wrapped text its full width, prefer tooltips for descriptions. Verify with the dev-only `gui-lint` oracle — open the screen and confirm `[gui-lint] <Screen> — OK, no layout violations`. Read this before touching any `gui/` screen.
- **[solutions/conventions/setblockandupdate-side-effect-bypass-2026-04-24.md](solutions/conventions/setblockandupdate-side-effect-bypass-2026-04-24.md)** — `Level.setBlockAndUpdate` bypasses item-use side-effects (welcome-mat registration, sign→job-board conversion, 2-high door upper half). When test harness code places these blocks, mirror the side-effect manually or place the target block directly.
- **[solutions/conventions/spread-heavy-work-across-ticks.md](solutions/conventions/spread-heavy-work-across-ticks.md)** — When one computation costs more than a game tick, spread it across ticks with the idiom: resumable queue + per-tick pump + occupy-the-tick-while-in-progress + a *bound*. The two instances (`LevelRoomDetector`/`MultiLevelRoomDetector` count-bound vs `TownPossibleWork` time-bound) share the concept, not a mechanism — do not extract a shared abstraction. Read before adding a third variant; choose the bound (count vs time) by whether one unit's cost is roughly constant.
- **[solutions/design-patterns/cross-scenario-entity-pollution-two-layer-fix-2026-04-24.md](solutions/design-patterns/cross-scenario-entity-pollution-two-layer-fix-2026-04-24.md)** — Sequential autotest scenarios sharing one `ServerLevel` need both a wide `discard()`-based teardown sweep AND an `ownerFlagPos` filter on read-side queries to prevent cross-scenario entity pollution. Either layer alone is insufficient.
- **[solutions/best-practices/verify-brigadier-arg-order-and-flag-bit-semantics-2026-04-24.md](solutions/best-practices/verify-brigadier-arg-order-and-flag-bit-semantics-2026-04-24.md)** — Verify `RunCommand` strings against the actual Brigadier `register()` chain (left-to-right traversal) before authoring blueprints. Verify flag-bit semantics against the production handler, not the bit name — bits can be repurposed as gates.

## TODO / Future Work

- **[todo/special_rule_result.md](todo/special_rule_result.md)** — Support special result values in job JSON instead of `minecraft:air` for jobs fully dependent on special rules.
- **[todo/test-command-improvement.md](todo/test-command-improvement.md)** — ~~Use existing flag position as origin for `/qt test` command instead of player position.~~ DONE
- **[todo/room-walkable-vs-reachable.md](todo/room-walkable-vs-reachable.md)** — Deferred: count a room's interior by whether a villager can *reach* a cell (not just stand in it), so a room over half floating furniture at head height would pass. We kept the 0.5 fraction and pinned it with two regression tests instead.
