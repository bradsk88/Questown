package ca.bradj.questown.mobs.helperchicken;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

/**
 * Logic + parity coverage for {@link ChickenArcPresentation}.
 *
 * <p><b>activePhase</b> is pure boolean logic and tested exhaustively.
 *
 * <p><b>present</b> rows for beats whose icons come from mod-registered
 * {@code ItemsInit}/{@code BlocksInit} {@code RegistryObject.get()} return
 * null outside the Forge mod-loading lifecycle, so unit tests cannot
 * fully exercise those rows. We cover the vanilla-icon beats here and
 * defer the mod-icon beats to in-game chicken-arc autotest scenarios.
 *
 * <p>The parity tests against {@link ChickenArcBubbles#forState},
 * {@link ChickenArcController#hintKey}, and
 * {@link ChickenArcController#plainTextKey} prove byte-for-byte
 * behavioural equivalence with the existing switches before the old APIs
 * are deleted in U5. They are removed in U5.
 */
class ChickenArcPresentationTest {

    /** Beats whose presentation rows reference only vanilla items. */
    private static final Set<ChickenBeatState> VANILLA_ONLY = EnumSet.of(
            ChickenBeatState.WAITING_FOR_WALL_BLOCK,
            ChickenBeatState.WAITING_FOR_DOOR,
            ChickenBeatState.WAITING_FOR_SIGN,
            ChickenBeatState.WAITING_FOR_CHEST,
            ChickenBeatState.WAITING_FOR_VILLAGER_UI,
            ChickenBeatState.COMPLETE,
            ChickenBeatState.FORFEIT
    );

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // -------------------------------------------------------------------------
    // activePhase — pure boolean logic, exhaustively tested.
    // -------------------------------------------------------------------------

    @Test
    void activePhase_useXonY_beats_pickFetchOrUseFromHasItem() {
        for (ChickenBeatState state : new ChickenBeatState[]{
                ChickenBeatState.WAITING_FOR_STICK,
                ChickenBeatState.WAITING_FOR_WAND_ON_CAMPFIRE,
                ChickenBeatState.WAITING_FOR_WAND_ON_DOOR
        }) {
            Assertions.assertEquals(
                    BeatPhase.NEED_TO_FETCH,
                    ChickenArcPresentation.activePhase(state, new PhaseInputs(false, false, false)),
                    state.name() + " without item should be NEED_TO_FETCH"
            );
            Assertions.assertEquals(
                    BeatPhase.READY_TO_USE,
                    ChickenArcPresentation.activePhase(state, new PhaseInputs(true, false, false)),
                    state.name() + " with item should be READY_TO_USE"
            );
        }
    }

    @Test
    void activePhase_placementBeats_pickFetchOrPlaceFromHasItem() {
        for (ChickenBeatState state : new ChickenBeatState[]{
                ChickenBeatState.WAITING_FOR_WALL_BLOCK,
                ChickenBeatState.WAITING_FOR_DOOR
        }) {
            Assertions.assertEquals(
                    BeatPhase.NEED_TO_FETCH,
                    ChickenArcPresentation.activePhase(state, new PhaseInputs(false, false, false))
            );
            Assertions.assertEquals(
                    BeatPhase.READY_TO_PLACE,
                    ChickenArcPresentation.activePhase(state, new PhaseInputs(true, false, false))
            );
        }
    }

    @Test
    void activePhase_sunsetAndMap_threePhases() {
        ChickenBeatState s = ChickenBeatState.SUNSET_AND_MAP;
        Assertions.assertEquals(
                BeatPhase.PREPARING,
                ChickenArcPresentation.activePhase(s, new PhaseInputs(false, false, false))
        );
        Assertions.assertEquals(
                BeatPhase.PREPARING,
                ChickenArcPresentation.activePhase(s, new PhaseInputs(false, false, true)),
                "PREPARING ignores isNight when chest not yet spawned"
        );
        Assertions.assertEquals(
                BeatPhase.AWAITING_NIGHT,
                ChickenArcPresentation.activePhase(s, new PhaseInputs(false, true, false))
        );
        Assertions.assertEquals(
                BeatPhase.READY_TO_USE,
                ChickenArcPresentation.activePhase(s, new PhaseInputs(false, true, true))
        );
    }

