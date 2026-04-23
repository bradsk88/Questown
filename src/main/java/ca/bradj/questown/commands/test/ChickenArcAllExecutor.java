package ca.bradj.questown.commands.test;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Parallel to {@link TestAllExecutor}: iterates over the registered
 * chicken-arc scenarios, hands each one to a fresh
 * {@link ChickenArcTestExecutor}, accumulates pass/fail totals, and emits
 * a per-scenario summary line via {@link AutotestLogFormatter}.
 *
 * <p>Matches {@link TestAllExecutor}'s tick-driven contract: returns
 * {@code true} once the whole suite is done. When a scenario throws during
 * its own ticking, the exception is caught, reported as a {@code [FAIL]}
 * line, and the outer loop advances to the next scenario.
 */
public class ChickenArcAllExecutor {

    private final ServerLevel level;
    private final MinecraftServer server;
    private final ServerPlayer fakePlayer;
    private final BlockPos origin;
    private final TestOutput output;

    private final List<ChickenArcBlueprint> blueprints;
    private int currentIndex = 0;
    private ChickenArcTestExecutor currentExecutor;
    private final List<String> results = new ArrayList<>();
    private int passed = 0;

    public ChickenArcAllExecutor(
            ServerLevel level,
            MinecraftServer server,
            ServerPlayer fakePlayer,
            BlockPos origin,
            TestOutput output,
            List<ChickenArcBlueprint> blueprints
    ) {
        this.level = level;
        this.server = server;
        this.fakePlayer = fakePlayer;
        this.origin = origin;
        this.output = output;
        this.blueprints = blueprints;
    }

    public boolean tick() {
        if (currentExecutor != null) {
            boolean scenarioDone = tickCurrentScenarioSafely();
            if (!scenarioDone) {
                return false;
            }
            recordResult(blueprints.get(currentIndex).name(), currentExecutor.getPassed());
            currentExecutor = null;
            currentIndex++;
        }

        while (currentIndex < blueprints.size()) {
            ChickenArcBlueprint bp = blueprints.get(currentIndex);
            msg("=== Chicken " + (currentIndex + 1) + "/" + blueprints.size() + ": " + bp.name() + " ===");
            currentExecutor = new ChickenArcTestExecutor(
                    level, server, fakePlayer, origin, bp, output
            );
            return false;
        }

        printSummary();
        return true;
    }

    private boolean tickCurrentScenarioSafely() {
        try {
            return currentExecutor.tick();
        } catch (Exception e) {
            String name = blueprints.get(currentIndex).name();
            output.msg(AutotestLogFormatter.format(
                    AutotestLogFormatter.TRACK_CHICKEN_ARC,
                    name,
                    false,
                    "scenario crashed",
                    e.toString()
            ));
            return true;
        }
    }

    private void recordResult(String name, boolean scenarioPassed) {
        String line = AutotestLogFormatter.format(
                AutotestLogFormatter.TRACK_CHICKEN_ARC,
                name,
                scenarioPassed,
                "scenario",
                scenarioPassed ? "all expectations met" : "some expectations failed"
        );
        results.add(line);
        if (scenarioPassed) {
            this.passed++;
        }
    }

    private void printSummary() {
        msg("========== CHICKEN ARC TEST RESULTS ==========");
        for (String result : results) {
            msg(result);
        }
        msg("========================================");
        int total = blueprints.size();
        int pct = total == 0 ? 0 : (passed * 100 / total);
        msg("Passed: " + passed + "/" + total + " (" + pct + "%)");
        msg("========================================");
    }

    public int getPassed() {
        return passed;
    }

    public int getTotal() {
        return blueprints.size();
    }

    private void msg(String text) {
        output.msg(text);
    }
}
