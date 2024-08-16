package ca.bradj.questown.town;

import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VillagerBedsHandleTest {

    @Test
    public void testShouldAssignClosestBedsWhenObvious() {
        UUID villager1 = UUID.randomUUID();
        UUID villager2 = UUID.randomUUID();
        Position p1 = new Position(1, 1);
        Position p2 = new Position(-1, -1);
        VillagerBedsHandle<Position, UUID, Void> h = new VillagerBedsHandle<>(
                (x) -> ImmutableList.of(p1, p2),
                (x, p) -> 1.0, // All beds have same heal factor for this test
                (x, id) -> 0, // Villagers are undamaged for this test
                (id, p) -> {
                    if (id.equals(villager1)) {
                        if (p.equals(p1)) {
                            // Villager 1 is closest to p1
                            return 5.0;
                        }
                        if (p.equals(p2)) {
                            return 10.0;
                        }
                    }
                    if (id.equals(villager2)) {
                        if (p.equals(p1)) {
                            return 10.0;
                        }
                        if (p.equals(p2)) {
                            // Villager 2 is closest to p2
                            return 5.0;
                        }
                    }
                    throw new AssertionError("Shouldn't get here");
                }
        );

        h.tick(null, ImmutableList.of(villager1, villager2));
        Position b1 = h.getBestBed(villager1);
        Position b2 = h.getBestBed(villager2);

        assertEquals(p1, b1);
        assertEquals(p2, b2);
    }

}