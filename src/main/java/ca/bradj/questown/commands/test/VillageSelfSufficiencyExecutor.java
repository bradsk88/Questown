package ca.bradj.questown.commands.test;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.mobs.visitor.TownieNeed;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;


/**
 * Self-driving watchtower scenario: plays a just-past-onboarding town forward in
 * realtime (no warp) and surfaces soft-locks so a dev can design around them.
 * It is not a pass/fail gate: the output is a deterministic daily report plus
 * lock-detection dumps, keyed by day, on the {@code [village]} log prefix.
 *
 * <p>The seed is code-built each run (no save snapshot): an arena, a handful of
 * buildings, a flag + lit campfire + job board in a
 * registered store room, ~5 villagers via SpawnVisitorReward, and jobs
 * assigned directly (a hunter for food plus the starting jobs). The chicken
 * arc is skipped entirely.
 *
 * <p>Food needs two things. *Survival* is hunter-only: villagers eat raw meat via
 * the raw-food diner work and survive at a mood penalty, so no cook is needed to
 * keep them alive. *Working* is different: a hunter or gatherer eats a cooked
 * item from #questown:villager_food to keep working (their job's ingredient), so
 * the seed must stock the store chest with cooked meat, not raw. Seeding only raw
 * meat left the town in NO_WORK_POSSIBLE.
 *
 * <p>Watchtower layers (built up incrementally; this file currently holds the
 * seed + run window):
 * <ul>
 *   <li>container-flow counter (produced/consumed/stock per item per day)</li>
 *   <li>deterministic daily report (six categories + day-over-day delta)</li>
 *   <li>lock detectors (known-lock tripwires + forward-progress net)</li>
 *   <li>self-driving room builder (open room quest -&gt; flag.buildRoom + recipe block)</li>
 * </ul>
 */
public final class VillageSelfSufficiencyExecutor {

    private enum Phase {
        RESET_ARENA,
        BUILD_SEED,
        PLACE_FLAG,
        WAIT_FOR_FLAG,
        WAIT_FOR_ROOMS,
        FURNISH_ROOMS,
        SPAWN_VILLAGERS,
        WAIT_FOR_VILLAGERS,
        ASSIGN_JOBS,
        WAIT_FOR_JOBS,
        WATCH,
        DONE
    }

    private static final String PFX = "[self_suff] ";

    /** The run window in ticks. 5 in-game days is the default; tune via the
     *  questown.village.selfsuff.days system property (up to a sane ceiling). */
    private static final int DEFAULT_DAYS = 5;
    private static final int MAX_DAYS = 10;
    private static final int TICKS_PER_DAY = 24000;

    /** Spawn N villagers at the town gate. Two are assigned by the seed (one
     *  hunter, one gatherer); the third is left unassigned. Roster 3 is the
     *  threshold that opens the reworked onboarding tutorial phase 2.5
     *  (PHASE_JOB_CHANGE_FOOD): the batch staffs the cook (JOB_CHANGE), needs
     *  the kitchen_small room (built by the room-builder below), and completes
     *  via 8x villager_food under real ticks; roster 2 (the old seed) plateaus
     *  at the threshold and the phase never opens. */
    private static final int VILLAGER_COUNT = 3;

    /** Opening food block per cooked-meat item in the store chest. The seed is a
     *  one-time bootstrap, not a supply: the town is meant to outproduce its diner
     *  consumption once its job chain is working, and the watchtower measures how
     *  often the opening block drains to zero (a food-lock) before a random
     *  job-change quest brings up a cook. Sizing is a tuning knob, logged at seed
     *  time; too little locks on day one, too much hides the lock the test exists
     *  to find. 48 total (16x3) drained in a day with no production, so the block
     *  is raised to bridge the expected gap until the first cook turns the chain. */
    private static final int FOOD_SEED_PER_ITEM = 48;

    /** Buildings around the flag: 3x3 interior, one south door, one chest. */
    private static final int BUILDING_SIZE = 5;

    private final ServerLevel level;
    private final MinecraftServer server;
    private final ServerPlayer player;
    private final BlockPos origin;

    private Phase phase = Phase.RESET_ARENA;
    private @Nullable BlockPos flagPos;
    private @Nullable TownFlagBlockEntity flag;
    private @Nullable BlockPos campfirePos;
    private @Nullable BlockPos boardRoomCorner;
    private @Nullable BlockPos storeRoomCorner;
    private @Nullable BlockPos boardRoomDoorPos;
    private @Nullable BlockPos storeRoomDoorPos;
    private @Nullable BlockPos boardPos;
    private @Nullable BlockPos storeChestPos;
    private int ticks = 0;
    private int waitTicks = 0;
    private long watchEndTick = -1;

    /** Recipe IDs that have been completed by the builder. Do not re-build.
     *  Keyed by recipe ID (not batch UUID) because a batch can contain multiple
     *  room quests (job_board, store_room, town_gate all share one batch). */
    private final java.util.Set<net.minecraft.resources.ResourceLocation> completedRoomRecipes = new java.util.HashSet<>();
    /** Room recipes whose room has been built but whose quest is not yet COMPLETE.
     *  The builder waits for the room to be re-detected and the quest to close,
     *  then the sweep at the top of {@code driveRoomBuilder} latches the recipe
     *  into {@code completedRoomRecipes}. Mirrors the town_gate welcome-mat path.
     *  (Latching at build time would skip the quest before re-detection could
     *  close it, and a multi-ingredient recipe needs every block placed anyway.) */
    private final java.util.Set<net.minecraft.resources.ResourceLocation> builtRoomsAwaiting = new java.util.HashSet<>();
    /** Tick counter for the room-builder cadence (every 200 ticks = ~10s). The
     *  job-change driver rides the same cadence. */
    private int roomBuilderTicks = 0;
    /** Open JOB_CHANGE quests already driven, keyed by the quest's wanted
     *  "root/work" string. Pruned each cycle to the still-ACTIVE job-change
     *  quests, so a quest that closes (a townie now holds that job) releases its
     *  entry and a later identical quest can be driven again. Guarding by the
     *  wanted job (not the villager) means one driven change is enough per open
     *  quest: processJobChanges adopts ANY townie holding the exact job as the
     *  batch owner, so a single converted townie closes it. */
    private final java.util.Set<String> jobChangeDriven = new java.util.HashSet<>();
    /** Container-flow counter wiring the store chest (and future room chests)
     *  to a per-item produced/consumed/stock record. See ContainerFlowTracker. */
    private final ContainerFlowTracker flowTracker = new ContainerFlowTracker();
    /** Night-sleep counter; the daily report keys its day number on this (one
     *  report per completed night, plus a baseline at watch start). */
    private int nightsSlept = 0;
    /** True once the first night has been slept through; reported as a milestone
     *  in the daily report. */
    private boolean firstNightPassed = false;
    // DEV-DIAG: stall score-dump, 2026-10-03 (wigeon) — keep/remove per handoff
    private int seekingStallPasses = 0;
    private boolean seekingStallDiagEmitted = false;
    /** Milestones, latched once and reported from then on: a townie holding a
     *  cook job (the food chain is producing cooked food) and/or a farmer job. */
    private boolean cookUnlocked = false;
    private boolean farmerUnlocked = false;
    private boolean milestoneLogged = false;
    /** Latched once the opening food block has been written into the store chest.
     *  The write happens at watch start, after the flag is live and past its first
     *  tick (which restores chest contents from its captured NBT state and would
     *  flush a chest filled during furnish). */
    private boolean openingFoodSeeded = false;
    /** Last day number the daily report was emitted for; -1 before the first report. */
    private long lastReportedDay = -1;
    /** Snapshot of the six report categories at the last report, for the delta. */
    private ReportSnapshot lastSnapshot;

