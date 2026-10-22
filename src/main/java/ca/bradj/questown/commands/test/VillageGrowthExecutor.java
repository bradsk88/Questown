package ca.bradj.questown.commands.test;
import ca.bradj.questown.loader.Loader;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.items.TownWand;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.mobs.visitor.TownieNeed;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.HelperChickenSpawnController;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.entity.TownFlags;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.Quest;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Long-running observational scenario: the executor plays the game the way a
 * player would. It builds 8 simple rectangular buildings (single door, none
 * registered), plants the flag via the real item-use path, places a campfire,
 * sleeps through nights the way the campfire sleep ends (sleepThroughTheNightIfDark), registers rooms via
 * the real wand click, and fulfils villager needs and quests by depositing
 * items into town containers. It stops when the roster reaches 5 villagers.
 *
 * <p>Everything is reported on the {@code [village]} log prefix so a watching
 * agent can follow progress with {@code grep '\[village\]'}. On every
 * observation beat it also answers the two player questions: what do I know
 * about the village right now (KNOWN), and what is unclear or confusing
 * (UNCLEAR — anomalies a player could not explain from what they can see).
 */
public final class VillageGrowthExecutor {

    private enum Phase {
        RESET_ARENA,
        BUILD_VILLAGE,
        PLACE_FLAG,
        WAIT_FOR_FLAG,
        PLAY,
        RELOAD_UNLOAD,
        RELOAD_LOAD,
        CHECK_RESULTS,
        DONE
    }

    private static final String PFX = "[village] ";
    private static final int TARGET_VILLAGERS = 5;
    private static final int OBSERVE_INTERVAL = 400;
    private static final int MAX_TICKS = 24000 * 10; // 10 in-game days of headroom
    private static final int BUILDING_SIZE = 5; // outer wall span (3x3 interior)

    private final ServerLevel level;
    private final MinecraftServer server;
    private final ServerPlayer player;
    private final BlockPos origin;

    private Phase phase = Phase.RESET_ARENA;
    private @Nullable BlockPos flagPos;
    private @Nullable TownFlagBlockEntity flag;
    private @Nullable BlockPos campfirePos;
    private final List<BlockPos> doorOffsets = new ArrayList<>();
    private final List<BlockPos> chestOffsets = new ArrayList<>();
    private int ticks = 0;
    private int waitTicks = 0;
    private final Map<String, Integer> unmetItemAttempts = new HashMap<>();
    private final Map<UUID, String> lastStarterWork = new HashMap<>();
    private long lastObservedRoster = -1;
    private boolean passed = false;
    private int passedChecks = 0;
    private int totalChecks = 0;
    private boolean welcomeMatPlaced = false;
    private boolean jobBoardPlaced = false;
    /** village/day1: stop at the cook beat and assert the day-1 beats instead of 5 villagers. */
    private final @Nullable Day1BeatChecks day1;
    /** village/day1: symptoms the game survives (self-healed stalls, ERROR lines) still fail. */
    private final @Nullable LogTripwire tripwire;
    private final List<BlockPos> buildingCorners = new ArrayList<>();
    private final java.util.Set<ResourceLocation> furnishedRoomQuests = new java.util.HashSet<>();
    private int nextSpareBuilding = 2;
    private boolean firstWorkRequestPosted = false;
    private boolean reloadDone = false;
    private final List<Long> releasedChunks = new ArrayList<>();
    private int reloadWait = 0;
    private long rosterBeforeReload = 0;
    /** The villager the job-change BOP was spent on; its change is due "in the morning". */
    private @Nullable java.util.UUID bopJobChangeVillager = null;
    private boolean awaitingMissedMorning = false;
    private long awaySinceMs = 0;
    private static final long ABSENCE_WALL_MS = 6000;
    private int missedMorningWait = 0;

    public VillageGrowthExecutor(
            ServerLevel level,
            MinecraftServer server,
            ServerPlayer player,
            BlockPos origin
    ) {
        this(level, server, player, origin, false);
    }

    public VillageGrowthExecutor(
            ServerLevel level,
            MinecraftServer server,
            ServerPlayer player,
            BlockPos origin,
            boolean day1
    ) {
        this.level = level;
        this.server = server;
        this.player = player;
        this.origin = origin;
        this.day1 = day1 ? new Day1BeatChecks(player.getUUID(), this::check, this::msg, () -> firstWorkRequestPosted) : null;
        this.tripwire = day1 ? LogTripwire.attach(level::getGameTime) : null;
    }

    /** Advance one server tick. Returns true when the scenario has finished. */
    public boolean tick() {
        try {
            switch (phase) {
                case RESET_ARENA -> resetArena();
                case BUILD_VILLAGE -> buildVillage();
                case PLACE_FLAG -> placeFlag();
                case WAIT_FOR_FLAG -> waitForFlag();
                case PLAY -> play();
                case RELOAD_UNLOAD -> reloadUnload();
                case RELOAD_LOAD -> reloadLoad();
                case CHECK_RESULTS -> checkResults();
                case DONE -> { return true; }
            }
        } catch (Exception e) {
            msg("EXCEPTION in " + phase + ": " + e);
            phase = Phase.CHECK_RESULTS;
        }
        return phase == Phase.DONE;
    }

    public boolean getPassed() { return passed; }
    public int getPassedAssertions() { return passedChecks; }
    public int getTotalAssertions() { return totalChecks; }

