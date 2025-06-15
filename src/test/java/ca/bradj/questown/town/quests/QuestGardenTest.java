package ca.bradj.questown.town.quests;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

class QuestGardenTest {

    private static class TestQuestGarden extends AbstractQuestGarden<List<String>, String> {

        public TestQuestGarden(
                int idealTicks,
                int maxTicks,
                int maxCost
        ) {
            super(idealTicks, maxTicks, maxCost);
        }

        @Override
        protected List<String> getEmptyBatch() {
            return new ArrayList<>();
        }

        @Override
        protected String getRandomRoom(Collection<String> rooms) {
            return rooms.iterator().next();
        }

        @Override
        protected boolean hasBedAlready(List<String> strings) {
            return batch.contains("bed");
        }

        @Override
        protected boolean questAlreadyRequested(
                List<String> strings,
                String s
        ) {
            return false;
        }

        @Override
        protected int getBedCost() {
            return 1;
        }

        @Override
        protected int getCost(String randomRoom) {
            return 1;
        }

        @Override
        protected void addQuest(
                List<String> strings,
                String next
        ) {
            strings.add(next);
        }

        @Override
        protected void addBedQuest(
                UUID ownerUUID,
                List<String> strings
        ) {
            strings.add("bed");
        }
    }

    @Test
    public void testShouldAcceptBed_IfNeedMoreBed() {
        AbstractQuestGarden<List<String>, String> qg = new TestQuestGarden(
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE
        );
        Assertions.assertTrue(qg.grow(
                () -> false,
                () -> {
                    Assertions.fail("Test should not access rooms needs");
                    return null;
                },
                () -> {
                    Assertions.fail("Test should not access recipes");
                    return null;
                }
        ));
        Assertions.assertIterableEquals(
                ImmutableSet.of(
                        "bed"
                ), qg.get()
        );
    }

    // TODO: Test 0, 1, 2

    @Test
    public void testShouldAcceptNeededRoom_IfDoNotNeedBed_AndNeededRoomsAreSize1() {
        AbstractQuestGarden<List<String>, String> qg = new TestQuestGarden(
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE
        );
        Assertions.assertTrue(qg.grow(
                () -> true,
                () -> ImmutableList.of(new RoomNeed<>("baker", 1, 1)),
                () -> {
                    Assertions.fail("Test should not access recipes");
                    return null;
                }
        ));
        Assertions.assertIterableEquals(
                ImmutableSet.of(
                        "baker"
                ), qg.get()
        );
    }

    @Test
    public void testShouldAcceptNeededRandomRoom_IfDoNotNeedBed_AndNeededRoomsAreSize2() {
        AbstractQuestGarden<List<String>, String> qg = new TestQuestGarden(
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE
        ) {
            @Override
            protected String getRandomRoom(Collection<String> rooms) {
                return ImmutableList.copyOf(rooms).get(1);
            }
        };
        Assertions.assertTrue(qg.grow(
                () -> true,
                () -> ImmutableList.of(
                        new RoomNeed<>("baker", 1, 1),
                        new RoomNeed<>("mineshaft", 1, 1)
                ),
                () -> {
                    Assertions.fail("Test should not access recipes");
                    return null;
                }
        ));
        Assertions.assertIterableEquals(
                ImmutableSet.of(
                        "mineshaft"
                ), qg.get()
        );
    }

    @Test
    public void testShouldAcceptRandomOtherRoom_IfDoNotNeedBed_AndNeededRoomsAreSize0() {
        AbstractQuestGarden<List<String>, String> qg = new TestQuestGarden(
                0,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE
        ) {
            @Override
            protected String getRandomRoom(Collection<String> rooms) {
                return ImmutableList.copyOf(rooms).get(1);
            }
        };
        // No needed rooms
        Assertions.assertTrue(qg.grow(
                () -> true,
                ImmutableList::of, // No needed rooms
                () -> ImmutableList.of(
                        "kitchen",
                        "storeroom",
                        "soup_kitchen"
                )
        ));
        Assertions.assertIterableEquals(
                ImmutableSet.of(
                        "storeroom"
                ), qg.get()
        );
    }

