package ca.bradj.questown.commands.test;

import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.mobs.helperchicken.ChickenBeatState;
import ca.bradj.questown.mobs.helperchicken.HelperChickenEntity;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads the final state of a {@link TownFlagBlockEntity} after a chicken-arc
 * scenario finishes and emits one {@code [autotest] chicken-arc:<name>} line
 * per assertion in {@link ChickenArcExpectation}.
 *
 * <p>Item-delta assertions compose {@link TestResultChecker} so the two tracks
 * share one contract for item-count checks.
 */
public final class ChickenArcResultChecker {

    /** Result of evaluating an entire {@link ChickenArcExpectation}. */
    public record Result(boolean passed, int passedCount, int totalCount) {}

    private ChickenArcResultChecker() {
    }

    public static Result check(
            String scenarioName,
            ServerLevel level,
            TownFlagBlockEntity flag,
            BlockPos flagPos,
            int arenaHalfWidth,
            ChickenArcExpectation expectation,
            TestOutput output,
            Map<String, Integer> beforeCounts
    ) {
        List<Boolean> results = new ArrayList<>();

        expectation.finalBeatState().ifPresent(expected ->
                results.add(checkFinalBeatState(scenarioName, flag, expected, output))
        );

        expectation.expectedFlagBits().forEach((key, expected) ->
                results.add(checkFlagBit(scenarioName, flag, key, expected, output))
        );

        if (expectation.expectStatuePlaced()) {
            results.add(checkStatuePresent(scenarioName, level, flagPos, arenaHalfWidth, output));
        }

        if (expectation.expectChickenSpawned() || expectation.expectChickenDiscarded()) {
            results.add(checkChickenEntityState(
                    scenarioName, level, flagPos, arenaHalfWidth, expectation, output
            ));
        }

        expectation.itemDeltasOpt().ifPresent(itemDeltas ->
                results.add(checkItemDeltas(scenarioName, flag, beforeCounts, itemDeltas, output))
        );

        int passed = (int) results.stream().filter(Boolean::booleanValue).count();
        int total = results.size();
        return new Result(passed == total, passed, total);
    }

    private static boolean checkFinalBeatState(
            String name,
            TownFlagBlockEntity flag,
            ChickenBeatState expected,
            TestOutput output
    ) {
        ChickenBeatState actual = flag.getChickenBeatState();
        boolean pass = expected == actual;
        output.msg(AutotestLogFormatter.format(
                AutotestLogFormatter.TRACK_CHICKEN_ARC, name, pass,
                "final beat state",
                "expected=" + expected + " actual=" + actual
        ));
        return pass;
    }

    private static boolean checkFlagBit(
            String name,
            TownFlagBlockEntity flag,
            String key,
            boolean expected,
            TestOutput output
    ) {
        Boolean actual = readFlagBit(flag, key);
        if (actual == null) {
            output.msg(AutotestLogFormatter.format(
                    AutotestLogFormatter.TRACK_CHICKEN_ARC, name, false,
                    "flag bit " + key,
                    "unknown flag bit key — no accessor is registered"
            ));
            return false;
        }
        boolean pass = expected == actual;
        output.msg(AutotestLogFormatter.format(
                AutotestLogFormatter.TRACK_CHICKEN_ARC, name, pass,
                "flag bit " + key,
                "expected=" + expected + " actual=" + actual
        ));
        return pass;
    }

    /**
     * Resolve a named flag bit to a live accessor read. Returns null when the
     * key doesn't match any known accessor so the caller can log a clear
     * {@code [FAIL] unknown flag bit} message rather than silently passing.
     */
    private static Boolean readFlagBit(TownFlagBlockEntity flag, String key) {
        return switch (key) {
            case "chicken-first-gather-worldly-seeds-fired" -> flag.getChickenFirstGatherWorldlySeedsFired();
            case "chicken-arc-forfeit" -> flag.getChickenArcForfeit();
            case "chicken-ever-spawned" -> flag.getChickenEverSpawned();
            case "chicken-rotation-detected" -> flag.getChickenRotationDetected();
            case "chicken-observed-seeds-given" -> flag.getChickenObservedSeedsGiven();
            case "chicken-observed-villager-ui-open" -> flag.getChickenObservedVillagerUiOpen();
            case "chicken-observed-flag-ui-open" -> flag.getChickenObservedFlagUiOpen();
            default -> null;
        };
    }

