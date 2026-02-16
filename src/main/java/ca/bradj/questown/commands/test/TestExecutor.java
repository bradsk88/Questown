package ca.bradj.questown.commands.test;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.rewards.SpawnVisitorReward;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;

public class TestExecutor {

    private enum Phase {
        DESTROY_NEARBY_FLAGS,
        FLATTEN,
        PLACE_FLAG,
        WAIT_FOR_INIT,
        BUILD_ROOM,
        REGISTER_ROOM,
        WAIT_FOR_ROOM,
        SPAWN_VILLAGER,
        WAIT_FOR_VILLAGER,
        ASSIGN_JOB,
        SETTLE,
        CAPTURE_BEFORE,
        RUN_WARP,
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
    private final ServerPlayer player;
    private BlockPos origin;
    private final JobID jobId;
    private final int warpAmount;
    private final TestBlueprint blueprint;

    private Phase phase = Phase.DESTROY_NEARBY_FLAGS;
    private int waitTicks = 0;
    private int maxWaitTicks = 0;
    private BlockPos flagPos;
    private TownFlagBlockEntity tfbe;
    private Map<String, Integer> beforeCounts;
    private Map<String, Integer> warpDeltas;
    private Map<String, Integer> beforeRealtimeCounts;
    private long monitorEndTick;
    private int lastReportedPercent = 0;

    public TestExecutor(
            ServerLevel level,
            ServerPlayer player,
            BlockPos origin,
            JobID jobId,
            int warpAmount,
            TestBlueprint blueprint
    ) {
        this.level = level;
        this.player = player;
        this.origin = origin;
        this.jobId = jobId;
        this.warpAmount = warpAmount;
        this.blueprint = blueprint;
    }

