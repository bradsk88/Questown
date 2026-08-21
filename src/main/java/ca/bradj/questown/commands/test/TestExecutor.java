package ca.bradj.questown.commands.test;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.declarative.AbstractWorldInteraction;
import ca.bradj.questown.town.VillagerStatsData;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.interfaces.KnowledgeHolder;
import ca.bradj.questown.town.rewards.SpawnVisitorReward;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;
import java.util.UUID;

public class TestExecutor {

    private enum Phase {
        DESTROY_NEARBY_FLAGS,
        FLATTEN,
        SETTLE_BEFORE_PLACE,
        PLACE_FLAG,
        WAIT_FOR_INIT,
        BUILD_ROOM,
        REGISTER_ROOM,
        WAIT_FOR_ROOM,
        RUN_SETUP_HOOK,
        SPAWN_VILLAGER,
        WAIT_FOR_VILLAGER,
        ASSIGN_JOB,
        SETTLE,
        CAPTURE_BEFORE,
        RUN_WARP,
        NATURAL_WARP_FREEZE,
        NATURAL_WARP_WAIT,
        SETTLE_AFTER_WARP,
        CHECK_RESULTS,
        KILL_FOR_INSPECT,
        INSPECT_WARP_RESULTS,
        RESPAWN_VILLAGER,
        WAIT_FOR_RESPAWN,
        REASSIGN_JOB,
        REFILL_CHEST,
        CAPTURE_BEFORE_REALTIME,
        START_MONITOR,
        MONITORING,
        CHECK_REALTIME_RESULTS,
        DONE
    }

    private final ServerLevel level;
    private final TestOutput output;
    private BlockPos origin;
    private final JobID jobId;
    private final int warpAmount;
    private final TestBlueprint blueprint;
    private final boolean warpOnly;

    private Phase phase = Phase.DESTROY_NEARBY_FLAGS;
    private int waitTicks = 0;
    private int maxWaitTicks = 0;
    private BlockPos flagPos;
    private TownFlagBlockEntity tfbe;
    private Map<String, Integer> beforeCounts;
    private Map<BlockPos, Map<String, Integer>> beforeContainerContents;
    private int beforeKnowledgeCount;
    private Map<String, Integer> warpDeltas = new java.util.HashMap<>();
    private Map<String, Integer> beforeRealtimeCounts;
    private Map<BlockPos, Map<String, Integer>> beforeRealtimeContainerContents;
    private long monitorEndTick;
    private int lastReportedPercent = 0;
    private boolean warpPassed = false;
    private boolean realtimePassed = false;

    public TestExecutor(
            ServerLevel level,
            TestOutput output,
            BlockPos origin,
            JobID jobId,
            int warpAmount,
            TestBlueprint blueprint
    ) {
        this(level, output, origin, jobId, warpAmount, blueprint, false);
    }

    public TestExecutor(
            ServerLevel level,
            TestOutput output,
            BlockPos origin,
            JobID jobId,
            int warpAmount,
            TestBlueprint blueprint,
            boolean warpOnly
    ) {
        this.level = level;
        this.output = output;
        this.origin = origin;
        this.jobId = jobId;
        this.warpAmount = warpAmount;
        this.blueprint = blueprint;
        this.warpOnly = warpOnly;
        clearProficiencyTestSeams();
    }

    /**
     * Both proficiency test seams are process-global, so a scenario that leaves one set would
     * silently skew the next one's measurements rather than fail it. Clearing on construction
     * (not teardown) is what makes that impossible: a scenario that throws mid-run cannot poison
     * its successor, because the successor cleans up before it starts.
     */
    private static void clearProficiencyTestSeams() {
        AbstractWorldInteraction.resetProficiencyBearingActionsForTest();
        ServerJobsRegistry.clearProficiencyIdOverridesForTest();
    }

    private int effectiveWarpAmount() {
        if (blueprint.warpAmountOverride() != null) {
            return blueprint.warpAmountOverride();
        }
        return warpAmount;
    }

    private void setStartTimeIfNeeded() {
        if (blueprint.startTimeTick() == null) {
            return;
        }
        level.setDayTime(blueprint.startTimeTick());
        msg("Set world time to " + blueprint.startTimeTick() + " before warp");
    }