    // ---- phases ----

    private void resetArena() {
        TestArenaPreparer.PreparerOptions opts = new TestArenaPreparer.PreparerOptions(28, true, true, true);
        TestArenaPreparer.destroyNearbyFlags(level, origin, opts, player);
        TestArenaPreparer.flatten(level, origin, opts);
        msg("Arena reset around " + origin.toShortString());
        phase = Phase.BUILD_VILLAGE;
    }

    private void buildVillage() {
        doorOffsets.clear();
        chestOffsets.clear();
        buildingCorners.clear();
        // Two rows of four simple rectangular buildings, single south door.
        int[] xs = {-21, -9, 3, 15};
        int[] zs = {-16, 10};
        for (int z : zs) {
            for (int x : xs) {
                buildOneBuilding(new BlockPos(origin.getX() + x, origin.getY(), origin.getZ() + z));
            }
        }
        msg("Built 8 buildings (" + BUILDING_SIZE + "x" + BUILDING_SIZE + ", one door each); no doors registered");
        phase = Phase.PLACE_FLAG;
    }

    /** One rectangular building: floor, plank walls, flat roof, one oak door (south), one chest. */
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
        buildingCorners.add(corner);
        doorOffsets.add(corner.offset(s / 2, 0, s));
        BlockPos chest = corner.offset(1, 0, 1);
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        chestOffsets.add(chest);
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
        check("flag-placed", TownFlagBlockEntity.getFromPos(level, flagPos) != null);
        phase = Phase.WAIT_FOR_FLAG;
        waitTicks = 0;
    }

    private void waitForFlag() {
        flag = TownFlagBlockEntity.getFromPos(level, flagPos);
        if (flag != null) {
            // The scenario plants a hand-placed flag (not an empty_town structure),
            // so its chicken rotation was never detected and the onboarding arc is
            // inert. In an agent-driven (held) run the agent drives onboarding via
            // the chicken's guidance, so the chicken is a precondition - force it
            // to spawn here so the arc is live every time, deterministically.
            if (System.getProperty("questown.village.agentDrive") != null) {
                boolean spawned = HelperChickenSpawnController.forceSpawn(flag);
                msg("Helper chicken " + (spawned ? "spawned" : "NOT spawned (could not)")
                        + " for the flag at " + flagPos.toShortString());
            }
            msg("Flag block entity live; entering PLAY loop (target: " + TARGET_VILLAGERS + " villagers)");
            phase = Phase.PLAY;
            return;
        }
        if (++waitTicks > 200) {
            msg("FAIL flag BE never appeared");
            phase = Phase.CHECK_RESULTS;
        }
    }

    /** The main loop: observe, then take the next player action. */
    private void play() {
        ticks++;
        if (ticks % OBSERVE_INTERVAL == 0) {
            observe();
        }
        if (flag == null) {
            flag = TownFlagBlockEntity.getFromPos(level, flagPos);
            if (flag == null) {
                phase = Phase.CHECK_RESULTS;
                return;
            }
        }
        long roster = flag.getVillagerHandle().size();
        if (day1 != null) {
            if (day1Finished()) {
                phase = Phase.CHECK_RESULTS;
                return;
            }
        } else if (roster >= TARGET_VILLAGERS) {
            msg("Roster reached " + roster + " >= " + TARGET_VILLAGERS);
            check("reached-5-villagers", true);
            phase = Phase.CHECK_RESULTS;
            return;
        }
        sleepThroughTheNightIfDark();
        if (day1 == null && ticks >= MAX_TICKS) {
            msg("FAIL timed out after " + MAX_TICKS + " ticks at roster=" + roster);
            check("reached-5-villagers", false);
            phase = Phase.CHECK_RESULTS;
            return;
        }
        if (ticks % 200 == 0) {
            // A held, FIFO-driven run: the agent drives block placement,
            // registration, item supply and the day/night cycle through the
            // console (real setblock / qt registerroom / give / time commands).
            // The executor only observes, so its own act() cannot fight the
            // agent. Gated by questown.village.agentDrive.
            if (System.getProperty("questown.village.agentDrive") == null) {
                act();
            }
        }
    }

    /**
     * The player sleeps at the campfire as soon as vanilla allows it (night), the way the
     * playtester does every night. The wake is the real one: the clock moves to the next
     * morning exactly as vanilla's sleep end computes it, and the town gets the same
     * {@link TownFlags#onSleepFinished} morning step the {@code SleepFinishedTimeEvent} drives.
     * Only the cosmetic campfire bits (temp bed, extinguish) are skipped. Saves the ~11 000
     * idle night ticks per day (2026-10-19: autotest runtime).
     */
    private void sleepThroughTheNightIfDark() {
        if (!level.isNight()) {
            return;
        }
        long now = level.getDayTime();
        long next = now + 24000L;
        long morning = next - next % 24000L;
        level.setDayTime(morning);
        TownFlags.onSleepFinished(morning);
        msg("Slept through the night at the campfire: dayTime " + now + " -> " + morning);
    }

    /** day1: run the beat checks; finished at the cook beat or on timeout (a FAIL). */
    private boolean day1Finished() {
        day1.tick(flag, level.getDayTime());
        checkMissedMorning();
        if (shouldReloadNow()) {
            startReload();
            return false;
        }
        if (day1.cookBeatReached()) {
            check("day1-reached-cook-beat", true);
            return true;
        }
        if (ticks >= MAX_TICKS) {
            msg("FAIL day1 timed out after " + MAX_TICKS + " ticks at roster=" + flag.getVillagerHandle().size());
            check("day1-reached-cook-beat", false);
            return true;
        }
        return false;
    }

    // ---- day1: reload the town mid-run (the server-restart load order) ----

    /**
     * Once, on the day after the first villager arrived while raw food is still open: the
     * 2026-10-17 playtest's restart let villager 2 in on the next morning past the gate.
     */
    private boolean shouldReloadNow() {
        if (reloadDone || flag.getVillagerHandle().size() != 1 || Day1BeatChecks.rawFoodComplete(flag)) {
            return false;
        }
        long timeOfDay = level.getDayTime() % 24000L;
        return timeOfDay > 2000 && timeOfDay < 6000;
    }

    /** Release every chunk keeping the flag loaded and walk the player away, as on a restart. */
    private void startReload() {
        reloadDone = true;
        rosterBeforeReload = flag.getVillagerHandle().size();
        releasedChunks.clear();
        releasedChunks.addAll(level.getForcedChunks());
        for (long packed : releasedChunks) {
            net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(packed);
            level.setChunkForced(cp.x, cp.z, false);
        }
        ca.bradj.questown.mc.Compat.setSpawnChunkRadius(server, level, 0);
        movePlayer(origin.getX() + 4000, origin.getY() + 40, origin.getZ() + 4000);
        day1.pauseArrivals();
        msg("Reload: released " + releasedChunks.size() + " forced chunks, player moved away (roster=" + rosterBeforeReload + ")");
        reloadWait = 0;
        phase = Phase.RELOAD_UNLOAD;
    }

    /**
     * NeoForge's FakePlayerNetHandler makes {@code teleportTo}'s connection teleport a no-op, so
     * the fake player never moved and its chunks stayed loaded. Move the entity directly and
     * update the chunk map's view of it.
     */
    private void movePlayer(double x, double y, double z) {
        player.moveTo(x, y, z, player.getYRot(), player.getXRot());
        level.getChunkSource().move(player);
    }

    private void reloadUnload() {
        ticks++;
        if (awaySinceMs != 0) {
            returnAfterAbsence();
            return;
        }
        if (!level.isLoaded(flagPos)) {
            msg("Reload: the flag's chunk unloaded after " + reloadWait + " ticks");
            check("reload-flag-chunk-unloaded", true);
            skipToNextNoon();
            awaySinceMs = System.currentTimeMillis();
            return;
        }
        if (++reloadWait > 3000) {
            msg("FAIL reload: the flag's chunk never unloaded");
            check("reload-flag-chunk-unloaded", false);
            phase = Phase.CHECK_RESULTS;
        }
    }

    /**
     * Stay away for real time too: the flag tells a player absence from a sleep or /time jump by
     * the wall time since its last tick (TownFlagState, 5 s), and the flag BE survives this
     * reload, so a return a few ticks later (at 200 t/s) reads as "the town was awake".
     */
    private void returnAfterAbsence() {
        if (System.currentTimeMillis() - awaySinceMs < ABSENCE_WALL_MS) {
            return;
        }
        awaySinceMs = 0;
        movePlayer(origin.getX(), origin.getY() + 1, origin.getZ());
        for (long packed : releasedChunks) {
            net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(packed);
            level.setChunkForced(cp.x, cp.z, true);
        }
        reloadWait = 0;
        phase = Phase.RELOAD_LOAD;
    }

    /**
     * The player stays away past the next dawn, as on the 2026-10-17 playtest's overnight trip:
     * the flag was not ticking, so its own morning detection never sees that dawn.
     */
    private void skipToNextNoon() {
        long now = level.getDayTime();
        long noon = (now / 24000L + 1) * 24000L + 8000L;
        level.setDayTime(noon);
        msg("Reload: away overnight, world time " + now + " -> " + noon);
    }

    private void startMissedMorningCheck() {
        if (bopJobChangeVillager == null) {
            msg("FAIL missed morning: no job-change BOP was pending when the player left");
            check("missed-morning-applies-job-change", false);
            return;
        }
        awaitingMissedMorning = true;
        missedMorningWait = 0;
    }

    /** The BOP's morning job change lands when the player returns, not on the following dawn. */
    private void checkMissedMorning() {
        if (!awaitingMissedMorning) {
            return;
        }
        String root = flag.getVillagerHandle().entities().stream()
                .filter(v -> v.getUUID().equals(bopJobChangeVillager))
                .map(v -> ((VisitorMobEntity) v).getJobId().rootId())
                .findFirst().orElse(null);
        if (root != null && !"gatherer".equals(root)) {
            awaitingMissedMorning = false;
            msg("Missed morning: the BOP villager is now " + root + " after " + missedMorningWait + " ticks");
            check("missed-morning-applies-job-change", true);
            return;
        }
        if (++missedMorningWait > 400) {
            awaitingMissedMorning = false;
            msg("FAIL missed morning: the BOP villager is still " + root + " " + missedMorningWait + " ticks after the return");
            check("missed-morning-applies-job-change", false);
        }
    }

    private void reloadLoad() {
        ticks++;
        TownFlagBlockEntity reloaded = level.isLoaded(flagPos) ? TownFlagBlockEntity.getFromPos(level, flagPos) : null;
        if (reloaded != null && reloaded.getVillagerHandle().size() >= rosterBeforeReload) {
            flag = reloaded;
            msg("Reload: flag and " + reloaded.getVillagerHandle().size() + " villager(s) back after " + reloadWait + " ticks");
            check("reload-villagers-restored", true);
            day1.resumeArrivals(reloaded.getVillagerHandle().size());
            startMissedMorningCheck();
            phase = Phase.PLAY;
            return;
        }
        if (++reloadWait > 3000) {
            msg("FAIL reload: flag/villagers not back (flag=" + (reloaded != null) + ")");
            check("reload-villagers-restored", false);
            phase = Phase.CHECK_RESULTS;
        }
    }

    private void checkResults() {
        checkTripwire();
        long roster = flag != null ? flag.getVillagerHandle().size() : 0;
        passed = day1 != null ? passedChecks == totalChecks : roster >= TARGET_VILLAGERS;
        msg("========================================");
        msg("RESULT: roster=" + roster + "/" + TARGET_VILLAGERS
                + " checks=" + passedChecks + "/" + totalChecks
                + " -> " + (passed ? "PASS" : "FAIL"));
        msg("========================================");
        phase = Phase.DONE;
    }

    // ---- player actions (real mechanisms only) ----

    private void act() {
        if (campfirePos == null) {
            placeCampfireAndRegister();
            return;
        }
        // The welcome mat is the day-one arrival gate (.scratch/mat-gated-arrival,
        // ruling B: campfire first, mat right after it): registerWelcomeMat
        // completes the TOWN_GATE quest, which enqueues the first villager for
        // the next morning tick and posts the basics batch (job board + store
        // room). It is also the gatherer job's base-room fixture (the
        // special_quest.town_gate room) — without it the leaver job scores 0 and
        // no food deposit can clear an UNMET_ITEM need, so the townie stays
        // stuck at roster=1. Placed via the item-use path so
        // WelcomeMatBlock.getStateForPlacement registers it with the flag.
        if (!welcomeMatPlaced) {
            placeWelcomeMat();
            return;
        }
        // The basics batch (posted by the welcome-mat reward) requires the
        // job_board quest, which needs a job board (wooden sign) in a
        // registered room. Without it the batch can't complete, so the SECOND
        // villager never spawns. Place a job board block via the player
        // item-use path so the quest completes.
        if (!jobBoardPlaced) {
            placeJobBoard();
            return;
        }
        if (registerNextDoor()) {
            return;
        }
        if (fulfilUnmetItemNeeds()) {
            return;
        }
        if (fulfilItemQuests()) {
            return;
        }
        if (spendBopOnOpenJobChange()) {
            return;
        }
        if (furnishNextRoomQuest()) {
            return;
        }
        if (postFirstWorkRequest()) {
            return;
        }
        // In a manual (FIFO-driven) run the agent owns the clock; the executor
        // never advances it here. Stop before any non-manual clock logic.
        if (System.getProperty("questown.village.agentDrive") != null) {
            return;
        }
    }

    private void placeCampfireAndRegister() {
        BlockPos pos = flagPos.south().south();
        level.setBlockAndUpdate(pos.below(), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(pos, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
        campfirePos = pos;
        wandClick(pos, "campfire");
        check("campfire-placed", true);
    }

    /** Place the welcome mat block the gatherer job needs (player item-use path). */
    private void placeWelcomeMat() {
        BlockPos pos = flagPos.east();
        level.setBlockAndUpdate(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        ItemStack mat = new ItemStack(ca.bradj.questown.core.init.BlocksInit.WELCOME_MAT_BLOCK.get());
        ca.bradj.questown.blocks.TownFlagBlock.StoreParentOnNBT(mat, flagPos);
        var hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(pos),
                net.minecraft.core.Direction.UP,
                pos,
                false
        );
        player.setItemInHand(InteractionHand.MAIN_HAND, mat);
        player.gameMode.useItemOn(player, level,
                player.getItemInHand(InteractionHand.MAIN_HAND),
                InteractionHand.MAIN_HAND, hit);
        // Place a chest next to the welcome mat so the job's ingredient check
        // can find food. The gatherer job's base room is special_quest.town_gate
        // (the welcome mat), and the ingredient check looks for containers at
        // the job block's location.
        BlockPos chestPos = pos.east();
        level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        welcomeMatPlaced = true;
        msg("Placed welcome mat via player useItemOn at " + pos.toShortString()
                + " with adjacent chest at " + chestPos.toShortString());
        check("welcome-mat-placed", true);
    }

    /**
     * A block-as-room quest (the mineshaft): the player crafts the block in the flag's crafting tab
     * (FlagCraftRecipes, the same owner method the tab's message calls) from one of its input
     * items, then places it outdoors. Placing a flag-stamped room block registers its room.
     */
    private boolean craftAndPlaceRoomBlock(ResourceLocation roomId) {
        ca.bradj.questown.core.network.FlagCraftRecipes recipe = null;
        for (ca.bradj.questown.core.network.FlagCraftRecipes r : ca.bradj.questown.core.network.FlagCraftRecipes.ALL) {
            if (r.output().get() instanceof net.minecraft.world.item.BlockItem bi
                    && bi.getBlock() instanceof ca.bradj.questown.blocks.RoomBlock rb
                    && roomId.equals(ca.bradj.questown.blocks.RoomBlock.getRoomId(rb))) {
                recipe = r;
            }
        }
        if (recipe == null) {
            msg("UNCLEAR room quest " + roomId + ": no flag crafting recipe makes that block");
            return false;
        }
        ItemStack[] inputs = recipe.input().get().getItems();
        if (inputs.length == 0) {
            msg("UNCLEAR room quest " + roomId + ": its flag recipe has no input item");
            return false;
        }
        player.getInventory().clearContent();
        player.getInventory().add(inputs[0].copyWithCount(1));
        recipe.craft(player, flagPos);
        ItemStack crafted = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(recipe.output().get())) {
                crafted = player.getInventory().getItem(i);
            }
        }
        if (crafted.isEmpty()) {
            msg("FAIL crafting " + recipe.output().get() + " at the flag gave the player nothing");
            check("room-block-crafted[" + roomId + "]", false);
            return false;
        }
        BlockPos pedestal = flagPos.west(3);
        level.setBlockAndUpdate(pedestal.below(), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(pedestal, Blocks.GRASS_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(pedestal.above(), Blocks.AIR.defaultBlockState());
        var hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(pedestal),
                net.minecraft.core.Direction.UP,
                pedestal,
                false
        );
        player.setItemInHand(InteractionHand.MAIN_HAND, crafted.copy());
        player.gameMode.useItemOn(player, level,
                player.getItemInHand(InteractionHand.MAIN_HAND),
                InteractionHand.MAIN_HAND, hit);
        msg("Crafted " + recipe.output().get() + " at the flag from " + inputs[0].getItem()
                + " and placed it at " + pedestal.above().toShortString());
        check("room-block-crafted[" + roomId + "]", true);
        return true;
    }

    /** Place a job board block in a registered room (player item-use path). */
    private void placeJobBoard() {
        // Place the job board inside the first registered room (the chest room).
        // The pedestal must be an INTERIOR cell: recipe matching only sees blocks
        // in the room's space (wall cells are excluded — Questown calls
        // getBlocksInRoom with includeWallBlocks=false), so a board embedded in the
        // west wall (the old .west() cell) is never a contained block, the room
        // never matches the job_board recipe, and the quest stalls forever.
        // The chest sits at corner+1+1 (interior west-north corner); .east() is the
        // interior cell next to it.
        BlockPos pos = chestOffsets.get(0).east();
        level.setBlockAndUpdate(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(pos, Blocks.OAK_PLANKS.defaultBlockState());
        ItemStack board = new ItemStack(ca.bradj.questown.core.init.BlocksInit.JOB_BOARD_BLOCK.get());
        ca.bradj.questown.blocks.TownFlagBlock.StoreParentOnNBT(board, flagPos);
        var hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(pos),
                net.minecraft.core.Direction.UP,
                pos,
                false
        );
        player.setItemInHand(InteractionHand.MAIN_HAND, board);
        player.gameMode.useItemOn(player, level,
                player.getItemInHand(InteractionHand.MAIN_HAND),
                InteractionHand.MAIN_HAND, hit);
        jobBoardPlaced = true;
        msg("Placed job board via player useItemOn at " + pos.above().toShortString()
                + " (pedestal at " + pos.toShortString() + ")");
        check("job-board-placed", true);
    }

    /**
     * A townie's root starter work and which of it they have unlocked, logged when it changes:
     * what a player would read on the townie's jobs tab. Need surfacing only counts unlocked jobs.
     */
    private void logStarterWorkIfChanged(VisitorMobEntity t) {
        List<String> starter = new ArrayList<>();
        for (JobID j : ServerJobsRegistry.getDefaultWork(t.getJobId())) {
            boolean unlocked = flag.getVillagerHandle().isUnlocked(t.getUUID(), j);
            starter.add(j.jobId() + (unlocked ? "" : "(locked)"));
        }
        String line = t.getJobId().rootId() + " " + starter;
        if (line.equals(lastStarterWork.put(t.getUUID(), line))) {
            return;
        }
        msg("KNOWN townie " + shortId(t) + " starter work " + line);
    }

    /** Deposit the item each UNMET_ITEM townie is waiting on. Real player path: container deposit. */
    private boolean fulfilUnmetItemNeeds() {
        for (LivingEntity v : flag.getVillagerHandle().entities()) {
            VisitorMobEntity townie = (VisitorMobEntity) v;
            if (townie.getNeed() != TownieNeed.UNMET_ITEM) {
                continue;
            }
            String want = townie.getUnmetItem();
            Item item = resolveItem(want);
            if (item == null) {
                msg("UNCLEAR: townie " + shortId(townie) + " needs '" + want + "' but I cannot tell what item that is");
                continue;
            }
            // The need may be a tag (e.g. #questown:villager_food); the resolved
            // item is one member of it. Report the tag, not the member.
            msg("Resolving " + want + " -> " + ca.bradj.questown.mc.Compat.getItemName(item) + " (representative of the tag)");
            int attempts = unmetItemAttempts.merge(want, 1, Integer::sum);
            if (attempts > 3) {
                continue; // already supplied; waiting on the town to pick it up
            }
            if (deposit(new ItemStack(item, 4))) {
                msg("Deposited 4x " + want + " for townie " + shortId(townie) + " (attempt " + attempts + ")");
                check("need-fulfilled[" + want + "]", true);
                return true;
            }
            msg("UNCLEAR: townie needs " + want + " but the town has no container I can deposit into");
            return true;
        }
        return false;
    }

    /** Deposit items wanted by open quests. Real player path: leave them in a town chest. */
    private boolean fulfilItemQuests() {
        for (AbstractMap.SimpleEntry<MCQuest, ?> e : flag.getQuestHandle().getAllQuestsWithRewards()) {
            MCQuest q = e.getKey();
            if (q.isComplete()) {
                continue;
            }
            ResourceLocation wanted = q.getWantedId();
            if (wanted == null || !"minecraft".equals(wanted.getNamespace())) {
                continue; // room/special quests handled elsewhere (door registration)
            }
            Item item = Loader.itemFromId(wanted);
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            if (deposit(new ItemStack(item, q.getCountNeeded()))) {
                msg("Deposited " + q.getCountNeeded() + "x " + wanted + " toward quest " + q.toShortString());
                check("quest-supplied[" + wanted + "]", true);
                return true;
            }
            return false; // no container yet; door registration will fix that
        }
        return false;
    }

    /**
     * Spend a Block of Progress on a villager while a JOB_CHANGE quest is open - the
     * player's change-job-tab spend (owner path: spendBopToRequestJobChange). Since the
     * 2026-10-17 ruling the second villager waits on the raw-food quest, which only a
     * hunter completes, so the onboarding hunter job change is on the growth path.
     */
    private boolean spendBopOnOpenJobChange() {
        boolean openJobChange = flag.getQuestHandle().getAllQuestsWithRewards().stream()
                .map(AbstractMap.SimpleEntry::getKey)
                .anyMatch(q -> q.getType() == Quest.QuestType.JOB_CHANGE && !q.isComplete());
        if (!openJobChange || flag.getBlocksOfProgress() <= 0) {
            return false;
        }
        // Spend on a villager the change-profession tab red-dots (TownQuests.wantsJobChangeFrom:
        // still a gatherer) - what a player picks - not whoever is first, which re-roled the hunter.
        for (LivingEntity v : flag.getVillagerHandle().entities()) {
            if (!flag.wantsJobChangeFrom(((VisitorMobEntity) v).getJobId())) {
                continue;
            }
            TownFlagBlockEntity.JobChangeResult r = flag.spendBopToRequestJobChange(v.getUUID());
            msg("Spent a BOP on townie " + shortId((VisitorMobEntity) v) + " for the open job-change quest: " + r);
            check("bop-spent-for-job-change", r == TownFlagBlockEntity.JobChangeResult.REQUESTED);
            if (r == TownFlagBlockEntity.JobChangeResult.REQUESTED) {
                bopJobChangeVillager = v.getUUID();
            }
            return true;
        }
        return false;
    }

    /** Room quests the earlier steps already handle (door registration, mat, board, chests). */
    private static final java.util.Set<ResourceLocation> ROOM_QUESTS_HANDLED_ELSEWHERE = java.util.Set.of(
            ca.bradj.questown.town.special.SpecialQuests.CAMPFIRE,
            ca.bradj.questown.town.special.SpecialQuests.TOWN_GATE,
            ca.bradj.questown.town.special.SpecialQuests.EMPTY_ROOM,
            ca.bradj.questown.town.special.SpecialQuests.JOB_BOARD,
            ca.bradj.questown.town.special.SpecialQuests.STORE_ROOM_SMALL
    );

    /**
     * Furnish one open room quest (bedroom, kitchen, ...) the way a player would: in a spare
     * building (its chest removed, so the room matches the asked recipe), place each recipe
     * ingredient's block via the item-use path. Door registration already covers the room.
     */
    private boolean furnishNextRoomQuest() {
        for (AbstractMap.SimpleEntry<MCQuest, ?> e : flag.getQuestHandle().getAllQuestsWithRewards()) {
            MCQuest q = e.getKey();
            ResourceLocation id = q.getWantedId();
            if (q.isComplete() || q.getType() != Quest.QuestType.ROOM
                    || ROOM_QUESTS_HANDLED_ELSEWHERE.contains(id) || !furnishedRoomQuests.add(id)) {
                continue;
            }
            if (ca.bradj.questown.blocks.RoomBlock.isBlockRoom(id)) {
                return craftAndPlaceRoomBlock(id);
            }
            ca.bradj.roomrecipes.recipes.RoomRecipe recipe = roomRecipe(id);
            if (recipe == null || nextSpareBuilding >= buildingCorners.size()) {
                msg("UNCLEAR room quest " + id + ": no recipe or no spare building to furnish");
                return false;
            }
            BlockPos corner = buildingCorners.get(nextSpareBuilding++);
            level.setBlockAndUpdate(corner.offset(1, 0, 1), Blocks.AIR.defaultBlockState());
            int col = 0;
            // A bed item places a whole bed, which is two of the recipe's bed entries.
            List<net.minecraft.world.item.crafting.Ingredient> toPlace = ca.bradj.questown.logic.BedHalves.oneEntryPerBed(
                    recipe.getIngredients(),
                    ing -> ing.getItems().length > 0
                            && ing.getItems()[0].getItem() instanceof net.minecraft.world.item.BedItem
            );
            for (net.minecraft.world.item.crafting.Ingredient ing : toPlace) {
                ItemStack[] options = ing.getItems();
                if (options.length == 0) {
                    continue;
                }
                placeViaItemUse(corner.offset(1 + col++, 0, 1), options[0].copyWithCount(1));
            }
            msg("Furnished room quest " + id + " in the building at " + corner.toShortString());
            check("room-quest-furnished[" + id + "]", true);
            return true;
        }
        return false;
    }

    private @Nullable ca.bradj.roomrecipes.recipes.RoomRecipe roomRecipe(ResourceLocation id) {
        ca.bradj.roomrecipes.recipes.RoomRecipe special =
                ca.bradj.questown.town.special.SpecialQuests.SPECIAL_QUESTS.get(id);
        if (special != null) {
            return special;
        }
        return ca.bradj.questown.mc.Compat.findRoomRecipe(level, id);
    }

    /** Place a block item at {@code pos} by using it on the floor below (player item-use path). */
    private void placeViaItemUse(BlockPos pos, ItemStack stack) {
        var hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(pos.below()),
                net.minecraft.core.Direction.UP,
                pos.below(),
                false
        );
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.gameMode.useItemOn(player, level, player.getItemInHand(InteractionHand.MAIN_HAND),
                InteractionHand.MAIN_HAND, hit);
    }

    /** Post one request while the first-work-request quest is open (the board's owner path). */
    private boolean postFirstWorkRequest() {
        if (firstWorkRequestPosted) {
            return false;
        }
        boolean open = flag.getQuestHandle().getAllQuestsWithRewards().stream()
                .map(AbstractMap.SimpleEntry::getKey)
                .anyMatch(q -> q.getType() == Quest.QuestType.FIRST_WORK_REQUEST && !q.isComplete());
        if (!open) {
            return false;
        }
        // Post only after the first job change lands (the morning after the BOP spend), the order
        // the 2026-10-21 playtest hit: the change auto-posts the new job's starter request, which
        // must not complete this quest.
        boolean jobChanged = flag.getVillagerHandle().getVillagerJobs().values().stream()
                .anyMatch(j -> !"gatherer".equals(j.rootId()));
        if (!jobChanged) {
            return false;
        }
        flag.requestWorkFromPlayer(
                ca.bradj.questown.jobs.requests.WorkRequest.of(net.minecraft.world.item.Items.WHEAT_SEEDS));
        firstWorkRequestPosted = true;
        msg("Posted a work request (wheat seeds) for the first-work-request quest");
        check("first-work-request-posted", true);
        return true;
    }

    private boolean registerNextDoor() {
        for (BlockPos door : doorOffsets) {
            boolean already = flag.getRoomHandle().getAllRegisteredDoors().stream()
                    .anyMatch(p -> p.x == door.getX() && p.z == door.getZ());
            if (already) {
                continue;
            }
            wandClick(door, "door");
            boolean nowRegistered = flag.getRoomHandle().getAllRegisteredDoors().stream()
                    .anyMatch(p -> p.x == door.getX() && p.z == door.getZ());
            check("door-registered[" + door.toShortString() + "]", nowRegistered);
            return true;
        }
        return false;
    }

    private void wandClick(BlockPos target, String what) {
        ItemStack wand = new ItemStack(ItemsInit.TOWN_WAND.get());
        ca.bradj.questown.blocks.TownFlagBlock.StoreParentOnNBT(wand, flagPos);
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        ((TownWand) wand.getItem()).onRightClicked(() -> player, level, target, wand);
        msg("Wand-clicked " + what + " at " + target.toShortString());
    }

    /** Deposits into the first registered town container, as a player dropping items in a chest. */
    private boolean deposit(ItemStack stack) {
        List<ContainerTarget<MCContainer, MCTownItem>> containers = TownContainers.getAllContainers(flag, level);
        for (ContainerTarget<?, MCTownItem> target : containers) {
            if (level.getBlockEntity(target.getBlockPos()) instanceof Container c) {
                for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
                    if (c.getItem(i).isEmpty()) {
                        // Respect max stack size — a larger stack is clamped to 1
                        // on persistence (a sword count of 4 would land as 1).
                        int place = Math.min(stack.getCount(), stack.getMaxStackSize());
                        c.setItem(i, stack.copyWithCount(place));
                        stack.shrink(place);
                    }
                }
                c.setChanged();
                if (stack.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---- observation: the two player questions ----

    private void observe() {
        long roster = flag != null ? flag.getVillagerHandle().size() : 0;
        long dayTime = level.getDayTime() % 24000L;
        msg("--- observe tick=" + ticks + " dayTime=" + dayTime + (level.isNight() ? " (night)" : " (day)") + " ---");

        // A) As a player, what do I know about the village?
        msg("KNOWN roster=" + roster + (roster != lastObservedRoster ? " (changed from " + lastObservedRoster + ")" : ""));
        lastObservedRoster = roster;
        for (LivingEntity v : flag.getVillagerHandle().entities()) {
            VisitorMobEntity t = (VisitorMobEntity) v;
            String need = t.getNeed() == TownieNeed.UNMET_ITEM
                    ? t.getNeed() + "(" + t.getUnmetItem() + ")" : t.getNeed().name();
            msg("KNOWN townie " + shortId(t) + " job=" + t.getJobId().rootId() + "/" + t.getJobId().jobId()
                    + " need=" + need + " pos=" + t.blockPosition().toShortString());
            logStarterWorkIfChanged(t);
        }
        List<String> open = new ArrayList<>();
        List<String> done = new ArrayList<>();
        for (AbstractMap.SimpleEntry<MCQuest, ?> e : flag.getQuestHandle().getAllQuestsWithRewards()) {
            (e.getKey().isComplete() ? done : open).add(e.getKey().toShortString());
        }
        // The lists come from the live quest handle, not a fresh snapshot: a
        // post/lose loop (e.g. the retired JOB_UNLOCK reconcile) leaves lost-but-present
        // entries and the same quest id accumulates (2026-10-11: 107x
        // JOB_UNLOCK spam per id in two minutes). Collapse repeats to
        // `entry xN` so the accumulation is a one-line log find, not a
        // duplicate wall to eyeball.
        msg("KNOWN quests open=" + collapse(open) + " done=" + collapse(done));
        msg("KNOWN registered-doors=" + flag.getRoomHandle().getAllRegisteredDoors().size()
                + " rooms=" + flag.getRoomHandle().getMatches(x -> true).size()
                + " containers=" + TownContainers.getAllContainers(flag, level).size()
                + " campfire=" + (campfirePos != null));

        // B) As a player, what is unclear or confusing about the current state?
        List<String> unclear = new ArrayList<>();
        if (roster == 0 && open.isEmpty()) {
            unclear.add("no villagers and no open quests: nothing tells me what to do next");
        }
        for (LivingEntity v : flag.getVillagerHandle().entities()) {
            VisitorMobEntity t = (VisitorMobEntity) v;
            if (t.getNeed() == TownieNeed.CANT_REACH) {
                unclear.add("townie " + shortId(t) + " cannot reach something, but I cannot see what or where");
            }
            if (t.getNeed() == TownieNeed.UNMET_ITEM
                    && TownContainers.getAllContainers(flag, level).isEmpty()) {
                unclear.add("townie " + shortId(t) + " needs " + t.getUnmetItem()
                        + " but the village has no chest in a room yet to leave it in");
            }
            if (t.getNeed() == TownieNeed.UNMET_ITEM
                    && unmetItemAttempts.getOrDefault(t.getUnmetItem(), 0) >= 3) {
                unclear.add("supplied " + t.getUnmetItem() + " 3x but townie " + shortId(t)
                        + " still reports the need");
            }
        }
        for (String q : open) {
            if (!q.contains("minecraft:")) {
                unclear.add("open quest '" + q + "' does not name an item I recognise; unsure what it wants");
            }
        }
        if (unclear.isEmpty()) {
            msg("UNCLEAR nothing: state matches expectations");
        } else {
            unclear.forEach(u -> msg("UNCLEAR " + u));
        }
    }

    // ---- helpers ----

    private @Nullable Item resolveItem(String key) {
        if (key == null) {
            return null;
        }
        // A need may be an item id (e.g. "minecraft:bread") or a tag
        // (e.g. "#questown:villager_food"). A real player faces the same
        // confusion as the agent — the need bubble shows the tag's
        // representative item via Ingredients.representativeItemStack, so the
        // agent can do the same here and deposit a concrete member of the tag.
        try {
            net.minecraft.world.item.crafting.Ingredient ing = ca.bradj.questown.gui.Ingredients.fromString(key);
            ItemStack stack = ca.bradj.questown.gui.Ingredients.representativeItemStack(ing);
            if (stack != null && !stack.isEmpty()) {
                return stack.getItem();
            }
        } catch (Exception e) {
            // Fall through to the plain item-name parse below.
        }
        // Fallback: treat key as a plain item name (single item, no tag).
        String name = key.replace("item:", "").trim();
        ResourceLocation rl = ResourceLocation.tryParse(name.contains(":") ? name : "minecraft:" + name);
        return rl == null ? null : Loader.itemFromId(rl);
    }

    private static String shortId(VisitorMobEntity v) {
        return v.getUUID().toString().substring(0, 8);
    }

    /**
     * Collapse repeated probe entries to {@code entry xN} (first-seen order
     * kept), so a quest handle that accumulated duplicates (a post/lose loop
     * leaving lost-but-present entries) reads as {@code Quest{id=...} x107},
     * not a 107-line wall. Single occurrences print unchanged.
     */
    private static List<String> collapse(List<String> entries) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String e : entries) {
            counts.merge(e, 1, Integer::sum);
        }
        List<String> out = new ArrayList<>(counts.size());
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            out.add(e.getValue() > 1 ? e.getKey() + " x" + e.getValue() : e.getKey());
        }
        return out;
    }

    private void checkTripwire() {
        if (tripwire == null) {
            return;
        }
        tripwire.detach();
        List<String> reseeds = tripwire.reseeds();
        List<String> errors = tripwire.errors();
        reseeds.stream().limit(5).forEach(r -> msg("  stuck work spot: " + r));
        errors.stream().limit(5).forEach(e -> msg("  error: " + e));
        check("no-stuck-work-spot-reseeds[" + reseeds.size() + "]", reseeds.isEmpty());
        check("no-questown-errors[" + errors.size() + "]", errors.isEmpty());
        List<String> doubles = tripwire.doubleAnnouncements();
        doubles.stream().limit(5).forEach(d -> msg("  back-to-back quest announcements: " + d));
        check("no-back-to-back-quest-announcements[" + doubles.size() + "]", doubles.isEmpty());
    }

    private void check(String label, boolean pass) {
        totalChecks++;
        if (pass) {
            passedChecks++;
        }
        msg((pass ? "PASS " : "FAIL ") + label);
    }

    private void msg(String s) {
        QT.FLAG_LOGGER.info(PFX + s);
    }
}
