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

## Bugs

- **[bugs/cook-warp-state-clobbering.md](bugs/cook-warp-state-clobbering.md)** — (Fixed) Cook sub-jobs clobber each other's `processingState` during warp because they share a furnace BlockPos and only run one step per tick. Fixed by looping each sub-job to completion.
- **[bugs/always-consider-warp-monopoly.md](bugs/always-consider-warp-monopoly.md)** — `always_consider` jobs monopolize warp selection, starving production jobs like `cook/beef`. Detailed investigation of 4 root causes in job selection layers.
- **[bugs/cook-global-rule-dropped.md](bugs/cook-global-rule-dropped.md)** — Cook's global warp rule (`furnace_smelt_warp`) is lost when changing job phases. Medium severity, not yet affecting gameplay.
- **[bugs/realtime-villagers-job-loop.md](bugs/realtime-villagers-job-loop.md)** — How villagers cycle through jobs in real-time using shuffled selection.
- **[bugs/warp-ignores-night.md](bugs/warp-ignores-night.md)** — Warp simulation always assumes daytime regardless of when the warp was initiated.

## TODO / Future Work

- **[todo/farmer-cook-code-redundancy.md](todo/farmer-cook-code-redundancy.md)** — Identify and consolidate overlapping code between farmer and cook jobs for accessing world blocks.
- **[todo/special_rule_result.md](todo/special_rule_result.md)** — Support special result values in job JSON instead of `minecraft:air` for jobs fully dependent on special rules.
- **[todo/test-command-improvement.md](todo/test-command-improvement.md)** — ~~Use existing flag position as origin for `/qt test` command instead of player position.~~ DONE
- **[bugs/fishing-station-missing-facing-crash.md](bugs/fishing-station-missing-facing-crash.md)** — FishingStationBlock.getAttachPoint crashed when block at work position lacked FACING property during warp.
