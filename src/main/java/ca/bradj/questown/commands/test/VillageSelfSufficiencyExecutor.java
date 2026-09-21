package ca.bradj.questown.commands.test;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.core.init.items.ItemsInit;
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
 * buildings, a flag + lit campfire + welcome mat (town gate) + job board in a
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

    /** Spawn N villagers at the town gate. Onboarding ends with one hunter and
     *  one gatherer; that is the seed's effective end state. */
    private static final int VILLAGER_COUNT = 2;

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
    /** Tick counter for the room-builder cadence (every 200 ticks = ~10s). */
    private int roomBuilderTicks = 0;
    /** Container-flow counter wiring the store chest (and future room chests)
     *  to a per-item produced/consumed/stock record. See ContainerFlowTracker. */
    private final ContainerFlowTracker flowTracker = new ContainerFlowTracker();
    /** Last day seen at watch start; the daily report keys its delta on this. */
    private long lastReportDay = -1;

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
        // A just-past-onboarding town: flag + lit campfire + welcome mat (town
        // gate) + a job board in a registered room + one registered store room
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
            // The welcome mat is the town gate: the gatherer and hunter jobs
            // anchor to special_quest.town_gate (the welcome mat's base room).
            BlockPos matPos = flagPos.east();
            level.setBlockAndUpdate(matPos.below(), Blocks.GRASS_BLOCK.defaultBlockState());
            flag.placeWelcomeMat(matPos);

            // Register both room doors with the flag (the same path a wand click
            // on a door takes). The rectangular shells were laid in buildSeed; the
            // rooms are detected by the scan on a later tick once the doors are
            // registered.
            flag.getRoomHandle().registerDoor(boardRoomDoorPos);
            flag.getRoomHandle().registerDoor(storeRoomDoorPos);

            msg("Welcome mat at " + matPos.toShortString() + "; doors registered at "
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
            BlockPos boardPosIn = flag.interiorCells().get(boardCell);
            flag.placeBlockInInterior(boardCell, ca.bradj.questown.core.init.BlocksInit.JOB_BOARD_BLOCK.get());
            flag.registerJobsBoard(boardPosIn);
            msg("Job board placed in board room interior at " + boardPosIn.toShortString());
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
            flag.placeBlockInInterior(storeCell, net.minecraft.world.level.block.Blocks.CHEST);
            storeChestPos = flag.interiorCells().get(storeCell);
            if (level.getBlockEntity(storeChestPos) instanceof net.minecraft.world.Container c) {
                // Start watching this chest for flow; the counter is the store room's
                // sole production/consumption seam in the current seed.
                flowTracker.track(level, storeChestPos);
                // COOKED meat, not raw: a townie's working ingredient is
                // #questown:villager_food, and that tag is cooked items only (bread,
                // cooked_*, baked_potato, ...). Raw meat is the *survival* food a
                // raw-food diner work consumes, not the working ingredient a hunter or
                // gatherer eats to keep working. Seeding raw meat left the whole town
                // in NO_WORK_POSSIBLE (the work-seeker found no villager_food in any
                // registered room's chest). A chest in a registered room is searched
                // for ingredients by the work-seeker, so the store room's chest works.
                c.setItem(0, new ItemStack(net.minecraft.world.item.Items.COOKED_MUTTON, 16));
                c.setItem(1, new ItemStack(net.minecraft.world.item.Items.COOKED_BEEF, 16));
                c.setItem(2, new ItemStack(net.minecraft.world.item.Items.COOKED_PORKCHOP, 16));
                // A wooden sword completes the tutorial batch (PHASE_KICKOFF): the
                // open wooden_sword item quest requires a wooden sword in the store
                // room. Without it the town stays in the tutorial phase with no work
                // requests, so the seeded villagers have nothing to do. This is part
                // of the "just past onboarding" seed, not the watchtower doing the
                // player's job.
                c.setItem(3, new ItemStack(net.minecraft.world.item.Items.WOODEN_SWORD, 1));
                c.setItem(4, new ItemStack(net.minecraft.world.item.Items.STONE_SWORD, 1));
                c.setChanged();
            }
            msg("Store chest seeded at " + storeChestPos.toShortString()
                    + " (cooked meat + wooden/stone sword)");
        } else {
            msg("WARN store room has no interior cell; store chest not placed");
        }

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
        // The seed is just past onboarding: exactly one hunter (food chain is
        // hunter-only; villagers eat raw meat via the raw-food diner work) and
        // one gatherer. Assign them via ChangeJobReward (not raw
        // changeJobForVillager): the reward both sets the job AND posts the job's
        // output item as a work request on the job board. Without the work request
        // the job has nothing to produce and the villager sits in downtime forever
        // (work requests are not quests, and a job with no request has no work).
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
        // Give the flag a few ticks to publish the new jobs to the tile before
        // the watch begins (the daily report and lock detectors read live state).
        if (++waitTicks >= 200) {
            long days = readDays();
            watchEndTick = level.getGameTime() + (long) days * TICKS_PER_DAY;
            msg("Watching for " + days + " in-game days (" + (days * TICKS_PER_DAY) + " ticks) in realtime");
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
        }
        // TODO: daily report (six categories + day-over-day delta)
        // TODO: lock detectors (tripwires + forward-progress net)
        // Self-driving room builder: detect open room quests, build a room for
        // each, and place the recipe block once the room registers.
        roomBuilderTicks++;
        if (roomBuilderTicks % 200 == 0) {
            driveRoomBuilder();
            pollFlow();
        }
    }

    /** Poll tracked containers and log any item-flow changes since the last
     *  poll. The flow line is the watchtower's first hard number: it turns the
     *  "town stalls" observation into produced/consumed/stock per item. */
    private void pollFlow() {
        flowTracker.poll(level);
        for (String line : flowTracker.counter().reportLines()) {
            msg(line);
        }
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

    // ---- self-driving room builder ----

    /** Detects an open ROOM quest, builds a complete room for it (via the
     *  flag's {@code buildRoomForRecipe} owner method), which lays a 5x5 shell,
     *  registers the door, and places the recipe block inside. Runs every 200
     *  ticks from the watch loop. One quest at a time. */
    private void driveRoomBuilder() {
        if (flag == null || level == null) return;
        var allQuests = flag.getAllQuestsWithRewards();

        for (var entry : allQuests) {
            var quest = entry.getKey();
            if (quest.getType() != ca.bradj.questown.town.quests.Quest.QuestType.ROOM) continue;
            if (quest.getStatus() != ca.bradj.questown.town.quests.Quest.QuestStatus.ACTIVE) continue;
            UUID batchUuid = quest.getBatchUUID();
            if (batchUuid == null) {
                if (roomBuilderTicks % 1000 == 0) {
                    msg("[room-builder] ROOM quest " + quest.getWantedId() + " has null batchUUID; skipping");
                }
                continue;
            }
            @SuppressWarnings("unchecked")
            var recipeId = (net.minecraft.resources.ResourceLocation) quest.getWantedId();
            if (completedRoomRecipes.contains(recipeId)) {
                if (roomBuilderTicks % 1000 == 0) {
                    msg("[room-builder] skipping " + quest.getWantedId() + " (already completed)");
                }
                continue;
            }

            // Resolve the recipe block.
            net.minecraft.world.level.block.Block recipeBlock = resolveRecipeBlock(
                    (net.minecraft.resources.ResourceLocation) quest.getWantedId());
            if (recipeBlock == null) {
                msg("[room-builder] could not resolve recipe block for " + quest.getWantedId());
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

            // Build the room atomically: shell + door + recipe block.
            BlockPos built = flag.buildRoomForRecipe(doorPos, recipeBlock);
            if (built == null) {
                msg("[room-builder] buildRoomForRecipe failed at " + doorPos.toShortString());
                continue;
            }

            completedRoomRecipes.add(recipeId);
            msg("[room-builder] built room for quest " + quest.getWantedId()
                    + " at door " + doorPos.toShortString()
                    + ", placed " + recipeBlock);
            return; // One quest at a time; the room scan will detect it on a later tick.
        }

        // No open ROOM quests (or all completed).
        if (roomBuilderTicks % 1000 == 0) {
            msg("[room-builder] no open ROOM quests to build for");
        }
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

    /** Resolve the block to place for a room recipe quest. Looks up the
     *  RoomRecipe by its id, takes the first ingredient, and returns the
     *  first matching block. */
    @Nullable
    private net.minecraft.world.level.block.Block resolveRecipeBlock(net.minecraft.resources.ResourceLocation recipeId) {
        var recipes = ca.bradj.questown.logic.RoomRecipes.hydrate(level.getRecipeManager(), true);
        var recipe = recipes.get(recipeId);
        if (recipe == null) return null;
        var ingredients = recipe.getIngredients();
        if (ingredients.isEmpty()) return null;
        var first = ingredients.get(0);
        for (var stack : first.getItems()) {
            if (stack.getItem() instanceof net.minecraft.world.item.BlockItem bi) {
                return bi.getBlock();
            }
        }
        return null;
    }

    private void finish() {
        long roster = flag.getVillagerHandle().size();
        msg("========================================");
        msg("WATCH complete: roster=" + roster);
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