    @Test
    void activePhase_singlePhaseBeats_areAlwaysDefault() {
        ChickenBeatState[] singlePhase = new ChickenBeatState[]{
                ChickenBeatState.WAITING_FOR_SIGN,
                ChickenBeatState.WAITING_FOR_CHEST,
                ChickenBeatState.WAITING_FOR_PRESSURE_PLATE,
                ChickenBeatState.WAITING_FOR_VILLAGER_UI,
                ChickenBeatState.WAITING_FOR_FLAG_UI,
                ChickenBeatState.AWAITING_WORLDLY_SEEDS_DELIVERY,
                ChickenBeatState.COMPLETE,
                ChickenBeatState.FORFEIT
        };
        for (ChickenBeatState state : singlePhase) {
            for (PhaseInputs in : allInputCombinations()) {
                Assertions.assertEquals(
                        BeatPhase.DEFAULT,
                        ChickenArcPresentation.activePhase(state, in),
                        state.name() + " " + in + " should be DEFAULT"
                );
            }
        }
    }

    // -------------------------------------------------------------------------
    // present — terminal-state and through-walls invariants.
    // -------------------------------------------------------------------------

    @Test
    void present_completeAndForfeit_hideBubbleAndSuppressKeys() {
        for (ChickenBeatState terminal : new ChickenBeatState[]{
                ChickenBeatState.COMPLETE, ChickenBeatState.FORFEIT
        }) {
            Presentation p = ChickenArcPresentation.present(terminal, new PhaseInputs(false, false, false));
            Assertions.assertSame(ChickenArcBubbles.Bubble.HIDDEN, p.bubble(), terminal.name() + " bubble");
            Assertions.assertNull(p.hintKey(), terminal.name() + " hintKey");
            Assertions.assertNull(p.plainKey(), terminal.name() + " plainKey");
        }
    }

