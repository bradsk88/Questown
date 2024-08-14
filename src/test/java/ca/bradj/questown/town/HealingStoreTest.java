package ca.bradj.questown.town;

import ca.bradj.roomrecipes.core.space.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HealingStoreTest {

    private static final Position TEST_POSITION = new Position(0, 0);

    @Test
    public void testShouldHandleTickingWithNoAddedData() {
        HealingStore<Position> s = new HealingStore<Position>();
        for (int i = 0; i < 20; i++) {
            s.tick();
        }
        // Test passes if no exception thrown
    }

    @Test
    public void testShouldHaveInitialNoBoost() {
        HealingStore<Position> s = new HealingStore<Position>();
        Double initial = s.getHealFactor(TEST_POSITION);
        assertEquals(1, initial);
    }

    @Test
    public void testShouldBeBoosted() {
        HealingStore<Position> s = new HealingStore<Position>();
        s.addBoost(TEST_POSITION, "test", new TemporaryBoost(2, 100));
        Double boosted = s.getHealFactor(TEST_POSITION);
        assertEquals(2, boosted);
    }

    @Test
    public void testShouldBeBoostedFromGroundTruth() {
        HealingStore<Position> s = new HealingStore<Position>();
        s.putGroundTruth(TEST_POSITION, 5.0);
        s.addBoost(TEST_POSITION, "test", new TemporaryBoost(2, 100));
        Double boosted = s.getHealFactor(TEST_POSITION);
        assertEquals(10, boosted);
    }


    @Test
    public void testShouldStopBoosting() {
        HealingStore<Position> s = new HealingStore<Position>();
        int duration = 2;
        int factor = 2;
        s.addBoost(TEST_POSITION, "test", new TemporaryBoost(factor, duration));
        Double boosted = s.getHealFactor(TEST_POSITION);
        assertEquals(2, boosted);
        s.tick();
        boosted = s.getHealFactor(TEST_POSITION);
        assertEquals(2, boosted);
        s.tick();
        boosted = s.getHealFactor(TEST_POSITION);
        assertEquals(1, boosted);
        s.tick();
    }

    @Test
    public void testShouldHandleStoppedBoost() {
        HealingStore<Position> s = new HealingStore<Position>();
        int duration = 1;
        int factor = 2;
        s.addBoost(TEST_POSITION, "test", new TemporaryBoost(factor, duration));
        for (int i = 0; i < 20; i++) {
            s.tick();
        }
        // Test passes if no exception thrown
    }


}