**Status: Done.**

`JobPhaseModifier.afterWarpRecovery(ServerLevel, LivingEntity, BlockPos)` is called
after warp completes and mobs are recovered, once per active work block
(processingState > 0). The nearest villager entity is passed as the owner.

`DeployFishingHookRule.afterWarpRecovery` deploys the fishing hook if the work block
is a `FishingStationBlock`, restoring the visual when the fisher is mid-job at warp end.
The hook is added to the instance's list so `beforeExtract` cleans it up normally.

Other rules inherit the default no-op. New visual-only rules override
`afterWarpRecovery` following the same pattern.