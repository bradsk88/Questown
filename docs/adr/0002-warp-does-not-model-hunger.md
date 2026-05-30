# Warp does not model villager hunger

Hunger is a realtime-only concern. It drains in `SimpleVillagerHandle.tickHunger` (driven by the realtime flag tick) and refills via the `HUNGER_FILL` extract rule on the live villager handle. The warp path deliberately models neither: `MCTownState` has no fullness field, `MCTownState.withHungerFilledBy` is a stub returning `unchanged()`, and the warp `hungerUpdater` is `(in, up) -> in`. A town left for a long warp therefore returns with every villager at exactly the fullness it had when the player left — villagers neither starve nor eat while offline.

We chose this because warp exists to cheaply simulate *production* during player absence, not to faithfully replay every realtime subsystem. Fully modeling hunger in warp would mean draining it per simulated tick, routing dining through the warp interaction, and reconciling against the "warp ignores night" drain rules — significant work whose main user-visible payoff (offline starvation) is arguably undesirable anyway.

## Consequences

- **Parity invariant:** hunger fill and hunger drain in warp must always land together. If a future change wires `withHungerFilledBy` to actually fill fullness without also draining it during warp, villagers fill but never get hungry offline — and vice versa. Implement both halves or neither. (Warp-vs-realtime divergence is a recurring bug class in this codebase.)
- Dining-related state captured in a warp snapshot (e.g. food consumed at the ingredient step) is real, but its hunger payoff is not applied until the player is back and realtime ticking resumes.
