package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.IProductionJob;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

public class TypicalProductionJob<S> implements IProductionJob<S> {

    private final ImmutableList<Integer> sorted;

    public TypicalProductionJob(ImmutableList<Integer> sorted) {
        this.sorted = sorted;
    }

    @Override
    public ImmutableList<Integer> getAllWorkStatesSortedByPreference() {
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