    @Test
    public void testShouldStopAfterMaxTicks() {
        AbstractQuestGarden<List<String>, String> qg = new TestQuestGarden(0, 2, Integer.MAX_VALUE) {
            @Override
            protected String getRandomRoom(Collection<String> rooms) {
                return ImmutableList.copyOf(rooms).get(1);
            }
        };
        Assertions.assertTrue(qg.grow(
                () -> true,
                ImmutableList::of, // No needed rooms
                () -> ImmutableList.of(
                        "kitchen",
                        "storeroom",
                        "soup_kitchen"
                )
        ));
        Assertions.assertTrue(qg.grow(
                () -> true,
                ImmutableList::of, // No needed rooms
                () -> ImmutableList.of(
                        "kitchen",
                        "storeroom",
                        "soup_kitchen"
                )
        ));
        Assertions.assertFalse(qg.grow(
                () -> true,
                ImmutableList::of, // No needed rooms
                () -> ImmutableList.of(
                        "kitchen",
                        "storeroom",
                        "soup_kitchen"
                )
        ));
        Assertions.assertIterableEquals(
                ImmutableList.of(
                        "storeroom",
                        "storeroom",
                        "storeroom"
                ), qg.get()
        );
    }

    @Test
    public void testShouldStopAfterCostExceeded() {
        AbstractQuestGarden<List<String>, String> qg = new TestQuestGarden(0, Integer.MAX_VALUE, 2) {
            @Override
            protected String getRandomRoom(Collection<String> rooms) {
                return ImmutableList.copyOf(rooms).get(1);
            }
        };
        Assertions.assertTrue(qg.grow(
                () -> true,
                ImmutableList::of, // No needed rooms
                () -> ImmutableList.of(
                        "kitchen",
                        "storeroom",
                        "soup_kitchen"
                )
        ));
        Assertions.assertTrue(qg.grow(
                () -> true,
                ImmutableList::of, // No needed rooms
                () -> ImmutableList.of(
                        "kitchen",
                        "storeroom",
                        "soup_kitchen"
                )
        ));
        Assertions.assertFalse(qg.grow(
                () -> true,
                ImmutableList::of, // No needed rooms
                () -> ImmutableList.of(
                        "kitchen",
                        "storeroom",
                        "soup_kitchen"
                )
        ));
        Assertions.assertIterableEquals(
                ImmutableList.of(
                        "storeroom",
                        "storeroom"
                ), qg.get()
        );
    }

    @Test
    public void testShouldNotAcceptSmallRoomUntilIdealTick() {

        ImmutableMap<String, Integer> costs = ImmutableMap.of(
                "soup_pot", 1,
                "deluxe_cafe", 3
        );

        AbstractQuestGarden<List<String>, String> qg = new TestQuestGarden(2, Integer.MAX_VALUE, 5) {
            @Override
            protected String getRandomRoom(Collection<String> rooms) {
                return ImmutableList.copyOf(rooms).get(0);
            }

            @Override
            protected int getCost(String randomRoom) {
                return costs.get(randomRoom);
            }
        };
        ImmutableList<String> allRooms = ImmutableList.of(
                "soup_pot",
                "deluxe_cafe"
        );
        Assertions.assertTrue(qg.grow(
                () -> true,
                ImmutableList::of, // No needed rooms
                () -> allRooms
        ));
        Assertions.assertEquals(0, qg.getCostSoFar());

        Assertions.assertTrue(qg.grow(
                () -> true,
                ImmutableList::of, // No needed rooms
                () -> allRooms
        ));
        Assertions.assertEquals(1, qg.getCostSoFar());
    }

    @Test
    public void testShouldAcceptMaxOneBed() {

        ImmutableMap<String, Integer> costs = ImmutableMap.of(
                "soup_pot", 1,
                "deluxe_cafe", 3
        );

        AbstractQuestGarden<List<String>, String> qg = new TestQuestGarden(0, Integer.MAX_VALUE, 5) {
            @Override
            protected String getRandomRoom(Collection<String> rooms) {
                return ImmutableList.copyOf(rooms).get(0);
            }

            @Override
            protected int getCost(String randomRoom) {
                return costs.get(randomRoom);
            }
        };
        ImmutableList<String> allRooms = ImmutableList.of(
                "soup_pot",
                "deluxe_cafe"
        );
        Assertions.assertTrue(qg.grow(
                () -> false, // Not enough beds
                ImmutableList::of, // No needed rooms
                () -> allRooms
        ));
        Assertions.assertEquals(1, qg.getCostSoFar());
        Assertions.assertIterableEquals(
                ImmutableSet.of(
                        "bed"
                ), qg.get()
        );

        Assertions.assertTrue(qg.grow(
                () -> false, // Not enough beds
                ImmutableList::of, // No needed rooms
                () -> allRooms
        ));
        Assertions.assertEquals(2, qg.getCostSoFar());
        Assertions.assertIterableEquals(
                ImmutableSet.of(
                        "bed",
                        "soup_pot"
                ), qg.get()
        );
    }

    // TODO: Test: Reject duplicates recipes until after reaching ideal threshold

}