    public boolean getWarpPassed() {
        // skipWarp scenarios report through the realtime check (the custom assertion runs there),
        // so XFAIL must flip that result too — warpPassed is never set on the skipWarp path.
        boolean effective = blueprint.skipWarp() ? realtimePassed : warpPassed;
        if (blueprint.expectedFailure()) {
            // XFAIL: the path under test is expected to fail, so the scenario passes only when it
            // does. An unexpected pass (XPASS) flips this to false → a suite failure that flags
            // the bug has been fixed and the expectedFailure marker should be removed.
            return !effective;
        }
        return effective;
    }

    public boolean isExpectedFailure() {
        return blueprint.expectedFailure();
    }

    public JobID getJobId() {
        return jobId;
    }

    /**
     * Called each server tick. Returns true when the executor is done.
     */
    public boolean tick() {
        switch (phase) {
            case DESTROY_NEARBY_FLAGS -> destroyNearbyFlags();
            case FLATTEN -> flatten();
            case SETTLE_BEFORE_PLACE -> settleBeforePlace();
            case PLACE_FLAG -> placeFlag();
            case WAIT_FOR_INIT -> waitForInit();
            case BUILD_ROOM -> buildRoom();
            case REGISTER_ROOM -> registerRoom();
            case WAIT_FOR_ROOM -> waitForRoom();
            case RUN_SETUP_HOOK -> runSetupHook();
            case SPAWN_VILLAGER -> spawnVillager();
            case WAIT_FOR_VILLAGER -> waitForVillager();
            case ASSIGN_JOB -> assignJob();
            case SETTLE -> settle();
            case CAPTURE_BEFORE -> captureBefore();
            case RUN_WARP -> runWarp();
            case NATURAL_WARP_FREEZE -> naturalWarpFreeze();
            case NATURAL_WARP_WAIT -> naturalWarpWait();
            case SETTLE_AFTER_WARP -> settleAfterWarp();
            case CHECK_RESULTS -> checkResults();
            case KILL_FOR_INSPECT -> killForInspect();
            case INSPECT_WARP_RESULTS -> inspectWarpResults();
            case RESPAWN_VILLAGER -> respawnVillager();
            case WAIT_FOR_RESPAWN -> waitForRespawn();
            case REASSIGN_JOB -> reassignJob();
            case REFILL_CHEST -> refillChest();
            case CAPTURE_BEFORE_REALTIME -> captureBeforeRealtime();
            case START_MONITOR -> startMonitor();
            case MONITORING -> monitor();
            case CHECK_REALTIME_RESULTS -> checkRealtimeResults();
            case DONE -> { return true; }
        }
        return phase == Phase.DONE;
    }

    private void destroyNearbyFlags() {
        TestArenaPreparer.PreparerResult result = TestArenaPreparer.destroyNearbyFlags(
                level, origin, TestArenaPreparer.PreparerOptions.jobsTrackDefaults(), null
        );
        if (result.strayVisitorsKilled() > 0) {
            msg("Killed " + result.strayVisitorsKilled() + " stray visitor(s)");
        }
        if (result.flagsDestroyed() > 0) {
            msg("Destroyed " + result.flagsDestroyed() + " nearby flag(s)");
        }
        if (result.newOrigin() != null) {
            origin = result.newOrigin();
            msg("Using existing flag position as origin: " + origin.toShortString());
        }
        phase = Phase.FLATTEN;
    }

    // Vertical clearance above the tallest blueprint block, so a grown arborist tree (small oak
    // needs ~9) has room and no prior-scenario residue survives into this scenario's build volume.
    private static final int TREE_HEADROOM = 10;

    private void flatten() {
        msg("Flattening arena (full build volume + tree headroom)...");
        TestArenaPreparer.ClearRegion region = TestArenaPreparer.buildVolume(
                TestArenaPreparer.PreparerOptions.jobsTrackDefaults().halfWidth(),
                blueprint.blocks(),
                TREE_HEADROOM
        );
        TestArenaPreparer.flatten(level, origin, region);
        phase = Phase.SETTLE_BEFORE_PLACE;
        waitTicks = 0;
        maxWaitTicks = 5;
    }

    private void settleBeforePlace() {
        if (tickTimeout(null)) {
            phase = Phase.PLACE_FLAG;
            return;
        }
        waitTicks++;
    }

