package ca.bradj.questown.logic;

import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.production.TownNeedsMap;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.roomrecipes.adapter.RoomWithBlocks;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.function.Function;

class TownNeedsTest {

    private final ProductionStatus STATUS_0 = ProductionStatus.fromJobBlockStatus(0);
    private final ProductionStatus STATUS_1 = ProductionStatus.fromJobBlockStatus(1);
    private final ProductionStatus STATUS_2 = ProductionStatus.fromJobBlockStatus(2);

    private final Room ROOM_A = new Room(new Position(0, 0), InclusiveSpaces.from(-1, -1).to(1, 1));

    private final Emptyable EMPTY = () -> true;
    private final Emptyable NOT_EMPTY = () -> false;

    private void AssertIsCompletelyEmpty(TownNeedsMap<ProductionStatus> result) {
        Assertions.assertTrue(result.roomsWhereSuppliesCanBeUsed.get(STATUS_0).isEmpty());
        Assertions.assertTrue(result.roomsWhereSuppliesCanBeUsed.get(STATUS_1).isEmpty());
        Assertions.assertTrue(result.roomsWhereSuppliesCanBeUsed.get(STATUS_2).isEmpty());
        Assertions.assertTrue(result.roomsWhereWorkCanBeDoneByEntity.get(STATUS_0).isEmpty());
        Assertions.assertTrue(result.roomsWhereWorkCanBeDoneByEntity.get(STATUS_1).isEmpty());
        Assertions.assertTrue(result.roomsWhereWorkCanBeDoneByEntity.get(STATUS_2).isEmpty());
    }

    @Test
    void shouldHandle_WhenJobHasNoRequirements_AndTownIsEmpty() {
        TownNeedsMap<ProductionStatus> result = TownNeeds.getRoomsNeedingIngredientsOrTools(
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                (i) -> ImmutableList.of(),
                ImmutableMap.of(),
                (p) -> null,
                (p) -> true,
                s -> null, // no tool requirements -> no tool statuses
                2
        );
        AssertIsCompletelyEmpty(result);
    }

    @Test
    void shouldHandle_WhenJobDoesHaveRequirements_ButTownIsEmpty() {
        TownNeedsMap<ProductionStatus> result = TownNeeds.getRoomsNeedingIngredientsOrTools(
                ImmutableMap.of(
                        STATUS_0, NOT_EMPTY,
                        STATUS_1, NOT_EMPTY,
                        STATUS_2, NOT_EMPTY
                ), ImmutableMap.of(
                        STATUS_0, 1, // One ingredient required
                        STATUS_1, 1,
                        STATUS_2, 1
                ), ImmutableMap.of(
                        STATUS_0, NOT_EMPTY,
                        STATUS_1, NOT_EMPTY,
                        STATUS_2, NOT_EMPTY
                ),
                (i) -> ImmutableList.of(), ImmutableMap.of(),
                (p) -> null,
                (p) -> true,
                s -> false, // Town is empty, we don't have any of the required tools
                2
        );
        AssertIsCompletelyEmpty(result);
    }

    @Test
    void shouldHandle_WhenJobHasNoRequirements_AndTownIsNotEmpty() {
        TownNeedsMap<ProductionStatus> result = TownNeeds.getRoomsNeedingIngredientsOrTools(
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                (i) -> ImmutableList.of(
                        new RoomWithBlocks<>(ROOM_A, ImmutableMap.of("Position1", "Pumpkin"))
                ),
                ImmutableMap.of(),
                (p) -> ImmutableMap.of(
                        "Position1", AbstractWorkStatusStore.State.freshAtState(0),
                        "Position2", AbstractWorkStatusStore.State.freshAtState(1),
                        "Position3", AbstractWorkStatusStore.State.freshAtState(2)
                ).get(p),
                (p) -> true,
                s -> null, // no tool requirements -> no tool statuses
                2
        );
        AssertIsCompletelyEmpty(result);
    }

