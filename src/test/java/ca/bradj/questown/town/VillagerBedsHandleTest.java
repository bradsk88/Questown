package ca.bradj.questown.town;

import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VillagerBedsHandleTest {

    private static class TestVillagerBeds extends VillagerBedsHandle<Position, String, Void> {

        public TestVillagerBeds(
                ImmutableList<Position> bedHeads,
                ImmutableMap<Position, Double> healFactors,
                ImmutableMap<String, Integer> damageTicksLeft,
                ImmutableMap<String, ImmutableMap<Position, Double>> distance
        ) {
            super(
                    x -> bedHeads,
                    (x, p) -> healFactors.get(p),
                    (x, v) -> damageTicksLeft.get(v),
                    (v, p) -> distance.get(v).get(p),
                    String::toString
            );
        }

    }

    @Test
    public void testShouldAssignClosestBedsWhenObvious() {

        String villager1 = "V1";
        String villager2 = "V2";
        Position p1 = new Position(1, 1);
        Position p2 = new Position(-1, -1);

        VillagerBedsHandle<Position, String, Void> h = new TestVillagerBeds(
                ImmutableList.of(p1, p2),
                ImmutableMap.of(
                        p1, 1.0,
                        p2, 1.0
                ),
                ImmutableMap.of(
                        villager1, 0, // No one is damaged
                        villager2, 0
                ),
                ImmutableMap.of(
                        villager1, ImmutableMap.of(
                                p1, 5.0,
                                p2, 10.0
                        ),
                        villager2, ImmutableMap.of(
                                p1, 10.0,
                                p2, 5.0
                        )
                )
        );

        h.tick(null, ImmutableList.of(villager1, villager2));
        Position v1Bed = h.getBestBed(villager1);
        Position v2Bed = h.getBestBed(villager2);

        assertEquals(p1, v1Bed);
        assertEquals(p2, v2Bed);
    }

    @Test
    public void testShouldAssignMostHealingBedEvenIfFurthest() {
        String villager1 = "v1";
        String villager2 = "v2";
        Position p1 = new Position(1, 1);
        Position p2 = new Position(2, 2);

        VillagerBedsHandle<Position, String, Void> h = new TestVillagerBeds(
                ImmutableList.of(p1, p2),
                ImmutableMap.of(
                        p1, 1.0,
                        p2, 2.0 // Position 2 has a high rate of hungerUpdater
                ),
                ImmutableMap.of(
                        villager1, 1, // Villager 1 is damaged
                        villager2, 0
                ),
                ImmutableMap.of(
                        villager1, ImmutableMap.of(
                                p1, 5.0, // Villager 1 is closest to p1
                                p2, 10.0
                        ),
                        villager2, ImmutableMap.of(
                                p1, 10.0,
                                p2, 5.0 // Villager 2 is closest to p2
                        )
                )
        );

        h.tick(null, ImmutableList.of(villager1, villager2));
        Position v1Bed = h.getBestBed(villager1);
        Position v2Bed = h.getBestBed(villager2);

        assertEquals(p2, v1Bed); // Villager 1 prefers hungerUpdater spot over close spot
        assertEquals(p1, v2Bed);
    }

    @Test
    public void testShouldAssignHealingBedToMostDamagedVillager_1() {

        String villager1 = "V1";
        String villager2 = "V2";
        Position p1 = new Position(1, 1);
        Position p2 = new Position(2, 2);

        VillagerBedsHandle<Position, String, Void> h = new TestVillagerBeds(
                ImmutableList.of(p1, p2),
                ImmutableMap.of(
                        p1, 1.0,
                        p2, 2.0
                ),
                ImmutableMap.of(
                        villager1, 2,
                        villager2, 1
                ),
                ImmutableMap.of(
                        villager1, ImmutableMap.of(
                                p1, 5.0,
                                p2, 10.0
                        ),
                        villager2, ImmutableMap.of(
                                p1, 5.0,
                                p2, 10.0
                        )
                )
        );

        h.tick(null, ImmutableList.of(villager1, villager2));
        Position v1Bed = h.getBestBed(villager1);
        Position v2Bed = h.getBestBed(villager2);

        assertEquals(p2, v1Bed); // Villager 1 is most damage, so they take hungerUpdater spot over close spot
        assertEquals(p1, v2Bed);
    }
    @Test
    public void testShouldAssignHealingBedToMostDamagedVillager_2() {

        String villager1 = "V1";
        String villager2 = "V2";
        Position p1 = new Position(1, 1);
        Position p2 = new Position(2, 2);

        VillagerBedsHandle<Position, String, Void> h = new TestVillagerBeds(
                ImmutableList.of(p1, p2),
                ImmutableMap.of(
                        p1, 1.0,
                        p2, 2.0
                ),
                ImmutableMap.of(
                        villager1, 1,
                        villager2, 2
                ),
                ImmutableMap.of(
                        villager1, ImmutableMap.of(
                                p1, 5.0,
                                p2, 10.0
                        ),
                        villager2, ImmutableMap.of(
                                p1, 5.0,
                                p2, 10.0
                        )
                )
        );

        h.tick(null, ImmutableList.of(villager1, villager2));
        Position v1Bed = h.getBestBed(villager1);
        Position v2Bed = h.getBestBed(villager2);

        assertEquals(p1, v1Bed);
        assertEquals(p2, v2Bed); // Villager 2 is most damage, so they take hungerUpdater spot over close spot
    }

}