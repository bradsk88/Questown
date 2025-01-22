package ca.bradj.questown.town.quests;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

class QuestGardenTest {

    private static class TestQuestGarden extends AbstractQuestGarden<Set<String>, String> {

        public TestQuestGarden(
                int idealTicks,
                int maxTicks,
                int maxCost
        ) {
            super(idealTicks, maxTicks, maxCost);
        }

        @Override
        protected Set<String> getEmptyBatch() {
            return new HashSet<>();
        }

        @Override
        protected String getRandomRoom(Collection<String> rooms) {
            return rooms.iterator().next();
        }

        @Override
        protected boolean questAlreadyRequested(
                Set<String> strings,
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
                Set<String> strings,
                String next
        ) {
            strings.add(next);
        }

        @Override
        protected void addBedQuest(
                UUID ownerUUID,
                Set<String> strings
        ) {
            strings.add("bed");
        }
    }

    @Test
    public void testShouldAcceptBed_IfNeedMoreBed() {
        AbstractQuestGarden<Set<String>, String> qg = new TestQuestGarden(
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
        AbstractQuestGarden<Set<String>, String> qg = new TestQuestGarden(
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE
        );
        Assertions.assertTrue(qg.grow(
                () -> true,
                () -> ImmutableList.of(new RoomNeed<>("baker", 1)),
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
        AbstractQuestGarden<Set<String>, String> qg = new TestQuestGarden(
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
                        new RoomNeed<>("baker", 1),
                        new RoomNeed<>("mineshaft", 1)
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
        AbstractQuestGarden<Set<String>, String> qg = new TestQuestGarden(
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
        AbstractQuestGarden<Set<String>, String> qg = new TestQuestGarden(0, 2, Integer.MAX_VALUE) {
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
                ImmutableSet.of(
                        "storeroom"
                ), qg.get()
        );
    }

    @Test
    public void testShouldStopAfterCostExceeded() {
        AbstractQuestGarden<Set<String>, String> qg = new TestQuestGarden(0, Integer.MAX_VALUE, 2) {
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
                ImmutableSet.of(
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

        AbstractQuestGarden<Set<String>, String> qg = new TestQuestGarden(2, Integer.MAX_VALUE, 5) {
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

    // TODO[ASAP]: Avoid adding the same quest multiple times

}