    @Test
    void shouldHandle_WhenJobDoesHaveRequirements_AndOneMatchingRoomFound_AndToolsAreHeld() {
        TownNeedsMap<ProductionStatus> result = job(
                true,
                true,
                STATUS_0,
                true
        );
        Assertions.assertFalse(result.roomsWhereWorkCanBeDoneByEntity.isEmpty());
        Assertions.assertFalse(result.roomsWhereSuppliesCanBeUsed.isEmpty());
        Assertions.assertEquals(1, result.roomsWhereSuppliesCanBeUsed.get(STATUS_0).size());
        Assertions.assertEquals(1, result.roomsWhereWorkCanBeDoneByEntity.get(STATUS_0).size());
        Assertions.assertEquals(
                ROOM_A,
                ImmutableList.copyOf(result.roomsWhereSuppliesCanBeUsed.get(STATUS_0)).get(0)
        );
        Assertions.assertEquals(
                ROOM_A,
                ImmutableList.copyOf(result.roomsWhereWorkCanBeDoneByEntity.get(STATUS_0)).get(0)
        );
    }

    @Test
    void shouldHandle_WhenJobDoesHaveRequirements_AndOneMatchingRoomFound_AndToolsAreNotHeld() {
        TownNeedsMap<ProductionStatus> result = job(
                true,
                true,
                STATUS_0,
                false
        );
        // Work can not be done
        Assertions.assertFalse(result.roomsWhereWorkCanBeDoneByEntity.isEmpty());
        Assertions.assertTrue(result.roomsWhereWorkCanBeDoneByEntity.get(STATUS_0).isEmpty());

        // Supplies can be used though
        Assertions.assertFalse(result.roomsWhereSuppliesCanBeUsed.isEmpty());
        Assertions.assertEquals(1, result.roomsWhereSuppliesCanBeUsed.get(STATUS_0).size());
        Assertions.assertEquals(ROOM_A, result.roomsWhereSuppliesCanBeUsed.get(STATUS_0).get(0));
    }

    @NotNull
    private TownNeedsMap<ProductionStatus> job(
            boolean requiresIngredients,
            boolean requiresTools,
            @Nullable ProductionStatus matchingRoom,
            boolean toolsAreInInventory
    ) {
        ImmutableMap<ProductionStatus, Emptyable> ingredientsRequiredAtStates = ImmutableMap.of(
                STATUS_0, NOT_EMPTY,
                STATUS_1, NOT_EMPTY,
                STATUS_2, NOT_EMPTY
        );
        if (!requiresIngredients) {
            ingredientsRequiredAtStates = ImmutableMap.of(
                    STATUS_0, EMPTY,
                    STATUS_1, EMPTY,
                    STATUS_2, EMPTY
            );
        }
        ImmutableMap<ProductionStatus, Integer> ingredientQtyRequiredAtStates = ImmutableMap.of(
                STATUS_0, 1, // One ingredient required
                STATUS_1, 1,
                STATUS_2, 1
        );
        if (!requiresIngredients) {
            ingredientQtyRequiredAtStates = ImmutableMap.of(
                    STATUS_0, 0, // No ingredients required
                    // STATUS 1: Some quantities unspecified
                    STATUS_2, 0
            );
        }

        ImmutableMap<ProductionStatus, Emptyable> toolsRequiredAtStates = ImmutableMap.of(
                STATUS_0, NOT_EMPTY,
                STATUS_1, NOT_EMPTY,
                STATUS_2, NOT_EMPTY
        );
        if (!requiresTools) {
            toolsRequiredAtStates = ImmutableMap.of(
                    STATUS_0, EMPTY,
                    STATUS_1, EMPTY,
                    STATUS_2, EMPTY
            );
        }

        Function<ProductionStatus, @NotNull Collection<? extends RoomWithBlocks<? extends Room, String, String>>> matchinRooms =
                (i) -> Util.getOrDefaultCollection(
                        ImmutableMap.of(
                                matchingRoom, ImmutableList.of(
                                        new RoomWithBlocks<>(
                                                ROOM_A,
                                                ImmutableMap.of("Position1", "Pumpkin")
                                        )
                                )
                        ),
                        i,
                        ImmutableList.of()
                );
        if (matchingRoom == null) {
            matchinRooms = (i) -> ImmutableList.of();
        }

        Function<String, AbstractWorkStatusStore.@Nullable State> jobBlocks = (p) -> ImmutableMap.of(
                "Position1", AbstractWorkStatusStore.State.freshAtState(0),
                "Position2", AbstractWorkStatusStore.State.freshAtState(1),
                "Position3", AbstractWorkStatusStore.State.freshAtState(2)
        ).get(p);
        if (matchingRoom == null) {
            jobBlocks = p -> null;
        }

        Function<ProductionStatus, @Nullable Boolean> haveTool = s -> true;
        if (!toolsAreInInventory) {
            haveTool = s -> false;
        }

        return TownNeeds.getRoomsNeedingIngredientsOrTools(
                ingredientsRequiredAtStates, ingredientQtyRequiredAtStates, toolsRequiredAtStates,
                matchinRooms,
                ImmutableMap.of(),
                jobBlocks,
                (p) -> true,
                haveTool, // We have all required tools, so room should be detected
                2
        );
    }

