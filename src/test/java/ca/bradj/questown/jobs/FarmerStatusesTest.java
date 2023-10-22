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
            boolean hasSupplies,
            boolean hasSpace
    ) implements TownProvider {
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
    ) implements EntityInvStateProvider {
    }

    @Test
    void inMorning_shouldBe_WalkingToFarm_IfFarmExistsAndNotInside() {
        boolean inside = false;
        GathererJournal.Status s = FarmerStatuses.handleMorning(
                GathererJournal.Status.IDLE,
                new ConstTown(true, true),
                new ConstFarm(ALL_WORK_POSSIBLE),
                new ConstEntity(false, false, false, ImmutableMap.of()),
                inside
        );
        Assertions.assertEquals(GathererJournal.Status.GOING_TO_JOBSITE, s);
    }

    @Test
    void inMorning_shouldBe_HARVESTING_IfMatureCropsPresentInFarm_AndInventoryIsEmpty() {
        boolean inside = true;
        GathererJournal.Status s = FarmerStatuses.handleMorning(
                GathererJournal.Status.IDLE,
                new ConstTown(false, true),
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
                new ConstTown(false, true),
                new ConstFarm(ALL_WORK_POSSIBLE),
                new ConstEntity(false, true, true, ImmutableMap.of()),
                inside
        );
        Assertions.assertEquals(GathererJournal.Status.FARMING_HARVESTING, s);
    }

    @Test
    void inMorning_shouldBe_LEAVING_FARM_IfMatureCropsPresentInFarm_ButInventoryIsFullOfNonSupplies_AndIsInFarm() {
        boolean inside = true;
        GathererJournal.Status s = FarmerStatuses.handleMorning(
                GathererJournal.Status.IDLE,
                new ConstTown(false, true),
                new ConstFarm(ALL_WORK_POSSIBLE),
                new ConstEntity(true, true, true, ImmutableMap.of()),
                inside
        );
        Assertions.assertEquals(GathererJournal.Status.LEAVING_FARM, s);
    }

    @Test
    void inMorning_shouldBe_DROPPING_LOOT_IfMatureCropsPresentInFarm_ButInventoryIsFullOfNonSupplies() {
        boolean inside = false;
        GathererJournal.Status s = FarmerStatuses.handleMorning(
                GathererJournal.Status.IDLE,
                new ConstTown(false, true),
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
                new ConstTown(false, true),
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
                new ConstTown(false, true),
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
    void inMorning_shouldBe_NO_SPACE_IfTownHasNoSpace() {
        boolean inside = true;
        GathererJournal.Status s = FarmerStatuses.handleMorning(
                GathererJournal.Status.IDLE,
                new ConstTown(false, false),
                new ConstFarm(ImmutableList.of(
                        FarmerJob.FarmerAction.HARVEST,
                        FarmerJob.FarmerAction.PLANT,
                        FarmerJob.FarmerAction.TILL
                )),
                new ConstEntity(true, true, true, ImmutableMap.of(
                )),
                inside
        );
        Assertions.assertEquals(GathererJournal.Status.NO_SPACE, s);
    }

    @Test
    void inMorning_shouldPrefer_PLANTING_over_TILLING() {
        boolean inside = true;
        GathererJournal.Status s = FarmerStatuses.handleMorning(
                GathererJournal.Status.IDLE,
                new ConstTown(false, false),
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
                new ConstTown(false, false),
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
                new ConstTown(false, false),
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
                new ConstTown(false, false),
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
                new ConstTown(false, false),
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
                new ConstTown(false, false),
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
    @Test
    void inMorning_shouldPrefer_COMPOSTING_over_WEEDING() {
        boolean inside = true;
        GathererJournal.Status s = FarmerStatuses.handleMorning(
                GathererJournal.Status.IDLE,
                new ConstTown(false, false),
                new ConstFarm(ImmutableList.of(
                        FarmerJob.FarmerAction.COMPOST,
                        FarmerJob.FarmerAction.WEED
                )),
                new ConstEntity(false, false, true, ImmutableMap.of(
                        GathererJournal.Status.FARMING_COMPOSTING, true
                )),
                inside
        );
        Assertions.assertEquals(GathererJournal.Status.FARMING_COMPOSTING, s);
    }
    @Test
    void inMorning_should_COMPOST_ifCant_TILL_OR_PLANT() {
        boolean inside = true;
        GathererJournal.Status s = FarmerStatuses.handleMorning(
                GathererJournal.Status.IDLE,
                new ConstTown(false, false),
                new ConstFarm(ImmutableList.of(
                        FarmerJob.FarmerAction.COMPOST
                )),
                new ConstEntity(true, false, true, ImmutableMap.of(
                        GathererJournal.Status.FARMING_TILLING, true,
                        GathererJournal.Status.FARMING_PLANTING, true,
                        GathererJournal.Status.FARMING_COMPOSTING, true
                )),
                inside
        );
        Assertions.assertEquals(GathererJournal.Status.FARMING_COMPOSTING, s);
    }
    @Test
    void inMorning_should_WEED_ifTownHasWeeds_AndCantTillPlantOrCompost() {
        boolean inside = true;
        GathererJournal.Status s = FarmerStatuses.handleMorning(
                GathererJournal.Status.IDLE,
                new ConstTown(false, false),
                new ConstFarm(ImmutableList.of(
                        FarmerJob.FarmerAction.WEED,
                        FarmerJob.FarmerAction.BONE
                )),
                new ConstEntity(false, false, true, ImmutableMap.of(
                        GathererJournal.Status.FARMING_TILLING, false,
                        GathererJournal.Status.FARMING_PLANTING, false,
                        GathererJournal.Status.FARMING_COMPOSTING, false
                )),
                inside
        );
        Assertions.assertEquals(GathererJournal.Status.FARMING_WEEDING, s);
    }
    @Test
    void inMorning_should_GET_SUPPLIES_ifTownHasSeeds_AndWeeds_WhileOutOfFarm() {
        boolean inside = false;
        GathererJournal.Status s = FarmerStatuses.handleMorning(
                GathererJournal.Status.IDLE,
                new ConstTown(true, false),
                new ConstFarm(ImmutableList.of(
                        FarmerJob.FarmerAction.WEED,
                        FarmerJob.FarmerAction.PLANT
                )),
                new ConstEntity(false, false, false, ImmutableMap.of(
                        GathererJournal.Status.FARMING_TILLING, false,
                        GathererJournal.Status.FARMING_PLANTING, false,
                        GathererJournal.Status.FARMING_COMPOSTING, false
                )),
                inside
        );
        Assertions.assertEquals(GathererJournal.Status.COLLECTING_SUPPLIES, s);
    }
    @Test
    void inMorning_should_LEAVE_FARM_ifTownHasSeeds_AndWeeds_WhileInFarm() {
        boolean inside = true;
        GathererJournal.Status s = FarmerStatuses.handleMorning(
                GathererJournal.Status.IDLE,
                new ConstTown(true, false),
                new ConstFarm(ImmutableList.of(
                        FarmerJob.FarmerAction.WEED,
                        FarmerJob.FarmerAction.PLANT
                )),
                new ConstEntity(false, false, true, ImmutableMap.of(
                        GathererJournal.Status.FARMING_TILLING, false,
                        GathererJournal.Status.FARMING_PLANTING, false,
                        GathererJournal.Status.FARMING_COMPOSTING, false
                )),
                inside
        );
        Assertions.assertEquals(GathererJournal.Status.LEAVING_FARM, s);
    }
}