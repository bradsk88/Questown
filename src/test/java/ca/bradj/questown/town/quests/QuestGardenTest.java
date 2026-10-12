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
        protected boolean isOvergrown() {
            return false;
        }

        @Override
        protected int bedQuestCount(List<String> strings) {
            return (int) strings.stream().filter(s -> s.equals("bed")).count();
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
                () -> 1,
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

    // The needed-rooms 0/1/2 paths are covered by the tests below:
    // ...AndNeededRoomsAreSize0 (falls back to a random recipe),
    // ...AndNeededRoomsAreSize1 (takes the single needed room),
    // ...AndNeededRoomsAreSize2 (picks randomly among the needed rooms).

    @Test
    public void testShouldAcceptNeededRoom_IfDoNotNeedBed_AndNeededRoomsAreSize1() {
        AbstractQuestGarden<List<String>, String> qg = new TestQuestGarden(
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE
        );
        Assertions.assertTrue(qg.grow(
                () -> 0,
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
                () -> 0,
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
                () -> 0,
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
                () -> 0,
                ImmutableList::of, // No needed rooms
                () -> ImmutableList.of(
                        "kitchen",
                        "storeroom",
                        "soup_kitchen"
                )
        ));
        Assertions.assertTrue(qg.grow(
                () -> 0,
                ImmutableList::of, // No needed rooms
                () -> ImmutableList.of(
                        "kitchen",
                        "storeroom",
                        "soup_kitchen"
                )
        ));
        Assertions.assertFalse(qg.grow(
                () -> 0,
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
                () -> 0,
                ImmutableList::of, // No needed rooms
                () -> ImmutableList.of(
                        "kitchen",
                        "storeroom",
                        "soup_kitchen"
                )
        ));
        Assertions.assertTrue(qg.grow(
                () -> 0,
                ImmutableList::of, // No needed rooms
                () -> ImmutableList.of(
                        "kitchen",
                        "storeroom",
                        "soup_kitchen"
                )
        ));
        Assertions.assertFalse(qg.grow(
                () -> 0,
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
                () -> 0,
                ImmutableList::of, // No needed rooms
                () -> allRooms
        ));
        Assertions.assertEquals(0, qg.getCostSoFar());

        Assertions.assertTrue(qg.grow(
                () -> 0,
                ImmutableList::of, // No needed rooms
                () -> allRooms
        ));
        Assertions.assertEquals(1, qg.getCostSoFar());
    }

    @Test
    public void testShouldAcceptOneBedPerGrowTick_WhenOnlyOneMissing() {

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
                () -> 1, // Not enough beds
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
                () -> 1, // Not enough beds
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

    /**
     * Day-1 playtest: the town was under-bedded because the garden posted at most one bed
     * quest per batch. Now it posts one bed quest per grow tick until the batch covers the
     * actual shortfall (here 3), then switches to room quests.
     */
    @Test
    public void testShouldAcceptMultipleBeds_UntilShortfallCovered() {
        AbstractQuestGarden<List<String>, String> qg = new TestQuestGarden(
                0, Integer.MAX_VALUE, Integer.MAX_VALUE
        ) {
            @Override
            protected String getRandomRoom(Collection<String> rooms) {
                return rooms.iterator().next(); // "soup_pot"
            }
        };
        ImmutableList<String> allRooms = ImmutableList.of("soup_pot");
        // 3 beds missing: each grow posts a bed until 3 are in the batch...
        Assertions.assertTrue(qg.grow(() -> 3, ImmutableList::of, () -> allRooms));
        Assertions.assertIterableEquals(ImmutableList.of("bed"), qg.get());
        Assertions.assertTrue(qg.grow(() -> 3, ImmutableList::of, () -> allRooms));
        Assertions.assertIterableEquals(ImmutableList.of("bed", "bed"), qg.get());
        Assertions.assertTrue(qg.grow(() -> 3, ImmutableList::of, () -> allRooms));
        Assertions.assertIterableEquals(ImmutableList.of("bed", "bed", "bed"), qg.get());
        // ...then, with the shortfall covered, the next grow posts a room quest.
        Assertions.assertTrue(qg.grow(() -> 3, ImmutableList::of, () -> allRooms));
        Assertions.assertIterableEquals(ImmutableList.of("bed", "bed", "bed", "soup_pot"), qg.get());
    }

    // Duplicates were previously rejected by this TODO; the run-16 kitchen x3
    // batch turned it into a live defect - the three tests below pin the rule.

    /**
     * Run-16 shape: the cook's stock jobs keep the kitchen in the room-need
     * queue (one RoomNeed, timesNeeded > 1), so the needed-room path re-picks
     * the same recipe every grow tick. A batch carries unique requests - the
     * repeat waits for a later batch, it does not fill this one.
     */
    @Test
    public void testShouldAddNeededRoomOnce_EvenWhenNeededMultipleTimes() {
        AbstractQuestGarden<List<String>, String> qg = new TestQuestGarden(
                0, Integer.MAX_VALUE, Integer.MAX_VALUE
        ) {
            @Override
            protected boolean questAlreadyRequested(List<String> batch, String id) {
                return batch.contains(id);
            }
        };
        for (int i = 0; i < 3; i++) {
            Assertions.assertTrue(qg.grow(
                    () -> 0,
                    () -> ImmutableList.of(new RoomNeed<>("kitchen", 3, 3)),
                    () -> ImmutableList.of("kitchen", "armory", "bedroom")
            ));
        }
        Assertions.assertIterableEquals(ImmutableList.of("kitchen"), qg.get());
    }

    @Test
    public void testShouldFallBackToRandomRoom_WhenAllNeededRoomsAreAlreadyRequested() {
        AbstractQuestGarden<List<String>, String> qg = new TestQuestGarden(
                0, Integer.MAX_VALUE, Integer.MAX_VALUE
        ) {
            @Override
            protected String getRandomRoom(Collection<String> rooms) {
                return ImmutableList.copyOf(rooms).get(1);
            }

            @Override
            protected boolean questAlreadyRequested(List<String> batch, String id) {
                return batch.contains(id);
            }
        };
        Assertions.assertTrue(qg.grow(
                () -> 0,
                () -> ImmutableList.of(new RoomNeed<>("kitchen", 3, 3)),
                () -> ImmutableList.of("kitchen", "armory", "bedroom")
        ));
        // kitchen is already in the batch - the next grow must pick a fresh room
        Assertions.assertTrue(qg.grow(
                () -> 0,
                () -> ImmutableList.of(new RoomNeed<>("kitchen", 3, 3)),
                () -> ImmutableList.of("kitchen", "armory", "bedroom")
        ));
        Assertions.assertIterableEquals(
                ImmutableList.of("kitchen", "armory"), qg.get()
        );
    }

    @Test
    public void testShouldNotRepeatRandomRoomInBatch() {
        AbstractQuestGarden<List<String>, String> qg = new TestQuestGarden(
                0, Integer.MAX_VALUE, Integer.MAX_VALUE
        ) {
            @Override
            protected boolean questAlreadyRequested(List<String> batch, String id) {
                return batch.contains(id);
            }
        };
        // random pick is deterministic (index 0) - every grow re-picks "kitchen"
        for (int i = 0; i < 3; i++) {
            Assertions.assertTrue(qg.grow(
                    () -> 0,
                    ImmutableList::of, // No needed rooms
                    () -> ImmutableList.of("kitchen", "armory")
            ));
        }
        Assertions.assertIterableEquals(ImmutableList.of("kitchen"), qg.get());
    }
}