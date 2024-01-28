package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FarmerJobTest {

    @Test
    public void test_FarmWithDirtAndTilledAndCompost_ShouldPlant_Order1() {
        @Nullable WorkSpot ws = FarmerJob.getWorkSpot(
                ImmutableList.of(
                        new WorkSpot<>(new Position(0, 0), FarmerJob.FarmerAction.COMPOST, 0, new Position(0, 1)),
                        new WorkSpot<>(new Position(1, 1), FarmerJob.FarmerAction.TILL, 0, new Position(0, 1)),
                        new WorkSpot<>(new Position(2, 2), FarmerJob.FarmerAction.PLANT, 0, new Position(0, 1))
                ),
                FarmerActions.forWheatSeeds()
        );
        Assertions.assertEquals(FarmerJob.FarmerAction.PLANT, ws.action());
        Assertions.assertEquals(new Position(2, 2), ws.position());
    }

    @Test
    public void test_FarmWithDirtAndTilledAndCompost_ShouldPlant_Order2() {
        @Nullable WorkSpot ws = FarmerJob.getWorkSpot(
                ImmutableList.of(
                        new WorkSpot<>(new Position(2, 2), FarmerJob.FarmerAction.PLANT, 0, new Position(0, 1)),
                        new WorkSpot<>(new Position(0, 0), FarmerJob.FarmerAction.COMPOST, 0, new Position(0, 1)),
                        new WorkSpot<>(new Position(1, 1), FarmerJob.FarmerAction.TILL, 0, new Position(0, 1))
                ),
                FarmerActions.forWheatSeeds()
        );
        Assertions.assertEquals(FarmerJob.FarmerAction.PLANT, ws.action());
        Assertions.assertEquals(new Position(2, 2), ws.position());
    }

    @Test
    public void test_FarmWithDirtAndTilledAndCompost_ShouldPlant_Order3() {
        @Nullable WorkSpot ws = FarmerJob.getWorkSpot(
                ImmutableList.of(
                        new WorkSpot<>(new Position(2, 2), FarmerJob.FarmerAction.PLANT, 0, new Position(0, 1)),
                        new WorkSpot<>(new Position(1, 1), FarmerJob.FarmerAction.TILL, 0, new Position(0, 1)),
                        new WorkSpot<>(new Position(0, 0), FarmerJob.FarmerAction.COMPOST, 0, new Position(0, 1))
                ),
                FarmerActions.forWheatSeeds()
        );
        Assertions.assertEquals(FarmerJob.FarmerAction.PLANT, ws.action());
        Assertions.assertEquals(new Position(2, 2), ws.position());
    }

    @Test
    public void test_FarmWithDirtAndTilledAndCompost_ShouldPlant_Order4() {
        @Nullable WorkSpot ws = FarmerJob.getWorkSpot(
                ImmutableList.of(
                        new WorkSpot<>(new Position(2, 2), FarmerJob.FarmerAction.PLANT, 0, new Position(0, 1)),
                        new WorkSpot<>(new Position(0, 0), FarmerJob.FarmerAction.COMPOST, 0, new Position(0, 1)),
                        new WorkSpot<>(new Position(1, 1), FarmerJob.FarmerAction.TILL, 0, new Position(0, 1))
                ),
                FarmerActions.forWheatSeeds()
        );
        Assertions.assertEquals(FarmerJob.FarmerAction.PLANT, ws.action());
        Assertions.assertEquals(new Position(2, 2), ws.position());
    }

    @Test
    public void test_FarmWithDirtAndCompost_ShouldTill_Order1() {
        @Nullable WorkSpot ws = FarmerJob.getWorkSpot(
                ImmutableList.of(
                        new WorkSpot<>(new Position(0, 0), FarmerJob.FarmerAction.COMPOST, 0, new Position(0, 1)),
                        new WorkSpot<>(new Position(1, 1), FarmerJob.FarmerAction.TILL, 0, new Position(0, 1))
                ),
                FarmerActions.forWheatSeeds()
        );
        Assertions.assertEquals(FarmerJob.FarmerAction.TILL, ws.action());
        Assertions.assertEquals(new Position(1, 1), ws.position());
    }

    @Test
    public void test_FarmWithDirtAndCompost_ShouldTill_Order2() {
        @Nullable WorkSpot ws = FarmerJob.getWorkSpot(
                ImmutableList.of(
                        new WorkSpot<>(new Position(1, 1), FarmerJob.FarmerAction.TILL, 0, new Position(0, 1)),
                        new WorkSpot<>(new Position(0, 0), FarmerJob.FarmerAction.COMPOST, 0, new Position(0, 1))
                ),
                FarmerActions.forWheatSeeds()
        );
        Assertions.assertEquals(FarmerJob.FarmerAction.TILL, ws.action());
        Assertions.assertEquals(new Position(1, 1), ws.position());
    }
}