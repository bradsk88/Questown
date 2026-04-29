# Chicken arc presentation as a (beat, phase) table

The chicken arc had three parallel switches over `ChickenBeatState` taking overlapping inputs and producing related outputs (bubble icon, hint lang key, plain lang key). They drifted — most visibly when the bubble cycled through three SUNSET_AND_MAP phases while the hint stayed frozen, and when "with item" hint variants disagreed with placement-beat bubbles.

We consolidated them into a single `ChickenArcPresentation` module: a per-(beat, phase) table where each row authors all three outputs together. `BeatPhase` is one global enum (six values) rather than per-beat nested enums — `NEED_TO_FETCH` and `READY_TO_USE` recur across multiple beats and naming them as one cross-cutting concept beat-by-beat would fragment that. The `BeatPhase` decision is made once from `PhaseInputs`; bubble + hint + plain all read from the same row, so drift is impossible by construction.

Tests assert only `expectedPhase` (the logic) and never on the leaf bubble/hint/plain keys (the data). Result-checkers derive the live phase from real world state and compare to expected, so the assertion validates the system end-to-end rather than re-asserting table contents.

## Considered and rejected

- **Per-beat nested phase enums.** Type-safe but fragments the cross-cutting `NEED_TO_FETCH` / `READY_TO_USE` concepts.
- **Sibling-field record without a phase concept.** Improves locality but doesn't strengthen the invariant — authors stay free to enumerate phases differently across switches.
- **Push the table onto `ChickenBeatState` enum-with-data.** Couples beat identity to lang keys and `ItemStack`s; bloats the enum file and forecloses contextual presentation (per biome, per language flavor).
- **Leaf assertions on bubble/hint/plain in tests.** Re-asserts table data without validating logic; couples tests to lang-key churn.
