package ca.bradj.questown.mobs.helperchicken;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * End-to-end curriculum coverage for F1, F2, F3, F4 — all of which depend on
 * {@link ca.bradj.questown.town.entity.TownFlagBlockEntity}, a spawned
 * {@link HelperChickenEntity}, and a real {@code ServerLevel}. Without those
 * the sequencing is validated in-game via {@code /_qtdev}. The pure transition
 * logic is already covered by {@link ChickenArcTransitionsTest} and bubble
 * shape by {@link ChickenArcBubblesTest}.
 */
class CurriculumSequenceTest {

    @Test
    void TODO_f1_stickToCampfireLit() {
        Assertions.fail(
                "TODO[U6]: F1 end-to-end — spawn chicken, give player wand, light " +
                        "campfire via U7 wand branch, observe transition to " +
                        "SUNSET_AND_MAP. Not unit-testable."
        );
    }

    @Test
    void TODO_f4_seedsDeliveryRunsTransform() {
        Assertions.fail(
                "TODO[U6]: F4 end-to-end — drop Worldly Seeds into a town chest, " +
                        "observe through-walls bubble, right-click chicken with seeds, " +
                        "assert statue placed + beat state = COMPLETE + chicken " +
                        "discarded. Covered by /_qtdev in-game verification."
        );
    }

    @Test
    void TODO_f3_outOfOrderPlacementRespectsR7b() {
        Assertions.fail(
                "TODO[U6]: F3 out-of-order placement — place chest before sign, " +
                        "then place sign, assert both beats advance in one tick in " +
                        "enum-ordinal order."
        );
    }

    @Test
    void TODO_uiObservations_fireFromRealOpenPaths() {
        Assertions.fail(
                "TODO[U6]: verify TownVillagerUIs.openMenu fires " +
                        "ChickenArcUiObservations.markVillagerUiOpened and " +
                        "showMultiStatusUI fires markFlagUiOpened. Hooks live at the " +
                        "tail of each opener."
        );
    }
}
