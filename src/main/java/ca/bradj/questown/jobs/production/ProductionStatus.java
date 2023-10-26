package ca.bradj.questown.jobs.production;

import ca.bradj.questown.gui.SessionUniqueOrdinals;
import ca.bradj.questown.jobs.IStatusFactory;
import ca.bradj.questown.jobs.smelter.SmelterStatus;
import com.google.common.collect.ImmutableList;

public enum ProductionStatus implements IProductionStatus<ProductionStatus> {
    IDLE,
    GOING_TO_JOB,
    INSERTING_INGREDIENTS,
    WORKING_ON_PRODUCTION,
    EXTRACTING_PRODUCT,
    DROPPING_LOOT,
    COLLECTING_SUPPLIES,
    UNSET, NO_SPACE, NO_SUPPLIES, RELAXING;


    static {
        for (ProductionStatus s : values()) {
            SessionUniqueOrdinals.register(s);
        }
    }

    public static final IStatusFactory<ProductionStatus> FACTORY = new IStatusFactory<ProductionStatus>() {
        @Override
        public ProductionStatus droppingLoot() {
            return DROPPING_LOOT;
        }

        @Override
        public ProductionStatus noSpace() {
            return NO_SPACE;
        }

        @Override
        public ProductionStatus goingToJobSite() {
            return GOING_TO_JOB;
        }

        @Override
        public ProductionStatus noSupplies() {
            return NO_SUPPLIES;
        }

        @Override
        public ProductionStatus collectingSupplies() {
            return COLLECTING_SUPPLIES;
        }

        @Override
        public ProductionStatus idle() {
            return IDLE;
        }

        @Override
        public ProductionStatus extractingProduct() {
            return EXTRACTING_PRODUCT;
        }

        @Override
        public ProductionStatus relaxing() {
            return RELAXING;
        }
    };

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

    @Override
    public boolean isExtractingProduct() {
        return this == EXTRACTING_PRODUCT;
    }

    @Override
    public boolean isInsertingIngredients() {
        return this == INSERTING_INGREDIENTS;
    }
}
