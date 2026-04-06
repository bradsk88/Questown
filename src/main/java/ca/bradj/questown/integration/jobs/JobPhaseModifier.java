package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.jobs.JobBlockTestContext;
import org.jetbrains.annotations.Nullable;

/**
 * Allows modifying the behavior of job phases via injection through
 * the SpecialRulesRegistry.
 *
 * <h3>Tier system</h3>
 * Rules fall into one of two tiers:
 * <ul>
 *   <li><b>Tier 1 (QT-native)</b>: accesses the world only through
 *       {@code QTWorldAccess} methods. Works correctly during time warp
 *       and in unit tests. Implement {@link QTNativeRule} to declare this.</li>
 *   <li><b>Tier 2 (MC-native)</b>: calls
 *       {@code event.world().asServerLevel()} to reach Minecraft APIs.
 *       Must handle a {@code null} return gracefully (warp/test context).
 *       Do <em>not</em> implement {@link QTNativeRule}.</li>
 * </ul>
 * Tier 2 rules are logged at startup by {@link ca.bradj.questown.integration.SpecialRulesRegistry}.
 *
 * @see ca.bradj.questown.jobs.declarative.PrePostHooks#processMulti
 * @see QTNativeRule
 */
public abstract class JobPhaseModifier {

    @SuppressWarnings("RedundantMethodOverride")
    public static JobPhaseModifier NO_OP = new JobPhaseModifier() {
        @Override
        public <X> @Nullable X beforeExtract(
                X input,
                BeforeExtractEvent<X> event
        ) {
            return null;
        }

        @Override
        public <CONTEXT> @Nullable CONTEXT afterInsertItem(
                CONTEXT ctxInput,
                AfterInsertItemEvent<CONTEXT> event
        ) {
            return null;
        }

        @Override
        public <CONTEXT> @Nullable CONTEXT afterDropLoot(
                CONTEXT ctxInput,
                AfterDropLootEvent event
        ) {
            return null;
        }

        @Override
        public Void beforeMoveToNextState(
                BeforeMoveToNextStateEvent event
        ) {
            return null;
        }

        @Override
        public void beforeTick(BeforeTickEvent bxEvent) {

        }

        @Override
        public void beforeFindJobSite(BeforeFindJobSiteEvent event) {
        }

        @Override
        public <X> X onWarpTick(X town, WarpTickEvent event) {
            return town;
        }

        @Override
        public void afterWarpRecovery(
                net.minecraft.server.level.ServerLevel level,
                net.minecraft.world.entity.LivingEntity villager,
                net.minecraft.core.BlockPos workBlock
        ) {
        }
    };

    // Return null if nothing happens.
    // Return either a modified input (via functions available on event) or the
    // original input if something happened.
    // IMPORTANT: When a non-null value is returned, the default result generation is skipped.
    public <CONTEXT> @Nullable CONTEXT beforeExtract(
            CONTEXT ctxInput,
            BeforeExtractEvent<CONTEXT> event
    ) {
        return null;
    }

    public <CONTEXT> @Nullable CONTEXT afterExtract(
            CONTEXT ctxInput,
            AfterExtractEvent<CONTEXT> event
    ) {
        return null;
    }

    public <CONTEXT> @Nullable CONTEXT afterInsertItem(
            CONTEXT ctxInput,
            AfterInsertItemEvent<CONTEXT> event
    ) {
        return null;
    }

    public <CONTEXT> @Nullable CONTEXT afterDropLoot(
            CONTEXT ctxInput,
            AfterDropLootEvent event
    ) {
        return null;
    }

    // TOOD: Potentially phase out. Was used for farmer_till but changed that to
    // run beforeExtract for better state management.
    public Void beforeMoveToNextState(
            BeforeMoveToNextStateEvent event
    ) {
        return null;
    }

    public void beforeTick(BeforeTickEvent bxEvent) {
    }

    public void beforeInit(BeforeInitEvent bxEvent) {

    }

    public void beforeFindJobSite(BeforeFindJobSiteEvent event) {
        // Default: do nothing
    }

    public boolean postJobBlockCheckPassed(
            JobBlockTestContext ctx
    ) {
        return true;
    }
    public void beforeMaxTicksJobChange(
            BeforeMaxTicksJobChangeEvent ctx
    ) {
    }

    /**
     * Called between villager warp steps during time warp.
     * Default returns town unchanged (pass-through).
     * Override to apply world-level effects like crop growth.
     *
     * Unlike beforeExtract (null = didn't handle), this
     * always chains — every rule runs and the town state
     * threads through via processMulti. Compute effects
     * proportionally to {@code event.tickDelta()}, not
     * assuming any particular call frequency.
     */
    public <X> X onWarpTick(X town, WarpTickEvent event) {
        return town;
    }

    /**
     * Called once after time warp completes and villager entities have been
     * recovered, for each work block that was in active use (processingState > 0)
     * at warp end.
     * <p>
     * Override to restore visual-only effects that were skipped during warp
     * (e.g. cosmetic entities). The default no-op is appropriate for most rules.
     *
     * @param level      the server level (never null — this fires after recovery)
     * @param villager   the nearest recovered villager entity for this work block
     * @param workBlock  the active work block position
     */
    public void afterWarpRecovery(
            net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.entity.LivingEntity villager,
            net.minecraft.core.BlockPos workBlock
    ) {
    }
}