    @Test
    void shouldHandle_WhenJobDoesHaveRequirements_ButAllRoomsHaveNonMatchingStates() {
        TownNeedsMap<ProductionStatus> result = TownNeeds.getRoomsNeedingIngredientsOrTools(
                ImmutableMap.of(
                        STATUS_0, NOT_EMPTY,
                        STATUS_1, EMPTY, // Empty requirements at stage 1
                        STATUS_2, NOT_EMPTY
                ), ImmutableMap.of(
                        STATUS_0, 1, // One ingredient required
                        STATUS_1, 0,  // Zero requireed at stage 1
                        STATUS_2, 1
                ), ImmutableMap.of(
                        STATUS_0, NOT_EMPTY,
                        STATUS_1, EMPTY, // Empty requirements at stage 1
                        STATUS_2, NOT_EMPTY
                ),
                (i) -> Util.getOrDefaultCollection(
                        ImmutableMap.of(
                                1, ImmutableList.of(
                                        new RoomWithBlocks<>(
                                                ROOM_A,
                                                ImmutableMap.of("Position1", "Pumpkin")
                                        )
                                )
                        ),
                        i,
                        ImmutableList.of()
                ),
                ImmutableMap.of(),
                (p) -> ImmutableMap.of(
                        "Position1", AbstractWorkStatusStore.State.freshAtState(0),
                        "Position2", AbstractWorkStatusStore.State.freshAtState(1),
                        "Position3", AbstractWorkStatusStore.State.freshAtState(2)
                ).get(p),
                (p) -> true,
                s -> true, // We have all the required tools
                2
        );
        AssertIsCompletelyEmpty(result);
    }


    @Test
    void shouldHandle_WhenJobRequiresToolAtState0_AndIngredientAtState1_AndRoomHasState0() {
        TownNeedsMap<ProductionStatus> result = TownNeeds.getRoomsNeedingIngredientsOrTools(
                ImmutableMap.of(
                        STATUS_0, EMPTY, // No ingredients at state 0
                        STATUS_1, NOT_EMPTY // Requirement at state 1
                ), ImmutableMap.of(
                        STATUS_0, 0, // Zero ingredients required
                        STATUS_1, 1 // One
                ), ImmutableMap.of(
                        STATUS_0, NOT_EMPTY,
                        STATUS_1, EMPTY
                ),
                (i) -> Util.getOrDefaultCollection(
                        ImmutableMap.of(
                                STATUS_0, ImmutableList.of(
                                        new RoomWithBlocks<>(
                                                ROOM_A,
                                                ImmutableMap.of("Position1", "Pumpkin")
                                        )
                                )
                        ),
                        i,
                        ImmutableList.of()
                ),
                ImmutableMap.of(),
                (p) -> Util.getOrDefault(
                        ImmutableMap.of(
                                "Position1", AbstractWorkStatusStore.State.freshAtState(0)
                        ),
                        p,
                        AbstractWorkStatusStore.State.fresh()
                ),
                (p) -> true,
                s -> true, // We have the required tools
                2
        );
        Assertions.assertFalse(result.roomsWhereWorkCanBeDoneByEntity.isEmpty());
        Assertions.assertEquals(1, result.roomsWhereWorkCanBeDoneByEntity.get(STATUS_0).size());
        Assertions.assertFalse(result.roomsWhereSuppliesCanBeUsed.isEmpty());
        Assertions.assertEquals(1, result.roomsWhereSuppliesCanBeUsed.get(STATUS_0).size());
        Assertions.assertEquals(ROOM_A, result.roomsWhereWorkCanBeDoneByEntity.get(STATUS_0).get(0));
        Assertions.assertEquals(ROOM_A, result.roomsWhereSuppliesCanBeUsed.get(STATUS_0).get(0));
    }

