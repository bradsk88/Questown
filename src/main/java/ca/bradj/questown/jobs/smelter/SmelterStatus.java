package ca.bradj.questown.jobs.smelter;

import ca.bradj.questown.gui.SessionUniqueOrdinals;
import ca.bradj.questown.jobs.production.IProductionStatus;
import ca.bradj.questown.jobs.IStatusFactory;

public enum SmelterStatus implements IProductionStatus<SmelterStatus> {

    UNSET,
    DROPPING_LOOT,
    GOING_TO_JOBSITE,
    NO_SUPPLIES,
    COLLECTING_SUPPLIES,
    IDLE,
    NO_SPACE,
    RELAXING,
    WORK_COLLECTING_RAW_PRODUCT,
    WORK_INSERTING_ORE,
    WORK_PROCESSING_ORE;

    static {
        for (SmelterStatus s : values()) {
            SessionUniqueOrdinals.register(s);
        }
    }

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
        public SmelterStatus extractingProduct() {
            return WORK_COLLECTING_RAW_PRODUCT;
        }

        @Override
        public SmelterStatus relaxing() {
            return RELAXING;
        }
    };

    public static SmelterStatus from(String s) {
        for (SmelterStatus ss : values()) {
            if (ss.name().equals(s)) {
                return ss;
            }
        }
        return SmelterStatus.UNSET;
    }

    @Override
    public IStatusFactory<SmelterStatus> getFactory() {
        return FACTORY;
    }

    @Override
    public boolean isGoingToJobsite() {
        return this == GOING_TO_JOBSITE;
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

    @Override
    public boolean isWorkingOnProduction() {
        return this == WORK_PROCESSING_ORE;
    }
}