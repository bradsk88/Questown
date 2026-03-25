package ca.bradj.questown.commands.test;

import ca.bradj.questown.commands.test.TestBlueprintRegistry.TestEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

public class TestAllExecutor {

    private final ServerLevel level;
    private final TestOutput output;
    private final BlockPos origin;
    private final int warpAmount;

    private final List<TestEntry> jobs;
    private int currentIndex = 0;
    private TestExecutor currentExecutor;
    private final List<String> results = new ArrayList<>();
    private int passed = 0;

    public TestAllExecutor(
            ServerLevel level,
            TestOutput output,
            BlockPos origin,
            int warpAmount
    ) {
        this.level = level;
        this.output = output;
        this.origin = origin;
        this.warpAmount = warpAmount;
        this.jobs = TestBlueprintRegistry.getTestableJobs();
    }

    public boolean tick() {
        if (currentExecutor != null) {
            if (!currentExecutor.tick()) {
                return false;
            }
            recordResult();
            currentExecutor = null;
            currentIndex++;
        }

        if (currentIndex >= jobs.size()) {
            printSummary();
            return true;
        }

        TestEntry entry = jobs.get(currentIndex);

        msg("=== Test " + (currentIndex + 1) + "/" + jobs.size() + ": " +
                entry.name() + " ===");

        boolean warpOnly = !entry.blueprint().realtimePhase();
        currentExecutor = new TestExecutor(
                level, output, origin, entry.jobId(), warpAmount, entry.blueprint(), warpOnly
        );

        return false;
    }

    private void recordResult() {
        TestEntry entry = jobs.get(currentIndex);
        if (currentExecutor.getWarpPassed()) {
            results.add("[PASS] " + entry.name());
            passed++;
        } else {
            results.add("[FAIL] " + entry.name() + ": Some expectations failed");
        }
    }

    private void printSummary() {
        msg("========== WARP TEST RESULTS ==========");
        for (String result : results) {
            msg(result);
        }
        msg("========================================");
        msg("Passed: " + passed + "/" + jobs.size() + " (" + (passed * 100 / jobs.size()) + "%)");
        msg("========================================");
    }

    public int getPassed() {
        return passed;
    }

    public int getTotal() {
        return jobs.size();
    }

    private void msg(String text) {
        output.msg(text);
    }
}
