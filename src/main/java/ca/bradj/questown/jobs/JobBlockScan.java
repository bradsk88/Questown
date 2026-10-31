package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.logic.InclusiveSpaces;

/**
 * Which cells of a room may hold a job block. Room recipe matching (RoomRecipes'
 * {@code RecipeDetectionClean.getBlocksInRoom}) counts every block from corner to corner, walls
 * included, so a furnace built into a kitchen wall completes the kitchen quest. The job scans used
 * the interior only, and the cook then never found that furnace (r17, 2026-10-25: the dev's cook
 * walked to it with raw mutton and never inserted). The job-block scans (live work spots, state
 * seeding, the warp's room positions) use this so they see what matching saw. The villager still
 * stands inside: {@code InteractionSpots.find} prefers an interior neighbour for a wall block.
 * Pinned by {@code JobBlockScanTest}.
 */
public final class JobBlockScan {

    public static final InclusiveSpaces.PositionType CELLS = InclusiveSpaces.PositionType.WALLS_AND_INTERIOR;

    private JobBlockScan() {
    }
}
