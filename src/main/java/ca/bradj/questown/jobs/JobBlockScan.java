package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.logic.InclusiveSpaces;

/** Which cells of a room may hold a job block: walls included, since room matching counts them. */
public final class JobBlockScan {

    public static final InclusiveSpaces.PositionType CELLS = InclusiveSpaces.PositionType.WALLS_AND_INTERIOR;

    private JobBlockScan() {
    }
}
