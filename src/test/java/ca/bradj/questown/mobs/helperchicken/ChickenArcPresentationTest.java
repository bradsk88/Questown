package ca.bradj.questown.mobs.helperchicken;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

/** {@link ChickenArcPresentation}: every phase choice, and the rows for lessons with vanilla icons. */
class ChickenArcPresentationTest {

    private static final Set<ChickenBeatState> VANILLA_ONLY = EnumSet.of(
            ChickenBeatState.WAITING_FOR_WALL_BLOCK,
            ChickenBeatState.WAITING_FOR_DOOR,
            ChickenBeatState.WAITING_FOR_SIGN,
            ChickenBeatState.WAITING_FOR_CHEST,
            ChickenBeatState.WAITING_FOR_ROOM_BUILD,
            ChickenBeatState.WAITING_FOR_VILLAGER_UI,
            ChickenBeatState.COMPLETE,
            ChickenBeatState.FORFEIT
    );

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

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
    void activePhase_sunsetAndMap_twoPhases() {
        ChickenBeatState s = ChickenBeatState.SUNSET_AND_MAP;
        // The sleep lesson waits for night, then goes to the campfire; the chest doesn't change it.
        Assertions.assertEquals(
                BeatPhase.AWAITING_NIGHT,
                ChickenArcPresentation.activePhase(s, new PhaseInputs(false, false, false))
        );
        Assertions.assertEquals(
                BeatPhase.AWAITING_NIGHT,
                ChickenArcPresentation.activePhase(s, new PhaseInputs(false, true, false)),
                "day ignores chestSpawned (the chest beat dropped it earlier)"
        );
        Assertions.assertEquals(
                BeatPhase.READY_TO_USE,
                ChickenArcPresentation.activePhase(s, new PhaseInputs(false, false, true))
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
                ChickenBeatState.WAITING_FOR_FREE_FOOD,
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
    void activePhase_roomBuild_isAlwaysDefault() {
        // The room-build lesson ignores what the player holds.
        Assertions.assertEquals(
                BeatPhase.DEFAULT,
                ChickenArcPresentation.activePhase(
                        ChickenBeatState.WAITING_FOR_ROOM_BUILD, new PhaseInputs(false, false, false))
        );
        Assertions.assertEquals(
                BeatPhase.DEFAULT,
                ChickenArcPresentation.activePhase(
                        ChickenBeatState.WAITING_FOR_ROOM_BUILD, new PhaseInputs(true, false, false))
        );
    }

    @Test
    void activePhase_pressurePlate_threePhases() {
        ChickenBeatState s = ChickenBeatState.WAITING_FOR_PRESSURE_PLATE;
        Assertions.assertEquals(
                BeatPhase.NEED_TO_FETCH,
                ChickenArcPresentation.activePhase(s, new PhaseInputs(false, false, false, false))
        );
        Assertions.assertEquals(
                BeatPhase.READY_TO_USE,
                ChickenArcPresentation.activePhase(s, new PhaseInputs(false, false, false, true))
        );
        Assertions.assertEquals(
                BeatPhase.READY_TO_PLACE,
                ChickenArcPresentation.activePhase(s, new PhaseInputs(true, false, false, false))
        );
        Assertions.assertEquals(
                BeatPhase.READY_TO_PLACE,
                ChickenArcPresentation.activePhase(s, new PhaseInputs(true, false, false, true))
        );
    }

    @Test
    void activePhase_worldlySeedsDelivery_threePhases() {
        ChickenBeatState s = ChickenBeatState.AWAITING_WORLDLY_SEEDS_DELIVERY;
        Assertions.assertEquals(
                BeatPhase.AWAITING_GATHERER,
                ChickenArcPresentation.activePhase(s, new PhaseInputs(false, false, false, false, false, false))
        );
        Assertions.assertEquals(
                BeatPhase.DEFAULT,
                ChickenArcPresentation.activePhase(s, new PhaseInputs(false, false, false, false, false, true))
        );
        Assertions.assertEquals(
                BeatPhase.READY_TO_PLACE,
                ChickenArcPresentation.activePhase(s, new PhaseInputs(false, false, false, false, true, false))
        );
    }

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
        // Checked at the phase level; present() needs the mod's items.
        Assertions.assertEquals(
                BeatPhase.AWAITING_GATHERER,
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
