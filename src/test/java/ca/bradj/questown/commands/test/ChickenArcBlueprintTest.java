package ca.bradj.questown.commands.test;

import ca.bradj.questown.mobs.helperchicken.ChickenBeatState;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ChickenArcBlueprintTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // -------------------------------------------------------------------------
    // ChickenArcBlueprint
    // -------------------------------------------------------------------------

    @Test
    void builder_producesMinimalViableBlueprint() {
        ChickenArcBlueprint bp = ChickenArcBlueprint.builder("stub").build();
        Assertions.assertEquals("stub", bp.name());
        Assertions.assertEquals("chicken", bp.category());
        Assertions.assertEquals(Rotation.NONE, bp.startRotation());
        Assertions.assertFalse(bp.placeFlagViaCommand());
        Assertions.assertTrue(
                bp.forceRotationDetected(),
                "happy-path default is to force rotation-detected on the flag BE"
        );
        Assertions.assertTrue(bp.scriptedActions().isEmpty());
        Assertions.assertEquals(10, bp.effectiveHalfWidth());
    }

    @Test
    void builder_withHalfWidthOverride_reflectsInEffectiveHalfWidth() {
        ChickenArcBlueprint bp = ChickenArcBlueprint.builder("wide")
                .halfWidthOverride(25)
                .build();
        Assertions.assertEquals(25, bp.effectiveHalfWidth());
    }

    @Test
    void constructor_rejectsNullName() {
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new ChickenArcBlueprint(
                        null, "chicken", Rotation.NONE, false, true,
                        List.of(), ChickenArcExpectation.builder().build(),
                        60, 0, null
                )
        );
    }

    @Test
    void constructor_rejectsBlankName() {
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> ChickenArcBlueprint.builder(" ").build()
        );
    }

    @Test
    void constructor_rejectsNullStartRotation() {
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new ChickenArcBlueprint(
                        "x", "chicken", null, false, true,
                        List.of(), ChickenArcExpectation.builder().build(),
                        60, 0, null
                )
        );
    }

    @Test
    void constructor_defensiveCopyOfScriptedActions() {
        List<ChickenArcScriptedAction> mutable = new ArrayList<>();
        mutable.add(new ChickenArcScriptedAction.AdvanceTicks(5));
        ChickenArcBlueprint bp = ChickenArcBlueprint.builder("d")
                .actions(mutable)
                .build();
        mutable.add(new ChickenArcScriptedAction.AdvanceTicks(999));
        Assertions.assertEquals(1, bp.scriptedActions().size(),
                "blueprint's action list must not reflect post-construction mutation");
    }

    @Test
    void constructor_resultActionListIsUnmodifiable() {
        ChickenArcBlueprint bp = ChickenArcBlueprint.builder("d")
                .actions(List.of(new ChickenArcScriptedAction.AdvanceTicks(1)))
                .build();
        Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> bp.scriptedActions().add(new ChickenArcScriptedAction.AdvanceTicks(1))
        );
    }

    @Test
    void constructor_warpZeroAndNoActionsIsValid() {
        ChickenArcBlueprint bp = ChickenArcBlueprint.builder("idle").build();
        Assertions.assertEquals(0, bp.warpAmount());
        Assertions.assertTrue(bp.scriptedActions().isEmpty());
    }

    // -------------------------------------------------------------------------
    // ChickenArcExpectation
    // -------------------------------------------------------------------------

    @Test
    void expectationBuilder_producesDefaults() {
        ChickenArcExpectation e = ChickenArcExpectation.builder().build();
        Assertions.assertTrue(e.finalBeatState().isEmpty());
        Assertions.assertTrue(e.expectedFlagBits().isEmpty());
        Assertions.assertFalse(e.expectStatuePlaced());
        Assertions.assertFalse(e.expectChickenDiscarded());
        Assertions.assertTrue(e.expectChickenSpawned(),
                "default is to expect a spawn — non-spawn scenarios must opt out");
        Assertions.assertTrue(e.itemDeltasOpt().isEmpty());
    }

    @Test
    void expectationBuilder_threadsFieldsThrough() {
        ChickenArcExpectation e = ChickenArcExpectation.builder()
                .finalBeat(ChickenBeatState.COMPLETE)
                .statuePlaced(true)
                .chickenDiscarded(true)
                .flagBits(Map.of(
                        "chicken-first-gather-worldly-seeds-fired", true,
                        "chicken-arc-forfeit", false
                ))
                .build();
        Assertions.assertEquals(ChickenBeatState.COMPLETE, e.finalBeatState().orElseThrow());
        Assertions.assertTrue(e.expectStatuePlaced());
        Assertions.assertTrue(e.expectChickenDiscarded());
        Assertions.assertEquals(2, e.expectedFlagBits().size());
    }

    @Test
    void expectation_defensiveCopiesFlagBitsMap() {
        Map<String, Boolean> mutable = new HashMap<>();
        mutable.put("chicken-arc-forfeit", true);
        ChickenArcExpectation e = new ChickenArcExpectation(
                null, mutable, false, false, true, null
        );
        mutable.put("chicken-arc-forfeit", false);
        Assertions.assertEquals(Boolean.TRUE, e.expectedFlagBits().get("chicken-arc-forfeit"));
    }

    @Test
    void expectation_nullFlagBitsBecomesEmpty() {
        ChickenArcExpectation e = new ChickenArcExpectation(
                null, null, false, false, true, null
        );
        Assertions.assertTrue(e.expectedFlagBits().isEmpty());
    }

    @Test
    void expectation_flagBitsMapIsUnmodifiable() {
        ChickenArcExpectation e = ChickenArcExpectation.builder()
                .flagBits(Map.of("k", true))
                .build();
        Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> e.expectedFlagBits().put("z", false)
        );
    }

    // -------------------------------------------------------------------------
    // ChickenArcScriptedAction — sealed exhaustiveness + postActionWaitTicks
    // -------------------------------------------------------------------------

    @Test
    void advanceTicks_postActionWaitEqualsTickCount() {
        ChickenArcScriptedAction.AdvanceTicks a = new ChickenArcScriptedAction.AdvanceTicks(42);
        Assertions.assertEquals(42, a.postActionWaitTicks());
    }

    @Test
    void scriptedAction_isSealed() {
        Assertions.assertTrue(
                ChickenArcScriptedAction.class.isSealed(),
                "ChickenArcScriptedAction must be sealed so the executor's dispatch is exhaustive"
        );
    }

    /**
     * Every subtype must satisfy the {@code postActionWaitTicks()} contract. This is a
     * runtime proxy for "the executor has a handler for each subtype" — if someone adds
     * a new subtype without handling it, the executor's switch will fail exhaustiveness
     * at compile time, and any call that exercises the action here documents which
     * subtypes exist.
     */
    @Test
    void scriptedAction_allKnownSubtypesExposeWaitTicks() {
        List<ChickenArcScriptedAction> all = List.of(
                new ChickenArcScriptedAction.GiveItem(
                        net.minecraft.world.item.Items.STICK, 1, 5),
                new ChickenArcScriptedAction.GiveBoundWand(BlockPos.ZERO, 1),
                new ChickenArcScriptedAction.WandRightClick(BlockPos.ZERO, 2),
                new ChickenArcScriptedAction.PlaceBlock(
                        BlockPos.ZERO,
                        net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState(),
                        3),
                new ChickenArcScriptedAction.RegisterDoorViaWand(BlockPos.ZERO, 4),
                new ChickenArcScriptedAction.DepositIntoContainer(
                        net.minecraft.world.item.ItemStack.EMPTY, 5),
                new ChickenArcScriptedAction.SetUpRegisteredRoomWithChest(20),
                new ChickenArcScriptedAction.MarkVillagerUiOpened(1),
                new ChickenArcScriptedAction.MarkFlagUiOpened(1),
                new ChickenArcScriptedAction.AdvanceTicks(10),
                new ChickenArcScriptedAction.RightClickChickenWithHand(
                        net.minecraft.world.item.Items.STICK, 6),
                new ChickenArcScriptedAction.RunCommand("/help", 2)
        );
        for (ChickenArcScriptedAction a : all) {
            Assertions.assertTrue(
                    a.postActionWaitTicks() >= 0,
                    "postActionWaitTicks must be non-negative for " + a.getClass().getSimpleName()
            );
        }
        Assertions.assertEquals(
                12,
                all.size(),
                "update this test when adding a new ChickenArcScriptedAction subtype"
        );
    }

    @Test
    void scriptedAction_subtypeCountMatchesSealedHierarchy() {
        Class<?>[] permitted = ChickenArcScriptedAction.class.getPermittedSubclasses();
        Assertions.assertEquals(
                12,
                permitted.length,
                "the executor's dispatch switch must cover exactly this many subtypes"
        );
    }
}
