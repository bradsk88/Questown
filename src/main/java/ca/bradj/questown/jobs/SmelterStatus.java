package ca.bradj.questown.jobs;

public enum SmelterStatus implements IStatus<SmelterStatus> {

    DROPPING_LOOT,
    GOING_TO_JOBSITE,
    NO_SUPPLIES,
    COLLECTING_SUPPLIES,
    IDLE,
    NO_SPACE,
    RELAXING, COLLECTING_ORE, PROCESSING_ORE, INSERTING_ORE, INSERTING_COAL;

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
            return COLLECTING_ORE;
        }
    };

    @Override
    public IStatusFactory<SmelterStatus> getFactory() {
        return FACTORY;
    }
}
