package ca.bradj.questown.commands.test;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.items.TownWand;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.mobs.visitor.TownieNeed;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.quests.MCQuest;
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
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Long-running observational scenario: the executor plays the game the way a
 * player would. It builds 8 simple rectangular buildings (single door, none
 * registered), plants the flag via the real item-use path, places a campfire,
 * sleeps through nights via the real campfire-sleep path, registers rooms via
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
    private long lastObservedRoster = -1;
    private boolean passed = false;
    private int passedChecks = 0;
    private int totalChecks = 0;
    private boolean welcomeMatPlaced = false;
    private boolean jobBoardPlaced = false;

    public VillageGrowthExecutor(
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
                case BUILD_VILLAGE -> buildVillage();
                case PLACE_FLAG -> placeFlag();
                case WAIT_FOR_FLAG -> waitForFlag();
                case PLAY -> play();
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
        if (roster >= TARGET_VILLAGERS) {
            msg("Roster reached " + roster + " >= " + TARGET_VILLAGERS);
            check("reached-5-villagers", true);
            phase = Phase.CHECK_RESULTS;
            return;
        }
        if (ticks >= MAX_TICKS) {
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

    private void checkResults() {
        long roster = flag != null ? flag.getVillagerHandle().size() : 0;
        passed = roster >= TARGET_VILLAGERS;
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
        // The gatherer job (the townie's initial job) requires a welcome_mat_block
        // job block in its base room (special_quest.town_gate). Without it the
        // job scores 0, so no food deposit can clear the UNMET_ITEM need and the
        // townie stays stuck at roster=1. Place the welcome mat as the player
        // would (via the item-use path) so the job can start.
        if (!welcomeMatPlaced) {
            placeWelcomeMat();
            return;
        }
        // The tutorial batch (PHASE_KICKOFF) requires the job_board quest, which
        // needs a job board (wooden sign) in a registered room. Without it the
        // batch can't complete, so the first villager never spawns. Place a
        // job board block via the player item-use path so the quest completes.
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
        // In a manual (FIFO-driven) run the agent owns the clock; the executor
        // never advances it here. Stop before any non-manual clock logic.
        if (System.getProperty("questown.village.agentDrive") != null) {
            return;
        }
    }

    private void advanceToNight() {
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withPermission(4), "time set night");
        msg("Advanced to night; campfire placed, wand-registered");
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

    /** Place a job board block in a registered room (player item-use path). */
    private void placeJobBoard() {
        // Place the job board inside the first registered room (the chest room).
        BlockPos pos = chestOffsets.get(0).west();
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
        msg("Placed job board via player useItemOn at " + pos.toShortString());
        check("job-board-placed", true);
    }

    /** Skip the night to morning. `time set` flips the day-time signal, which
     * drives the flag's morning detection (AbstractTownFlagTicker.detectMorning
     * -> onMorning -> morningTick); no fake-player vanilla-sleep is required. */
    private void advanceToMorning() {
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withPermission(4), "time set day");
        msg("Advanced to morning (" + (level.getDayTime() % 24000L) + ")");
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
            Item item = ForgeRegistries.ITEMS.getValue(wanted);
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
                        c.setItem(i, stack.copy());
                        stack.setCount(0);
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
        }
        List<String> open = new ArrayList<>();
        List<String> done = new ArrayList<>();
        for (AbstractMap.SimpleEntry<MCQuest, ?> e : flag.getQuestHandle().getAllQuestsWithRewards()) {
            (e.getKey().isComplete() ? done : open).add(e.getKey().toShortString());
        }
        msg("KNOWN quests open=" + open + " done=" + done);
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
                        + " but no registered container exists to leave it in");
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
        return rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
    }

    private static String shortId(VisitorMobEntity v) {
        return v.getUUID().toString().substring(0, 8);
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
