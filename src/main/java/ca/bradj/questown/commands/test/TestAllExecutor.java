package ca.bradj.questown.commands.test;

import ca.bradj.questown.commands.test.TestBlueprintRegistry.AnyTestEntry;
import ca.bradj.questown.commands.test.TestBlueprintRegistry.TestEntry;
import ca.bradj.questown.commands.test.TestBlueprintRegistry.WorldgenCheck;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TestAllExecutor {

    private final ServerLevel level;
    private final TestOutput output;
    private final BlockPos origin;
    private final int warpAmount;

    private final List<AnyTestEntry> jobs;
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
        this(level, output, origin, warpAmount, (String) null);
    }

    public TestAllExecutor(
            ServerLevel level,
            TestOutput output,
            BlockPos origin,
            int warpAmount,
            @Nullable String category
    ) {
        this(level, output, origin, warpAmount, TestBlueprintRegistry.resolveJobs(category));
    }

    public TestAllExecutor(
            ServerLevel level,
            TestOutput output,
            BlockPos origin,
            int warpAmount,
            List<AnyTestEntry> jobs
    ) {
        this.level = level;
        this.output = output;
        this.origin = origin;
        this.warpAmount = warpAmount;
        this.jobs = jobs;
    }

    public boolean tick() {
        if (currentExecutor != null) {
            if (!currentExecutor.tick()) {
                return false;
            }
            recordResult(jobs.get(currentIndex).name(), currentExecutor.getWarpPassed());
            currentExecutor = null;
            currentIndex++;
        }

        while (currentIndex < jobs.size()) {
            AnyTestEntry entry = jobs.get(currentIndex);
            msg("=== Test " + (currentIndex + 1) + "/" + jobs.size() + ": " + entry.name() + " ===");

            if (entry instanceof WorldgenCheck wc) {
                recordResult(entry.name(), wc.check().apply(level));
                currentIndex++;
                continue;
            }

            TestEntry te = (TestEntry) entry;
            boolean warpOnly = !te.blueprint().realtimePhase();
            currentExecutor = new TestExecutor(
                    level, output, origin, te.jobId(), warpAmount, te.blueprint(), warpOnly
            );
            return false;
        }

        printSummary();
        return true;
    }

    private void recordResult(String name, boolean passed) {
        String line = AutotestLogFormatter.format(
                AutotestLogFormatter.TRACK_JOBS,
                name,
                passed,
                "scenario",
                passed ? "all expectations met" : "some expectations failed"
        );
        results.add(line);
        if (passed) {
            this.passed++;
        }
    }

    private void printSummary() {
        msg("========== WARP TEST RESULTS ==========");
        for (String result : results) {
            msg(result);
        }
        msg("========================================");
        int percentage = jobs.isEmpty() ? 0 : (passed * 100 / jobs.size());
        msg("Passed: " + passed + "/" + jobs.size() + " (" + percentage + "%)");
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
