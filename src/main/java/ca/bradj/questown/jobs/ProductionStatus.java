package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;

public enum ProductionStatus implements IStatus<ProductionStatus> {
    IDLE,
    GOING_TO_JOB,
    INSERTING_INGREDIENTS,
    WORKING_ON_PRODUCTION,
    EXTRACTING_PRODUCT,
    DROPPING_LOOT,
    COLLECTING_SUPPLIES,
    UNSET;

    public static ProductionStatus from(String s) {
        for (ProductionStatus ss : values()) {
            if (ss.name().equals(s)) {
                return ss;
            }
        }
        return ProductionStatus.UNSET;
    }

    @Override
    public IStatusFactory<ProductionStatus> getFactory() {
        return null;
    }

    @Override
    public boolean isGoingToJobsite() {
        return this == GOING_TO_JOB;
    }

    @Override
    public boolean isWorkingOnProduction() {
        return this == WORKING_ON_PRODUCTION;
    }

    @Override
    public boolean isDroppingLoot() {
        return this == DROPPING_LOOT;
    }

    @Override
    public boolean isCollectingSupplies() {
        return this == COLLECTING_SUPPLIES;
    }

    @Override
    public boolean isUnset() {
        return this == UNSET;
    }

    @Override
    public boolean isAllowedToTakeBreaks() {
        return !ImmutableList.of(
                GOING_TO_JOB,
                WORKING_ON_PRODUCTION,
                DROPPING_LOOT,
                COLLECTING_SUPPLIES
        ).contains(this);
    }
}