    /**
     * Lock-detector state. Each detector tracks how long its condition has been
     * true and fires once (with a re-arm) when it exceeds a bound. The
     * forward-progress net compares day-snapshots and fires when all tracked
     * values are unchanged across a full day.
     */
    /** Per-villager production-status tick counter, keyed by villager UUID.
     *  A value of 0 means the villager is not currently in a lock status. */
    private final java.util.Map<java.util.UUID, Integer> lockStatusTicks = new java.util.HashMap<>();
    /** Per-villager UNMET_ITEM tick counter, keyed by villager UUID. */
    private final java.util.Map<java.util.UUID, Integer> unmetItemTicks = new java.util.HashMap<>();
    /** Tick at which a ROOM quest was first seen ACTIVE, keyed by recipe path. */
    private final java.util.Map<String, Long> roomQuestOpenSince = new java.util.HashMap<>();
    /** Peak roster seen so far, for the leaver-never-returning detector. */
    private long peakRoster = 0;
    /** Tick at which the roster dropped below peak, or -1 if at peak. */
    private long rosterDropSinceTick = -1;
    /** Last day-snapshot for the forward-progress net. */
    private ReportSnapshot prevDaySnapshot;
    /** Consecutive days with no change in (roster, questsOpen, questsDone, stock). */
    private long noProgressDays = 0;
    /** Ticks the town has been at or below 50% of peak roster. */
    private long lowRosterTicks = 0;

    /** Bound (in ticks) after which a lock status is reported. ~100s. */
    private static final int LOCK_STATUS_BOUND_TICKS = 2000;
    /** Bound (in ticks) after which UNMET_ITEM is reported. ~100s. */
    private static final int UNMET_ITEM_BOUND_TICKS = 2000;
    /** Bound (in ticks) after which a ROOM quest open is reported. ~5 game days. */
    private static final long ROOM_QUEST_OPEN_BOUND_TICKS = 120_000L;
    /** Bound (in ticks) after which a roster drop is reported. ~5 game days. */
    private static final long ROSTER_DROP_BOUND_TICKS = 120_000L;
    /** Bound (in ticks) after which low roster (below 50% peak) is reported. */
    private static final long LOW_ROSTER_BOUND_TICKS = 120_000L;
    /** Forward-progress net: minimum days of no-change before firing. */
    private static final long PROGRESS_NET_MIN_DAYS = 2;

    /**
     * Run all lock detectors once per tick. Each detector logs a dump on
     * detection and re-arms after a cooldown; it does not kill the run.
     */
    private void checkLockDetectors() {
        long nowTick = level.getGameTime();
        var villagerHandle = flag.getVillagerHandle();
        long currentRoster = villagerHandle.size();

        // Track peak roster and roster drop.
        if (currentRoster > peakRoster) {
            peakRoster = currentRoster;
            rosterDropSinceTick = -1;
        }
        if (currentRoster < peakRoster && rosterDropSinceTick < 0) {
            rosterDropSinceTick = nowTick;
        }

        // --- Per-villager lock-status and UNMET_ITEM detectors ---
        // Build the set of current villager UUIDs so we can clear stale entries.
        var currentUuids = new java.util.HashSet<java.util.UUID>();
        for (LivingEntity v : villagerHandle.entities()) {
            VisitorMobEntity t = (VisitorMobEntity) v;
            java.util.UUID uuid = t.getUUID();
            currentUuids.add(uuid);

            // Production status (NO_JOBSITE / NO_SUPPLIES / NO_WORK_POSSIBLE).
            var status = t.getStatusForServer();
            String statusName = status != null ? status.name() : "";
            boolean isLockStatus = statusName.equals("NO_JOBSITE")
                    || statusName.equals("NO_SUPPLIES")
                    || statusName.equals("NO_WORK_POSSIBLE");

            if (isLockStatus) {
                int ticksIn = lockStatusTicks.merge(uuid, 1, Integer::sum);
                if (ticksIn == LOCK_STATUS_BOUND_TICKS) {
                    msg("[lock] NO_JOBSITE/NO_SUPPLIES/NO_WORK_POSSIBLE: "
                            + uuid.toString().substring(0, 8)
                            + ":" + t.getJobId().rootId()
                            + "/" + t.getJobId().jobId()
                            + " status=" + statusName
                            + " for " + (ticksIn / 20) + "s"
                            + " (unmet item: " + t.getUnmetItem() + ")");
                    dumpLockContext(uuid, t);
                }
            } else {
                lockStatusTicks.remove(uuid);
            }

            // UNMET_ITEM need.
            TownieNeed need = t.getNeed();
            if (need == TownieNeed.UNMET_ITEM) {
                int ticksUnmet = unmetItemTicks.merge(uuid, 1, Integer::sum);
                if (ticksUnmet == UNMET_ITEM_BOUND_TICKS) {
                    msg("[lock] UNMET_ITEM: "
                            + uuid.toString().substring(0, 8)
                            + ":" + t.getJobId().rootId()
                            + "/" + t.getJobId().jobId()
                            + " missing=" + t.getUnmetItem()
                            + " for " + (ticksUnmet / 20) + "s");
                    dumpLockContext(uuid, t);
                }
            } else {
                unmetItemTicks.remove(uuid);
            }
        }
        // Clear stale entries for villagers no longer on the roster.
        lockStatusTicks.keySet().retainAll(currentUuids);
        unmetItemTicks.keySet().retainAll(currentUuids);

        // --- Room quest open-too-long detector ---
        for (var entry : flag.getAllQuestsWithRewards()) {
            var quest = entry.getKey();
            if (quest.getType() != ca.bradj.questown.town.quests.Quest.QuestType.ROOM) continue;
            String recipePath = quest.getWantedId().getPath();
            if (quest.getStatus() == ca.bradj.questown.town.quests.Quest.QuestStatus.ACTIVE) {
                roomQuestOpenSince.computeIfAbsent(recipePath, k -> nowTick);
                long openTicks = nowTick - roomQuestOpenSince.get(recipePath);
                if (openTicks == ROOM_QUEST_OPEN_BOUND_TICKS) {
                    msg("[lock] ROOM_QUEST_OPEN_TOO_LONG: " + recipePath
                            + " open for " + (openTicks / 20000.0) + " game-days");
                }
            } else {
                roomQuestOpenSince.remove(recipePath);
            }
        }

        // --- Leaver-never-returning detector ---
        if (rosterDropSinceTick >= 0) {
            long dropTicks = nowTick - rosterDropSinceTick;
            if (dropTicks == ROSTER_DROP_BOUND_TICKS) {
                msg("[lock] LEAVER_NEVER_RETURNING: roster dropped to "
                        + currentRoster + " (peak " + peakRoster
                        + ") and has not recovered for " + (dropTicks / 20) + "s");
            }
        }

        // --- Low-roster detector (below 50% of peak) ---
        if (peakRoster > 0 && currentRoster < peakRoster / 2) {
            lowRosterTicks++;
            if (lowRosterTicks == LOW_ROSTER_BOUND_TICKS) {
                msg("[lock] LOW_ROSTER: roster=" + currentRoster
                        + " < 50% of peak=" + peakRoster
                        + " for " + (lowRosterTicks / 20) + "s");
            }
        } else {
            lowRosterTicks = 0;
        }

        // --- Forward-progress net: no change in (roster, questsOpen, questsDone, stock) across days ---
        // Uses the noProgressDays counter, updated at the top of each day by
        // advanceProgressNet() (called from emitDailyReport on day-number change).
        if (noProgressDays >= PROGRESS_NET_MIN_DAYS && lastSnapshot != null) {
            msg("[lock] NO_FORWARD_PROGRESS: no change in (roster, questsOpen, questsDone, stock) "
                    + "across " + noProgressDays + " consecutive days: "
                    + "roster=" + lastSnapshot.roster()
                    + " questsOpen=" + lastSnapshot.questsOpen()
                    + " questsDone=" + lastSnapshot.questsDone()
                    + " stock=" + lastSnapshot.stockTotal());
        }
    }

    /**
     * Called from emitDailyReport when the day number advances. Compares the
     * just-written lastSnapshot against prevDaySnapshot, increments
     * noProgressDays if unchanged, resets if changed, and shifts the baseline.
     */
    private void advanceProgressNet() {
        if (lastSnapshot == null) return;
        if (prevDaySnapshot == null) {
            prevDaySnapshot = lastSnapshot;
            return;
        }
        boolean changed = lastSnapshot.roster() != prevDaySnapshot.roster()
                || lastSnapshot.questsOpen() != prevDaySnapshot.questsOpen()
                || lastSnapshot.questsDone() != prevDaySnapshot.questsDone()
                || lastSnapshot.stockTotal() != prevDaySnapshot.stockTotal();
        if (changed) {
            noProgressDays = 0;
        } else {
            noProgressDays++;
        }
        prevDaySnapshot = lastSnapshot;
    }

