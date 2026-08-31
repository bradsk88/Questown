# Worldly Seeds reach the player through the first gatherer, not a recipe

The chicken onboarding tutorial ends when the player hands Worldly Seeds to the helper
chicken. That completes the arc and turns the chicken into a stone statue. Worldly
Seeds are otherwise a plain item: they stack to 64, have no recipe, are not plantable,
and have no right-click handler. A stale scratch note called them "unproducible,"
which is false: it described the item's own properties, not how the player gets one.

The decision is that the player obtains Worldly Seeds through the gatherer, the v1
town job, as a one-time drop rather than a craft or a standing loot.

The gatherer is the first job a town runs (`ServerJobsRegistry` sets its initial job
to `GathererUnmappedNoToolWorkQtrDay`). `ChickenArcLootGuarange.maybePrepend` prepends
a single Worldly Seeds drop to the gatherer's result set. It is guarded by
`shouldPrepend`, which requires the chicken to have spawned, the arc to be un-forfeited,
and `chickenFirstGatherWorldlySeedsFired` to be unset, so it fires exactly once per
town. The prepend is wired into both gatherer result paths, the realtime interaction
(`RealtimeWorldInteraction.getResults`) and the warp interaction
(`TimeWarpWorldInteraction.getResults`), so the drop arrives whether the player is
present or has left. The `chickenFirstGatherWorldlySeedsFired` bit is persisted on the
flag block entity (`TownFlagTileData`), so it survives a reload and is scoped to the
town, not the run. It flips in `maybeFlipFirstGatherBit` the moment the seeds land in a
container, which is why the guarantee stops after the first delivery.

The chicken's final beat makes this path actionable instead of a dead end.
`AWAITING_WORLDLY_SEEDS_DELIVERY` is two-phase. When no container holds the seeds, the
chicken walks to the gate (`GATE_CENTER_OFFSET`) and shows "it waits at the gate. the
villager will return with Worldly Seeds", pointing the player at where the gatherer
comes back. When a container holds the seeds, the chicken pecks that container and asks
for them as before. This replaced an earlier form that just pecked a chest and asked for
seeds the player had no way to make.

## Considered and rejected

- **A craftable recipe for Worldly Seeds.** The item is deliberately a one-time
  tutorial trigger, not a standing mechanic. A recipe would let the player make as many
  as they like and would mis-signal that Worldly Seeds are a repeatable resource.
- **A standing gatherer loot (always, not once).** The arc needs exactly one delivery
  to complete, and the "first gatherer brings them" story fits a once-per-town guarantee
  better than a repeatable drop that could outlast the tutorial.
- **The one-time free food (dirt on the flag base, `giveBonusFood`).** Free food feeds a
  townie's hunger; it is a different item and a different mechanic than a currency the
  chicken collects. Routing the seeds through it would conflate the two and make the
  chicken ask for a thing the flag gives in a different form.
- **Making the townie's generic `UNMET_ITEM` need point at the flag base for free food.**
  This was the stale scratch note's suggestion, based on the free-food premise. Because
  the seeds come from the gatherer, the correct nudge is the chicken's gate-wait, not a
  flag-base prompt.