    @Test
    void shouldHandle_WhenJobRequiresToolAtState0_AndIngredientAtState1_AndRoomHasState1() {
        TownNeedsMap<ProductionStatus> result = TownNeeds.getRoomsNeedingIngredientsOrTools(
                ImmutableMap.of(
                        STATUS_0, EMPTY, // Empty requirements at state 0
                        STATUS_1, NOT_EMPTY
                ), ImmutableMap.of(
                        STATUS_0, 0, // Zero ingredients required
                        STATUS_1, 1 // One
                ), ImmutableMap.of(
                        STATUS_0, NOT_EMPTY,
                        STATUS_1, EMPTY
                ),
                (i) -> Util.getOrDefaultCollection(
                        ImmutableMap.of(
                                STATUS_1, ImmutableList.of(
                                        new RoomWithBlocks<>(
                                                ROOM_A,
                                                ImmutableMap.of("Position1", "Pumpkin")
                                        )
                                )
                        ),
                        i,
                        ImmutableList.of()
                ),
                ImmutableMap.of(),
                (p) -> Util.getOrDefault(
                        ImmutableMap.of(
                                "Position1", AbstractWorkStatusStore.State.freshAtState(1)
                        ),
                        p,
                        AbstractWorkStatusStore.State.fresh()
                ),
                (p) -> true,
                s -> ImmutableMap.of(
                        STATUS_0, true // We have the tools required for status 0
                        // Other status have no tool requirements (null)
                ).get(s),
                2
        );
        Assertions.assertFalse(result.roomsWhereSuppliesCanBeUsed.isEmpty());
        Assertions.assertEquals(1, result.roomsWhereSuppliesCanBeUsed.get(STATUS_1).size());
        Assertions.assertFalse(result.roomsWhereWorkCanBeDoneByEntity.isEmpty());
        Assertions.assertEquals(1, result.roomsWhereWorkCanBeDoneByEntity.get(STATUS_1).size());
        Assertions.assertEquals(ROOM_A, result.roomsWhereSuppliesCanBeUsed.get(STATUS_1).get(0));
        Assertions.assertEquals(ROOM_A, result.roomsWhereWorkCanBeDoneByEntity.get(STATUS_1).get(0));
    }

    @Test
    void shouldHandle_WhenJobRequiresToolAtState0_AndRoomHasState0_ButMissingTools() {
        @NotNull TownNeedsMap<ProductionStatus> result = job(false, true, STATUS_0, false);
        Assertions.assertFalse(result.roomsWhereWorkCanBeDoneByEntity.isEmpty());
        Assertions.assertTrue(result.roomsWhereWorkCanBeDoneByEntity.get(STATUS_0).isEmpty());
        Assertions.assertFalse(result.roomsWhereSuppliesCanBeUsed.isEmpty());
        Assertions.assertFalse(result.roomsWhereSuppliesCanBeUsed.get(STATUS_0).isEmpty());
        Assertions.assertEquals(ROOM_A, result.roomsWhereSuppliesCanBeUsed.get(STATUS_0).get(0));
    }

    @Test
    void shouldHandle_WhenJobRequiresToolAtState0_AndNoIngredientsAtState0_AndRoomHasState0_AndHasTools() {
        TownNeedsMap<ProductionStatus> result = TownNeeds.getRoomsNeedingIngredientsOrTools(
                ImmutableMap.of(
                        STATUS_0, EMPTY,
                        STATUS_1, EMPTY,
                        STATUS_2, EMPTY
                ), ImmutableMap.of(
                        STATUS_0, 0, // No ingredient required
                        STATUS_1, 0,
                        STATUS_2, 0
                ), ImmutableMap.of(
                        STATUS_0, NOT_EMPTY,
                        STATUS_1, NOT_EMPTY,
                        STATUS_2, NOT_EMPTY
                ),
                (i) -> ImmutableList.of(new RoomWithBlocks<>(
                        ROOM_A,
                        ImmutableMap.of("Position1", "Pumpkin")
                )),
                ImmutableMap.of(),
                (p) -> ImmutableMap.of(
                        "Position1", AbstractWorkStatusStore.State.freshAtState(0)
                ).get(p),
                (p) -> true,
                s -> true, // Tools are in inventory (or town)
                2
        );
        Assertions.assertFalse(result.roomsWhereSuppliesCanBeUsed.isEmpty());
        Assertions.assertFalse(result.roomsWhereSuppliesCanBeUsed.get(STATUS_0).isEmpty());
        // TODO: Fill out assertions
    }

