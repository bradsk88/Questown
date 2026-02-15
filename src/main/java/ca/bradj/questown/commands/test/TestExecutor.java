package ca.bradj.questown.commands.test;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.core.init.BlocksInit;
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
        CHECK_RESULTS,
        BROADCAST,
        START_MONITOR,
        MONITORING,
        DONE
    }

    private final ServerLevel level;
    private final ServerPlayer player;
    private final BlockPos origin;
    private final JobID jobId;
    private final int warpAmount;
    private final TestBlueprint blueprint;

    private Phase phase = Phase.DESTROY_NEARBY_FLAGS;
    private int waitTicks = 0;
    private int maxWaitTicks = 0;
    private BlockPos flagPos;
    private TownFlagBlockEntity tfbe;
    private MCTownState beforeState;
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
            case CHECK_RESULTS -> checkResults();
            case BROADCAST -> { /* handled in checkResults */ }
            case START_MONITOR -> startMonitor();
            case MONITORING -> monitor();
            case DONE -> { return true; }
        }
        return phase == Phase.DONE;
    }

    private void destroyNearbyFlags() {
        int destroyed = 0;
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                for (int y = -1; y <= 4; y++) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockEntity be = level.getBlockEntity(pos);
                    if (!(be instanceof TownFlagBlockEntity tf)) {
                        continue;
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
        phase = Phase.FLATTEN;
    }

    private void flatten() {
        msg("Flattening 15x15 area...");
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                BlockPos groundPos = origin.offset(x, -1, z);
                level.setBlockAndUpdate(groundPos, Blocks.COBBLESTONE.defaultBlockState());
                for (int y = 0; y <= 4; y++) {
                    level.setBlockAndUpdate(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
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
        beforeState = tfbe.captureCurrentState();
        if (beforeState != null) {
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
        MCTownState afterState = tfbe.warpTime(warpAmount);
        if (afterState == null) {
            error("Warp returned null state");
            phase = Phase.DONE;
            return;
        }
        msg("Warp complete. Checking results...");
        TestResultChecker.Result result = TestResultChecker.check(beforeState, afterState, blueprint.expectation());
        broadcastResult(result);
        phase = Phase.START_MONITOR;
    }

    private void checkResults() {
        // handled inline in runWarp
    }

    private void broadcastResult(TestResultChecker.Result result) {
        String tag = result.passed() ? "[PASS]" : "[FAIL]";
        msg(tag + " " + result.summary());
        for (String detail : result.details()) {
            msg("  " + detail);
        }
    }

    private void startMonitor() {
        monitorEndTick = level.getGameTime() + warpAmount;
        lastReportedPercent = 0;
        msg("Monitoring real-time effects for " + warpAmount + " ticks...");
        phase = Phase.MONITORING;
    }

    private void monitor() {
        long currentTick = level.getGameTime();
        if (currentTick >= monitorEndTick) {
            msg("Monitoring complete");
            phase = Phase.DONE;
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