    private static boolean checkStatuePresent(
            String name,
            ServerLevel level,
            BlockPos flagPos,
            int halfWidth,
            TestOutput output
    ) {
        BlockPos nearest = findNearestStatue(level, flagPos, halfWidth);
        boolean pass = nearest != null;
        String details = pass
                ? "found at " + nearest.toShortString()
                : "no STONE_CHICKEN_STATUE within half-width=" + halfWidth + " of " + flagPos.toShortString();
        output.msg(AutotestLogFormatter.format(
                AutotestLogFormatter.TRACK_CHICKEN_ARC, name, pass,
                "statue placed",
                details
        ));
        return pass;
    }

    private static BlockPos findNearestStatue(
            ServerLevel level,
            BlockPos flagPos,
            int halfWidth
    ) {
        BlockPos nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (int x = -halfWidth; x <= halfWidth; x++) {
            for (int z = -halfWidth; z <= halfWidth; z++) {
                for (int y = -2; y <= 4; y++) {
                    BlockPos pos = flagPos.offset(x, y, z);
                    if (!level.getBlockState(pos).is(BlocksInit.STONE_CHICKEN_STATUE.get())) {
                        continue;
                    }
                    double d = pos.distSqr(flagPos);
                    if (d < nearestDistSq) {
                        nearestDistSq = d;
                        nearest = pos;
                    }
                }
            }
        }
        return nearest;
    }

    private static boolean checkChickenEntityState(
            String name,
            ServerLevel level,
            BlockPos flagPos,
            int halfWidth,
            ChickenArcExpectation expectation,
            TestOutput output
    ) {
        AABB area = new AABB(
                flagPos.offset(-halfWidth, -5, -halfWidth),
                flagPos.offset(halfWidth, 10, halfWidth)
        );
        int live = 0;
        for (HelperChickenEntity e : level.getEntitiesOfClass(HelperChickenEntity.class, area)) {
            if (e.isAlive()) {
                live++;
            }
        }
        if (expectation.expectChickenDiscarded()) {
            boolean pass = live == 0;
            output.msg(AutotestLogFormatter.format(
                    AutotestLogFormatter.TRACK_CHICKEN_ARC, name, pass,
                    "chicken discarded",
                    "live count=" + live + " (expected 0)"
            ));
            return pass;
        }
        if (expectation.expectChickenSpawned()) {
            boolean pass = live >= 1;
            output.msg(AutotestLogFormatter.format(
                    AutotestLogFormatter.TRACK_CHICKEN_ARC, name, pass,
                    "chicken spawned",
                    "live count=" + live + " (expected >= 1)"
            ));
            return pass;
        }
        boolean pass = live == 0;
        output.msg(AutotestLogFormatter.format(
                AutotestLogFormatter.TRACK_CHICKEN_ARC, name, pass,
                "chicken not spawned",
                "live count=" + live + " (expected 0)"
        ));
        return pass;
    }

    private static boolean checkItemDeltas(
            String name,
            TownFlagBlockEntity flag,
            Map<String, Integer> beforeCounts,
            TestExpectation itemDeltas,
            TestOutput output
    ) {
        MCTownState afterState = flag.captureCurrentState();
        if (afterState == null) {
            output.msg(AutotestLogFormatter.format(
                    AutotestLogFormatter.TRACK_CHICKEN_ARC, name, false,
                    "item deltas",
                    "failed to capture current state from flag"
            ));
            return false;
        }
        Map<String, Integer> after = TestResultChecker.snapshotItemCounts(afterState);
        Map<String, Integer> before = beforeCounts != null ? beforeCounts : Map.of();
        TestResultChecker.Result result = TestResultChecker.check(before, after, itemDeltas);
        output.msg(AutotestLogFormatter.format(
                AutotestLogFormatter.TRACK_CHICKEN_ARC, name, result.passed(),
                "item deltas",
                result.summary()
        ));
        for (String detail : result.details()) {
            output.msg("  " + detail);
        }
        return result.passed();
    }
}
