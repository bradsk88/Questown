package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;

public enum SmelterStatus implements IStatus<SmelterStatus> {

    UNSET,
    DROPPING_LOOT,
    GOING_TO_JOBSITE,
    NO_SUPPLIES,
    COLLECTING_SUPPLIES,
    IDLE,
    NO_SPACE,
    RELAXING, WORK_COLLECTING_ORE, PROCESSING_ORE, WORK_INSERTING_ORE, WORK_INSERTING_COAL;

    static final IStatusFactory<SmelterStatus> FACTORY = new IStatusFactory<>() {

        @Override
        public SmelterStatus droppingLoot() {
            return DROPPING_LOOT;
        }

        @Override
        public SmelterStatus noSpace() {
            return NO_SPACE;
        }

        @Override
        public SmelterStatus goingToJobSite() {
            return GOING_TO_JOBSITE;
        }

        @Override
        public SmelterStatus noSupplies() {
            return NO_SUPPLIES;
        }

        @Override
        public SmelterStatus collectingSupplies() {
            return COLLECTING_SUPPLIES;
        }

        @Override
        public SmelterStatus idle() {
            return IDLE;
        }

        @Override
        public SmelterStatus collectingFinishedProduct() {
            return WORK_COLLECTING_ORE;
        }
    };

    @Override
    public IStatusFactory<SmelterStatus> getFactory() {
        return FACTORY;
    }

    @Override
    public boolean isGoingToJobsite() {
        return this == GOING_TO_JOBSITE;
    }

    @Override
    public boolean isWorkingOnProduction() {
        return ImmutableList.of(
                WORK_COLLECTING_ORE,
                WORK_INSERTING_ORE,
                WORK_INSERTING_COAL
        ).contains(this);
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
        return isGoingToJobsite() || isCollectingSupplies() || isDroppingLoot();
    }
}