    private void placeFlag() {
        flagPos = origin;
        level.setBlockAndUpdate(flagPos, BlocksInit.COBBLESTONE_TOWN_FLAG.get().defaultBlockState());
        msg("Placed town flag at " + flagPos.toShortString());
        phase = Phase.WAIT_FOR_INIT;
        waitTicks = 0;
        maxWaitTicks = 200;
    }

    private void waitForInit() {
        BlockEntity be = level.getBlockEntity(flagPos);
        if (!(be instanceof TownFlagBlockEntity flag)) {
            if (tickTimeout("Town flag block entity not found")) {
                return;
            }
            waitTicks++;
            return;
        }
        tfbe = flag;
        if (tfbe.isInitialized()) {
            msg("Town flag initialized");
            phase = Phase.BUILD_ROOM;
            return;
        }
        if (tickTimeout("Town flag failed to initialize")) {
            return;
        }
        waitTicks++;
    }

    private void buildRoom() {
        msg("Building room...");
        RoomBuilder.build(level, flagPos, blueprint);
        phase = Phase.REGISTER_ROOM;
    }

    private void registerRoom() {
        BlockPos offsetWorldPos = flagPos.offset(blueprint.doorOrGateOffset());
        switch (blueprint.roomType()) {
            case FARM -> {
                tfbe.getRoomHandle().registerFenceGate(offsetWorldPos);
                msg("Registered fence gate at " + offsetWorldPos.toShortString());
            }
            case WELCOME_MAT -> {
                tfbe.registerWelcomeMat(offsetWorldPos);
                msg("Registered welcome mat at " + offsetWorldPos.toShortString());
            }
            case BLOCK_ROOM -> {
                tfbe.getRoomHandle().registerBlockAsRoom(blueprint.roomId(), offsetWorldPos);
                msg("Registered block room at " + offsetWorldPos.toShortString());
            }
            default -> {
                tfbe.getRoomHandle().registerDoor(offsetWorldPos);
                msg("Registered door at " + offsetWorldPos.toShortString());
            }
        }
        if (blueprint.supplyDoorOffset() != null) {
            BlockPos supplyDoorWorldPos = flagPos.offset(blueprint.supplyDoorOffset());
            tfbe.getRoomHandle().registerDoor(supplyDoorWorldPos);
            msg("Registered supply room door at " + supplyDoorWorldPos.toShortString());
        }
        if (blueprint.extraBlockRoomOffset() != null && blueprint.extraBlockRoomId() != null) {
            BlockPos extraWorldPos = flagPos.offset(blueprint.extraBlockRoomOffset());
            tfbe.getRoomHandle().registerBlockAsRoom(blueprint.extraBlockRoomId(), extraWorldPos);
            msg("Registered extra block room " + blueprint.extraBlockRoomId() + " at " + extraWorldPos.toShortString());
        }
        phase = Phase.WAIT_FOR_ROOM;
        waitTicks = 0;
        maxWaitTicks = 300;
    }

    private void waitForRoom() {
        if (!tfbe.getRoomHandle().getRoomsMatching(blueprint.roomId()).isEmpty()) {
            msg("Room detected: " + blueprint.roomId());
            phase = Phase.RUN_SETUP_HOOK;
            return;
        }
        if (tickTimeout("Room detection timed out for " + blueprint.roomId())) {
            return;
        }
        waitTicks++;
    }

    private void runSetupHook() {
        TestBlueprint.PostPlacementSetup hook = blueprint.setupHook();
        if (hook == null) {
            phase = Phase.SPAWN_VILLAGER;
            return;
        }
        msg("Running post-placement setup hook...");
        if (!hook.run(level, flagPos, tfbe, output)) {
            error("Post-placement setup hook failed");
            phase = Phase.DONE;
            return;
        }
        phase = Phase.SPAWN_VILLAGER;
    }

    private void spawnVillager() {
        int count = blueprint.effectiveVillagerCount();
        msg("Spawning " + count + " villager(s)...");
        for (int i = 0; i < count; i++) {
            tfbe.addImmediateReward(new SpawnVisitorReward(tfbe));
        }
        phase = Phase.WAIT_FOR_VILLAGER;
        waitTicks = 0;
        maxWaitTicks = 100 * blueprint.effectiveVillagerCount();
    }