    /**
     * Called each server tick. Returns true when the executor is done.
     */
    public boolean tick() {
        switch (phase) {
            case DESTROY_NEARBY_FLAGS -> destroyNearbyFlags();
            case FLATTEN -> flatten();
            case PLACE_FLAG -> placeFlag();
            case WAIT_FOR_INIT -> waitForInit();
            case BUILD_ROOM -> buildRoom();
            case REGISTER_ROOM -> registerRoom();
            case WAIT_FOR_ROOM -> waitForRoom();
            case SPAWN_VILLAGER -> spawnVillager();
            case WAIT_FOR_VILLAGER -> waitForVillager();
            case ASSIGN_JOB -> assignJob();
            case SETTLE -> settle();
            case CAPTURE_BEFORE -> captureBefore();
            case RUN_WARP -> runWarp();
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
        int destroyed = 0;
        BlockPos firstFlagPos = null;
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                for (int y = -1; y <= 4; y++) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockEntity be = level.getBlockEntity(pos);
                    if (!(be instanceof TownFlagBlockEntity tf)) {
                        continue;
                    }
                    if (firstFlagPos == null) {
                        firstFlagPos = pos;
                    }
                    tf.getVillagerHandle().entities().forEach(LivingEntity::kill);
                    level.removeBlockEntity(pos);
                    level.removeBlock(pos, true);
                    destroyed++;
                }
            }
        }
        if (destroyed > 0) {
            msg("Destroyed " + destroyed + " nearby flag(s)");
        }
        if (firstFlagPos != null) {
            origin = firstFlagPos;
            msg("Using existing flag position as origin: " + origin.toShortString());
        }
        phase = Phase.FLATTEN;
    }

    private void flatten() {
        msg("Flattening 15x15 area...");
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                for (int y = 4; y >= 0; y--) {
                    BlockPos pos = origin.offset(x, y, z);
                    level.removeBlockEntity(pos);
                    level.removeBlock(pos, false);
                }
                BlockPos groundPos = origin.offset(x, -1, z);
                level.setBlockAndUpdate(groundPos, Blocks.COBBLESTONE.defaultBlockState());
            }
        }
        phase = Phase.PLACE_FLAG;
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
        BlockPos doorWorldPos = flagPos.offset(blueprint.doorOrGateOffset());
        if (blueprint.roomType() == TestBlueprint.RoomType.FARM) {
            tfbe.getRoomHandle().registerFenceGate(doorWorldPos);
            msg("Registered fence gate at " + doorWorldPos.toShortString());
        } else {
            tfbe.getRoomHandle().registerDoor(doorWorldPos);
            msg("Registered door at " + doorWorldPos.toShortString());
        }
        phase = Phase.WAIT_FOR_ROOM;
        waitTicks = 0;
        maxWaitTicks = 300;
    }

    private void waitForRoom() {
        if (!tfbe.getRoomHandle().getRoomsMatching(blueprint.roomId()).isEmpty()) {
            msg("Room detected: " + blueprint.roomId());
            phase = Phase.SPAWN_VILLAGER;
            return;
        }
        if (tickTimeout("Room detection timed out for " + blueprint.roomId())) {
            return;
        }
        waitTicks++;
    }

    private void spawnVillager() {
        msg("Spawning villager...");
        tfbe.addImmediateReward(new SpawnVisitorReward(tfbe));
        phase = Phase.WAIT_FOR_VILLAGER;
        waitTicks = 0;
        maxWaitTicks = 100;
    }

    private void waitForVillager() {
        if (tfbe.getVillagerHandle().size() > 0) {
            msg("Villager spawned");
            phase = Phase.ASSIGN_JOB;
            return;
        }
        if (tickTimeout("Villager spawn timed out")) {
            return;
        }
        waitTicks++;
    }

    private void assignJob() {
        VillagerUUID vuid = tfbe.getVillagerHandle().getVillagerJobs().keySet().stream()
                .findFirst()
                .orElse(null);
        if (vuid == null) {
            error("No villager found to assign job");
            phase = Phase.DONE;
            return;
        }
        tfbe.getVillagerHandle().changeJobForVillager(vuid, jobId, false);
        tfbe.getVillagerHandle().unlockJob(VillagerUUID.get(vuid), jobId);
        msg("Assigned job " + jobId.rootId() + ":" + jobId.jobId() + " to villager");
        phase = Phase.SETTLE;
        waitTicks = 0;
        maxWaitTicks = 10;
    }

    private void settle() {
        if (tickTimeout(null)) {
            phase = Phase.CAPTURE_BEFORE;
            return;
        }
        waitTicks++;
    }

    private void captureBefore() {
        MCTownState state = tfbe.captureCurrentState();
        if (state != null) {
            beforeCounts = TestResultChecker.snapshotItemCounts(state);
            msg("State captured, starting warp of " + warpAmount + " ticks...");
            phase = Phase.RUN_WARP;
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
        MCTownState warpState = tfbe.warpTime(warpAmount);
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
        broadcastResult("WARP", result);
        phase = Phase.KILL_FOR_INSPECT;
    }

    private void broadcastResult(String phaseLabel, TestResultChecker.Result result) {
        String tag = result.passed() ? "[PASS]" : "[FAIL]";
        msg(tag + " " + phaseLabel + ": " + result.summary());
        for (String detail : result.details()) {
            msg("  " + detail);
        }
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
        VillagerUUID vuid = tfbe.getVillagerHandle().getVillagerJobs().keySet().stream()
                .findFirst()
                .orElse(null);
        if (vuid == null) {
            error("No villager to reassign");
            phase = Phase.DONE;
            return;
        }
        tfbe.getVillagerHandle().changeJobForVillager(vuid, jobId, false);
        tfbe.getVillagerHandle().unlockJob(VillagerUUID.get(vuid), jobId);
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
        setDaytime();
        monitorEndTick = level.getGameTime() + warpAmount;
        lastReportedPercent = 0;
        msg("Monitoring real-time effects for " + warpAmount + " ticks...");
        phase = Phase.MONITORING;
    }

    private void monitor() {
        long currentTick = level.getGameTime();
        if (currentTick >= monitorEndTick) {
            msg("Monitoring complete");
            phase = Phase.CHECK_REALTIME_RESULTS;
            return;
        }
        long elapsed = currentTick - (monitorEndTick - warpAmount);
        int percent = (int) (elapsed * 100L / warpAmount);
        int threshold = (percent / 10) * 10;
        if (threshold > lastReportedPercent && threshold <= 100) {
            lastReportedPercent = threshold;
            msg("Monitor: " + threshold + "% (" + elapsed + "/" + warpAmount + " ticks)");
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
        phase = Phase.START_MONITOR;
    }

    private void checkRealtimeResults() {
        MCTownState afterState = tfbe.captureCurrentState();
        if (afterState == null) {
            error("Failed to capture state after realtime");
            phase = Phase.DONE;
            return;
        }
        Map<String, Integer> afterCounts = TestResultChecker.snapshotItemCounts(afterState);
        TestResultChecker.Result result = TestResultChecker.check(beforeRealtimeCounts, afterCounts, blueprint.expectation());
        broadcastResult("REALTIME", result);
        compareWarpVsRealtime(result.deltas());
        phase = Phase.DONE;
    }

    private void compareWarpVsRealtime(Map<String, Integer> realtimeDeltas) {
        msg("--- WARP vs REALTIME comparison ---");
        for (TestExpectation.ExpectedProduct product : blueprint.expectation().products()) {
            String item = product.itemRegistryName();
            int warpDelta = warpDeltas.getOrDefault(item, 0);
            int rtDelta = realtimeDeltas.getOrDefault(item, 0);
            int diff = Math.abs(warpDelta - rtDelta);
            String status = diff == 0 ? "[EXACT]" : diff <= 1 ? "[CLOSE]" : "[DIFF]";
            msg(String.format("  %s %s: warp=%d, realtime=%d, diff=%d", status, item, warpDelta, rtDelta, diff));
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
        Compat.sendMessage(player, Component.literal("[qt test] " + text));
        QT.FLAG_LOGGER.info("[qt test] {}", text);
    }

    private void error(String text) {
        Compat.sendMessage(player, Component.literal("[qt test ERROR] " + text));
        QT.FLAG_LOGGER.error("[qt test] {}", text);
    }
}