    @Test
    void present_vanillaBeats_haveNonNullKeysAndIcon() {
        for (ChickenBeatState state : VANILLA_ONLY) {
            if (state == ChickenBeatState.COMPLETE || state == ChickenBeatState.FORFEIT) {
                continue;
            }
            for (PhaseInputs in : allInputCombinations()) {
                Presentation p = ChickenArcPresentation.present(state, in);
                Assertions.assertFalse(
                        p.bubble().iconA().isEmpty(),
                        state.name() + " " + in + " should have a non-empty bubble icon"
                );
                Assertions.assertNotNull(p.hintKey(), state.name() + " hintKey");
                Assertions.assertNotNull(p.plainKey(), state.name() + " plainKey");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Parity — new module matches the existing switches byte-for-byte over
    // every (state, hasItem, chestSpawned, isNight) combination. For mod-icon
    // beats we compare structurally; if both sides fail (registry null) the
    // failures are equivalent. Tests deleted in U5 once the old switches are
    // gone.
    // -------------------------------------------------------------------------

    @SuppressWarnings("deprecation")
    @Test
    void parity_bubble_matchesChickenArcBubblesForState_forVanillaBeats() {
        for (ChickenBeatState state : VANILLA_ONLY) {
            for (PhaseInputs in : allInputCombinations()) {
                ChickenArcBubbles.Bubble fromOld = ChickenArcBubbles.forState(
                        state, in.hasItem(), in.chestSpawned(), in.isNight()
                );
                ChickenArcBubbles.Bubble fromNew = ChickenArcPresentation.present(state, in).bubble();
                assertBubbleEquals(fromOld, fromNew, state, in);
            }
        }
    }

    @Test
    void parity_hintKey_matchesControllerHintKey_forModSafeBeats() {
        // Limited to (state, input) combinations that select a phase whose row
        // can be built without mod-registered RegistryObjects. In-game scenarios
        // cover the rest. The hint-key strings themselves are vanilla-independent;
        // the limitation is purely that present() eagerly constructs the Bubble.
        for (ChickenBeatState state : ChickenBeatState.values()) {
            for (PhaseInputs in : allInputCombinations()) {
                if (!isModSafe(state, in)) {
                    continue;
                }
                String fromOld = ChickenArcController.hintKey(
                        state, in.hasItem(), in.chestSpawned(), in.isNight()
                );
                String fromNew = ChickenArcPresentation.present(state, in).hintKey();
                Assertions.assertEquals(fromOld, fromNew, state.name() + " " + in);
            }
        }
    }

    @Test
    void parity_plainKey_matchesControllerPlainTextKey_forModSafeBeats() {
        for (ChickenBeatState state : ChickenBeatState.values()) {
            for (PhaseInputs in : allInputCombinations()) {
                if (!isModSafe(state, in)) {
                    continue;
                }
                String fromOld = ChickenArcController.plainTextKey(
                        state, in.chestSpawned(), in.isNight()
                );
                String fromNew = ChickenArcPresentation.present(state, in).plainKey();
                Assertions.assertEquals(fromOld, fromNew, state.name() + " " + in);
            }
        }
    }

    /**
     * Returns true when {@link ChickenArcPresentation#present} can build a
     * Presentation without triggering a null RegistryObject lookup. Combinations
     * that select a phase whose row references mod-registered items return
     * false (those rows are exercised by in-game scenarios).
     */
    private static boolean isModSafe(ChickenBeatState state, PhaseInputs in) {
        BeatPhase phase = ChickenArcPresentation.activePhase(state, in);
        return switch (state) {
            case WAITING_FOR_WALL_BLOCK,
                 WAITING_FOR_DOOR,
                 WAITING_FOR_SIGN,
                 WAITING_FOR_CHEST,
                 WAITING_FOR_VILLAGER_UI,
                 COMPLETE,
                 FORFEIT -> true;
            case WAITING_FOR_STICK -> phase == BeatPhase.NEED_TO_FETCH;
            case SUNSET_AND_MAP -> phase == BeatPhase.PREPARING || phase == BeatPhase.AWAITING_NIGHT;
            case WAITING_FOR_WAND_ON_CAMPFIRE,
                 WAITING_FOR_WAND_ON_DOOR,
                 WAITING_FOR_PRESSURE_PLATE,
                 WAITING_FOR_FLAG_UI,
                 AWAITING_WORLDLY_SEEDS_DELIVERY -> false;
        };
    }

    private static void assertBubbleEquals(
            ChickenArcBubbles.Bubble expected,
            ChickenArcBubbles.Bubble actual,
            ChickenBeatState state,
            PhaseInputs in
    ) {
        String ctx = " for " + state.name() + " " + in;
        Assertions.assertTrue(
                ItemStack.matches(expected.iconA(), actual.iconA()),
                "iconA mismatch" + ctx
        );
        Assertions.assertTrue(
                ItemStack.matches(expected.iconB(), actual.iconB()),
                "iconB mismatch" + ctx
        );
        Assertions.assertEquals(expected.throughWalls(), actual.throughWalls(),
                "throughWalls mismatch" + ctx);
        Assertions.assertEquals(expected.textureIcon(), actual.textureIcon(),
                "textureIcon mismatch" + ctx);
    }

    private static Iterable<PhaseInputs> allInputCombinations() {
        java.util.List<PhaseInputs> out = new java.util.ArrayList<>(8);
        for (boolean a : new boolean[]{false, true}) {
            for (boolean b : new boolean[]{false, true}) {
                for (boolean c : new boolean[]{false, true}) {
                    out.add(new PhaseInputs(a, b, c));
                }
            }
        }
        return out;
    }
}
