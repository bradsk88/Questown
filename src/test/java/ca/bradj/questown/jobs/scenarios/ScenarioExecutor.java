package ca.bradj.questown.jobs.scenarios;

/**
 * Interface for executing villager work scenarios.
 * Implementations can execute scenarios using different methods:
 * - WarpScenarioExecutor: Uses AbstractAdvanceTime (time warp simulation)
 * - RealtimeScenarioExecutor: Uses JobLogic.tick (real-time tick simulation)
 */
public interface ScenarioExecutor {

    /**
     * Result of executing a scenario.
     */
    record ExecutionResult(
            TestTownState finalState,
            int cyclesCompleted,
            int foodConsumed,
            long ticksProcessed,
            String executorType
    ) {}

    /**
     * Executes the given scenario for the specified number of ticks.
     *
     * @param scenario The scenario to execute
     * @param ticks    Number of ticks to simulate
     * @return The execution result
     */
    ExecutionResult execute(VillagerWorkScenario scenario, long ticks);

    /**
     * Gets the name/type of this executor (for logging/debugging).
     */
    String getExecutorType();
}
