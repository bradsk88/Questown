package ca.bradj.questown.jobs;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @deprecated Use Job
 */
public interface LegacyJob<STATUS, SUP_CAT> {
    @Nullable STATUS tryChoosingItemlessWork();

    @Nullable STATUS tryUsingSupplies(Map<SUP_CAT, Boolean> supplyItemStatus);
}