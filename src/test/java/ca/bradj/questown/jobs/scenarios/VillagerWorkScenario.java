package ca.bradj.questown.jobs.scenarios;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.production.ProductionStatus;

import java.util.Map;

/**
 * A declarative description of a villager work scenario.
 * This record captures the initial state and expected outcomes for testing
 * both warp-based and realtime-based job execution.
 */
public record VillagerWorkScenario(
        // Scenario identification
        String name,
        String description,

        // Initial state
        JobID initialJob,
        ProductionStatus initialStatus,
        Map<String, Integer> containerItems,      // item name -> count
        Map<String, Integer> villagerItems,       // item name -> count (in inventory)
        int processingState,                      // current work state (0 = fresh)
        int workRemaining,                        // work left at current work block
        long timerTicks,                          // timer for timed states (e.g., gathering wait)

        // Expected outcomes (after running the scenario)
        Map<String, Integer> expectedContainerChanges,  // item name -> delta (positive = added, negative = removed)
        int expectedCyclesCompleted,
        int expectedFoodConsumed
) {

    /**
     * Builder for creating scenarios.
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private String description = "";
        private JobID initialJob = new JobID("test", "test_job");
        private ProductionStatus initialStatus = ProductionStatus.IDLE;
        private Map<String, Integer> containerItems = Map.of();
        private Map<String, Integer> villagerItems = Map.of();
        private int processingState = 0;
        private int workRemaining = 0;
        private long timerTicks = 0;
        private Map<String, Integer> expectedContainerChanges = Map.of();
        private int expectedCyclesCompleted = 0;
        private int expectedFoodConsumed = 0;

        public Builder(String name) {
            this.name = name;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder initialJob(JobID job) {
            this.initialJob = job;
            return this;
        }

        public Builder initialJob(String root, String jobId) {
            this.initialJob = new JobID(root, jobId);
            return this;
        }

        public Builder initialStatus(ProductionStatus status) {
            this.initialStatus = status;
            return this;
        }

        public Builder containerItems(Map<String, Integer> items) {
            this.containerItems = items;
            return this;
        }

        public Builder villagerItems(Map<String, Integer> items) {
            this.villagerItems = items;
            return this;
        }

        public Builder processingState(int state) {
            this.processingState = state;
            return this;
        }

        public Builder workRemaining(int work) {
            this.workRemaining = work;
            return this;
        }

        public Builder timerTicks(long ticks) {
            this.timerTicks = ticks;
            return this;
        }

        public Builder expectedContainerChanges(Map<String, Integer> changes) {
            this.expectedContainerChanges = changes;
            return this;
        }

        public Builder expectedCyclesCompleted(int cycles) {
            this.expectedCyclesCompleted = cycles;
            return this;
        }

        public Builder expectedFoodConsumed(int food) {
            this.expectedFoodConsumed = food;
            return this;
        }

        public VillagerWorkScenario build() {
            return new VillagerWorkScenario(
                    name,
                    description,
                    initialJob,
                    initialStatus,
                    containerItems,
                    villagerItems,
                    processingState,
                    workRemaining,
                    timerTicks,
                    expectedContainerChanges,
                    expectedCyclesCompleted,
                    expectedFoodConsumed
            );
        }
    }
}
