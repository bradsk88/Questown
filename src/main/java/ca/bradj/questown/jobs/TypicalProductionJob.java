package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.IProductionJob;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class TypicalProductionJob<S> implements IProductionJob<S> {

    private final ImmutableList<S> sorted;

    public TypicalProductionJob(ImmutableList<S> sorted) {
        this.sorted = sorted;
    }

    @Override
    public ImmutableList<S> getAllWorkStatusesSortedByPreference() {
        return sorted;
    }

    @Override
    public @Nullable S tryChoosingItemlessWork() {
        return null;
    }

    @Override
    public @Nullable S tryUsingSupplies(Map<Integer, Boolean> supplyItemStatus) {
        return null;
    }
}
