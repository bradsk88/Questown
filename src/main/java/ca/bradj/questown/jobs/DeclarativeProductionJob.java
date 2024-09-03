package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.production.IProductionStatus;
import ca.bradj.questown.jobs.production.ProductionJob;
import ca.bradj.questown.town.Claim;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.logging.log4j.Marker;

import java.util.Collection;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

@SuppressWarnings("deprecation")
abstract class DeclarativeProductionJob<
        STATUS extends IProductionStatus<STATUS>,
        SNAPSHOT extends Snapshot<MCHeldItem>,
        JOURNAL extends Journal<STATUS, MCHeldItem, SNAPSHOT>
> extends ProductionJob<STATUS, SNAPSHOT, JOURNAL> {
    public DeclarativeProductionJob(
            UUID ownerUUID,
            int inventoryCapacity,
            Marker logMarker,
            BiFunction<Integer, SignalSource, JOURNAL> journalInit,
            IProductionStatusFactory<STATUS> sFac,
            ImmutableMap<STATUS, Collection<String>> specialRules,
            ImmutableList<String> specialGlobalRules,
            Supplier<Claim> claimSupplier,
            WorkLocation location
    ) {
        super(
                ownerUUID,
                inventoryCapacity,
                logMarker,
                journalInit,
                sFac,
                specialRules,
                specialGlobalRules,
                claimSupplier,
                location
        );
    }
}
