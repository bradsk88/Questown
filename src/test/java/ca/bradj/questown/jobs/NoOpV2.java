package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class NoOpV2 implements JobStatuses.Job<JobStatusesTest.TestStatus, JobStatusesTest.TestStatus> {
    @Override
    public ImmutableMap<Integer, StatusSupplier<JobStatusesTest.@Nullable TestStatus>> getSupplyUsesKeyedByPriority(Map<JobStatusesTest.TestStatus, Boolean> supplyItemStatus) {
        return ImmutableMap.of();
    }

    @Override
    public ImmutableMap<Integer, StatusSupplier<JobStatusesTest.@Nullable TestStatus>> getItemlessWorkKeyedByPriority() {
        return ImmutableMap.of();
    }

    @Override
    public @Nullable JobStatusesTest.TestStatus fromInt(int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public @Nullable JobStatusesTest.TestStatus convert(JobStatusesTest.TestStatus i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StatusSupplier<JobStatusesTest.TestStatus> tryChoosingItemlessWork() {
        throw new UnsupportedOperationException();
    }

    @Override
    public StatusSupplier<JobStatusesTest.TestStatus> tryUsingSupplies(Map<JobStatusesTest.TestStatus, Boolean> supplyItemStatus) {
        throw new UnsupportedOperationException();
    }
}
