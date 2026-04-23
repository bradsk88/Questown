package ca.bradj.questown.mobs.helperchicken;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link ChickenArcBubbles#forState}. The mapping is authored and
 * purely static; these tests protect against accidental regressions when
 * beat states are added or reordered.
 *
 * <p>Some beats use mod-registered {@code ItemsInit.*} registry objects
 * which are null outside the Forge mod-loading lifecycle. Those states are
 * covered by a {@code TODO_} failing assertion per CLAUDE.md rather than
 * simulated with vanilla placeholders — reaching them in tests requires a
 * full integration harness. The coverage below exercises every beat whose
 * icons come from vanilla {@code Items.*} plus the two
 * {@link ChickenArcBubbles.Bubble#HIDDEN} terminal states.
 */
class ChickenArcBubblesTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void complete_hidesBubble() {
        ChickenArcBubbles.Bubble b = ChickenArcBubbles.forState(ChickenBeatState.COMPLETE);
        Assertions.assertTrue(b.iconA().isEmpty());
        Assertions.assertTrue(b.iconB().isEmpty());
        Assertions.assertFalse(b.throughWalls());
    }

    @Test
    void forfeit_hidesBubble() {
        ChickenArcBubbles.Bubble b = ChickenArcBubbles.forState(ChickenBeatState.FORFEIT);
        Assertions.assertTrue(b.iconA().isEmpty());
        Assertions.assertTrue(b.iconB().isEmpty());
    }

    @Test
    void waitingForStick_showsOneVanillaIcon() {
        ChickenArcBubbles.Bubble b = ChickenArcBubbles.forState(
                ChickenBeatState.WAITING_FOR_STICK
        );
        Assertions.assertFalse(b.iconA().isEmpty());
        Assertions.assertTrue(b.iconB().isEmpty());
        Assertions.assertFalse(b.throughWalls());
    }

    @Test
    void sunsetAndMap_showsTwoVanillaIcons() {
        ChickenArcBubbles.Bubble b = ChickenArcBubbles.forState(
                ChickenBeatState.SUNSET_AND_MAP
        );
        Assertions.assertFalse(b.iconA().isEmpty());
        Assertions.assertFalse(b.iconB().isEmpty());
        Assertions.assertFalse(b.throughWalls());
    }

    @Test
    void wallBlock_showsOneVanillaIcon() {
        ChickenArcBubbles.Bubble b = ChickenArcBubbles.forState(
                ChickenBeatState.WAITING_FOR_WALL_BLOCK
        );
        Assertions.assertFalse(b.iconA().isEmpty());
    }

    @Test
    void door_showsOneVanillaIcon() {
        ChickenArcBubbles.Bubble b = ChickenArcBubbles.forState(
                ChickenBeatState.WAITING_FOR_DOOR
        );
        Assertions.assertFalse(b.iconA().isEmpty());
    }

    @Test
    void sign_showsOneVanillaIcon() {
        ChickenArcBubbles.Bubble b = ChickenArcBubbles.forState(
                ChickenBeatState.WAITING_FOR_SIGN
        );
        Assertions.assertFalse(b.iconA().isEmpty());
    }

    @Test
    void chest_showsOneVanillaIcon() {
        ChickenArcBubbles.Bubble b = ChickenArcBubbles.forState(
                ChickenBeatState.WAITING_FOR_CHEST
        );
        Assertions.assertFalse(b.iconA().isEmpty());
    }

    @Test
    void villagerUi_showsOneVanillaIcon() {
        ChickenArcBubbles.Bubble b = ChickenArcBubbles.forState(
                ChickenBeatState.WAITING_FOR_VILLAGER_UI
        );
        Assertions.assertFalse(b.iconA().isEmpty());
    }

    // -------------------------------------------------------------------------
    // Mod-registered-item beats: these icons come from ItemsInit / BlocksInit
    // RegistryObjects whose .get() returns null outside the Forge mod-loading
    // lifecycle. Covered by in-game verification per CLAUDE.md.
    // -------------------------------------------------------------------------

    @Test
    void TODO_modRegisteredBeats_bubbleShapeVerifiedInGame() {
        Assertions.fail(
                "TODO[U4]: WAITING_FOR_WAND_ON_CAMPFIRE, WAITING_FOR_WAND_ON_DOOR, " +
                        "WAITING_FOR_PRESSURE_PLATE, WAITING_FOR_FLAG_UI, and " +
                        "AWAITING_WORLDLY_SEEDS_DELIVERY icons reference mod-registered " +
                        "RegistryObjects whose .get() is null under a bare unit-test bootstrap. " +
                        "Shape verified in-game: two-icon alternation for the two wand-on-X " +
                        "beats, single icon for PRESSURE_PLATE and FLAG_UI, and " +
                        "through-walls=true for AWAITING_WORLDLY_SEEDS_DELIVERY."
        );
    }
}
