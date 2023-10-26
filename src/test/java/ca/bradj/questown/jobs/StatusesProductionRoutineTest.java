package ca.bradj.questown.jobs;

import ca.bradj.questown.Questown;
import ca.bradj.questown.jobs.production.IProductionJob;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;

import ca.bradj.questown.jobs.JobStatusesTest.TestStatus;
import ca.bradj.questown.jobs.JobStatusesTest.ConstInventory;

public class StatusesProductionRoutineTest {

    RoomRecipeMatch<Room> arbitraryRoom = new RoomRecipeMatch<>(
            new Room(
                    new Position(0, 0),
                    new InclusiveSpace(new Position(0, 0), new Position(1, 1))
            ),
            new ResourceLocation(Questown.MODID, "bakery"),
            ImmutableList.of()
    );

    private record TestJobTown(
            boolean hasSupplies,
            boolean hasSpace,
            Collection<Room> roomsWithCompletedProduct,
            Map<Integer, ? extends Collection<Room>> roomsNeedingIngredientsByState
    ) implements JobTownProvider<Room> {
    }

    private record TestEntityLoc(
            @Nullable RoomRecipeMatch<Room> getEntityCurrentJobSite
    ) implements EntityLocStateProvider<Room> {
    }

    private static class NoOpProductionJob implements IProductionJob<TestStatus> {

        @Override
        public @Nullable TestStatus tryChoosingItemlessWork() {
            return null;
        }

        @Override
        public @Nullable TestStatus tryUsingSupplies(Map<TestStatus, Boolean> supplyItemStatus) {
            return null;
        }

        @Override
        public ImmutableList<TestStatus> getAllWorkStatusesSortedByPreference() {
            return ImmutableList.of(
                    TestStatus.ITEM_WORK,
                    TestStatus.ITEM_WORK_2
            );
        }
    }

    private static class FailProductionJob implements IProductionJob<TestStatus> {

        AssertionError err = new AssertionError(
                String.format("Itemless work is not allowed when using %s", getClass().getName())
        );

        @Override
        public @Nullable TestStatus tryChoosingItemlessWork() {
            throw err;
        }

        @Override
        public @Nullable TestStatus tryUsingSupplies(Map<Integer, Boolean> supplyItemStatus) {
            throw err;
        }

        @Override
        public ImmutableList<TestStatus> getAllWorkStatusesSortedByPreference() {
            return ImmutableList.of(
                    TestStatus.ITEM_WORK,
                    TestStatus.ITEM_WORK_2
            );
        }
    }

    @Test
    void InMorning_StatusShouldBe_DroppingLoot_WhenEntityIsInJobSite_AndInventoryIsOnlyWork1_AndAllBlocksNeedWork2() {
        boolean hasSupplies = true;
        TestStatus s = JobStatuses.productionRoutine(
                TestStatus.GOING_TO_JOB,
                true,
                new ConstInventory(false, false, ImmutableMap.of(
                        TestStatus.ITEM_WORK, true, // <- Has items for work 1
                        TestStatus.ITEM_WORK_2, false // <- Does not have items for work 2
                )),
                new TestEntityLoc(arbitraryRoom),
                new TestJobTown(
                        hasSupplies, true,
                        ImmutableList.of(),
                        ImmutableMap.of(
                                // Job site needs item work 2 (not item work 1)
                                TestStatus.ITEM_WORK_2, ImmutableList.of(arbitraryRoom.room)
                        )
                ),
                new NoOpProductionJob(), // Do not provide alternate logic for doing supply-work
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.DROPPING_LOOT, s);
    }

