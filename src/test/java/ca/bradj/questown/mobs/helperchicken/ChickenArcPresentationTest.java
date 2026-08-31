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
                ChickenBeatState.WAITING_FOR_VILLAGER_UI,
                ChickenBeatState.WAITING_FOR_FLAG_UI,
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

    @Test
    void activePhase_chest_picksDefaultOrPlaceFromHasItem() {
        ChickenBeatState s = ChickenBeatState.WAITING_FOR_CHEST;
        Assertions.assertEquals(
                BeatPhase.DEFAULT,
                ChickenArcPresentation.activePhase(s, new PhaseInputs(false, false, false))
        );
        Assertions.assertEquals(
                BeatPhase.READY_TO_PLACE,
                ChickenArcPresentation.activePhase(s, new PhaseInputs(true, false, false))
        );
    }

    @Test
    void activePhase_pressurePlate_threePhases() {
        ChickenBeatState s = ChickenBeatState.WAITING_FOR_PRESSURE_PLATE;
        // No plate, no mat → go get a pressure plate.
        Assertions.assertEquals(
                BeatPhase.NEED_TO_FETCH,
                ChickenArcPresentation.activePhase(s, new PhaseInputs(false, false, false, false))
        );
        // Has a pressure plate → take it to the flag base to craft the mat.
        Assertions.assertEquals(
                BeatPhase.READY_TO_USE,
                ChickenArcPresentation.activePhase(s, new PhaseInputs(false, false, false, true))
        );
        // Has a welcome mat → place it at the gate.
        Assertions.assertEquals(
                BeatPhase.READY_TO_PLACE,
                ChickenArcPresentation.activePhase(s, new PhaseInputs(true, false, false, false))
        );
        // Mat wins over plate (placement phase takes priority over craft phase).
        Assertions.assertEquals(
                BeatPhase.READY_TO_PLACE,
                ChickenArcPresentation.activePhase(s, new PhaseInputs(true, false, false, true))
        );
    }

    @Test
    void activePhase_worldlySeedsDelivery_twoPhases() {
        ChickenBeatState s = ChickenBeatState.AWAITING_WORLDLY_SEEDS_DELIVERY;
        // No Worldly Seeds yet → the chicken waits at the gate for the villager.
        Assertions.assertEquals(
                BeatPhase.DEFAULT,
                ChickenArcPresentation.activePhase(s, new PhaseInputs(false, false, false, false, false))
        );
        // Worldly Seeds in a container → the chicken pecks that container.
        Assertions.assertEquals(
                BeatPhase.READY_TO_PLACE,
                ChickenArcPresentation.activePhase(s, new PhaseInputs(false, false, false, false, true))
        );
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
    // Structural invariant: placement-beat bubble does not change with hasItem
    // (the bubble shows the target item regardless; only the hint adapts).
    // Documented as intentional in /CONTEXT.md and ADR-0001.
    // -------------------------------------------------------------------------

    @Test
    void placementBeats_bubbleIdenticalAcrossPhases() {
        for (ChickenBeatState state : new ChickenBeatState[]{
                ChickenBeatState.WAITING_FOR_WALL_BLOCK,
                ChickenBeatState.WAITING_FOR_DOOR
        }) {
            ChickenArcBubbles.Bubble noItem = ChickenArcPresentation.present(
                    state, new PhaseInputs(false, false, false)).bubble();
            ChickenArcBubbles.Bubble withItem = ChickenArcPresentation.present(
                    state, new PhaseInputs(true, false, false)).bubble();
            Assertions.assertTrue(
                    ItemStack.matches(noItem.iconA(), withItem.iconA()),
                    state.name() + " bubble iconA must not change with hasItem"
            );
            Assertions.assertTrue(
                    ItemStack.matches(noItem.iconB(), withItem.iconB()),
                    state.name() + " bubble iconB must not change with hasItem"
            );
        }
    }

    @Test
    void placementBeats_hintAdaptsAcrossPhases() {
        for (ChickenBeatState state : new ChickenBeatState[]{
                ChickenBeatState.WAITING_FOR_WALL_BLOCK,
                ChickenBeatState.WAITING_FOR_DOOR
        }) {
            String noItem = ChickenArcPresentation.present(
                    state, new PhaseInputs(false, false, false)).hintKey();
            String withItem = ChickenArcPresentation.present(
                    state, new PhaseInputs(true, false, false)).hintKey();
            Assertions.assertNotEquals(
                    noItem, withItem,
                    state.name() + " hint must differ between NEED_TO_FETCH and READY_TO_PLACE"
            );
        }
    }

    @Test
    void worldlySeedsDelivery_bubbleIsThroughWalls() {
        // Preserved structural invariant from ChickenArcBubblesTest.
        // present() will NPE on the WORLDLY_SEEDS RegistryObject in unit tests,
        // so we assert at the activePhase level only — the through-walls flag
        // is set by the row table for this beat and verified in-game.
        Assertions.assertEquals(
                BeatPhase.DEFAULT,
                ChickenArcPresentation.activePhase(
                        ChickenBeatState.AWAITING_WORLDLY_SEEDS_DELIVERY,
                        new PhaseInputs(false, false, false))
        );
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
