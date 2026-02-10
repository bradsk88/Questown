package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.integration.SpecialRulesRegistry;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.jobs.WarpTickEvent;
import ca.bradj.questown.world.QTWorldAccess;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.function.Supplier;

import static ca.bradj.questown.jobs.declarative.PrePostHooks.processMulti;

public class WarpTickHook {

    public static <X> X run(
            Collection<String> rules,
            QTWorldAccess world,
            X town,
            long currentTick,
            long tickDelta,
            Supplier<Collection<BlockPos>> workBlocks
    ) {
        ImmutableList<JobPhaseModifier> appliers =
                SpecialRulesRegistry.getRuleAppliers(rules);
        WarpTickEvent event = new WarpTickEvent(
                world, currentTick, tickDelta, workBlocks
        );
        X result = processMulti(
                town, appliers,
                (t, a) -> a.onWarpTick(t, event)
        );
        return result != null ? result : town;
    }
}