    @Test
    void InMorning_StatusShouldStay_GoingToJobSite_WhenCurrentJobSiteHasNoJobs_AndAnotherJobSiteHasJobs() {
        RoomRecipeMatch<Room> currentRoom = new RoomRecipeMatch<>(
                new Room(
                        new Position(0, 1),
                        new InclusiveSpace(new Position(2, 3), new Position(4, 5))
                ),
                new ResourceLocation(Questown.MODID, "jobsite"),
                ImmutableList.of()
        );

        boolean hasSupplies = true;
        TestStatus s = JobStatuses.productionRoutine(
                TestStatus.GOING_TO_JOB,
                true,
                new ConstInventory(false, false, ImmutableMap.of(
                        TestStatus.ITEM_WORK, true // <- Has item to do work
                )),
                new TestEntityLoc(currentRoom),
                new TestJobTown(
                        hasSupplies, true,
                        ImmutableList.of(),
                        ImmutableMap.of(
                                TestStatus.ITEM_WORK, // <- site needs item work done
                                ImmutableList.of(arbitraryRoom.room) // <- site is not current room
                        )
                ),
                new FailProductionJob(), // Shouldn't do any non-standard work
                TestStatus.FACTORY
        );
        Assertions.assertNull(s);
    }

    @Test
    void StatusShouldStay_GoingToJobSite_WhenEntityIsNotInJobSite_AndHasSupplies() {
        boolean hasSupplies = true;
        TestStatus s = JobStatuses.productionRoutine(
                TestStatus.GOING_TO_JOB,
                true,
                new ConstInventory(false, false, ImmutableMap.of(
                        TestStatus.ITEM_WORK, true // <- Has item to do work
                )),
                new TestEntityLoc(null),
                new TestJobTown(
                        hasSupplies, true,
                        ImmutableList.of(),
                        ImmutableMap.of(
                                TestStatus.ITEM_WORK, // <- site needs item work done
                                ImmutableList.of(arbitraryRoom.room) // <- site is not current room
                        )
                ),
                new FailProductionJob(), // Shouldn't do any non-standard work
                TestStatus.FACTORY
        );
        Assertions.assertNull(s);
    }

    @Test
    void InMorning_StatusShouldBe_Idle_WhenAllSitesFull_AndInJobSite() {
        boolean hasSupplies = true; // Town has supplies, but there's nowhere to use them
        TestStatus s = JobStatuses.productionRoutine(
                TestStatus.ITEM_WORK,
                true,
                new ConstInventory(false, false, ImmutableMap.of(
                        // Inventory is empty. So we don't need to drop loot
                )),
                new TestEntityLoc(arbitraryRoom), // <- In a job site already
                new TestJobTown(
                        hasSupplies, true,
                        ImmutableList.of(), // No rooms have product to collect
                        ImmutableMap.of(
                                // Empty map means no rooms have any work to do
                                // TestStatus.ITEM_WORK, ImmutableList.of(arbitraryRoom.room)
                        )
                ),
                new FailProductionJob(), // Shouldn't do any non-standard work
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.IDLE, s);
    }

    @Test
    void InMorning_StatusShouldBe_Idle_WhenAllSitesFull_AndOutOfJobSite() {
        boolean hasSupplies = true; // Town has supplies, but there's nowhere to use them
        TestStatus s = JobStatuses.productionRoutine(
                TestStatus.ITEM_WORK,
                true,
                new ConstInventory(false, false, ImmutableMap.of(
                        // Inventory is empty. So we don't need to drop loot
                )),
                new TestEntityLoc(null), // <- Not in a job site
                new TestJobTown(
                        hasSupplies, true,
                        ImmutableList.of(), // No rooms have product to collect
                        ImmutableMap.of(
                                // Empty map means no rooms have any work to do
                                // TestStatus.ITEM_WORK, ImmutableList.of(arbitraryRoom.room)
                        )
                ),
                new FailProductionJob(), // Shouldn't do any non-standard work
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.IDLE, s);
    }