    private void waitForVillager() {
        int expected = blueprint.effectiveVillagerCount();
        if (tfbe.getVillagerHandle().size() >= expected) {
            msg(expected + " villager(s) spawned");
            phase = Phase.ASSIGN_JOB;
            return;
        }
        if (tickTimeout("Villager spawn timed out")) {
            return;
        }
        waitTicks++;
    }

    private void assignJob() {
        for (VillagerUUID vuid : tfbe.getVillagerHandle().getVillagerJobs().keySet()) {
            tfbe.getVillagerHandle().changeJobForVillager(vuid, jobId, false);
            tfbe.getVillagerHandle().unlockJob(VillagerUUID.get(vuid), jobId);
        }
        msg("Assigned job " + jobId.rootId() + ":" + jobId.jobId() + " to " +
                blueprint.effectiveVillagerCount() + " villager(s)");
        phase = Phase.SETTLE;
        waitTicks = 0;
        maxWaitTicks = 10;
    }

    private void settle() {
        if (!tickTimeout(null)) {
            waitTicks++;
            return;
        }
        if (blueprint.drainHungerBeforeTest()) {
            drainHunger();
        }
        phase = blueprint.skipWarp() ? Phase.CAPTURE_BEFORE_REALTIME : Phase.CAPTURE_BEFORE;
    }

    private void drainHunger() {
        for (VillagerUUID vuid : tfbe.getVillagerHandle().getVillagerJobs().keySet()) {
            tfbe.getVillagerHandle().fillHunger(VillagerUUID.get(vuid), 0.0f);
        }
        msg("Drained villager hunger to 0");
    }

    private void captureBefore() {
        MCTownState state = tfbe.captureCurrentState();
        if (state != null) {
            beforeCounts = TestResultChecker.snapshotItemCounts(state);
            beforeContainerContents = TestResultChecker.snapshotContainerContents(state);
            beforeKnowledgeCount = computeKnowledgeCount();
            if (blueprint.useNaturalWarp()) {
                msg("State captured, simulating " + effectiveWarpAmount() + " ticks of player absence...");
                phase = Phase.NATURAL_WARP_FREEZE;
            } else {
                msg("State captured, starting warp of " + effectiveWarpAmount() + " ticks...");
                phase = Phase.RUN_WARP;
            }
            return;
        }
        if (waitTicks >= 50) {
            error("Failed to capture state before warp");
            phase = Phase.DONE;
            return;
        }
        waitTicks++;
    }

    private void runWarp() {
        setStartTimeIfNeeded();
        MCTownState warpState = tfbe.warpTime(effectiveWarpAmount());
        if (warpState == null) {
            error("Warp returned null state");
            phase = Phase.DONE;
            return;
        }
        logWarpContents(warpState);
        msg("Warp complete. Letting world settle...");
        phase = Phase.SETTLE_AFTER_WARP;
        waitTicks = 0;
        maxWaitTicks = 40;
    }

    private void naturalWarpFreeze() {
        setStartTimeIfNeeded();
        tfbe.freezeWarpReferenceTick();
        long currentTime = level.getDayTime();
        long targetTime = currentTime + effectiveWarpAmount();
        level.setDayTime(targetTime);
        msg("Froze warp reference at tick " + currentTime + ", advanced world to " + targetTime);
        msg("Next flag tick will detect " + effectiveWarpAmount() + " tick gap and trigger natural warp...");
        phase = Phase.NATURAL_WARP_WAIT;
        waitTicks = 0;
        maxWaitTicks = 40;
    }

    private void naturalWarpWait() {
        if (tickTimeout(null)) {
            msg("Natural warp complete. Letting world settle...");
            phase = Phase.SETTLE_AFTER_WARP;
            waitTicks = 0;
            maxWaitTicks = 40;
            return;
        }
        waitTicks++;
    }

    private void logWarpContents(MCTownState state) {
        msg("--- Warp state snapshot ---");
        for (int i = 0; i < state.containers.size(); i++) {
            var ct = state.containers.get(i);
            msg("  Container " + i + " (" + ct.getBlockPos().toShortString() + "): " + ct.toShortString(false));
        }
        for (int i = 0; i < state.villagers.size(); i++) {
            var v = state.villagers.get(i);
            var items = v.journal.items().stream()
                    .filter(it -> !it.isEmpty())
                    .map(MCHeldItem::getShortName)
                    .toList();
            msg("  Villager " + i + " (" + v.journal.jobId() + "): " + items);
        }
    }