    private void dumpLockContext(java.util.UUID uuid, VisitorMobEntity t) {
        msg("[lock]   context: job=" + t.getJobId()
                + " need=" + t.getNeed()
                + " unmetItem=" + t.getUnmetItem()
                + " status=" + t.getStatusForServer());
        // Quest summary.
        long open = 0, done = 0;
        for (var entry : flag.getAllQuestsWithRewards()) {
            if (entry.getKey().isComplete()) done++;
            else open++;
        }
        msg("[lock]   quests open=" + open + " done=" + done);
    }

    /** The six report categories at one point in time, for the day-over-day delta. */
    private record ReportSnapshot(
            long roster,
            long questsOpen,
            long questsDone,
            long unmetItem,
            long cantReach,
            long noneNeed,
            long stockTotal,
            long producedTotal,
            long consumedTotal
    ) {
        static ReportSnapshot ZERO = new ReportSnapshot(0,0,0,0,0,0,0,0,0);
    }

    public VillageSelfSufficiencyExecutor(
            ServerLevel level,
            MinecraftServer server,
            ServerPlayer player,
            BlockPos origin
    ) {
        this.level = level;
        this.server = server;
        this.player = player;
        this.origin = origin;
    }

    /** Advance one server tick. Returns true when the scenario has finished. */
    public boolean tick() {
        try {
            switch (phase) {
                case RESET_ARENA -> resetArena();
                case BUILD_SEED -> buildSeed();
                case PLACE_FLAG -> placeFlag();
                case WAIT_FOR_FLAG -> waitForFlag();
                case WAIT_FOR_ROOMS -> waitForRooms();
                case FURNISH_ROOMS -> furnishRooms();
                case SPAWN_VILLAGERS -> spawnVillagers();
                case WAIT_FOR_VILLAGERS -> waitForVillagers();
                case ASSIGN_JOBS -> assignJobs();
                case WAIT_FOR_JOBS -> waitForJobs();
                case WATCH -> watch();
                case DONE -> { return true; }
            }
        } catch (Exception e) {
            msg("EXCEPTION in " + phase + ": " + e);
            phase = Phase.DONE;
        }
        return phase == Phase.DONE;
    }

    // ---- seed ----

    private void resetArena() {
        TestArenaPreparer.PreparerOptions opts = new TestArenaPreparer.PreparerOptions(28, true, true, true);
        TestArenaPreparer.destroyNearbyFlags(level, origin, opts, player);
        TestArenaPreparer.flatten(level, origin, opts);
        msg("Arena reset around " + origin.toShortString());
        phase = Phase.BUILD_SEED;
    }

