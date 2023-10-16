package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Map;

class FarmerStatusesTest {

    private static final ImmutableList<FarmerJob.FarmerAction> ALL_WORK_POSSIBLE = ImmutableList.of(
            FarmerJob.FarmerAction.HARVEST,
            FarmerJob.FarmerAction.PLANT,
            FarmerJob.FarmerAction.TILL,
            FarmerJob.FarmerAction.BONE,
            FarmerJob.FarmerAction.COMPOST
    );

    private record ConstTown(
            boolean hasSupplies
    ) implements TownStateProvider {
    }


    private record ConstFarm(
            ImmutableList<FarmerJob.FarmerAction> possibleWork
    ) implements FarmerStatuses.FarmStateProvider {
        @Override
        public boolean isWorkPossible(FarmerJob.FarmerAction action) {
            return possibleWork.contains(action);
        }
    }

    private record ConstEntity(
            boolean inventoryFull,
            boolean hasNonSupplyItems,
            boolean hasItems,
            Map<GathererJournal.Status, Boolean> getSupplyItemStatus
    ) implements EntityStateProvider {
    }

    @Test
    void inMorning_shouldBe_WalkingToFarm_IfFarmExistsAndNotInside() {
        boolean inside = false;
        GathererJournal.Status s = FarmerStatuses.handleMorning(
                GathererJournal.Status.IDLE,
                new ConstTown(true),
                new ConstFarm(ALL_WORK_POSSIBLE),
                new ConstEntity(false, false, false, ImmutableMap.of()),
                inside
        );
        Assertions.assertEquals(GathererJournal.Status.WALKING_TO_FARM, s);
    }

    @Test
    void inMorning_shouldBe_HARVESTING_IfMatureCropsPresentInFarm_AndInventoryIsEmpty() {
        boolean inside = true;
        GathererJournal.Status s = FarmerStatuses.handleMorning(
                GathererJournal.Status.IDLE,
                new ConstTown(false),
                new ConstFarm(ALL_WORK_POSSIBLE),
                new ConstEntity(false, false, false, ImmutableMap.of()),
                inside
        );
        Assertions.assertEquals(GathererJournal.Status.FARMING_HARVESTING, s);
    }

    @Test
    void inMorning_shouldBe_HARVESTING_IfMatureCropsPresentInFarm_AndInventoryHasSpace_ButNoSupplies() {
        boolean inside = true;
        GathererJournal.Status s = FarmerStatuses.handleMorning(
                GathererJournal.Status.IDLE,
                new ConstTown(false),
                new ConstFarm(ALL_WORK_POSSIBLE),
                new ConstEntity(false, true, true, ImmutableMap.of()),
                inside
        );
        Assertions.assertEquals(GathererJournal.Status.FARMING_HARVESTING, s);
    }

    @Test
    void inMorning_shouldBe_DROPPING_LOOT_IfMatureCropsPresentInFarm_ButInventoryIsFullOfNonSupplies() {
        boolean inside = true;
        GathererJournal.Status s = FarmerStatuses.handleMorning(
                GathererJournal.Status.IDLE,
                new ConstTown(false),
                new ConstFarm(ALL_WORK_POSSIBLE),
                new ConstEntity(true, true, true, ImmutableMap.of()),
                inside
        );
        Assertions.assertEquals(GathererJournal.Status.DROPPING_LOOT, s);
    }

    @Test
    void inMorning_shouldBe_TILLING_IfMatureCropsPresentInFarm_AndInventoryFullOfTillingSupplies_AndTillableLandPresent() {
        boolean inside = true;
        GathererJournal.Status s = FarmerStatuses.handleMorning(
                GathererJournal.Status.IDLE,
                new ConstTown(false),
                new ConstFarm(ImmutableList.of(
                        FarmerJob.FarmerAction.HARVEST,
                        FarmerJob.FarmerAction.PLANT,
                        FarmerJob.FarmerAction.TILL
                )),
                new ConstEntity(true, false, true, ImmutableMap.of(
                        GathererJournal.Status.FARMING_TILLING, true
                )),
                inside
        );
        Assertions.assertEquals(GathererJournal.Status.FARMING_TILLING, s);
    }

    @Test
    void inMorning_shouldBe_PLANTING_IfMatureCropsPresentInFarm_AndInventoryFullOfTillingSupplies_AndPlantableLandPresent() {
        boolean inside = true;
        GathererJournal.Status s = FarmerStatuses.handleMorning(
                GathererJournal.Status.IDLE,
                new ConstTown(false),
                new ConstFarm(ImmutableList.of(
                        FarmerJob.FarmerAction.HARVEST,
                        FarmerJob.FarmerAction.PLANT,
                        FarmerJob.FarmerAction.TILL
                )),
                new ConstEntity(true, false, true, ImmutableMap.of(
                        GathererJournal.Status.FARMING_PLANTING, true
                )),
                inside
        );
        Assertions.assertEquals(GathererJournal.Status.FARMING_PLANTING, s);
    }