    private void settleAfterWarp() {
        if (tickTimeout(null)) {
            setDaytime();
            phase = Phase.CHECK_RESULTS;
            return;
        }
        waitTicks++;
    }

    private void checkResults() {
        msg("Checking warp results...");
        MCTownState afterState = tfbe.captureCurrentState();
        if (afterState == null) {
            error("Failed to capture state after warp");
            phase = Phase.DONE;
            return;
        }
        Map<String, Integer> afterCounts = TestResultChecker.snapshotItemCounts(afterState);
        TestResultChecker.Result result = TestResultChecker.check(beforeCounts, afterCounts, blueprint.expectation());
        warpDeltas = result.deltas();
        boolean containerPassed = checkContainerContentsIfNeeded(
                beforeContainerContents, afterState, blueprint.expectation());
        warpPassed = result.passed() && checkKnowledgeGrowthIfNeeded() && containerPassed;
        broadcastResult("WARP", result);
        if (warpOnly) {
            phase = Phase.DONE;
            return;
        }
        phase = Phase.KILL_FOR_INSPECT;
    }

    private void broadcastResult(String phaseLabel, TestResultChecker.Result result) {
        msg(AutotestLogFormatter.format(
                AutotestLogFormatter.TRACK_JOBS,
                testName(),
                result.passed(),
                phaseLabel,
                result.summary()
        ));
        for (String detail : result.details()) {
            msg("  " + detail);
        }
    }

    private String testName() {
        return jobId.rootId() + ":" + jobId.jobId();
    }

    private void killForInspect() {
        tfbe.getVillagerHandle().entities().forEach(LivingEntity::kill);
        msg("Killed villager. Inspect chest now — continuing in 10 seconds...");
        phase = Phase.INSPECT_WARP_RESULTS;
        waitTicks = 0;
        maxWaitTicks = 200;
    }

    private void inspectWarpResults() {
        if (tickTimeout(null)) {
            phase = Phase.RESPAWN_VILLAGER;
            return;
        }
        waitTicks++;
    }

    private void respawnVillager() {
        tfbe.addImmediateReward(new SpawnVisitorReward(tfbe));
        phase = Phase.WAIT_FOR_RESPAWN;
        waitTicks = 0;
        maxWaitTicks = 100;
    }

    private void waitForRespawn() {
        if (tfbe.getVillagerHandle().size() > 0) {
            phase = Phase.REASSIGN_JOB;
            return;
        }
        if (tickTimeout("Villager respawn timed out")) {
            return;
        }
        waitTicks++;
    }

    private void reassignJob() {
        if (tfbe.getVillagerHandle().getVillagerJobs().isEmpty()) {
            error("No villager to reassign");
            phase = Phase.DONE;
            return;
        }
        for (VillagerUUID vuid : tfbe.getVillagerHandle().getVillagerJobs().keySet()) {
            tfbe.getVillagerHandle().changeJobForVillager(vuid, jobId, false);
            tfbe.getVillagerHandle().unlockJob(VillagerUUID.get(vuid), jobId);
        }
        msg("Reassigned job " + jobId.rootId() + ":" + jobId.jobId());
        phase = Phase.REFILL_CHEST;
    }

    private void setDaytime() {
        long dayTime = level.getDayTime() % 24000;
        if (dayTime > 12000) {
            long ticksUntilDay = 24000 - dayTime;
            level.setDayTime(level.getDayTime() + ticksUntilDay);
            msg("Set time to day");
        }
    }

    private void startMonitor() {
        level.setDayTime(1000);
        int rt = blueprint.effectiveRealtimeTicks(effectiveWarpAmount());
        monitorEndTick = level.getGameTime() + rt;
        lastReportedPercent = 0;
        msg("Monitoring real-time effects for " + rt + " ticks (extrapolating to " + effectiveWarpAmount() + ")...");
        phase = Phase.MONITORING;
    }

