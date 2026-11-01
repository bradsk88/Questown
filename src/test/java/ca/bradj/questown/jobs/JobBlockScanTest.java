package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * A kitchen furnace in the east wall (x -147 of walls x -149..-147): room matching counts it, so
 * the cook's work-spot scan must see it too.
 */
class JobBlockScanTest {

    @Test
    void theJobBlockScanCoversAFurnaceInTheWall() {
        InclusiveSpace kitchen = InclusiveSpace.from(-149, -155).to(-147, -151);

        Assertions.assertTrue(InclusiveSpaces.getPositions(kitchen, JobBlockScan.CELLS)
                .contains(new Position(-147, -154)));
    }
}
