package ca.bradj.questown.commands.test;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.mc.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TestAllExecutor {

    private final ServerLevel level;
    private final ServerPlayer player;
    private final BlockPos origin;
    private final int warpAmount;

    private final List<Map.Entry<JobID, TestBlueprint>> jobs;
    private int currentIndex = 0;
    private TestExecutor currentExecutor;
    private final List<String> results = new ArrayList<>();
    private int passed = 0;

    public TestAllExecutor(
            ServerLevel level,
            ServerPlayer player,
            BlockPos origin,
            int warpAmount
    ) {
        this.level = level;
        this.player = player;
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

        Map.Entry<JobID, TestBlueprint> entry = jobs.get(currentIndex);
        JobID jobId = entry.getKey();
        TestBlueprint blueprint = entry.getValue();

        msg("=== Test " + (currentIndex + 1) + "/" + jobs.size() + ": " +
                jobId.rootId() + "/" + jobId.jobId() + " ===");

        currentExecutor = new TestExecutor(
                level, player, origin, jobId, warpAmount, blueprint, true
        );

        return false;
    }

    private void recordResult() {
        JobID jobId = currentExecutor.getJobId();
        String label = jobId.rootId() + "/" + jobId.jobId();
        if (currentExecutor.getWarpPassed()) {
            results.add("[PASS] " + label);
            passed++;
        } else {
            results.add("[FAIL] " + label + ": Some expectations failed");
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

    private void msg(String text) {
        Compat.sendMessage(player, Component.literal("[_qtdev testall] " + text));
        QT.FLAG_LOGGER.info("[_qtdev testall] {}", text);
    }
}