    private void monitor() {
        long currentTick = level.getGameTime();
        if (currentTick >= monitorEndTick) {
            msg("Monitoring complete");
            phase = Phase.CHECK_REALTIME_RESULTS;
            return;
        }
        int rt = blueprint.effectiveRealtimeTicks(effectiveWarpAmount());
        long elapsed = currentTick - (monitorEndTick - rt);
        int percent = (int) (elapsed * 100L / rt);
        int threshold = (percent / 10) * 10;
        if (threshold > lastReportedPercent && threshold <= 100) {
            lastReportedPercent = threshold;
            msg("Monitor: " + threshold + "% (" + elapsed + "/" + rt + " ticks)");
        }
    }

    private void refillChest() {
        msg("Refilling chest for realtime phase...");
        RoomBuilder.placeChest(level, flagPos, blueprint);
        phase = Phase.CAPTURE_BEFORE_REALTIME;
    }

    private void captureBeforeRealtime() {
        MCTownState state = tfbe.captureCurrentState();
        if (state == null) {
            error("Failed to capture state before realtime");
            phase = Phase.DONE;
            return;
        }
        beforeRealtimeCounts = TestResultChecker.snapshotItemCounts(state);
        beforeRealtimeContainerContents = TestResultChecker.snapshotContainerContents(state);
        if (!runPostSpawnActionIfNeeded()) {
            phase = Phase.DONE;
            return;
        }
        phase = Phase.START_MONITOR;
    }

    private boolean runPostSpawnActionIfNeeded() {
        TestBlueprint.PostSpawnAction action = blueprint.postSpawnAction();
        if (action == null) {
            return true;
        }
        msg("Running post-spawn ritual trigger...");
        if (!action.run(level, flagPos, tfbe, output)) {
            error("Post-spawn ritual trigger failed");
            return false;
        }
        return true;
    }

    private void checkRealtimeResults() {
        MCTownState afterState = tfbe.captureCurrentState();
        if (afterState == null) {
            error("Failed to capture state after realtime");
            phase = Phase.DONE;
            return;
        }
        Map<String, Integer> afterCounts = TestResultChecker.snapshotItemCounts(afterState);
        Map<String, Integer> rawDeltas = TestResultChecker.computeDeltas(beforeRealtimeCounts, afterCounts);

        boolean itemsPassed;
        if (blueprint.realtimeExpectation() != null) {
            TestResultChecker.Result result = TestResultChecker.checkDeltas(rawDeltas, blueprint.realtimeExpectation());
            broadcastResult("REALTIME", result);
            itemsPassed = result.passed();
        } else {
            Map<String, Integer> scaledDeltas = scaleDeltas(rawDeltas);
            TestResultChecker.Result result = TestResultChecker.checkDeltas(scaledDeltas, blueprint.expectation());
            broadcastResult("REALTIME (extrapolated)", result);
            itemsPassed = result.passed();
            if (!blueprint.skipWarp()) {
                compareWarpVsRealtime(scaledDeltas);
            }
        }

        boolean fullnessPassed = checkFullnessIfNeeded();
        boolean heldPassed = checkVillagerHeldIfNeeded(afterState);
        TestExpectation containerExpectation = blueprint.realtimeExpectation() != null
                ? blueprint.realtimeExpectation()
                : blueprint.expectation();
        boolean containerPassed = checkContainerContentsIfNeeded(
                beforeRealtimeContainerContents, afterState, containerExpectation);
        boolean customPassed = checkCustomAssertionIfNeeded();
        realtimePassed = itemsPassed && fullnessPassed && heldPassed && containerPassed && customPassed;
        phase = Phase.DONE;
    }

    private boolean checkCustomAssertionIfNeeded() {
        TestBlueprint.CustomAssertion assertion = blueprint.customAssertion();
        if (assertion == null) {
            return true;
        }
        boolean ok = assertion.check(level, flagPos, tfbe, output);
        msg("CUSTOM assertion: " + (ok ? "PASS" : "FAIL"));
        return ok;
    }

    private boolean checkContainerContentsIfNeeded(
            Map<BlockPos, Map<String, Integer>> before,
            MCTownState afterState,
            TestExpectation expectation
    ) {
        if (expectation == null || expectation.containerContents().isEmpty()) {
            return true;
        }
        // Blueprints express chest positions as flag-relative offsets; the snapshots are keyed by
        // absolute world positions, so resolve the offsets against this scenario's flag origin.
        TestExpectation resolved = expectation.withContainerContents(
                expectation.containerContents().stream()
                        .map(c -> new TestExpectation.ExpectedContainerContent(
                                flagPos.offset(c.chest()), c.item(), c.minDelta(), c.maxDelta()))
                        .toList()
        );
        Map<BlockPos, Map<String, Integer>> after = TestResultChecker.snapshotContainerContents(afterState);
        TestResultChecker.Result result = TestResultChecker.checkContainerContents(before, after, resolved);
        broadcastResult("CONTAINER", result);
        return result.passed();
    }

