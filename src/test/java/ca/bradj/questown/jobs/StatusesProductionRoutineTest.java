package ca.bradj.questown.jobs;

import ca.bradj.questown.Questown;
import ca.bradj.questown.jobs.production.IProductionJob;
import ca.bradj.questown.jobs.production.IProductionStatus;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public class StatusesProductionRoutineTest {

    // These names are just here to help better understand what the "magic
    // numbers" used by this test represent.
    private static final int BLOCK_READY_FOR_INGREDIENTS = 0;
    private static final int BLOCK_READY_FOR_WORK = 1;
    private static final int BLOCK_READY_TO_EXTRACT_PRODUCT = 2;

    Room arbitraryRoom = new Room(
            new Position(0, 0),
            new InclusiveSpace(new Position(0, 0), new Position(1, 1))
    );

    record TestInventory(
            boolean inventoryFull,
            boolean hasNonSupplyItems,
            Map<Integer, Boolean> getSupplyItemStatus
    ) implements EntityInvStateProvider<Integer> {
        @Override
        public boolean hasNonSupplyItems(boolean allowCaching) {
            return hasNonSupplyItems;
        }
    }

    /**
     * @deprecated Use TestJobTownWithTime
     **/
    private record TestJobTown(
            boolean hasSupplies,
            boolean hasSpace,
            Collection<Room> roomsWithCompletedProduct,
            Map<Integer, Collection<Room>> roomsToGetSuppliesForByState,
            boolean isCachingAllowed
    ) implements JobTownProvider<Room> {
        public TestJobTown(
                boolean hasSupplies,
                boolean hasSpace,
                Collection<Room> roomsWithCompletedProduct,
                Map<Integer, Collection<Room>> roomsNeedingIngredientsByState
        ) {
            this(hasSupplies, hasSpace, roomsWithCompletedProduct, roomsNeedingIngredientsByState, false);
        }

        @Override
        public boolean isUnfinishedTimeWorkPresent() {
            return false;
        }

        @Override
        public Collection<Integer> getStatesWithUnfinishedItemlessWork() {
            return ImmutableList.of();
        }
    }

    private record TestEntityLoc(
            @Nullable Room getEntityCurrentJobSite
    ) implements EntityLocStateProvider<Room> {
    }

    public static class PTestStatus implements IProductionStatus<PTestStatus> {

        static final PTestStatus DROPPING_LOOT = new PTestStatus("dropping_loot");
        static final PTestStatus NO_SPACE = new PTestStatus("no_space");
        static final PTestStatus GOING_TO_JOB = new PTestStatus("going_to_job");
        static final PTestStatus NO_SUPPLIES = new PTestStatus("no_supplies");
        static final PTestStatus COLLECTING_SUPPLIES = new PTestStatus("collecting_supplies");
        static final PTestStatus IDLE = new PTestStatus("idle");
        static final PTestStatus INGREDIENTS = new PTestStatus("inserting");
        static final PTestStatus ITEM_WORK = new PTestStatus("item_work");
        static final PTestStatus COLLECTING_PRODUCT = new PTestStatus("collecting_product");
        static final PTestStatus RELAXING = new PTestStatus("relaxing");
        static final PTestStatus WAITING = new PTestStatus("waiting");
        static final PTestStatus NO_JOBSITE = new PTestStatus("no_jobsite");

        public static final IProductionStatusFactory<PTestStatus> FACTORY = new IProductionStatusFactory<>() {
            @Override
            public PTestStatus fromJobBlockState(int s) {
                return switch (s) {
                    case BLOCK_READY_FOR_INGREDIENTS -> INGREDIENTS;
                    case BLOCK_READY_FOR_WORK -> ITEM_WORK;
                    case BLOCK_READY_TO_EXTRACT_PRODUCT -> COLLECTING_PRODUCT;
                    default -> throw new IllegalStateException("Unexpected state " + s);
                };
            }

            @Override
            public PTestStatus waitingForTimedState() {
                return WAITING;
            }

            @Override
            public PTestStatus droppingLoot() {
                return DROPPING_LOOT;
            }

            @Override
            public PTestStatus noSpace() {
                return NO_SPACE;
            }

            @Override
            public PTestStatus goingToJobSite() {
                return GOING_TO_JOB;
            }

            @Override
            public PTestStatus noSupplies() {
                return NO_SUPPLIES;
            }

            @Override
            public PTestStatus collectingSupplies() {
                return COLLECTING_SUPPLIES;
            }

            @Override
            public PTestStatus idle() {
                return IDLE;
            }

            @Override
            public PTestStatus extractingProduct() {
                return COLLECTING_PRODUCT;
            }

            @Override
            public PTestStatus relaxing() {
                return RELAXING;
            }
            @Override
            public PTestStatus noJobSite() {
                return NO_JOBSITE;
            }
        };

        private final String inner;

        protected PTestStatus(String inner) {
            this.inner = inner;
        }

        @Override
        public IStatusFactory<PTestStatus> getFactory() {
            return FACTORY;
        }

        @Override
        public boolean isGoingToJobsite() {
            return this == GOING_TO_JOB;
        }

        @Override
        public boolean isDroppingLoot() {
            return this == DROPPING_LOOT;
        }

        @Override
        public boolean isCollectingSupplies() {
            return this == COLLECTING_SUPPLIES;
        }

        @Override
        public String name() {
            return inner;
        }

        @Override
        public String nameV2() {
            return name();
        }

        @Override
        public boolean isUnset() {
            return false;
        }

        @Override
        public boolean isAllowedToTakeBreaks() {
            return false;
        }

        @Override
        public @Nullable String getCategoryId() {
            return "test";
        }

        @Override
        public boolean isBusy() {
            return true;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PTestStatus that = (PTestStatus) o;
            return Objects.equals(inner, that.inner);
        }

        @Override
        public int hashCode() {
            return Objects.hash(inner);
        }

        @Override
        public String toString() {
            return inner;
        }

        @Override
        public boolean isWorkingOnProduction() {
            return this == ITEM_WORK;
        }

        @Override
        public boolean isExtractingProduct() {
            return this == COLLECTING_PRODUCT;
        }

        @Override
        public boolean isWaitingForTimers() {
            return this == WAITING;
        }
    }

    private static class NoOpProductionJob implements IProductionJob<PTestStatus> {

        @Override
        public @Nullable PTestStatus tryChoosingItemlessWork() {
            return null;
        }

        @Override
        public @Nullable PTestStatus tryUsingSupplies(Map<Integer, Boolean> supplyItemStatus) {
            return null;
        }

        @Override
        public ImmutableList<Integer> getAllWorkStatesSortedByPreference() {
            return ImmutableList.of(
                    BLOCK_READY_FOR_INGREDIENTS,
                    BLOCK_READY_FOR_WORK
            );
        }
    }

    private static class FailProductionJob implements IProductionJob<PTestStatus> {

        AssertionError err = new AssertionError(
                String.format("Itemless work is not allowed when using %s", getClass().getName())
        );

        @Override
        public @Nullable PTestStatus tryChoosingItemlessWork() {
            throw err;
        }

        @Override
        public @Nullable PTestStatus tryUsingSupplies(Map<Integer, Boolean> supplyItemStatus) {
            throw err;
        }

        @Override
        public ImmutableList<Integer> getAllWorkStatesSortedByPreference() {
            return ImmutableList.of(
                    BLOCK_READY_FOR_INGREDIENTS,
                    BLOCK_READY_FOR_WORK
            );
        }
    }

    @Disabled("Re-evaluate")
    @Test
    void InMorning_StatusShouldBe_DroppingLoot_WhenEntityIsInJobSite_AndInventoryIsOnlyWork1_AndAllBlocksNeedWork2() {
        boolean hasSupplies = true;
        PTestStatus s = JobStatuses.productionRoutine(
                PTestStatus.GOING_TO_JOB,
                true,
                new TestInventory(false, false, ImmutableMap.of(
                        BLOCK_READY_FOR_INGREDIENTS, true, // <- Has ingredients
                        BLOCK_READY_FOR_WORK, false // <- Does not have items for work
                )),
                new TestEntityLoc(arbitraryRoom),
                new TestJobTown(
                        hasSupplies, true,
                        ImmutableList.of(),
                        ImmutableMap.of(
                                // Job site needs work (not ingredients)
                                BLOCK_READY_FOR_INGREDIENTS, ImmutableList.of(),
                                BLOCK_READY_FOR_WORK, ImmutableList.of(arbitraryRoom)
                        )
                ),
                new NoOpProductionJob(), // Do not provide alternate logic for doing supply-work
                PTestStatus.FACTORY
        );
        Assertions.assertEquals(PTestStatus.DROPPING_LOOT, s);
    }

    @Test
    void InMorning_StatusShouldStay_GoingToJobSite_WhenCurrentJobSiteHasNoJobs_AndAnotherJobSiteHasJobs() {
        Room currentRoom = new Room(
                new Position(0, 1),
                new InclusiveSpace(new Position(2, 3), new Position(4, 5))
        );

        boolean hasSupplies = true;
        PTestStatus s = JobStatuses.productionRoutine(
                PTestStatus.GOING_TO_JOB,
                true,
                new TestInventory(false, false, ImmutableMap.of(
                        BLOCK_READY_FOR_INGREDIENTS, true, // Has ingredients
                        BLOCK_READY_FOR_WORK, false
                )),
                new TestEntityLoc(currentRoom),
                new TestJobTown(
                        hasSupplies, true,
                        ImmutableList.of(),
                        ImmutableMap.of(
                                BLOCK_READY_FOR_INGREDIENTS, // <- site needs ingredients
                                ImmutableList.of(arbitraryRoom) // <- site is not current room
                        )
                ),
                new FailProductionJob(), // Shouldn't do any non-standard work
                PTestStatus.FACTORY
        );
        Assertions.assertNull(s);
    }

    @Test
    void StatusShouldStay_GoingToJobSite_WhenEntityIsNotInJobSite_AndHasSupplies() {
        boolean hasSupplies = true;
        PTestStatus s = JobStatuses.productionRoutine(
                PTestStatus.GOING_TO_JOB,
                true,
                new TestInventory(false, false, ImmutableMap.of(
                        BLOCK_READY_FOR_INGREDIENTS, true, // <- Has ingredients
                        BLOCK_READY_FOR_WORK, false
                )),
                new TestEntityLoc(null),
                new TestJobTown(
                        hasSupplies, true,
                        ImmutableList.of(),
                        ImmutableMap.of(
                                BLOCK_READY_FOR_INGREDIENTS, // <- site needs ingredients
                                ImmutableList.of(arbitraryRoom) // <- site is not current room
                        )
                ),
                new FailProductionJob(), // Shouldn't do any non-standard work
                PTestStatus.FACTORY
        );
        Assertions.assertNull(s);
    }

    @Test
    void InMorning_StatusShouldBe_Idle_WhenAllSitesFull_AndInJobSite() {
        boolean hasSupplies = true; // Town has supplies, but there's nowhere to use them
        PTestStatus s = JobStatuses.productionRoutine(
                PTestStatus.ITEM_WORK,
                true,
                new TestInventory(false, false, ImmutableMap.of(
                        // Inventory is empty. So we don't need to drop loot
                )),
                new TestEntityLoc(arbitraryRoom), // <- In a job site already
                new TestJobTown(
                        hasSupplies, true,
                        ImmutableList.of(), // No rooms have product to collect
                        ImmutableMap.of(
                                // Empty map means no rooms have any work to do
                                // PTestStatus.ITEM_WORK, ImmutableList.of(arbitraryRoom)
                        )
                ),
                new FailProductionJob(), // Shouldn't do any non-standard work
                PTestStatus.FACTORY
        );
        Assertions.assertEquals(PTestStatus.NO_JOBSITE, s);
    }

    @Test
    void InMorning_StatusShouldBe_NoJobSite_WhenAllSitesFull_AndOutOfJobSite() {
        boolean hasSupplies = true; // Town has supplies, but there's nowhere to use them
        PTestStatus s = JobStatuses.productionRoutine(
                PTestStatus.ITEM_WORK,
                true,
                new TestInventory(false, false, ImmutableMap.of(
                        // Inventory is empty. So we don't need to drop loot
                )),
                new TestEntityLoc(null), // <- Not in a job site
                new TestJobTown(
                        hasSupplies, true,
                        ImmutableList.of(), // No rooms have product to collect
                        ImmutableMap.of(
                                // Empty map means no rooms have any work to do
                                // PTestStatus.ITEM_WORK, ImmutableList.of(arbitraryRoom)
                        )
                ),
                new FailProductionJob(), // Shouldn't do any non-standard work
                PTestStatus.FACTORY
        );
        Assertions.assertEquals(PTestStatus.NO_JOBSITE, s);
    }

    @Test
    void StatusShouldBe_INGREDIENTS_WhenSitesNeedItemWork_AndEntityInJobSite_WithSupplies() {
        boolean hasSupplies = true; // Town has supplies, but there's nowhere to use them
        Map<Integer, Boolean> invItemsForWork = ImmutableMap.of(
                BLOCK_READY_FOR_INGREDIENTS, true // We have ingredients
        );
        Map<Integer, Collection<Room>> workToBeDone = ImmutableMap.of(
                BLOCK_READY_FOR_INGREDIENTS, ImmutableList.of(arbitraryRoom) // There is work to be done
        );
        PTestStatus s = JobStatuses.productionRoutine(
                PTestStatus.IDLE,
                true,
                new TestInventory(false, false, invItemsForWork),
                new TestEntityLoc(arbitraryRoom), // <- Entity is in the job site
                new TestJobTown(
                        hasSupplies, true,
                        ImmutableList.of(), // No rooms have product to collect
                        workToBeDone
                ),
                new FailProductionJob(), // Shouldn't do any non-standard work
                PTestStatus.FACTORY
        );
        Assertions.assertEquals(PTestStatus.INGREDIENTS, s);
    }

    @Test
    void StatusShouldBe_work_WhenSitesNeedWork_AndEntityInJobSite_WithSupplies() {
        boolean hasSupplies = true; // Town has supplies, but there's nowhere to use them
        Map<Integer, Boolean> invItemsForWork = ImmutableMap.of(
                BLOCK_READY_FOR_INGREDIENTS, true, // We have ingredients
                BLOCK_READY_FOR_WORK, true // We have the items for work
        );
        Map<Integer, Collection<Room>> workToBeDone = ImmutableMap.of(
                BLOCK_READY_FOR_INGREDIENTS, ImmutableList.of(),
                BLOCK_READY_FOR_WORK, ImmutableList.of(arbitraryRoom) // There is work 2 be done
        );
        PTestStatus s = JobStatuses.productionRoutine(
                PTestStatus.IDLE,
                true,
                new TestInventory(false, false, invItemsForWork),
                new TestEntityLoc(arbitraryRoom), // <- Entity is in the job site
                new TestJobTown(
                        hasSupplies, true,
                        ImmutableList.of(), // No rooms have product to collect
                        workToBeDone
                ),
                new FailProductionJob(), // Shouldn't do any non-standard work
                PTestStatus.FACTORY
        );
        Assertions.assertEquals(PTestStatus.ITEM_WORK, s);
    }

    @Test
    void StatusShouldBe_WORK_insteadOfINGREDIENTS_WhenSitesNeedBothKindsOfWork_AndEntityInJobSiteThatNeedsWORK_WithSupplies() {
        RoomRecipeMatch<Room> otherRoom = new RoomRecipeMatch<>(
                new Room(
                        new Position(0, 1),
                        new InclusiveSpace(new Position(2, 3), new Position(4, 5))
                ),
                new ResourceLocation(Questown.MODID, "jobsite"),
                ImmutableList.of()
        );

        boolean hasSupplies = true; // Town has supplies, but there's nowhere to use them
        Map<Integer, Boolean> invItemsForWork = ImmutableMap.of(
                BLOCK_READY_FOR_INGREDIENTS, true, // We have the ingredients
                BLOCK_READY_FOR_WORK, true // We have the items for work
        );
        Map<Integer, Collection<Room>> workToBeDone = ImmutableMap.of(
                BLOCK_READY_FOR_INGREDIENTS, ImmutableList.of(otherRoom.room), // There are ing. needed in another room
                BLOCK_READY_FOR_WORK, ImmutableList.of(arbitraryRoom) // There is work to be done in this room
        );
        PTestStatus s = JobStatuses.productionRoutine(
                PTestStatus.IDLE,
                true,
                new TestInventory(false, false, invItemsForWork),
                new TestEntityLoc(arbitraryRoom), // <- Entity is in the job site to do work
                new TestJobTown(
                        hasSupplies, true,
                        ImmutableList.of(), // No rooms have product to collect
                        workToBeDone
                ),
                new FailProductionJob(), // Shouldn't do any non-standard work
                PTestStatus.FACTORY
        );
        Assertions.assertEquals(PTestStatus.ITEM_WORK, s);
    }

    @Disabled("Re-evaluate")
    @Test
    void StatusShouldBe_WORK_insteadOfINGREDIENTS_DueToPreferences_WhenSiteNeedsBothKindsOfWork_AndEntityInJobSite_WithSupplies() {
        ImmutableList<Integer> preferences = ImmutableList.of(
                BLOCK_READY_FOR_WORK,
                BLOCK_READY_FOR_INGREDIENTS
        );

        boolean hasSupplies = true; // Town has supplies, but there's nowhere to use them
        Map<Integer, Boolean> invItemsForWork = ImmutableMap.of(
                BLOCK_READY_FOR_INGREDIENTS, true, // We have the ingredients
                BLOCK_READY_FOR_WORK, true // We have the items for work
        );
        Map<Integer, Collection<Room>> workToBeDone = ImmutableMap.of(
                BLOCK_READY_FOR_INGREDIENTS, ImmutableList.of(arbitraryRoom), // There are ingredients needed
                BLOCK_READY_FOR_WORK, ImmutableList.of(arbitraryRoom) // There is work to be done
        );
        PTestStatus s = JobStatuses.productionRoutine(
                PTestStatus.IDLE,
                true,
                new TestInventory(false, false, invItemsForWork),
                new TestEntityLoc(arbitraryRoom), // <- Entity is in the job site
                new TestJobTown(
                        hasSupplies, true,
                        ImmutableList.of(), // No rooms have product to collect
                        workToBeDone
                ),

                new FailProductionJob() { // Shouldn't do any non-standard work


                    @Override
                    public ImmutableList<Integer> getAllWorkStatesSortedByPreference() {
                        return preferences;
                    }
                },
                PTestStatus.FACTORY
        );
        Assertions.assertEquals(PTestStatus.ITEM_WORK, s);
    }

    @Test
    void StatusShouldBe_INGREDIENTS_insteadOfWORK_DueToPreferences_WhenSiteNeedsBothKindsOfWork_AndEntityInJobSite_WithSupplies() {
        ImmutableList<Integer> preferences = ImmutableList.of(
                BLOCK_READY_FOR_INGREDIENTS,
                BLOCK_READY_FOR_WORK
        );

        boolean hasSupplies = true; // Town has supplies, but there's nowhere to use them
        Map<Integer, Boolean> invItemsForWork = ImmutableMap.of(
                BLOCK_READY_FOR_INGREDIENTS, true, // We have the ingredients
                BLOCK_READY_FOR_WORK, true // We have the items for work
        );
        Map<Integer, Collection<Room>> workToBeDone = ImmutableMap.of(
                BLOCK_READY_FOR_INGREDIENTS, ImmutableList.of(arbitraryRoom), // There are ingredients needed
                BLOCK_READY_FOR_WORK, ImmutableList.of(arbitraryRoom) // There is work to be done
        );
        PTestStatus s = JobStatuses.productionRoutine(
                PTestStatus.IDLE,
                true,
                new TestInventory(false, false, invItemsForWork),
                new TestEntityLoc(arbitraryRoom), // <- Entity is in the job site
                new TestJobTown(
                        hasSupplies, true,
                        ImmutableList.of(), // No rooms have product to collect
                        workToBeDone
                ),

                new FailProductionJob() { // Shouldn't do any non-standard work


                    @Override
                    public ImmutableList<Integer> getAllWorkStatesSortedByPreference() {
                        return preferences;
                    }
                },
                PTestStatus.FACTORY
        );
        Assertions.assertEquals(PTestStatus.INGREDIENTS, s);
    }

    @Test
    void StatusShouldBe_collectingProduct_insteadOfItemWorks_EvenWhenSiteNeedsBothKindsOfWork_WhileInJobSite() {

        boolean hasSupplies = true; // Town has supplies, but there's nowhere to use them
        Map<Integer, Boolean> invItemsForWork = ImmutableMap.of(
                BLOCK_READY_FOR_INGREDIENTS, true, // We have the ingredients
                BLOCK_READY_FOR_WORK, true // We have the items for work
        );
        Map<Integer, Collection<Room>> workToBeDone = ImmutableMap.of(
                BLOCK_READY_FOR_INGREDIENTS, ImmutableList.of(arbitraryRoom), // There are ingredients needed
                BLOCK_READY_FOR_WORK, ImmutableList.of(arbitraryRoom) // There is work to be done
        );
        PTestStatus s = JobStatuses.productionRoutine(
                PTestStatus.IDLE,
                true,
                new TestInventory(false, false, invItemsForWork),
                new TestEntityLoc(arbitraryRoom), // <- Entity is in the job site
                new TestJobTown(
                        hasSupplies, true,
                        ImmutableList.of(
                                arbitraryRoom // THERE IS PRODUCT TO COLLECT
                        ),
                        workToBeDone
                ),

                new FailProductionJob(), // Shouldn't do any non-standard work
                PTestStatus.FACTORY
        );
        Assertions.assertEquals(PTestStatus.COLLECTING_PRODUCT, s);
    }

    @Test
    void StatusShouldBe_GoingToJobSite_InsteadOfCollectingProduct_WhenOutOfSite() {
        boolean hasSupplies = true; // Town has supplies
        Map<Integer, Boolean> invItemsForWork = ImmutableMap.of(
                BLOCK_READY_FOR_INGREDIENTS, true, // We have the ingredients
                BLOCK_READY_FOR_WORK, true // We have the items for work
        );
        Map<Integer, Collection<Room>> workToBeDone = ImmutableMap.of(
                BLOCK_READY_FOR_INGREDIENTS, ImmutableList.of(arbitraryRoom), // There are ingr. needed
                BLOCK_READY_FOR_WORK, ImmutableList.of(arbitraryRoom) // There is work to be done
        );
        PTestStatus s = JobStatuses.productionRoutine(
                PTestStatus.IDLE,
                true,
                new TestInventory(false, false, invItemsForWork),
                new TestEntityLoc(null), // <- Entity is OUTSIDE
                new TestJobTown(
                        hasSupplies, true,
                        ImmutableList.of(
                                arbitraryRoom // THERE IS PRODUCT TO COLLECT
                        ),
                        workToBeDone
                ),

                new FailProductionJob(), // Shouldn't do any non-standard work
                PTestStatus.FACTORY
        );
        Assertions.assertEquals(PTestStatus.GOING_TO_JOB, s);
    }

    @Test
    void StatusShouldPrefer_INGREDIENTS_OverCollectingSupplies_WhenSiteNeedsINGREDIENTS_AndAlreadyInSite() {
        boolean hasSupplies = true; // Town has supplies, but there's nowhere to use them
        Map<Integer, Boolean> invItemsForWork = ImmutableMap.of(
                BLOCK_READY_FOR_INGREDIENTS, true,
                BLOCK_READY_FOR_WORK, true
        );
        Map<Integer, Collection<Room>> workToBeDone = ImmutableMap.of(
                BLOCK_READY_FOR_INGREDIENTS, ImmutableList.of(arbitraryRoom), // There are ingr. needed
                BLOCK_READY_FOR_WORK, ImmutableList.of(arbitraryRoom) // There is work to be done
        );
        PTestStatus s = JobStatuses.productionRoutine(
                PTestStatus.IDLE,
                true,
                new TestInventory(false, false, invItemsForWork),
                new TestEntityLoc(arbitraryRoom), // <- Entity is in the job site already
                new TestJobTown(
                        hasSupplies, true,
                        ImmutableList.of(), // No finished products
                        workToBeDone
                ),

                new FailProductionJob(), // Shouldn't do any non-standard work
                PTestStatus.FACTORY
        );
        Assertions.assertEquals(PTestStatus.INGREDIENTS, s);
    }

    @Test
    void StatusShouldBe_GoingToJob_IfRoomHasFinishedItems_AndNotInSite() {
        boolean hasSupplies = true; // Town has supplies, but there's nowhere to use them
        Map<Integer, Boolean> invItemsForWork = ImmutableMap.of(
                BLOCK_READY_FOR_INGREDIENTS, true,
                BLOCK_READY_FOR_WORK, true
        );
        Map<Integer, Collection<Room>> workToBeDone = ImmutableMap.of(
                BLOCK_READY_FOR_INGREDIENTS, ImmutableList.of(arbitraryRoom), // There are ingr. needed
                BLOCK_READY_FOR_WORK, ImmutableList.of(arbitraryRoom) // There is work to be done
        );
        PTestStatus s = JobStatuses.productionRoutine(
                PTestStatus.IDLE,
                true,
                new TestInventory(false, false, invItemsForWork),
                new TestEntityLoc(null), // <- Entity is NOT in job site
                new TestJobTown(
                        hasSupplies, true,
                        ImmutableList.of(arbitraryRoom), // Finished products exist
                        workToBeDone
                ),

                new FailProductionJob(), // Shouldn't do any non-standard work
                PTestStatus.FACTORY
        );
        Assertions.assertEquals(PTestStatus.GOING_TO_JOB, s);
    }

    private record TestJobTownWithTime(
            boolean hasSupplies,
            boolean hasSpace,
            Collection<Room> roomsWithCompletedProduct,
            Map<Integer, Collection<Room>> roomsToGetSuppliesForByState,
            boolean isUnfinishedTimeWorkPresent,
            boolean isCachingAllowed
    ) implements JobTownProvider<Room> {
        private TestJobTownWithTime(
                boolean hasSupplies,
                boolean hasSpace,
                Collection<Room> roomsWithCompletedProduct,
                Map<Integer, Collection<Room>> roomsNeedingIngredientsByState,
                boolean isUnfinishedTimeWorkPresent
        ) {
            this(hasSupplies, hasSpace, roomsWithCompletedProduct, roomsNeedingIngredientsByState, isUnfinishedTimeWorkPresent, false);
        }

        @Override
        public Collection<Integer> getStatesWithUnfinishedItemlessWork() {
            return ImmutableList.of();
        }
    }

    @Test
    void StatusShouldBe_WaitingForNextStage_IfRoomNeedsTime() {
        boolean hasSupplies = true; // Town has supplies, but there's nowhere to use them

        Map<Integer, Boolean> invItemsForWork = ImmutableMap.of(
                // Villager has no items (otherwise would choose status: dropping loot)
                BLOCK_READY_FOR_INGREDIENTS, false,
                BLOCK_READY_FOR_WORK, false
        );
        Map<Integer, Collection<Room>> workToBeDone = ImmutableMap.of(
                BLOCK_READY_FOR_INGREDIENTS, ImmutableList.of(), // Ingredients have been provided already
                BLOCK_READY_FOR_WORK, ImmutableList.of() // There is no work to be done
        );

        PTestStatus s = JobStatuses.productionRoutine(
                PTestStatus.IDLE,
                true,
                new TestInventory(false, false, invItemsForWork),
                new TestEntityLoc(null), // <- Entity is NOT in job site
                new TestJobTownWithTime(
                        hasSupplies, true,
                        ImmutableList.of(), // There are no finished products available to pick up
                        workToBeDone,
                        true // A time-based job is ticking away
                ),
                new NoOpProductionJob(), // Causes us to skip the "use supplies" work
                PTestStatus.FACTORY
        );
        Assertions.assertEquals(PTestStatus.WAITING, s);
    }

    @Disabled("The title doesn't seem to match the test. Update the test?")
    @Test
    void StatusShouldBe_CollectingSupplies_IfWorkIsNeededOnClaimedSpot_AndIngrRequiredOnUnclaimed() {
        boolean hasSupplies = true; // Town has supplies, but there's nowhere to use them

        Map<Integer, Boolean> invItemsForWork = ImmutableMap.of(
                // Villager has no items (otherwise would choose status: dropping loot)
                BLOCK_READY_FOR_INGREDIENTS, false,
                BLOCK_READY_FOR_WORK, false
        );
        Map<Integer, Collection<Room>> workToBeDone = ImmutableMap.of(
                BLOCK_READY_FOR_INGREDIENTS, ImmutableList.of(), // Ingredients have been provided already
                BLOCK_READY_FOR_WORK, ImmutableList.of() // There is no work to be done
        );

        PTestStatus s = JobStatuses.productionRoutine(
                PTestStatus.IDLE,
                true,
                new TestInventory(false, false, invItemsForWork),
                new TestEntityLoc(null), // <- Entity is NOT in job site
                new TestJobTownWithTime(
                        hasSupplies, true,
                        ImmutableList.of(), // There are no finished products available to pick up
                        workToBeDone,
                        true // A time-based job is ticking away
                ),
                new NoOpProductionJob(), // Causes us to skip the "use supplies" work
                PTestStatus.FACTORY
        );
        Assertions.assertEquals(PTestStatus.WAITING, s);
    }

}
