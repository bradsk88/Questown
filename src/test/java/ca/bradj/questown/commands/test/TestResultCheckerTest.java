package ca.bradj.questown.commands.test;

import ca.bradj.questown.commands.test.TestExpectation.ExpectedContainerContent;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Unit coverage for the per-position container-content oracle (U1). A fetch relocates an item
 * (town-wide delta ~0), so the town-wide {@link TestResultChecker#check} model cannot gate it;
 * {@link TestResultChecker#checkContainerContents} asserts both-sides conservation per position.
 */
class TestResultCheckerTest {

    private static final BlockPos CHEST_A = new BlockPos(1, 0, 1);
    private static final BlockPos CHEST_B = new BlockPos(5, 0, 5);
    private static final String ITEM = "minecraft:diamond";

    private static TestExpectation conservation() {
        return new TestExpectation(List.of(), 0, 0, List.of(
                new ExpectedContainerContent(CHEST_A, ITEM, null, -4),
                new ExpectedContainerContent(CHEST_B, ITEM, 4, null)
        ));
    }

    @Test
    void relocation_sourceLosesAndTargetGains_passes() {
        Map<BlockPos, Map<String, Integer>> before = Map.of(
                CHEST_A, Map.of(ITEM, 4),
                CHEST_B, Map.of()
        );
        Map<BlockPos, Map<String, Integer>> after = Map.of(
                CHEST_A, Map.of(),
                CHEST_B, Map.of(ITEM, 4)
        );
        TestResultChecker.Result result = TestResultChecker.checkContainerContents(before, after, conservation());
        Assertions.assertTrue(result.passed(), result.details().toString());
    }

    @Test
    void dupe_targetGainsButSourceUnchanged_fails() {
        Map<BlockPos, Map<String, Integer>> before = Map.of(
                CHEST_A, Map.of(ITEM, 4),
                CHEST_B, Map.of()
        );
        Map<BlockPos, Map<String, Integer>> after = Map.of(
                CHEST_A, Map.of(ITEM, 4),
                CHEST_B, Map.of(ITEM, 4)
        );
        TestResultChecker.Result result = TestResultChecker.checkContainerContents(before, after, conservation());
        Assertions.assertFalse(result.passed(), "source unchanged + target gained is a dupe");
    }

    @Test
    void loss_sourceLosesButTargetUnchanged_fails() {
        Map<BlockPos, Map<String, Integer>> before = Map.of(
                CHEST_A, Map.of(ITEM, 4),
                CHEST_B, Map.of()
        );
        Map<BlockPos, Map<String, Integer>> after = Map.of(
                CHEST_A, Map.of(),
                CHEST_B, Map.of()
        );
        TestResultChecker.Result result = TestResultChecker.checkContainerContents(before, after, conservation());
        Assertions.assertFalse(result.passed(), "source lost + target unchanged is a loss");
    }

    @Test
    void noContainerExpectation_passesWithNoEffect() {
        TestExpectation townWideOnly = new TestExpectation(List.of(), 0, 0);
        TestResultChecker.Result result = TestResultChecker.checkContainerContents(
                Map.of(), Map.of(), townWideOnly);
        Assertions.assertTrue(result.passed());
        Assertions.assertTrue(result.details().isEmpty());
    }

    @Test
    void containerMissingFromSnapshot_failsWithClearDetail() {
        Map<BlockPos, Map<String, Integer>> snapshot = Map.of(CHEST_A, Map.of(ITEM, 4));
        TestResultChecker.Result result = TestResultChecker.checkContainerContents(
                snapshot, snapshot, conservation());
        Assertions.assertFalse(result.passed());
        Assertions.assertTrue(
                result.details().stream().anyMatch(d -> d.contains("container not found")),
                "expected a 'container not found' detail, got: " + result.details()
        );
    }
}