    private boolean checkVillagerHeldIfNeeded(MCTownState afterState) {
        TestExpectation expectation = blueprint.expectedVillagerHeld();
        if (expectation == null) {
            return true;
        }
        Map<String, Integer> held = TestResultChecker.snapshotVillagerHeldCounts(afterState);
        TestResultChecker.Result result = TestResultChecker.checkDeltas(held, expectation);
        broadcastResult("VILLAGER HELD", result);
        return result.passed();
    }

    private boolean checkFullnessIfNeeded() {
        Float minFullness = blueprint.minExpectedFullnessAfter();
        if (minFullness == null) {
            return true;
        }
        boolean allPassed = true;
        for (Map.Entry<VillagerUUID, JobID> entry : tfbe.getVillagerHandle().getVillagerJobs().entrySet()) {
            VillagerUUID vuid = entry.getKey();
            UUID uuid = VillagerUUID.get(vuid);
            VillagerStatsData stats = tfbe.getVillagerHandle().getStats(uuid);
            float fullness = stats.fullnessPercent();
            boolean passed = fullness >= minFullness;
            if (!passed) allPassed = false;
            msg(String.format("[%s] Villager fullness: %.0f%% (min: %.0f%%)",
                    passed ? "PASS" : "FAIL", fullness * 100, minFullness * 100));
        }
        return allPassed;
    }

    private int computeKnowledgeCount() {
        if (tfbe == null) {
            return 0;
        }
        KnowledgeHolder<?, MCHeldItem, ?> handle = tfbe.getKnowledgeHandle();
        if (handle == null) {
            return 0;
        }
        return handle.getAllKnownGatherResults().size();
    }

    private boolean checkKnowledgeGrowthIfNeeded() {
        if (blueprint.minKnowledgeGrowth() == null) {
            return true;
        }
        int after = computeKnowledgeCount();
        int grown = after - beforeKnowledgeCount;
        boolean ok = grown == blueprint.minKnowledgeGrowth();
        msg(String.format(
                "KNOWLEDGE [%s] grown=%d min=%d",
                ok ? "PASS" : "FAIL", grown, blueprint.minKnowledgeGrowth()));
        return ok;
    }

    private Map<String, Integer> scaleDeltas(Map<String, Integer> raw) {
        int rt = blueprint.effectiveRealtimeTicks(effectiveWarpAmount());
        int warp = effectiveWarpAmount();
        if (rt == warp) {
            return raw;
        }
        double scale = (double) warp / rt;
        Map<String, Integer> scaled = new java.util.HashMap<>();
        raw.forEach((k, v) -> scaled.put(k, (int) Math.round(v * scale)));
        return scaled;
    }

    private void compareWarpVsRealtime(Map<String, Integer> scaledRealtimeDeltas) {
        msg("--- WARP vs REALTIME comparison (realtime extrapolated to " + effectiveWarpAmount() + " ticks) ---");
        for (TestExpectation.ExpectedProduct product : blueprint.expectation().products()) {
            String item = product.itemRegistryName();
            int warpDelta = warpDeltas.getOrDefault(item, 0);
            int rtDelta = scaledRealtimeDeltas.getOrDefault(item, 0);
            int diff = Math.abs(warpDelta - rtDelta);
            String status = diff == 0 ? "[EXACT]" : diff <= 1 ? "[CLOSE]" : "[DIFF]";
            msg(String.format("  %s %s: warp=%d, realtime(scaled)=%d, diff=%d", status, item, warpDelta, rtDelta, diff));
        }
    }

    private boolean tickTimeout(String errorMessage) {
        if (waitTicks < maxWaitTicks) {
            return false;
        }
        if (errorMessage != null) {
            error(errorMessage + " (timeout after " + maxWaitTicks + " ticks)");
        }
        phase = Phase.DONE;
        return true;
    }

    private void msg(String text) {
        output.msg(text);
    }

    private void error(String text) {
        output.error(text);
    }
}
