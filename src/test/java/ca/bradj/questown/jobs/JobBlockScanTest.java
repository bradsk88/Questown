package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** A furnace in a kitchen wall counts as a work spot, as room matching counts it. */
class JobBlockScanTest {

    @Test
    void theJobBlockScanCoversAFurnaceInTheWall() {
        InclusiveSpace kitchen = InclusiveSpace.from(-149, -155).to(-147, -151);

        Assertions.assertTrue(InclusiveSpaces.getPositions(kitchen, JobBlockScan.CELLS)
                .contains(new Position(-147, -154)));
    }
}