    @Test
    void shouldHandle_WhenJobRequiresToolAtState0_AndNullIngredientsAtState0_AndRoomHasState0_AndHasTools() {
        TownNeedsMap<ProductionStatus> result = TownNeeds.getRoomsNeedingIngredientsOrTools(
                ImmutableMap.of(
                        // Null ingredients
                ), ImmutableMap.of(
                        STATUS_0, 0, // No ingredient required
                        STATUS_1, 0,
                        STATUS_2, 0
                ), ImmutableMap.of(
                        STATUS_0, NOT_EMPTY,
                        STATUS_1, NOT_EMPTY,
                        STATUS_2, NOT_EMPTY
                ),
                (i) -> ImmutableList.of(new RoomWithBlocks<>(
                        ROOM_A,
                        ImmutableMap.of("Position1", "Pumpkin")
                )),
                ImmutableMap.of(),
                (p) -> ImmutableMap.of(
                        "Position1", AbstractWorkStatusStore.State.freshAtState(0)
                ).get(p),
                (p) -> true,
                s -> true, // Tools are in inventory (or town)
                2
        );
        Assertions.assertFalse(result.roomsWhereSuppliesCanBeUsed.isEmpty());
        Assertions.assertFalse(result.roomsWhereSuppliesCanBeUsed.get(STATUS_0).isEmpty());
        // TODO: Fill out assertions
    }

    @Test
    void shouldHandle_WhenJobRequiresToolAtState0_AndRequiresIngredientsAtState1_AndRoomHasState1_AndHasTools() {
        TownNeedsMap<ProductionStatus> result = TownNeeds.getRoomsNeedingIngredientsOrTools(
                ImmutableMap.of(
                        STATUS_1, NOT_EMPTY
                ), ImmutableMap.of(
                        STATUS_0, 0, // No ingredient required
                        STATUS_1, 1,
                        STATUS_2, 0
                ), ImmutableMap.of(
                        STATUS_0, NOT_EMPTY
                ),
                (i) -> Util.getOrDefault(ImmutableMap.of(
                        STATUS_1, ImmutableList.of(new RoomWithBlocks<>(
                                ROOM_A,
                                ImmutableMap.of("Position1", "Pumpkin")
                        ))
                ), i, ImmutableList.of()),
                ImmutableMap.of(),
                (p) -> ImmutableMap.of(
                        "Position1", AbstractWorkStatusStore.State.freshAtState(1)
                ).get(p),
                (p) -> true,
                s -> true, // Tools are in inventory (or town)
                2
        );
        Assertions.assertFalse(result.roomsWhereSuppliesCanBeUsed.isEmpty());
        Assertions.assertTrue(result.roomsWhereSuppliesCanBeUsed.get(STATUS_0).isEmpty());
        Assertions.assertFalse(result.roomsWhereSuppliesCanBeUsed.get(STATUS_1).isEmpty());
        // TODO: Fill out assertions
    }
    @Test
    void shouldHandle_WhenJobRequiresToolAtState0_AndNullIngredientsAtState1_AndRoomHasState1_AndHasTools_AndSpecialRoomsAtState1() {
        TownNeedsMap<ProductionStatus> result = TownNeeds.getRoomsNeedingIngredientsOrTools(
                ImmutableMap.of(
                        // Null
                ), ImmutableMap.of(
                        STATUS_0, 0, // No ingredient required
                        STATUS_1, 1,
                        STATUS_2, 0
                ), ImmutableMap.of(
                        STATUS_0, NOT_EMPTY
                ),
                (i) -> Util.getOrDefault(ImmutableMap.of(
                        STATUS_1, ImmutableList.of(new RoomWithBlocks<>(
                                ROOM_A,
                                ImmutableMap.of("Position1", "Pumpkin")
                        ))
                ), i, ImmutableList.of()),
                ImmutableMap.of(
                        STATUS_1, ImmutableList.of(ROOM_A)
                ),
                (p) -> ImmutableMap.of(
                        "Position1", AbstractWorkStatusStore.State.freshAtState(1)
                ).get(p),
                (p) -> true,
                s -> true, // Tools are in inventory (or town)
                2
        );
        Assertions.assertFalse(result.roomsWhereSuppliesCanBeUsed.isEmpty());
        Assertions.assertTrue(result.roomsWhereSuppliesCanBeUsed.get(STATUS_0).isEmpty());
        Assertions.assertFalse(result.roomsWhereSuppliesCanBeUsed.get(STATUS_1).isEmpty());
        // TODO: Fill out assertions
    }
}