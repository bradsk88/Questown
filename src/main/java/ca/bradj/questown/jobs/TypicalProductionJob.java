package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.IProductionJob;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class TypicalProductionJob<S> implements IProductionJob<S> {

    private final ImmutableList<Integer> sorted;
    private final int maxState;

    public TypicalProductionJob(ImmutableList<Integer> sorted) {
        this.sorted = sorted;
        this.maxState = sorted.stream().max(Integer::compare).get();
    }

    @Override
    public ImmutableList<Integer> getAllWorkStatesSortedByPreference() {
        return sorted;
    }

    @Override
    public int getMaxState() {
        return maxState;
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