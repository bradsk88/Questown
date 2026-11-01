package ca.bradj.questown.jobs.production;

import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.leaver.RankBoost;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A container goal must be reached at the room's door level, not wherever the container's own height
 * happens to let the ground pathfinder get there. When a room's roof is reachable (stairs or a run
 * of 1-block jumps), a walk target kept at the container's own height is satisfied by going over the
 * roof instead of entering the room at the door. {@link ProductionJob#navTargetForContainer} pins the
 * target to the room's floor level ({@code yCoord}) so the path is forced through the door.
 */
class ProductionJobNavTest {

    /** A chest at (10, 60, 20) sitting in a room whose floor (yCoord) is 55. */
    private static final MCRoom ROOM = new MCRoom(
            new Position(5, 10),
            ImmutableList.of(InclusiveSpace.from(5, 10).to(5, 10)),
            55
    );

    private static ContainerTarget<MCContainer, MCTownItem> targetWith(
            Position interact,
            int yPosition,
            MCRoom room
    ) {
        return new ContainerTarget<MCContainer, MCTownItem>(
                new Position(10, 20),
                yPosition,
                interact,
                fakeContainer(),
                () -> true,
                (Consumer<MCTownItem>) x -> {
                },
                (Predicate<MCTownItem>) x -> true,
                1.0f,
                room
        );
    }

    private static ContainerTarget.Container<MCTownItem> fakeContainer() {
        return new ContainerTarget.Container<MCTownItem>() {
            @Override
            public int size() {
                return 0;
            }

            @Override
            public MCTownItem getItem(int i) {
                return null;
            }

            @Override
            public MCTownItem removeItem(int index) {
                return null;
            }

            @Override
            public boolean isFull() {
                return false;
            }

            @Override
            public String toShortString() {
                return "";
            }

            @Override
            public String toShortString(boolean includeAir) {
                return "";
            }

            @Override
            public boolean canAcceptIfSpaceAllows(MCTownItem item) {
                return true;
            }

            @Override
            public RankBoost getItemAcceptanceRankBoost() {
                return RankBoost.SAME_AS_VANILLA_CHEST;
            }

            @Override
            public boolean setItem(int index, MCTownItem item) {
                return false;
            }
        };
    }

    @Test
    void navTarget_isAtRoomFloorLevel_notContainerLevel() {
        ContainerTarget<MCContainer, MCTownItem> t = targetWith(
                new Position(10, 21), 60, ROOM
        );

        BlockPos nav = ProductionJob.navTargetForContainer(t);

        // X/Z are the interaction spot in front of the chest; Y is pinned to the room floor (55),
        // not the container's own height (60).
        assertEquals(new BlockPos(10, 55, 21), nav);
    }

    @Test
    void navTarget_fallsBackToContainerLevelWhenNoRoom() {
        ContainerTarget<MCContainer, MCTownItem> t = targetWith(
                new Position(10, 21), 60, null
        );

        BlockPos nav = ProductionJob.navTargetForContainer(t);

        assertEquals(new BlockPos(10, 60, 21), nav);
    }
}
