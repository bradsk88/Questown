package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.IProductionJob;
import ca.bradj.questown.jobs.production.IProductionStatus;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class TypicalProductionJob<S extends IProductionStatus<S>> implements IProductionJob<S> {

    private final ImmutableList<S> sorted;
    private final S maxState;

    public TypicalProductionJob(ImmutableList<S> sorted) {
        this.sorted = sorted;
        this.maxState = sorted.stream().max(Comparable::compareTo).get();
    }

    @Override
    public ImmutableList<S> getAllWorkStatesSortedByPreference() {
        return sorted;
    }

    @Override
    public S getMaxState() {
        return maxState;
    }

    @Override
    public @Nullable StatusSupplier<S> tryChoosingItemlessWork() {
        return null;
    }

    @Override
    public @Nullable StatusSupplier<S> tryUsingSupplies(Map<S, Boolean> supplyItemStatus) {
        return null;
    }

}