    @Test
    void StatusShouldBe_itemWork_WhenSitesNeedItemWork_AndEntityInJobSite_WithSupplies() {
        boolean hasSupplies = true; // Town has supplies, but there's nowhere to use them
        Map<TestStatus, Boolean> invItemsForWork = ImmutableMap.of(
                TestStatus.ITEM_WORK, true // We have the items for work
        );
        Map<TestStatus, ImmutableList<Room>> workToBeDone = ImmutableMap.of(
                TestStatus.ITEM_WORK, ImmutableList.of(arbitraryRoom.room) // There is work to be done
        );
        TestStatus s = JobStatuses.productionRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(false, false, invItemsForWork),
                new TestEntityLoc(arbitraryRoom), // <- Entity is in the job site
                new TestJobTown(
                        hasSupplies, true,
                        ImmutableList.of(), // No rooms have product to collect
                        workToBeDone
                ),
                new FailProductionJob(), // Shouldn't do any non-standard work
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.ITEM_WORK, s);
    }

    @Test
    void StatusShouldBe_itemWork2_WhenSitesNeedItemWork2_AndEntityInJobSite_WithSupplies() {
        boolean hasSupplies = true; // Town has supplies, but there's nowhere to use them
        Map<TestStatus, Boolean> invItemsForWork = ImmutableMap.of(
                TestStatus.ITEM_WORK, true, // We have the items for work
                TestStatus.ITEM_WORK_2, true // We have the items for work 2
        );
        Map<TestStatus, ImmutableList<Room>> workToBeDone = ImmutableMap.of(
                TestStatus.ITEM_WORK_2, ImmutableList.of(arbitraryRoom.room) // There is work 2 be done
        );
        TestStatus s = JobStatuses.productionRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(false, false, invItemsForWork),
                new TestEntityLoc(arbitraryRoom), // <- Entity is in the job site
                new TestJobTown(
                        hasSupplies, true,
                        ImmutableList.of(), // No rooms have product to collect
                        workToBeDone
                ),
                new FailProductionJob(), // Shouldn't do any non-standard work
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.ITEM_WORK_2, s);
    }

    @Test
    void StatusShouldBe_itemWork2_insteadOfItemWork_WhenSitesNeedBothKindsOfWork_AndEntityInJobSiteThatNeedsWork2_WithSupplies() {
        RoomRecipeMatch<Room> otherRoom = new RoomRecipeMatch<>(
                new Room(
                        new Position(0, 1),
                        new InclusiveSpace(new Position(2, 3), new Position(4, 5))
                ),
                new ResourceLocation(Questown.MODID, "jobsite"),
                ImmutableList.of()
        );

        boolean hasSupplies = true; // Town has supplies, but there's nowhere to use them
        Map<TestStatus, Boolean> invItemsForWork = ImmutableMap.of(
                TestStatus.ITEM_WORK, true, // We have the items for work
                TestStatus.ITEM_WORK_2, true // We have the items for work 2
        );
        Map<TestStatus, ImmutableList<Room>> workToBeDone = ImmutableMap.of(
                TestStatus.ITEM_WORK, ImmutableList.of(otherRoom.room), // There is work 1 to be done in another room
                TestStatus.ITEM_WORK_2, ImmutableList.of(arbitraryRoom.room) // There is work 2 be done
        );
        TestStatus s = JobStatuses.productionRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(false, false, invItemsForWork),
                new TestEntityLoc(arbitraryRoom), // <- Entity is in the job site to do work 2
                new TestJobTown(
                        hasSupplies, true,
                        ImmutableList.of(), // No rooms have product to collect
                        workToBeDone
                ),
                new FailProductionJob(), // Shouldn't do any non-standard work
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.ITEM_WORK_2, s);
    }

    @Test
    void StatusShouldBe_itemWork2_insteadOfItemWork_DueToPreferences_WhenSiteNeedsBothKindsOfWork_AndEntityInJobSite_WithSupplies() {
        ImmutableList<TestStatus> preferences = ImmutableList.of(
                TestStatus.ITEM_WORK_2,
                TestStatus.ITEM_WORK
        );

        boolean hasSupplies = true; // Town has supplies, but there's nowhere to use them
        Map<TestStatus, Boolean> invItemsForWork = ImmutableMap.of(
                TestStatus.ITEM_WORK, true, // We have the items for work
                TestStatus.ITEM_WORK_2, true // We have the items for work 2
        );
        Map<TestStatus, ImmutableList<Room>> workToBeDone = ImmutableMap.of(
                TestStatus.ITEM_WORK, ImmutableList.of(arbitraryRoom.room), // There is work 1 to be done
                TestStatus.ITEM_WORK_2, ImmutableList.of(arbitraryRoom.room) // There is work 2 to be done
        );
        TestStatus s = JobStatuses.productionRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(false, false, invItemsForWork),
                new TestEntityLoc(arbitraryRoom), // <- Entity is in the job site
                new TestJobTown(
                        hasSupplies, true,
                        ImmutableList.of(), // No rooms have product to collect
                        workToBeDone
                ),

                new FailProductionJob() { // Shouldn't do any non-standard work

                    @Override
                    public ImmutableList<TestStatus> getAllWorkStatusesSortedByPreference() {
                        return preferences;
                    }
                },
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.ITEM_WORK_2, s);
    }

    @Test
    void StatusShouldBe_collectingProduct_insteadOfItemWorks_EvenWhenSiteNeedsBothKindsOfWork_WhileInJobSite() {

        boolean hasSupplies = true; // Town has supplies, but there's nowhere to use them
        Map<TestStatus, Boolean> invItemsForWork = ImmutableMap.of(
                TestStatus.ITEM_WORK, true, // We have the items for work
                TestStatus.ITEM_WORK_2, true // We have the items for work 2
        );
        Map<TestStatus, ImmutableList<Room>> workToBeDone = ImmutableMap.of(
                TestStatus.ITEM_WORK, ImmutableList.of(arbitraryRoom.room), // There is work 1 to be done
                TestStatus.ITEM_WORK_2, ImmutableList.of(arbitraryRoom.room) // There is work 2 to be done
        );
        TestStatus s = JobStatuses.productionRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(false, false, invItemsForWork),
                new TestEntityLoc(arbitraryRoom), // <- Entity is in the job site
                new TestJobTown(
                        hasSupplies, true,
                        ImmutableList.of(
                                arbitraryRoom.room // THERE IS PRODUCT TO COLLECT
                        ),
                        workToBeDone
                ),

                new FailProductionJob(), // Shouldn't do any non-standard work
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.COLLECTING_PRODUCT, s);
    }

    @Test
    void StatusShouldBe_CollectingProduct_OverAllOtherStatuses_EvenWhenOutOfSite() {
        boolean hasSupplies = true; // Town has supplies
        Map<TestStatus, Boolean> invItemsForWork = ImmutableMap.of(
                TestStatus.ITEM_WORK, true, // We have the items for work
                TestStatus.ITEM_WORK_2, true // We have the items for work 2
        );
        Map<TestStatus, ImmutableList<Room>> workToBeDone = ImmutableMap.of(
                TestStatus.ITEM_WORK, ImmutableList.of(arbitraryRoom.room), // There is work 1 to be done
                TestStatus.ITEM_WORK_2, ImmutableList.of(arbitraryRoom.room) // There is work 2 to be done
        );
        TestStatus s = JobStatuses.productionRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(false, false, invItemsForWork),
                new TestEntityLoc(null), // <- Entity is OUTSIDE
                new TestJobTown(
                        hasSupplies, true,
                        ImmutableList.of(
                                arbitraryRoom.room // THERE IS PRODUCT TO COLLECT
                        ),
                        workToBeDone
                ),

                new FailProductionJob(), // Shouldn't do any non-standard work
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.COLLECTING_PRODUCT, s);
    }

    @Test
    void StatusShouldPrefer_ItemWork_OverCollectingSupplies_WhenSiteNeedsWork_AndAlreadyInSite() {
        boolean hasSupplies = true; // Town has supplies, but there's nowhere to use them
        Map<TestStatus, Boolean> invItemsForWork = ImmutableMap.of(
                TestStatus.ITEM_WORK, true,
                TestStatus.ITEM_WORK_2, true
        );
        Map<TestStatus, ImmutableList<Room>> workToBeDone = ImmutableMap.of(
                TestStatus.ITEM_WORK, ImmutableList.of(arbitraryRoom.room), // There is work 1 to be done
                TestStatus.ITEM_WORK_2, ImmutableList.of(arbitraryRoom.room) // There is work 2 to be done
        );
        TestStatus s = JobStatuses.productionRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(false, false, invItemsForWork),
                new TestEntityLoc(arbitraryRoom), // <- Entity is in the job site already
                new TestJobTown(
                        hasSupplies, true,
                        ImmutableList.of(), // No finished products
                        workToBeDone
                ),

                new FailProductionJob(), // Shouldn't do any non-standard work
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.ITEM_WORK, s);
    }
}