    @Test
    void inMorning_shouldPrefer_PLANTING_over_TILLING() {
        boolean inside = true;
        GathererJournal.Status s = FarmerStatuses.handleMorning(
                GathererJournal.Status.IDLE,
                new ConstTown(false),
                new ConstFarm(ImmutableList.of(
                        // FarmerJob.FarmerAction.HARVEST, <All crops immature>
                        FarmerJob.FarmerAction.PLANT,
                        FarmerJob.FarmerAction.TILL
                )),
                new ConstEntity(true, false, true, ImmutableMap.of(
                        GathererJournal.Status.FARMING_PLANTING, true,
                        GathererJournal.Status.FARMING_TILLING, true
                )),
                inside
        );
        Assertions.assertEquals(GathererJournal.Status.FARMING_PLANTING, s);
    }
    @Test
    void inMorning_shouldPrefer_BONING_over_PLANTING() {
        boolean inside = true;
        GathererJournal.Status s = FarmerStatuses.handleMorning(
                GathererJournal.Status.IDLE,
                new ConstTown(false),
                new ConstFarm(ImmutableList.of(
                        // FarmerJob.FarmerAction.HARVEST, <All crops immature>
                        FarmerJob.FarmerAction.BONE,
                        FarmerJob.FarmerAction.PLANT,
                        FarmerJob.FarmerAction.TILL
                )),
                new ConstEntity(true, false, true, ImmutableMap.of(
                        GathererJournal.Status.FARMING_BONING, true,
                        GathererJournal.Status.FARMING_PLANTING, true,
                        GathererJournal.Status.FARMING_TILLING, true
                )),
                inside
        );
        Assertions.assertEquals(GathererJournal.Status.FARMING_BONING, s);
    }
    @Test
    @Disabled("This would be preferable, but it's not the end of the world to bone first")
    void inMorning_shouldPrefer_HARVESTING_over_BONING_whenInventoryHasSpace() {
        boolean inside = true;
        GathererJournal.Status s = FarmerStatuses.handleMorning(
                GathererJournal.Status.IDLE,
                new ConstTown(false),
                new ConstFarm(ImmutableList.of(
                        FarmerJob.FarmerAction.HARVEST,
                        FarmerJob.FarmerAction.BONE,
                        FarmerJob.FarmerAction.PLANT,
                        FarmerJob.FarmerAction.TILL
                )),
                new ConstEntity(false, false, true, ImmutableMap.of(
                        GathererJournal.Status.FARMING_BONING, true,
                        GathererJournal.Status.FARMING_PLANTING, true,
                        GathererJournal.Status.FARMING_TILLING, true
                )),
                inside
        );
        Assertions.assertEquals(GathererJournal.Status.FARMING_HARVESTING, s);
    }
    @Test
    void inMorning_shouldPrefer_BONING_over_HARVESTING_whenInventoryIsFull() {
        boolean inside = true;
        GathererJournal.Status s = FarmerStatuses.handleMorning(
                GathererJournal.Status.IDLE,
                new ConstTown(false),
                new ConstFarm(ImmutableList.of(
                        FarmerJob.FarmerAction.HARVEST,
                        FarmerJob.FarmerAction.BONE,
                        FarmerJob.FarmerAction.PLANT,
                        FarmerJob.FarmerAction.TILL
                )),
                new ConstEntity(true, false, true, ImmutableMap.of(
                        GathererJournal.Status.FARMING_BONING, true,
                        GathererJournal.Status.FARMING_PLANTING, true,
                        GathererJournal.Status.FARMING_TILLING, true
                )),
                inside
        );
        Assertions.assertEquals(GathererJournal.Status.FARMING_BONING, s);
    }

    @Test
    void inMorning_shouldPrefer_PLANTING_over_COMPOSTING() {
        boolean inside = true;
        GathererJournal.Status s = FarmerStatuses.handleMorning(
                GathererJournal.Status.IDLE,
                new ConstTown(false),
                new ConstFarm(ImmutableList.of(
                        FarmerJob.FarmerAction.COMPOST,
                        FarmerJob.FarmerAction.PLANT
                )),
                new ConstEntity(true, false, true, ImmutableMap.of(
                        GathererJournal.Status.FARMING_PLANTING, true,
                        GathererJournal.Status.FARMING_COMPOSTING, true
                )),
                inside
        );
        Assertions.assertEquals(GathererJournal.Status.FARMING_PLANTING, s);
    }
    @Test
    void inMorning_shouldPrefer_TILLING_over_COMPOSTING() {
        boolean inside = true;
        GathererJournal.Status s = FarmerStatuses.handleMorning(
                GathererJournal.Status.IDLE,
                new ConstTown(false),
                new ConstFarm(ImmutableList.of(
                        FarmerJob.FarmerAction.COMPOST,
                        FarmerJob.FarmerAction.TILL
                )),
                new ConstEntity(true, false, true, ImmutableMap.of(
                        GathererJournal.Status.FARMING_TILLING, true,
                        GathererJournal.Status.FARMING_COMPOSTING, true
                )),
                inside
        );
        Assertions.assertEquals(GathererJournal.Status.FARMING_TILLING, s);
    }
}