    private void buildSeed() {
        // A just-past-onboarding town: flag + lit campfire + a job board in a
        // registered room + one registered store room
        // with a seeded supply chest. The rooms are laid as clean rectangular
        // shells (the growth scenario's building shape, which the wall walk closes
        // cleanly) and their doors are registered with the flag, so they are
        // *registered* rooms whose interior chests are containers the jobs draw
        // from. (flag.buildRoom's chicken shell leaks at this footprint, so a
        // registered simple room is the reliable seed.)

        // Lit campfire two blocks south of the flag spot; the flag auto-detects
        // it the same way the growth scenario's campfire is detected.
        campfirePos = origin.south(2);
        level.setBlockAndUpdate(campfirePos.below(), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(campfirePos, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        level.setBlockAndUpdate(campfirePos.above(), Blocks.AIR.defaultBlockState());

        // Two rooms: one for the job board, one for the store (supply chest).
        // 5x5 outer walls, single south door, 3x3 interior, chest at the interior
        // corner.
        boardRoomCorner = new BlockPos(origin.getX() + 10, origin.getY(), origin.getZ() + 8);
        storeRoomCorner = new BlockPos(origin.getX() - 12, origin.getY(), origin.getZ() - 12);
        buildOneBuilding(boardRoomCorner);
        buildOneBuilding(storeRoomCorner);
        boardRoomDoorPos = boardRoomDoor();
        storeRoomDoorPos = storeRoomDoor();

        msg("Seed built: campfire at " + campfirePos.toShortString()
                + ", board room (door " + boardRoomDoorPos.toShortString() + ")"
                + ", store room (door " + storeRoomDoorPos.toShortString() + ")");
        phase = Phase.PLACE_FLAG;
    }

    /** One rectangular building: floor, plank walls, flat roof, one oak door
     *  (south). Mirrors the growth scenario's building (which the wall walk
     *  closes cleanly, unlike flag.buildRoom's chicken shell). */
    private void buildOneBuilding(BlockPos corner) {
        int s = BUILDING_SIZE - 1;
        for (int dx = 0; dx <= s; dx++) {
            for (int dz = 0; dz <= s; dz++) {
                BlockPos at = corner.offset(dx, 0, dz);
                level.setBlockAndUpdate(at.below(), Blocks.DIRT.defaultBlockState());
                boolean edge = dx == 0 || dx == s || dz == 0 || dz == s;
                if (edge) {
                    boolean door = dx == s / 2 && dz == s;
                    for (int dy = 0; dy <= 2; dy++) {
                        BlockPos wall = at.above(dy);
                        if (door && dy <= 1) {
                            level.setBlockAndUpdate(wall, Blocks.OAK_DOOR.defaultBlockState()
                                    .setValue(DoorBlock.HALF, dy == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER));
                        } else {
                            level.setBlockAndUpdate(wall, Blocks.OAK_PLANKS.defaultBlockState());
                        }
                    }
                }
                level.setBlockAndUpdate(at.above(3), Blocks.OAK_PLANKS.defaultBlockState());
            }
        }
    }

    private BlockPos boardRoomDoor() {
        int s = BUILDING_SIZE - 1;
        return boardRoomCorner.offset(s / 2, 0, s);
    }

    private BlockPos storeRoomDoor() {
        int s = BUILDING_SIZE - 1;
        return storeRoomCorner.offset(s / 2, 0, s);
    }

    private void placeFlag() {
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsInit.TOWN_FLAG_BLOCK.get()));
        BlockPos ground = origin.below();
        level.setBlockAndUpdate(ground, Blocks.GRASS_BLOCK.defaultBlockState());
        var hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(ground), net.minecraft.core.Direction.UP, ground, false);
        player.gameMode.useItemOn(player, level, player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, hit);
        flagPos = origin;
        msg("Placed flag via player useItemOn at " + flagPos.toShortString());
        phase = Phase.WAIT_FOR_FLAG;
        waitTicks = 0;
    }

    private void waitForFlag() {
        flag = TownFlagBlockEntity.getFromPos(level, flagPos);
        if (flag != null) {
            // The welcome mat (town gate) is NOT placed here. It is the town gate:
            // the gatherer and hunter jobs anchor to special_quest.town_gate.
            // Placing it in the seed (before the quest is open) registers a mat
            // the quest never picks up, so the quest never closes and the builder
            // then lays a second mat inside a room shell. The room-builder places
            // it (via placeWelcomeMat, which registers it) once the quest becomes
            // available.

            // Register both room doors with the flag (the same path a wand click
            // on a door takes). The rectangular shells were laid in buildSeed; the
            // rooms are detected by the scan on a later tick once the doors are
            // registered.
            flag.getRoomHandle().registerDoor(boardRoomDoorPos);
            flag.getRoomHandle().registerDoor(storeRoomDoorPos);

            msg("Doors registered at "
                    + boardRoomDoorPos.toShortString() + " (board) and "
                    + storeRoomDoorPos.toShortString() + " (store)");
            msg("Flag live; waiting for rooms to register");
            phase = Phase.WAIT_FOR_ROOMS;
            waitTicks = 0;
            return;
        }
        if (++waitTicks > 200) {
            msg("FAIL flag BE never appeared");
            phase = Phase.DONE;
        }
    }

    /** Wait for both rooms to be detected by the scan. The scan runs on flag
     *  ticks, and the flag ticks only with a player nearby; in a held run the
     *  runClient observer may join late. Wait for the rooms rather than racing
     *  ahead on a timer (furnishing before detection places the chest outside a
     *  registered room, where the jobs cannot see it). */
    private void waitForRooms() {
        boolean boardReady = hasInteriorCell(boardRoomCorner);
        boolean storeReady = hasInteriorCell(storeRoomCorner);
        if (boardReady && storeReady) {
            msg("Both rooms registered");
            phase = Phase.FURNISH_ROOMS;
            return;
        }
        if (++waitTicks % 200 == 0) {
            msg("waiting for rooms: board=" + boardReady + " store=" + storeReady
                    + " (the scan runs on flag ticks, which need a player nearby)");
        }
    }

    /** Place the job board in the board room's interior and the seeded supply chest
     *  in the store room's interior. A chest in a registered room's interior is a
     *  registered container the jobs draw from; a chest outside a registered room is
     *  invisible to them. */
    private void furnishRooms() {
        // Job board in the board room's interior.
        int boardCell = firstInteriorCellIndex(boardRoomCorner);
        if (boardCell >= 0) {
            BlockPos boardPosIn = flag.placeBlockInInterior(boardCell, ca.bradj.questown.core.init.BlocksInit.JOB_BOARD_BLOCK.get());
            if (boardPosIn != null) {
                flag.registerJobsBoard(boardPosIn);
                msg("Job board placed in board room interior at " + boardPosIn.toShortString());
            } else {
                msg("WARN board room has no interior cell; job board not placed");
            }
        } else {
            msg("WARN board room has no interior cell; job board not placed");
        }

        // Store room: a chest seeded with the town's starting supplies. The food
        // chain is hunter-only: villagers eat raw meat via the raw-food diner work.
        // The hunter needs a sword as its tool; without one it sits in
        // UNMET_ITEM(Swords) forever (a soft-lock the watchtower should catch if
        // the seed did not supply it).
        int storeCell = firstInteriorCellIndex(storeRoomCorner);
        if (storeCell >= 0) {
            // placeBlockInInterior returns the real cell the chest landed on; capture
            // it in the same call rather than re-deriving it from interiorCells() (which
            // is recomputed per call and its index can shift, which is what left the old
            // seed one cell off the chest actually placed).
            storeChestPos = flag.placeBlockInInterior(storeCell, net.minecraft.world.level.block.Blocks.CHEST);
            if (storeChestPos != null) {
                // The chest is PLACED here but NOT filled here: the flag's first tick
                // restores chest contents from its captured NBT state and would flush a
                // chest filled during furnish. seedOpeningFood() writes the block in at
                // watch start, once, against this captured position.
                //
                // The opening block is food only, and COOKED meat, not raw: a townie's
                // working ingredient is #questown:villager_food, and that tag is cooked
                // items only (bread, cooked_*, baked_potato, ...). Raw meat is the
                // *survival* food a raw-food diner work consumes, not the working
                // ingredient a hunter or gatherer eats to keep working. Seeding raw
                // meat left the whole town in NO_WORK_POSSIBLE (the work-seeker found
                // no villager_food in any registered room's chest). A chest in a
                // registered room is searched for ingredients by the work-seeker, so
                // the store room's chest works. The wooden/stone sword is NOT seeded at
                // all: the sword *quest* only opens after the first villager arrives and
                // is satisfied the real-player path by satisfyOpenSwordQuestIfVillager.
                msg("Store chest placed at " + storeChestPos.toShortString()
                        + " (the " + (FOOD_SEED_PER_ITEM * 3) + " opening food block is seeded once the flag is live, post-first-tick)");
            } else {
                msg("WARN store room has no indexable interior cell; store chest not placed");
            }
        } else {
            msg("WARN store room has no interior cell; store chest not placed");
        }

        // The tutorial sword quest is NOT closed here. The seed places the town
        // "just past onboarding", but the wooden-sword item quest only opens once
        // the first villager arrives, and closing it is action-gated: a sword
        // deposited before the quest is open does not close it. Satisfying it at
        // furnish time misses it entirely (the run logged "no open sword quest to
        // complete at furnish"). The watch loop satisfies the open sword quest the
        // real-player path once a villager is present (satisfyOpenSwordQuestIfVillager),
        // which advances the town past PHASE_KICKOFF so natural progression opens
        // the farm/cook room quests the room builder can close.

        msg("Flag live; entering realtime watch");
        phase = Phase.SPAWN_VILLAGERS;
        waitTicks = 0;
    }

    private boolean hasInteriorCell(BlockPos corner) {
        return flag.interiorCells().stream().anyMatch(p -> insideRoom(corner, p));
    }

    private int firstInteriorCellIndex(BlockPos corner) {
        List<BlockPos> cells = flag.interiorCells();
        for (int i = 0; i < cells.size(); i++) {
            if (insideRoom(corner, cells.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /** A cell belongs to the room with the given corner if it is inside that
     *  building's interior (not on its walls). */
    private boolean insideRoom(BlockPos corner, BlockPos cell) {
        int s = BUILDING_SIZE - 1;
        int dx = cell.getX() - corner.getX();
        int dz = cell.getZ() - corner.getZ();
        boolean insideX = dx >= 1 && dx <= s - 1;
        boolean insideZ = dz >= 1 && dz <= s - 1;
        return insideX && insideZ;
    }

    private void spawnVillagers() {
        for (int i = 0; i < VILLAGER_COUNT; i++) {
            flag.addImmediateReward(new ca.bradj.questown.town.rewards.SpawnVisitorReward(flag));
        }
        msg("Queued " + VILLAGER_COUNT + " SpawnVisitorRewards");
        phase = Phase.WAIT_FOR_VILLAGERS;
        waitTicks = 0;
    }

    private void waitForVillagers() {
        long roster = flag.getVillagerHandle().size();
        if (roster >= VILLAGER_COUNT) {
            msg("Roster at " + roster);
            phase = Phase.ASSIGN_JOBS;
            return;
        }
        // The rewards claim only when the flag ticks, and the flag's active gate is a
        // nearby player; in held mode the runClient observer may join late. Wait for
        // the roster rather than racing ahead with fewer villagers (the job assignment
        // below is meaningless at roster=0).
        if (++waitTicks % 600 == 0) {
            msg("waiting for villagers: roster=" + roster + "/" + VILLAGER_COUNT
                    + " (the flag ticks only with a player nearby)");
        }
    }

    private void assignJobs() {
        // The seed is just past onboarding: one hunter (food chain is
        // hunter-only; villagers eat raw meat via the raw-food diner work) and
        // one gatherer, assigned here via ChangeJobReward (not raw
        // changeJobForVillager): the reward both sets the job AND posts the job's
        // output item as a work request on the job board. Without the work request
        // the job has nothing to produce and the villager sits in downtime forever
        // (work requests are not quests, and a job with no request has no work).
        // The third villager (i == 2) is deliberately left unassigned: the
        // reworked onboarding tutorial phase 2.5 staffs the cook via its own
        // JOB_CHANGE quest (roster >= 3 gate), not via the seed.
        int i = 0;
        for (VillagerUUID vuid : flag.getVillagerHandle().getVillagerJobs().keySet()) {
            UUID uuid = VillagerUUID.get(vuid);
            if (i == 0) {
                flag.addImmediateReward(new ca.bradj.questown.town.rewards.ChangeJobReward(flag, uuid, "hunter"));
            } else if (i == 1) {
                flag.addImmediateReward(new ca.bradj.questown.town.rewards.ChangeJobReward(flag, uuid, "gatherer"));
            }
            i++;
        }
        // Post the work requests directly. A job with no work request on the board
        // has nothing to produce and its villager sits in downtime; the work request
        // is what makes the job actionable (it is not the same as a quest). The
        // hunter's default work requests mutton; the gatherer's requests wheat
        // seeds (each job's initial_request). This is the "jobs assigned + work
        // requested" state a just-past-onboarding town has on its job board.
        flag.getWorkHandle().requestWork(ca.bradj.questown.jobs.requests.WorkRequest.of(
                net.minecraft.world.item.Items.MUTTON));
        flag.getWorkHandle().requestWork(ca.bradj.questown.jobs.requests.WorkRequest.of(
                net.minecraft.world.item.Items.WHEAT_SEEDS));
        msg("Queued ChangeJobReward (1 hunter + 1 gatherer); posted work requests mutton + wheat_seeds");
        phase = Phase.WAIT_FOR_JOBS;
        waitTicks = 0;
    }

    private void waitForJobs() {
        // Wait until the two job-change rewards have actually been claimed, not on a
        // blind timer. A job-change reward applies when the flag ticks (which needs a
        // nearby player, so it can lag); it both sets the job and posts the job's
        // output as a work request, which is what makes the job actionable. If the
        // watch starts before the change lands, the townies sit in seeking_work with
        // no work request and the run measures a phantom food-lock the seed caused.
        boolean hunterIn = false, gathererIn = false;
        for (var job : flag.getVillagerHandle().getVillagerJobs().values()) {
            if ("hunter".equals(job.rootId())) hunterIn = true;
            if ("gatherer".equals(job.rootId())) gathererIn = true;
        }
        if (hunterIn && gathererIn) {
            long days = readDays();
            watchEndTick = level.getGameTime() + (long) days * TICKS_PER_DAY;
            msg("Job changes complete (hunter + gatherer both assigned); watching for "
                    + days + " in-game days (" + (days * TICKS_PER_DAY) + " ticks) in realtime");
            // Baseline daily report at watch start (day 1), so the first night
            // sleep's report has a delta to compare against.
            emitDailyReport();
            phase = Phase.WATCH;
            return;
        }
        if (++waitTicks % 600 == 0) {
            msg("waiting for job changes: hunter=" + hunterIn + " gatherer=" + gathererIn
                    + " (the reward claims on flag ticks, which need a player nearby)");
        }
        if (waitTicks > 2400) {
            msg("WARN job changes not confirmed after 120s (hunter=" + hunterIn
                    + " gatherer=" + gathererIn + "); starting the watch anyway");
            long days = readDays();
            watchEndTick = level.getGameTime() + (long) days * TICKS_PER_DAY;
            emitDailyReport();
            phase = Phase.WATCH;
        }
    }

    // ---- watch ----

    private void watch() {
        ticks++;
        if (flag == null || flagPos == null) {
            flag = TownFlagBlockEntity.getFromPos(level, flagPos);
            if (flag == null) {
                msg("FAIL flag disappeared during watch");
                phase = Phase.DONE;
                return;
            }
        }
        if (level.getGameTime() >= watchEndTick) {
            finish();
            return;
        }
        // Periodic heartbeat so a held run is readable: roster, jobs, needs.
        if (ticks % 400 == 0) {
            heartbeat();
            maybeEmitSeekingStallDiag(); // DEV-DIAG: stall score-dump, 2026-10-03 (wigeon)
        }
        // Sleep through nights: the town's overnight phase (job changes taking
        // effect, the chicken arc's overnight steps) requires night passage, but a
        // realtime 5-day watch spends ~10 real minutes per game day waiting for
        // the night to pass. Skip the night the way the growth executor does:
        // `time set day` flips the day-time signal, which drives the flag's
        // morning detection (AbstractTownFlagTicker.detectMorning -> onMorning ->
        // morningTick); no fake-player vanilla sleep is required.
        if (level.isNight()) {
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withPermission(4), "time set day");
            firstNightPassed = true;
            nightsSlept++;
            msg("Sleeped through night; watch-day " + (nightsSlept + 1));
        }
        // Daily report: six categories + day-over-day delta, emitted once per
        // day-number change.
        emitDailyReport();
        // Lock detectors (tripwires + forward-progress net). Log + dump on
        // detection; do NOT kill the run.
        checkLockDetectors();
        // Run-end milestone marker (cook/farmer unlocked, town on cooked food).
        trackMilestones();
        // Self-driving room builder: detect open room quests, build a room for
        // each, and place the recipe block once the room registers.
        roomBuilderTicks++;
        if (roomBuilderTicks % 200 == 0) {
            driveRoomBuilder();
            // Fulfil open job-change quests (follow-up tutorial batches open a
            // JOB_CHANGE the room builder does not cover): drive a townie into the
            // wanted job the seed's way (ChangeJobReward) so processJobChanges
            // closes it and the cook/farmer milestone can fire.
            driveJobChange();
        }
        // Satisfy the open sword quest the real-player path, once a villager is
        // present. The sword quest only opens after the first villager arrives,
        // and each later sword quest (wooden then stone) opens at its own time, so
        // this re-checks every cycle rather than firing once. The deposit is the
        // once-per-quest player action (the same path a player dropping the sword
        // in the store chest takes); a re-check finds no open quest once closed.
        if (roomBuilderTicks % 200 == 0) {
            satisfyOpenSwordQuestIfVillager();
        }
        // Seed the opening food block into the store chest, once, now that the flag is live
        // (its first tick restores chest contents from the captured NBT state and would
        // flush a chest filled during furnish). One-shot; if the chest block entity is not
        // up yet the latch is not set and the next cycle retries.
        if (roomBuilderTicks % 200 == 0) {
            seedOpeningFood();
        }
    }

    /** Satisfy the currently-open sword item quest via the real player path, but
     *  only once at least one villager has arrived. The wooden-sword quest opens
     *  when the first villager spawns and the stone-sword quest later; satisfying
     *  each as it opens is what unblocks the hunter's sword job so the food chain
     *  can start. Before any villager the quest is not open, so there is nothing to
     *  satisfy (and pre-depositing would not close it). */
    private void satisfyOpenSwordQuestIfVillager() {
        if (flag == null || level == null) return;
        if (flag.getVillagerHandle().size() < 1) return;
        if (ca.bradj.questown.commands.SupplySwordCommand.completeSwordQuestsForFlag(flag, level)) {
            msg("[seed] satisfied the open sword quest (real player path); a villager is present");
        }
    }

    /** Seed the opening food block into the store chest, once. The chest position was
     *  captured at furnish from placeBlockInInterior's return (the real cell it landed on),
     *  so no lookup is needed here. The fill cannot happen at furnish: the flag's first
     *  tick restores chest contents from its captured NBT state and would flush a chest
     *  filled during furnish. Also points the flow tracker at the chest so the daily
     *  report's stock/produced/consumed measure the store seam (track AFTER seeding so
     *  the opening block is the seed baseline and is not counted as produced).
     *  One-shot (latched); if the block entity is not a Container yet the latch is not set
     *  and the next cycle retries. COOKED meat, not raw: a townie's working ingredient is
     *  #questown:villager_food (cooked items only); raw meat is the survival food a
     *  raw-food diner work consumes, and seeding raw meat left the town in
     *  NO_WORK_POSSIBLE. */
    private void seedOpeningFood() {
        if (openingFoodSeeded || flag == null || level == null || storeChestPos == null) return;
        if (level.getBlockEntity(storeChestPos) instanceof net.minecraft.world.Container c) {
            c.setItem(0, new ItemStack(net.minecraft.world.item.Items.COOKED_MUTTON, FOOD_SEED_PER_ITEM));
            c.setItem(1, new ItemStack(net.minecraft.world.item.Items.COOKED_BEEF, FOOD_SEED_PER_ITEM));
            c.setItem(2, new ItemStack(net.minecraft.world.item.Items.COOKED_PORKCHOP, FOOD_SEED_PER_ITEM));
            c.setChanged();
            flowTracker.track(level, storeChestPos);
            openingFoodSeeded = true;
            msg("[seed] seeded " + (FOOD_SEED_PER_ITEM * 3) + " opening food into the store chest at "
                    + storeChestPos.toShortString() + "; flow tracker pointed there");
        }
        // Otherwise the chest block entity is not up yet; leave the latch unset and retry next cycle.
    }

    /**
     * One daily-report snapshot of the economy. produced/consumed are the
     * *per-day window* (flow during the day that just ended); stock is the
     * point-in-time total in the tracked containers; demand is the unmet item
     * needs registered during the day that just ended. A point-in-time baseline
     * report (day 1, at watch start) carries a zero window for all four.
     */
    private record EconomySnapshot(long produced, long consumed, long stock, long demand) {
        static EconomySnapshot ZERO = new EconomySnapshot(0, 0, 0, 0);
    }

    /** Cumulative flow-counter produced/consumed at the last report, so the
     *  per-day window is the delta against it. The flow counter's {@code
     *  totalProduced}/{@code totalConsumed} are cumulative-since-tracked (a
     *  one-time seed baseline already excluded), so the window is a subtraction. */
    private long lastCumProduced = 0;
    private long lastCumConsumed = 0;
    /** True once the day-1 baseline report has consumed the pre-watch demand
     *  window (via {@code getUnmetNeedsWindowTotal}), so day 2's report shows
     *  day 1's demand, not the pre-watch accumulation. */
    private boolean demandBaselineAdvanced = false;

    /**
     * Take the next daily-report economy numbers. Polls the containers, then
     * reads the flow counter's raw (cumulative-since-tracked) produced/consumed
     * and stock; reads the economics handle's demand window (a per-call delta,
     * already bounded) and, on the first call, treats it as consumed pre-watch
     * state (reported as zero). Updates the stored cumulative baselines.
     */
    private EconomySnapshot nextEconomyReport() {
        flowTracker.poll(level);
        ContainerFlowCounter counter = flowTracker.counter();
        long cumProduced = 0;
        long cumConsumed = 0;
        for (String item : counter.produced().keySet()) {
            cumProduced += counter.totalProduced(item);
        }
        for (String item : counter.consumed().keySet()) {
            cumConsumed += counter.totalConsumed(item);
        }
        long stock = counter.lastStock().values().stream().mapToInt(Integer::intValue).sum();

        flag.getEconomicsHandle().update();
        long demand = flag.getEconomicsHandle().getUnmetNeedsWindowTotal();

        long producedWindow = Math.max(0, cumProduced - lastCumProduced);
        long consumedWindow = Math.max(0, cumConsumed - lastCumConsumed);
        lastCumProduced = cumProduced;
        lastCumConsumed = cumConsumed;

        long demandWindow;
        if (!demandBaselineAdvanced) {
            // Day 1: advance the baseline, report zero (there was no prior day).
            demandWindow = 0;
            demandBaselineAdvanced = true;
        } else {
            demandWindow = demand;
        }
        return new EconomySnapshot(producedWindow, consumedWindow, stock, demandWindow);
    }

    /** Emit the deterministic daily report: six categories with a day-over-day
     *  delta, on the {@code [village]} log prefix. Called once per day number
     *  change (the day rolls when {@code level.getDayTime()} crosses a multiple
     *  of 24000). The six categories:
     *  <ol>
     *    <li>roster + jobs (villager count, job id distribution)</li>
     *    <li>quests open/done</li>
     *    <li>general item economy (produced/consumed/stock from the flow counter)
     *        + demand side (unmet needs from {@code NoMCEconomics})</li>
     *    <li>mood aggregate (TownieNeed distribution: NONE / CANT_REACH / UNMET_ITEM)</li>
     *    <li>locks fired today (placeholder until the lock detectors land)</li>
     *    <li>milestones reached (first night passed, cook/farmer unlocked)</li>
     *  </ol>
     *  Each line carries a delta vs the previous day's snapshot. */
    private void emitDailyReport() {
        // Key the report on the number of nights slept (one per completed
        // in-game day), not the vanilla day counter. `time set day` resets
        // dayTime to 1000, so the vanilla day number never advances past 1 in a
        // watch that sleeps through nights. Day 1 is the baseline at watch
        // start; day N+1 is emitted after the Nth night sleep.
        long day = nightsSlept + 1;
        if (day == lastReportedDay) return;
        lastReportedDay = day;

        // Roster + jobs
        long roster = flag.getVillagerHandle().size();
        java.util.Map<String, Long> jobCounts = new java.util.TreeMap<>();
        long unmetItem = 0, cantReach = 0, noneNeed = 0;
        for (LivingEntity v : flag.getVillagerHandle().entities()) {
            VisitorMobEntity t = (VisitorMobEntity) v;
            jobCounts.merge(t.getJobId().rootId(), 1L, Long::sum);
            var need = t.getNeed();
            switch (need) {
                case UNMET_ITEM -> unmetItem++;
                case CANT_REACH -> cantReach++;
                case NONE -> noneNeed++;
            }
        }

        // Quests open/done
        long questsOpen = 0, questsDone = 0;
        for (var entry : flag.getAllQuestsWithRewards()) {
            var quest = entry.getKey();
            if (quest.isComplete()) questsDone++;
            else questsOpen++;
        }

        // Item economy: per-day window. nextEconomyReport() polls the
        // containers, computes the per-day produced/consumed window (the delta
        // against the stored cumulative baseline), and the per-day demand
        // window (a per-call delta from the economics handle). On the day-1
        // baseline it returns a zero window for all four.
        EconomySnapshot econSnap = nextEconomyReport();
        long producedTotal = econSnap.produced();
        long consumedTotal = econSnap.consumed();
        long stockTotal = econSnap.stock();
        long demandTotal = econSnap.demand();

        ReportSnapshot snap = new ReportSnapshot(
                roster, questsOpen, questsDone, unmetItem, cantReach, noneNeed,
                stockTotal, producedTotal, consumedTotal);
        ReportSnapshot prev = lastSnapshot != null ? lastSnapshot : ReportSnapshot.ZERO;
        lastSnapshot = snap;
        advanceProgressNet();

        // Emit the six category lines with deltas.
        msg("[village] day=" + day + " REPORT");
        msg("  roster=" + roster + " (" + jobCounts + ")"
                + delta(roster, prev.roster));
        msg("  quests open=" + questsOpen + " done=" + questsDone
                + delta(questsOpen, prev.questsOpen) + "/" + delta(questsDone, prev.questsDone));
        msg("  economy produced=" + producedTotal + " consumed=" + consumedTotal
                + " stock=" + stockTotal + " demand=" + demandTotal
                + delta(stockTotal, prev.stockTotal));
        msg("  mood none=" + noneNeed + " unmet=" + unmetItem + " cant_reach=" + cantReach
                + delta(unmetItem, prev.unmetItem));
        msg("  locks fired: " + activeLockSummary());
        msg("  milestones: firstNight=" + firstNightPassed
                + " cookUnlocked=" + cookUnlocked
                + " farmerUnlocked=" + farmerUnlocked);
    }

    /** Detect the run-end milestone: a townie holding a cook job (the food chain
     *  now produces cooked food) and/or a farmer job. Latched once; the first
     *  sighting is logged as the milestone event. */
    private void trackMilestones() {
        for (LivingEntity v : flag.getVillagerHandle().entities()) {
            String root = ((VisitorMobEntity) v).getJobId().rootId();
            if (root.equals("cook") && !cookUnlocked) {
                cookUnlocked = true;
                logMilestone("cook_unlocked (town on cooked food)");
            }
            if (root.equals("farmer") && !farmerUnlocked) {
                farmerUnlocked = true;
                logMilestone("farmer_unlocked");
            }
        }
    }

    private void logMilestone(String what) {
        milestoneLogged = true;
        msg("[village] MILESTONE: " + what + " on day=" + (nightsSlept + 1));
    }

    private static String delta(long now, long prev) {
        long d = now - prev;
        if (d == 0) return "";
        return " [" + (d > 0 ? "+" : "") + d + "]";
    }

    /** Compact summary of the lock conditions currently active (for the daily report). */
    private String activeLockSummary() {
        var out = new java.util.ArrayList<String>();
        for (var e : lockStatusTicks.entrySet()) {
            if (e.getValue() >= LOCK_STATUS_BOUND_TICKS) {
                out.add(e.getKey().toString().substring(0, 8) + ":lock_status(" + e.getValue() + "t)");
            }
        }
        for (var e : unmetItemTicks.entrySet()) {
            if (e.getValue() >= UNMET_ITEM_BOUND_TICKS) {
                out.add(e.getKey().toString().substring(0, 8) + ":unmet_item(" + e.getValue() + "t)");
            }
        }
        for (var e : roomQuestOpenSince.entrySet()) {
            long open = level.getGameTime() - e.getValue();
            if (open >= ROOM_QUEST_OPEN_BOUND_TICKS) out.add("room:" + e.getKey());
        }
        if (rosterDropSinceTick >= 0 && level.getGameTime() - rosterDropSinceTick >= ROSTER_DROP_BOUND_TICKS) {
            out.add("leaver_not_returning");
        }
        if (noProgressDays >= PROGRESS_NET_MIN_DAYS) out.add("no_forward_progress(" + noProgressDays + "d)");
        if (out.isEmpty()) return "none";
        return String.join(", ", out);
    }

    private void heartbeat() {
        long day = (level.getDayTime() / TICKS_PER_DAY) + 1;
        StringBuilder sb = new StringBuilder();
        sb.append("day=").append(day);
        for (LivingEntity v : flag.getVillagerHandle().entities()) {
            VisitorMobEntity t = (VisitorMobEntity) v;
            String need = t.getNeed().name();
            sb.append(" | ").append(t.getUUID().toString().substring(0, 8))
                    .append(':').append(t.getJobId().rootId()).append('/').append(t.getJobId().jobId())
                    .append(need == null || need.equals("NONE") ? "" : ":" + need);
        }
        msg("watch " + sb);
    }

    // DEV-DIAG: stall score-dump, 2026-10-03 (wigeon) — keep/remove per handoff
    /** When 2+ townies sit in seeking_work for 3+ heartbeat passes (~60s real),
     *  dump the work-seeker's per-job scores, gate-failure reasons, and board
     *  requests for each stalled root. One-shot (latched); read-only; fires only
     *  on a stall, so a healthy world is unaffected. Lets a log-only session
     *  distinguish a score regression from a selection-predicate rejection
     *  (unlocks / canAlwaysStart / day-fit) without a console or client. */
    private void maybeEmitSeekingStallDiag() {
        if (seekingStallDiagEmitted || flag == null) {
            return;
        }
        java.util.Set<String> seekingRoots = new java.util.HashSet<>();
        int seekingCount = 0;
        for (LivingEntity v : flag.getVillagerHandle().entities()) {
            var jid = ((VisitorMobEntity) v).getJobId();
            if (ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob.isSeekingWork(jid)) {
                seekingCount++;
                seekingRoots.add(jid.rootId());
            }
        }
        if (seekingCount < 2) {
            seekingStallPasses = 0;
            return;
        }
        if (++seekingStallPasses < 3) {
            return;
        }
        seekingStallDiagEmitted = true;
        QT.FLAG_LOGGER.info("[self_suff] [scoredump] stall signature: {} townies in seeking_work over 3+ heartbeat passes; dumping roots: {}",
                seekingCount, seekingRoots);
        for (String root : seekingRoots) {
            String dump;
            try {
                dump = flag.getPossibleWork().scoreDump(root);
            } catch (Exception e) {
                QT.FLAG_LOGGER.info("[self_suff] [scoredump] root={}: scoreDump failed: {}", root, e);
                continue;
            }
            for (String line : dump.split("\n")) {
                QT.FLAG_LOGGER.info("[self_suff] [scoredump] root={} | {}", root, line);
            }
        }
    }

    // ---- self-driving room builder ----

    /** Detects an open ROOM quest, builds a complete room for it (via the
     *  flag's {@code buildRoomForRecipe} owner method), which lays a 5x5 shell,
     *  registers the door, and places the recipe block inside. Runs every 200
     *  ticks from the watch loop. One quest at a time. */
    private void driveRoomBuilder() {
        if (flag == null || level == null) return;
        var allQuests = flag.getAllQuestsWithRewards();

        // A built room whose quest has closed: latch it as completed so the
        // builder never rebuilds it. The latch happens here, not at build time,
        // because the quest only closes once the room is re-detected and matched
        // against the recipe.
        for (var entry : allQuests) {
            var q = entry.getKey();
            if (q.getType() != ca.bradj.questown.town.quests.Quest.QuestType.ROOM || !q.isComplete()) continue;
            @SuppressWarnings("unchecked")
            var rid = (net.minecraft.resources.ResourceLocation) q.getWantedId();
            if (rid != null && builtRoomsAwaiting.remove(rid)) {
                completedRoomRecipes.add(rid);
                msg("[room-builder] " + rid + ": quest complete, room latched");
            }
        }

        for (var entry : allQuests) {
            var quest = entry.getKey();
            if (quest.getType() != ca.bradj.questown.town.quests.Quest.QuestType.ROOM) continue;
            if (quest.getStatus() != ca.bradj.questown.town.quests.Quest.QuestStatus.ACTIVE) continue;
            // A ROOM quest with a null batchUUID is a single-recipe room (e.g.
            // campfire) that stands on its own; the batchUUID guard was for the
            // multi-recipe tutorial batch (job_board/store_room/town_gate share
            // one) and over-filtered these. Build it on its recipe id alone.
            @SuppressWarnings("unchecked")
            var recipeId = (net.minecraft.resources.ResourceLocation) quest.getWantedId();
            if (completedRoomRecipes.contains(recipeId)) {
                if (roomBuilderTicks % 1000 == 0) {
                    msg("[room-builder] skipping " + quest.getWantedId() + " (already completed)");
                }
                continue;
            }
            // A room is already built for this recipe; wait for re-detection to
            // close the quest (mirrors the town_gate welcome-mat path).
            if (builtRoomsAwaiting.contains(recipeId)) {
                if (roomBuilderTicks % 1000 == 0) {
                    msg("[room-builder] " + recipeId + ": room built, waiting for re-detection");
                }
                continue;
            }

            // The town_gate quest's recipe is the welcome mat, a standalone job
            // block placed beside the flag and registered, not a block inside a
            // room. The seed does not place one (a mat laid before the quest is
            // open is never picked up), so if none is registered yet, place one
            // via placeWelcomeMat (which places and registers) the moment the
            // quest is open: the mat completes it on re-detection, with no
            // night-cycle requirement. If one is already registered, the quest
            // will complete on re-detection and the builder has nothing to do.
            if (recipeId.getPath().equals("special_quest.town_gate")) {
                if (!flag.getWelcomeMats().isEmpty()) {
                    // A mat is registered; the quest will complete on re-detection.
                    // Skip this cycle without marking it done, so the builder keeps
                    // watching until the quest actually closes.
                    if (roomBuilderTicks % 1000 == 0) {
                        msg("[room-builder] town_gate: welcome mat already registered, waiting for re-detection");
                    }
                    continue;
                }
                BlockPos matPos = findClearDoorSpot();
                if (matPos == null) {
                    msg("[room-builder] no clear spot for quest " + quest.getWantedId()
                            + "; will retry next cycle");
                    return;
                }
                flag.placeWelcomeMat(matPos);
                completedRoomRecipes.add(recipeId);
                msg("[room-builder] placed welcome mat for quest " + quest.getWantedId()
                        + " at " + matPos.toShortString() + " (registered, not a room)");
                return;
            }

            // Resolve the recipe's blocks: every ingredient, one entry per slot
            // (a multi-ingredient recipe like bedroom = light + 2 beds needs all
            // of them, not just the first).
            List<net.minecraft.world.level.block.Block> recipeBlocks = resolveRecipeBlocks(
                    (net.minecraft.resources.ResourceLocation) quest.getWantedId());
            if (recipeBlocks == null) {
                msg("[room-builder] could not resolve recipe blocks for " + quest.getWantedId());
                completedRoomRecipes.add(recipeId);
                continue;
            }

            // Find a clear door spot.
            BlockPos doorPos = findClearDoorSpot();
            if (doorPos == null) {
                msg("[room-builder] no clear door spot for quest " + quest.getWantedId()
                        + "; will retry next cycle");
                return;
            }

            // Build the room atomically: shell + door + recipe blocks.
            BlockPos built = flag.buildRoomForRecipe(doorPos, recipeBlocks);
            if (built == null) {
                msg("[room-builder] buildRoomForRecipe failed at " + doorPos.toShortString());
                continue;
            }

            // Do not latch as completed yet: the quest only closes once the room
            // is re-detected and matched. The sweep at the top of a later cycle
            // moves it to completedRoomRecipes when that happens.
            builtRoomsAwaiting.add(recipeId);
            msg("[room-builder] built room for quest " + quest.getWantedId()
                    + " at door " + doorPos.toShortString()
                    + ", placed " + recipeBlocks.size() + " block(s): " + recipeBlocks);
            return; // One quest at a time; the room scan will detect it on a later tick.
        }

        // No open ROOM quests (or all completed).
        if (roomBuilderTicks % 1000 == 0) {
            msg("[room-builder] no open ROOM quests to build for");
        }
    }

    /** Fulfil an open JOB_CHANGE quest by driving a townie into the wanted job,
     *  the same owner path the seed uses for its bootstrap hunter/gatherer: a
     *  {@link ca.bradj.questown.town.rewards.ChangeJobReward} sets the root,
     *  unlocks the root's default works, and posts the work request the
     *  work-seeker picks up; once a townie is actually doing the wanted work,
     *  {@code TownQuests.processJobChanges} closes the quest. One quest at a
     *  time, on the same 200-tick cadence as the room builder.
     *
     *  <p>Unlike the room builder this does not place blocks: the wanted work is
     *  already a buildable job, and a townie doing it is the completion signal.
     *  We only pick the townie and hand the change to the flag's reward path.
     *  We deliberately do NOT use the player's BOP path
     *  ({@code spendBopToRequestJobChange}): a plain BOP resolves to a random
     *  root, so it cannot be aimed at the quest's wanted job. */
    private void driveJobChange() {
        if (flag == null || level == null) return;
        if (flag.getVillagerHandle().size() < 1) return; // no townie to convert yet
        var allQuests = flag.getAllQuestsWithRewards();

        // Release entries whose quest has closed (no longer ACTIVE), so a later
        // identical quest in a new batch can still be driven.
        java.util.Set<String> activeJobChanges = new java.util.HashSet<>();
        for (var entry : allQuests) {
            var quest = entry.getKey();
            if (quest.getType() == ca.bradj.questown.town.quests.Quest.QuestType.JOB_CHANGE
                    && quest.getStatus() == ca.bradj.questown.town.quests.Quest.QuestStatus.ACTIVE) {
                activeJobChanges.add(String.valueOf(quest.getWantedId()));
            }
        }
        jobChangeDriven.retainAll(activeJobChanges);

        for (var entry : allQuests) {
            var quest = entry.getKey();
            if (quest.getType() != ca.bradj.questown.town.quests.Quest.QuestType.JOB_CHANGE) continue;
            if (quest.getStatus() != ca.bradj.questown.town.quests.Quest.QuestStatus.ACTIVE) continue;
            String wantedKey = String.valueOf(quest.getWantedId());
            if (jobChangeDriven.contains(wantedKey)) continue; // in flight; quest closing is the release

            ca.bradj.questown.jobs.JobID wanted = ca.bradj.questown.jobs.JobID.fromRL(
                    (net.minecraft.resources.ResourceLocation) quest.getWantedId());

            java.util.UUID target = pickJobChangeVillager(wanted);
            if (target == null) {
                msg("[job-change] no townie available to drive " + wanted + "; will retry next cycle");
                return;
            }
            flag.addImmediateReward(new ca.bradj.questown.town.rewards.ChangeJobReward(flag, target, wanted.rootId()));
            jobChangeDriven.add(wantedKey);
            msg("[job-change] requested " + wanted
                    + " for townie " + target.toString().substring(0, 8)
                    + " (ChangeJobReward -> root set, works unlocked, work request posted; quest closes on pickup)");
            return; // one quest at a time
        }

        // No open JOB_CHANGE quest to drive (or all in flight).
        if (roomBuilderTicks % 1000 == 0) {
            msg("[job-change] no open JOB_CHANGE quests to drive");
        }
    }

    /** Pick the townie to drive into {@code wanted}: prefer one outside the
     *  hunter root (so the conversion does not strip the town of its raw-food
     *  survival chain and confound the cook/farmer milestone the watch exists to
     *  measure), else the first townie. Returns {@code null} only when there are
     *  no townies at all (already guarded by the caller). */
    private @Nullable java.util.UUID pickJobChangeVillager(ca.bradj.questown.jobs.JobID wanted) {
        var villagerJobs = flag.getVillagerHandle().getVillagerJobs();
        java.util.UUID fallback = null;
        for (var e : villagerJobs.entrySet()) {
            java.util.UUID uuid = ca.bradj.questown.core.VillagerUUID.get(e.getKey());
            if (uuid == null) continue;
            if (fallback == null) fallback = uuid;
            String root = e.getValue().rootId();
            if (!root.equals("hunter") && !root.equals(wanted.rootId())) {
                return uuid;
            }
        }
        return fallback;
    }

    /** Find a clear door spot near the flag for a new room. Probes a ring of
     *  positions at increasing distance, skipping occupied cells and cells that
     *  already have a registered door. */
    @Nullable
    private BlockPos findClearDoorSpot() {
        BlockPos flagPos = flag.getBlockPos();
        int baseY = flagPos.getY();
        // Probe in a spiral: 3, 5, 7 blocks out in 8 directions.
        int[] dxs = {1, 0, -1, 0, 1, 0, -1, 0};
        int[] dzs = {0, 1, 0, -1, 1, 0, -1, 0};
        for (int dist : new int[]{5, 7, 9, 11, 13}) {
            for (int d = 0; d < 8; d++) {
                int x = flagPos.getX() + dxs[d] * dist;
                int z = flagPos.getZ() + dzs[d] * dist;
                BlockPos spot = new BlockPos(x, baseY, z);
                // Both the spot and the block above must be air for a door.
                if (!level.getBlockState(spot).isAir()) continue;
                if (!level.getBlockState(spot.above()).isAir()) continue;
                // The cell below must be solid (floor for the room).
                if (level.getBlockState(spot.below()).isAir()) continue;
                // Already a registered door? Skip.
                int scanLevel = baseY - flagPos.getY();
                if (flag.getRoomHandle().getAllRegisteredDoors().contains(
                        new ca.bradj.questown.town.rooms.TownPosition(x, z, scanLevel))) continue;
                // Avoid the seed buildings' footprints (known corners).
                if (boardRoomCorner != null && insideBuilding(boardRoomCorner, spot)) continue;
                if (storeRoomCorner != null && insideBuilding(storeRoomCorner, spot)) continue;
                return spot;
            }
        }
        return null;
    }

    /** Whether a position falls within the footprint of a seed building
     *  (corner + BUILDING_SIZE x BUILDING_SIZE). */
    private boolean insideBuilding(BlockPos corner, BlockPos pos) {
        int s = BUILDING_SIZE - 1;
        int dx = pos.getX() - corner.getX();
        int dz = pos.getZ() - corner.getZ();
        return dx >= -1 && dx <= s + 1 && dz >= -1 && dz <= s + 1;
    }

    /** Resolve the blocks to place for a room recipe quest: every ingredient,
     *  one entry per ingredient slot (multi-slot ingredients repeat), so a
     *  multi-ingredient recipe (bedroom = light + 2 beds) places every block the
     *  room needs. The room matcher compares the room's full block multiset
     *  against the recipe, so a shell with only the first ingredient never
     *  matches. Returns null when no ingredient resolves to a block item (the
     *  recipe can't be satisfied by placing blocks). */
    @Nullable
    private List<net.minecraft.world.level.block.Block> resolveRecipeBlocks(net.minecraft.resources.ResourceLocation recipeId) {
        var recipes = ca.bradj.questown.logic.RoomRecipes.hydrate(level.getRecipeManager(), true);
        var recipe = recipes.get(recipeId);
        if (recipe == null) return null;
        List<net.minecraft.world.level.block.Block> blocks = new java.util.ArrayList<>();
        for (var ing : recipe.getIngredients()) {
            for (var stack : ing.getItems()) {
                if (stack.getItem() instanceof net.minecraft.world.item.BlockItem bi) {
                    blocks.add(bi.getBlock());
                    break;
                }
            }
        }
        return blocks.isEmpty() ? null : blocks;
    }

    private void finish() {
        long roster = flag.getVillagerHandle().size();
        msg("========================================");
        msg("WATCH complete: roster=" + roster);
        msg("milestones: firstNight=" + firstNightPassed
                + " cookUnlocked=" + cookUnlocked
                + " farmerUnlocked=" + farmerUnlocked
                + (cookUnlocked ? "  [milestone: town on cooked food]" : ""));
        msg("========================================");
        phase = Phase.DONE;
    }

    // ---- helpers ----

    private long readDays() {
        try {
            long days = Long.parseLong(System.getProperty("questown.village.selfsuff.days", ""));
            if (days >= 1 && days <= MAX_DAYS) {
                return days;
            }
        } catch (NumberFormatException ignored) {
            // fall through to default
        }
        return DEFAULT_DAYS;
    }

    private void msg(String s) {
        QT.FLAG_LOGGER.info(PFX + s);
    }
}
