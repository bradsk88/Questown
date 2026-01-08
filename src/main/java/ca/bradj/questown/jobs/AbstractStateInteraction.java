package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.AbstractWorldInteraction;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.town.Claim;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

public abstract class AbstractStateInteraction<INPUTS, POS, ITEM extends Item<ITEM>, HELD_ITEM extends HeldItem<HELD_ITEM, ITEM>, TOWN> extends
        AbstractWorldInteraction<INPUTS, POS, ITEM, HELD_ITEM, TOWN> {
    public AbstractStateInteraction(
            JobID jobId,
            int villagerIndex,
            int interval,
            int maxState,
            DeclarativeJobChecks<INPUTS, HELD_ITEM, ITEM, ?, POS> checks,
            Function<INPUTS, Claim> claimSpots,
            Map<ProductionStatus, Collection<String>> specialRules
    ) {
        super(jobId, villagerIndex, interval, maxState, checks, claimSpots, specialRules);
    }

    public abstract TOWN simulateDropLoot(
            TOWN inState,
            ProductionStatus status
    );

    public abstract @Nullable TOWN simulateCollectSupplies(
            TOWN inState,
            int processingState
    );

    @Override
    protected boolean isEntityClose(
            INPUTS inputs,
            POS position
    ) {
        return true;
    }

    @Override
    protected boolean isServerUpAndTownDataReadable(INPUTS inputs) {
        return true;
    }
}
