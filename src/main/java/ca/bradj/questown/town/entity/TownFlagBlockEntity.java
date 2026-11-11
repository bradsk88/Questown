package ca.bradj.questown.town.entity;

import ca.bradj.questown.jobs.gatherer.Loots;
import ca.bradj.questown.jobs.BoardIdleExits;

import ca.bradj.questown.InventoryFullStrategy;
import ca.bradj.questown.QT;
import ca.bradj.questown.loader.ItemHandler;
import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.BlockOfProgress;
import ca.bradj.questown.blocks.FlagPhase;
import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.blocks.TownFlagSubBlocks;
import ca.bradj.questown.items.RelocationDeedItem;
import ca.bradj.questown.commands.DebugLogArgument;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.core.advancements.ApproachTownTrigger;
import ca.bradj.questown.core.advancements.RoomTrigger;
import ca.bradj.questown.core.advancements.VisitorTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.gui.FlagTabsEmbedding;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.ImmutableSnapshot;
import ca.bradj.questown.integration.minecraft.TownStateSerializer;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.Works;
import ca.bradj.questown.jobs.DiningStages;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.WorksBehaviour;
import ca.bradj.questown.jobs.declarative.BOPDepositorWork;
import ca.bradj.questown.jobs.declarative.DinerWork;
import ca.bradj.questown.jobs.declarative.DowntimeWork;
import ca.bradj.questown.jobs.declarative.ResterWork;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.requests.BoardRequests;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.core.advancements.TutorialTrigger;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.rewards.CampfireReadyReward;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.RoomShellPlan;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.*;
import ca.bradj.questown.town.econ.NoMCEconomics;
import ca.bradj.questown.town.interfaces.*;
import ca.bradj.questown.town.quests.*;
import ca.bradj.questown.town.rewards.SpawnVisitorReward;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.recipes.ActiveRecipes;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import ca.bradj.questown.town.rooms.AttentionBubbles;
import ca.bradj.questown.town.rooms.DeadDoorBubbles;
import ca.bradj.questown.town.rooms.DoorTrouble;
import ca.bradj.questown.world.MinecraftWorldAccess;
import com.google.common.collect.ImmutableMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import java.util.Optional;
import net.minecraftforge.event.entity.EntityEvent;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ca.bradj.questown.roomrecipes.Matches.getTopMatch;
import static ca.bradj.questown.roomrecipes.Matches.runForTopMatch;
import static ca.bradj.questown.town.entity.TownFlagState.NBT_TIME_WARP_REFERENCE_TICK;

public class TownFlagBlockEntity extends BlockEntity implements TownInterface,
        ActiveRecipes.ChangeListener<MCRoom, RoomRecipeMatch<MCRoom>>, TownPois.Listener {

    private final Map<String, Boolean> logToggles = new HashMap<>();

    final TownKnownBiomes biomes = new TownKnownBiomes();
    TownHealingHandle healing = new TownHealingHandle();
    private final TownFlagInitialization initializer;

    private static final String NBT_DEAD_DOORS = "deadDoors";
    private static final String NBT_ATTENTION_INCOMPLETE_QUESTS = "attentionIncompleteQuests";
    private static final String NBT_ATTENTION_NO_WORK_REQUESTS = "attentionNoWorkRequests";
    private static final String NBT_ATTENTION_JOB_BOARDS = "attentionJobBoards";
    private static final String NBT_ATTENTION_STORAGE_FULL = "attentionStorageFull";

    /** Registered doors with no room, and why. Synced to clients for the dead-door bubble. */
    private Map<BlockPos, DoorTrouble> deadDoors = ImmutableMap.of();

    /** The "!" bubbles' state, synced to clients: incomplete quests (over the flag), no work requests (over each job board). */
    private boolean attentionIncompleteQuests = false;
    private boolean attentionNoWorkRequests = false;
    private ImmutableList<BlockPos> attentionJobBoards = ImmutableList.of();
    /** The storage-full flag bubble's state. */
    private boolean attentionStorageFull = false;
    private final ca.bradj.questown.town.StorageBlockTally storageTally = new ca.bradj.questown.town.StorageBlockTally();

    boolean hasIncompleteQuests() {
        return quests.hasIncompleteQuests();
    }

    public Map<BlockPos, DoorTrouble> getDeadDoors() {
        return deadDoors;
    }

    /** The area a registered door's room covers, for door-trouble hints; empty when it is not enclosed. */
    public Optional<String> getRoomRangeForDoor(BlockPos doorPos) {
        MCRoom room = null;
        for (MCRoom r : roomsHandle.getRegisteredRooms().getAllRooms()) {
            if (r.getDoorPos().x == doorPos.getX() && r.getDoorPos().z == doorPos.getZ()) {
                room = r;
                break;
            }
        }
        if (room == null || room.getSpaces().isEmpty()) {
            return Optional.empty();
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (InclusiveSpace s : room.getSpaces()) {
            minX = Math.min(minX, s.getWestX());
            maxX = Math.max(maxX, s.getEastX());
            minZ = Math.min(minZ, s.getNorthZ());
            maxZ = Math.max(maxZ, s.getSouthZ());
        }
        return Optional.of("x " + minX + ".." + maxX + ", z " + minZ + ".." + maxZ
                + ", y " + doorPos.getY());
    }

    public void setDeadDoors(Map<BlockPos, DoorTrouble> updated) {
        if (this.deadDoors.equals(updated)) {
            return;
        }
        this.deadDoors = ImmutableMap.copyOf(updated);
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    private void writeDeadDoors(CompoundTag tag) {
        ListTag list = new ListTag();
        deadDoors.forEach((pos, trouble) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", pos.getX());
            entry.putInt("y", pos.getY());
            entry.putInt("z", pos.getZ());
            entry.putString("trouble", trouble.name());
            list.add(entry);
        });
        tag.put(NBT_DEAD_DOORS, list);
    }

    private void readDeadDoors(CompoundTag tag) {
        if (!tag.contains(NBT_DEAD_DOORS)) {
            return;
        }
        ImmutableMap.Builder<BlockPos, DoorTrouble> b = ImmutableMap.builder();
        for (Tag t : tag.getList(NBT_DEAD_DOORS, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            b.put(
                    new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z")),
                    DoorTrouble.fromName(entry.getString("trouble"))
            );
        }
        this.deadDoors = b.build();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        readDeadDoors(tag);
        readAttention(tag);
    }

    private void writeAttention(CompoundTag tag) {
        tag.putBoolean(NBT_ATTENTION_INCOMPLETE_QUESTS, hasIncompleteQuests());
        tag.putBoolean(NBT_ATTENTION_NO_WORK_REQUESTS, showsNoRequestsBubble());
        ListTag boards = new ListTag();
        for (BlockPos pos : workHandle.jobBoards) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", pos.getX());
            entry.putInt("y", pos.getY());
            entry.putInt("z", pos.getZ());
            boards.add(entry);
        }
        tag.put(NBT_ATTENTION_JOB_BOARDS, boards);
        tag.putBoolean(NBT_ATTENTION_STORAGE_FULL, storageTally.isBlocker());
    }

    private void readAttention(CompoundTag tag) {
        if (!tag.contains(NBT_ATTENTION_INCOMPLETE_QUESTS)) {
            return;
        }
        this.attentionIncompleteQuests = tag.getBoolean(NBT_ATTENTION_INCOMPLETE_QUESTS);
        this.attentionNoWorkRequests = tag.getBoolean(NBT_ATTENTION_NO_WORK_REQUESTS);
        this.attentionStorageFull = tag.getBoolean(NBT_ATTENTION_STORAGE_FULL);
        ImmutableList.Builder<BlockPos> b = ImmutableList.builder();
        for (Tag t : tag.getList(NBT_ATTENTION_JOB_BOARDS, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            b.add(new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z")));
        }
        this.attentionJobBoards = b.build();
    }

    /** Sends the "!" bubble state to nearby clients when it changed. Checked every 20 ticks. */
    void syncAttentionState() {
        if (!(level instanceof ServerLevel) || !isInitialized()) {
            return;
        }
        boolean incomplete = hasIncompleteQuests();
        boolean noRequests = showsNoRequestsBubble();
        ImmutableList<BlockPos> boards = ImmutableList.copyOf(workHandle.jobBoards);
        storageTally.observe(countStorageBlockedVillagers(), ca.bradj.questown.jobs.Jobs.townHasSpace(this), 20);
        boolean storageFull = storageTally.isBlocker();
        if (incomplete == attentionIncompleteQuests
                && noRequests == attentionNoWorkRequests
                && boards.equals(attentionJobBoards)
                && storageFull == attentionStorageFull) {
            return;
        }
        attentionIncompleteQuests = incomplete;
        attentionNoWorkRequests = noRequests;
        attentionJobBoards = boards;
        attentionStorageFull = storageFull;
        setChanged();
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }

    /**
     * The job board's "no requests" bubble only teaches the board: once a player has posted a request
     * it never returns, even when the board empties. A completed first-work-request quest counts, for
     * towns saved before the player's posts were recorded.
     */
    private boolean showsNoRequestsBubble() {
        return workHandle.getRequestedResults().isEmpty()
                && !workHandle.playerHasPosted()
                && !quests.hasCompletedFirstWorkRequest();
    }

    /** Villagers stopped because there is no free storage. */
    private int countStorageBlockedVillagers() {
        int n = 0;
        for (net.minecraft.world.entity.LivingEntity e : getVillagerHandle().entities()) {
            if (e instanceof VisitorMobEntity v
                    && ca.bradj.questown.jobs.production.ProductionStatus.NO_SPACE.equals(v.getStatusForServer())) {
                n++;
            }
        }
        return n;
    }

    public boolean isAttentionStorageFull() {
        return attentionStorageFull;
    }

    public boolean isAttentionQuestsIncomplete() {
        return attentionIncompleteQuests;
    }

    public boolean isAttentionNoWorkRequests() {
        return attentionNoWorkRequests;
    }

    public ImmutableList<BlockPos> getAttentionBoardPositions() {
        return attentionJobBoards;
    }
    private final NoMCEconomics economics = new NoMCEconomics();
    final TownFlagTicker ticker = new TownFlagTicker();

    // Deposited Blocks of Progress, oldest first, each keeping the job that earned it.
    final NonNullList<ItemStack> bopStacks = NonNullList.create();
    boolean tutorialBopGranted = false;
    int completedProceduralBatches = 0;

    public ItemHandler bopItemHandler() {
        return new TownFlagBOPItemHandler(this);
    }

    boolean givenBonusFood;

    // The board's auto-request setting, on by default.
    boolean boardAutoRequest = true;
    // Whether a player has opened the villagers tab; until then it has a red dot. True for towns saved before it existed.
    boolean villagersTabSeen = true;
    final SeenQuests seenQuests = new SeenQuests();
    // Whether a player has ever taken a Block of Progress out; until then that tab has a red dot.
    boolean bopWithdrawn = false;
    /** Saved: true once the first hunter's raw meat has closed the raw-food quest. */
    boolean firstHunterLootGuaranteeFired = false;


    public static void logStoredData(
            TownFlagBlockEntity entity,
            CompoundTag tTag
    ) {
        List<String> qss = entity.getAllQuests().stream().map(Quest::toShortString).toList();
        QT.FLAG_LOGGER.info("Town UUID: {}", entity.getUUID());
        QT.FLAG_LOGGER.info("Quests:\n{}", Strings.join(qss, '\n'));
        QT.FLAG_LOGGER.info("Villagers:\n{}", Strings.join(entity.getVillagers(), '\n'));
        QT.FLAG_LOGGER.info("Villager Jobs:\n{}", Strings.join(entity.getVillagerHandle().getJobs(), '\n'));
        QT.FLAG_LOGGER.info("Room Recipes:\n{}", Strings.join(entity.getRoomHandle().getMatches(x -> true), '\n'));

        String prettyJsonString = new GsonBuilder().setPrettyPrinting().create()
                                                   .toJson(JsonParser.parseString(tTag.toString()));
        QT.FLAG_LOGGER.info("NBT: {}", prettyJsonString);
    }

    public static TownFlagBlockEntity getFromPos(
            Level level,
            BlockPos flagPos
    ) {
        BlockEntity blockEntity = level.getBlockEntity(flagPos);
        if (blockEntity instanceof TownFlagBlockEntity tfbe) {
            return tfbe;
        }
        return null;
    }

    public TownHealingHandle getHealingHandle() {
        return healing;
    }

    public NoMCEconomics getEconomicsHandle() {
        return economics;
    }

    @Override
    public int getBlocksOfProgress() {
        return getBopCount();
    }

    public int getBopCount() {
        return bopStacks.size();
    }

    /** The job that earned each deposited Block of Progress, oldest first; "" for none. */
    public ImmutableList<String> depositedBopRootJobs() {
        return bopStacks.stream()
                .map(s -> BlockOfProgress.getEarnedBy(s)
                        .map(j -> j.rootId())
                        .orElse(""))
                .collect(ImmutableList.toImmutableList());
    }

    public enum JobChangeResult {
        REQUESTED, NO_BOP, NO_VILLAGER
    }

    /** The job a Block of Progress spend on this villager is promised to give, if any. */
    public Optional<JobID> promisedJobChange(UUID villagerUUID) {
        return quests.onboardingJobChangeGuarantee(VillagerUUID.from(villagerUUID));
    }

    /** Spends a Block of Progress on a job change: decides the job now and completes a matching job-change quest. An onboarding change applies now, others in the morning. */
    public Optional<JobID> requestJobRootChange(
            UUID villagerUUID,
            boolean instant,
            Optional<JobID> guaranteedRoot
    ) {
        Optional<JobID> promised = promisedJobChange(villagerUUID);
        Optional<JobID> effectiveGuarantee = promised.or(() -> guaranteedRoot);
        boolean now = instant || promised.isPresent();
        Optional<JobID> pending =
                getVillagerHandle().scheduleJobRootChange(villagerUUID, now, effectiveGuarantee);
        pending.ifPresent(root ->
                quests.markJobChangeCompleteAtSpend(VillagerUUID.from(villagerUUID), root)
        );
        return pending;
    }

    /** Dev-only: the change-job tab's spend, with the flag's oldest Block of Progress. */
    public JobChangeResult spendBopToRequestJobChange(UUID villagerUUID) {
        boolean inTown = getVillagerHandle().entities().stream()
                .anyMatch(e -> e.getUUID().equals(villagerUUID));
        if (!inTown) {
            return JobChangeResult.NO_VILLAGER;
        }
        Optional<net.minecraft.world.item.ItemStack> consumed = takeOldestBop();
        if (consumed.isEmpty()) {
            return JobChangeResult.NO_BOP;
        }
        requestJobRootChange(villagerUUID, false, Optional.empty());
        return JobChangeResult.REQUESTED;
    }

    /** The flag's oldest Block of Progress, without taking it. */
    public Optional<net.minecraft.world.item.ItemStack> peekOldestBop() {
        return bopStacks.isEmpty() ? Optional.empty() : Optional.of(bopStacks.get(0));
    }

    /** Takes {@code n} of the flag's Blocks of Progress, oldest first, or none if it has fewer. */
    public boolean takeBops(int n) {
        if (bopStacks.size() < n) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            takeOldestBop();
        }
        return true;
    }

    /** Takes the flag's oldest Block of Progress. */
    public Optional<net.minecraft.world.item.ItemStack> takeOldestBop() {
        if (bopStacks.isEmpty()) {
            return Optional.empty();
        }
        net.minecraft.world.item.ItemStack taken = bopStacks.remove(0);
        setChanged();
        syncBopFull();
        return Optional.of(taken);
    }

    public enum SkillUnlockResult {
        UNLOCKED, NO_BOP, NOT_UNLOCKABLE
    }

    /** Spends one of the flag's Blocks of Progress on a skill, as the skills tab does. The skill must be one the tab offers the villager. */
    public SkillUnlockResult spendBopToUnlockSkill(UUID villagerUUID, JobID id) {
        if (!getVillagerHandle().getUnlockableSkills(villagerUUID).contains(id)) {
            return SkillUnlockResult.NOT_UNLOCKABLE;
        }
        if (takeOldestBop().isEmpty()) {
            return SkillUnlockResult.NO_BOP;
        }
        unlockSkillFromPlayer(villagerUUID, id);
        return SkillUnlockResult.UNLOCKED;
    }

    /** For tests: adds Blocks of Progress to the flag directly. */
    public void grantBlockOfProgressForTest(int amount) {
        for (int i = 0; i < amount; i++) {
            bopStacks.add(new ItemStack(ItemsInit.BLOCK_OF_PROGRESS.get()));
        }
        setChanged();
        syncBopFull();
    }




    private static Map<String, InitPair> initPairs;

    public static void staticInitialize() {
        initPairs = TownFlagTileData.initialize();
    }

    public static final String ID = "flag_base_block_entity";

    final TownQuests quests = new TownQuests();
    final TownFlagSubBlocks subBlocks = new TownFlagSubBlocks(getBlockPos());
    final TownPois pois = new TownPois(subBlocks);
    final MCMorningRewards morningRewards = new MCMorningRewards(this);
    final MCAsapRewards asapRewards = new MCAsapRewards();
    /** Players already shown the camp-ready toast this session. */
    private final Set<UUID> campReadyToastShown = new HashSet<>();
    private UUID uuid = UUID.randomUUID();
    final TownFlagState state = new TownFlagState(this);
    final TownShutdownController shutdownController = new TownShutdownController();
    public long advancedTimeOnTick = -1;
    boolean isInitializedQuests = false;
    boolean changed = false;

    /**
     * Global work status store (used when ownerID is null).
     * Used by jobs with SHARED_WORK_STATUS special rule.
     *
     * IMPORTANT: Global and per-owner stores are INTENTIONALLY SEPARATE and should NOT interact.
     * They track different work states for different purposes:
     * - Global store: For shared work where any villager can continue another's work or work simultaneously
     * - Per-owner stores: For jobs with CLAIM_SPOT rule where work is owned by a specific villager
     */
    final TownWorkStatusStore jobHandle = new TownWorkStatusStore(
            (m, p) -> this.getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.WORK_STATUS).log(m, p)
    );
    /**
     * Per-owner work status stores (keyed by villager UUID).
     * Used by jobs with CLAIM_SPOT special rule (which implies owned work states).
     * Each villager gets their own isolated store so they can claim and work spots independently.
     *
     * IMPORTANT: Per-owner stores are INTENTIONALLY SEPARATE from the global store and from each other.
     * Do NOT copy states between stores - they are meant to be isolated.
     */
    final Map<UUID, TownWorkStatusStore> jobHandles = new HashMap<>();

    final TownWorkHandle workHandle = new TownWorkHandle(subBlocks, getBlockPos());
    final LinkedBlockingQueue<Function<TownFlagBlockEntity, Boolean>> initializers = new LinkedBlockingQueue<>();

    final TownKnowledgeStore knowledgeHandle = new TownKnowledgeStore();
    final TownQuestsHandle questsHandle = new TownQuestsHandle();
    final TownRoomsHandle roomsHandle = new TownRoomsHandle();
    final TownMessages messages = new TownMessages();
    public final TownFlagMenus menus = new TownFlagMenus();

    @Override
    public TownPossibleWork getPossibleWork() {
        return possibleWork;
    }

    final TownPossibleWork possibleWork = new TownPossibleWork();

    final TownVillagerHandle villagerHandle = new TownVillagerHandle();
    private final TownWorldInteraction world = new TownWorldInteraction();

    public TownFlagBlockEntity(
            BlockPos p_155229_,
            BlockState p_155230_
    ) {
        super(TilesInit.TOWN_FLAG.get(), p_155229_, p_155230_);
        this.initializer = new TownFlagInitializationImpl(this);
        // Don't write code here, it runs on both client and server.
        // Instead, put any initialization in onLoad
    }

    public static void tick(
            Level level,
            BlockPos blockEntityPos,
            BlockState state,
            TownFlagBlockEntity e
    ) {
        e.ticker.tick(level, blockEntityPos, state, e);
        if (level instanceof ServerLevel sl && e.isInitialized() && sl.getGameTime() % 20L == 0) {
            e.syncAttentionState();
        }
        if (level instanceof ServerLevel sl) {
            ca.bradj.questown.integration.minecraft.ChestTouches.tick(sl);
        }
        // TODO: The helper chicken ticked here: its spawn, its rotation check and its lessons. For its implementation see #409, #410, #411 and #412.
    }


    /** @param gameTime game time, which the sleepless expiry and rest times are read against */
    void morningTick(long gameTime) {
        // The second villager arrives only once the raw-food quest is done.
        TownQuests.ArrivalRelease<MCReward> release = quests.releaseArrivals(
                ImmutableList.copyOf(morningRewards.popChildren()),
                r -> r instanceof SpawnVisitorReward,
                townVillagerCount()
        );
        // A stack: arrivals go last so they come out first.
        release.others().forEach(this.asapRewards::push);
        release.arrivals().forEach(this.asapRewards::push);
        release.held().forEach(this.morningRewards::add);
        if (!release.held().isEmpty()) {
            QT.FLAG_LOGGER.info(
                    "Holding {} queued villager arrival(s): raw-food (LOOT) quest not complete",
                    release.held().size()
            );
        }
        this.setChanged();
        villagerHandle.handleMorning(gameTime);
        // Show "Your camp is ready" again tonight.
        campReadyToastShown.clear();
        roomsHandle.handleMorning();
        CompoundTag tag = Compat.getBlockStoredTagData(this);
        tag.putLong(NBT_TIME_WARP_REFERENCE_TICK, getServerLevel().getDayTime());
        TownInterface.DebugLogger loger = getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.TOWN_STATE_CHANGES);
        state.putStateOnTile(tag, getUUID(), loger);

    }

//    public static boolean debuggerReleaseControl() {
//        GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().getWindow(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
//        return true;
//    }

    public void setChanged() {
        if (isInitialized()) {
            super.setChanged();
            this.changed = true;
            possibleWork.invalidate();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        //////////////////////////
        //       WARNING        //
        //////////////////////////
        // Don't use this. Data gets stored on the tick. This function
        // NEVER works the way it claims to (the data saved here is NOT
        // present on the tag that gets passed to load())
        // Use writeTownData
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        ////////////////////////
        /////// WARNING ////////
        ////////////////////////
        // This function is NOT a safe place to deserialize server data
        // "this" is not the server entity - I don't know what it is.
        // Use the initializers stack to ensure you have a reliable
        // reference to the flag entity.
        // Use loadNextTick.

        loadNextTick(initializers);
    }

    private static void loadNextTick(Queue<Function<TownFlagBlockEntity, Boolean>> initializers) {
        initializers.add(t -> {
            logStoredData(t, Compat.getBlockStoredTagData(t));
            return true;
        });
        initPairs.forEach((key, pair) -> {
            initializers.add(t -> {
                CompoundTag tag = Compat.getBlockStoredTagData(t);
                if (tag.contains(key)) {
                    CompoundTag cTag = tag.getCompound(key);
                    if (!cTag.isEmpty()) {
                        return pair.fromTag().apply(cTag, t);
                    }
                }
                QT.FLAG_LOGGER.info("No data for {}. Skipping deserialization.", key);
                return true;
            });
        });
    }

    public void writeTownData(CompoundTag tag) {
        writeTownData(tag, true);
    }

    public void writeTownData(CompoundTag tag, boolean includeEconomicsData) {
        if (level == null) {
            return;
        }
        if (level.isClientSide()) {
            tag.putString("side", "client");
            return;
        } else {
            tag.putString("side", "server");
        }
//        if (roomsMap.numRecipes() > 0) {
//            tag.put(NBT_ACTIVE_RECIPES, ActiveRecipesSerializer.INSTANCE.serializeNBT(roomsMap.getRecipes(0)));
//        }
        TownFlagTileData.write(Util.getTick(getServerLevel()), tag, this.initializer);
        // TODO: Serialization for ASAPs
    }

    /** Only what the client reads (dead doors and the bubbles). The town data would outgrow the packet limit. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        writeDeadDoors(tag);
        writeAttention(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!(level instanceof ServerLevel sl)) {
            // Client side: let the bubble focus find this flag's dead doors.
            if (level != null && level.isClientSide()) {
                DeadDoorBubbles.register(this);
                AttentionBubbles.register(this);
            }
            return;
        }
        initializeFreshFlag(false);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        // Unloaded, not destroyed: sub-block listeners stop.
        subBlocks.retire();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide()) {
            DeadDoorBubbles.unregister(this);
            AttentionBubbles.unregister(this);
        }
    }

    public void initializeFreshFlag(boolean fullInit) {
        grantAdvancementOnApproach();
        if (fullInit) {
            loadNextTick(initializers);
        } else {
            initPairs.forEach((k, v) -> {
                v.onFlagPlace().accept(this);
            });
        }
        initializers.add(t -> {
            t.messages.initialize(t.getServerLevel());
            return true;
        });
        initializers.add(t -> {
            t.possibleWork.initialize(t);
            return true;
        });
        initializers.add(t -> {
            t.biomes.initialize(t);
            return true;
        });
        initializers.add(t -> {
            t.world.init(t);
            return true;
        });
        initializers.add(t -> {
            if (!this.isInitializedQuests) {
                t.setUpQuestsForNewlyPlacedFlag();
                t.seedStartingTimber();
            }
            t.pois.setListener(t);
            t.workHandle.addChangeListener(c -> {
                updateWorkersAfterRequestChange();
                setChanged();
            });
            t.workHandle.setInitialized();
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2);
            if (!level.isClientSide()) {
                TownFlags.register(uuid, t);
            }
            return true;
        });
    }

    // TODO[Performance]: Precompute ideal jobs regularly, ahead of time.
    //  When a change happens to the job board, or to the town state,
    //  determine the best possible use of people (this can be unit
    //  tested thoroughly. And then filter out any work that is already
    //  being done, and assign the rest to people who are idle or
    //  cannot complete their current work (e.g. no supplies)
    private void updateWorkersAfterRequestChange() {
        WorksBehaviour.TownData td = getTownData();
        BoardRequests requests = workHandle.getBoardRequests();
        Stream<VisitorMobEntity> relevantWorkers = villagerHandle.stream().filter(Objects::nonNull).filter(
                e -> !IStatus.isBusy(e.getStatusForServer())
                        || ServerJobsRegistry.firstAnswerable(td, e.getJobId(), requests).isEmpty()
        );
        relevantWorkers.forEach(e -> villagerHandle.changeJobForVillager(
                e.getVUID(),
                WorkSeekerJob.getIDForRoot(e.getJobId()),
                false
        ));
    }

    @Override
    public ServerLevel getServerLevel() {
        if (getLevel() instanceof ServerLevel sl) {
            return sl;
        }
        return null;
    }

    @Override
    public BlockPos getTownFlagBasePos() {
        return getBlockPos();
    }

    public boolean hasShownCampReadyToast(UUID player) {
        return campReadyToastShown.contains(player);
    }

    public boolean wantsJobChangeFrom(JobID villagerJob) {
        return quests.wantsJobChangeFrom(villagerJob);
    }

    public boolean hidesJobChangeFrom(JobID villagerJob) {
        return quests.hidesJobChangeFrom(villagerJob);
    }

    /** A villager died: remove it from the saved roster. */
    public void retireVillager(UUID villager) {
        villagerHandle.retireDisplayNumber(villager);
        setChanged();
    }

    public boolean hasVillagers() {
        return townVillagerCount() > 0;
    }

    /** Arrived villagers: the live ones or the saved roster, whichever is larger. */
    private long townVillagerCount() {
        return Math.max(villagerHandle.size(), villagerHandle.knownVillagerCount());
    }

    public boolean hasVillagerArrivingInMorning() {
        // Show "one more in the morning" only when the arrival will happen.
        return morningRewards.hasPendingSpawnVisitor() && quests.isArrivalGateOpen(townVillagerCount());
    }

    /** Shows a titled tutorial toast to every online player. */
    public void broadcastTutorialToast(String titleKey, String descriptionKey) {
        this.messages.broadcastTutorialToast(titleKey, descriptionKey);
    }

    /** Shows "Your camp is ready" once per session to each player who hasn't campfire-slept yet. */
    public void showCampReadyToastIfDue() {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        if (!quests.isCampfireQuestComplete()) {
            return;
        }
        // Not until vanilla allows sleeping.
        if (sl.isDay()) {
            return;
        }
        ResourceLocation slept = Questown.ResourceLocation(TutorialTrigger.Triggers.FirstCampfireSleep.getID());
        for (ServerPlayer p : sl.players()) {
            if (p.isSleeping() || !campReadyToastShown.add(p.getUUID())) {
                continue;
            }
            if (Compat.hasCompletedAdvancement(p, slept)) {
                continue;
            }
            messages.sendTutorialToast(p, CampfireReadyReward.TOAST_TITLE_KEY, CampfireReadyReward.TOAST_DESCRIPTION_KEY);
        }
    }

    public boolean hasPendingMorningReward() {
        return morningRewards.hasPendingMorningReward();
    }

    public void addMorningReward(MCReward ev) {
        this.morningRewards.add(ev);
        this.setChanged();
    }

    @Override
    public void addImmediateReward(MCReward r) {
        this.asapRewards.push(r);
        this.setChanged();
    }

    private void grantAdvancementOnApproach() {
        MinecraftForge.EVENT_BUS.addListener((EntityEvent.EnteringSection event) -> {
            if (event.getEntity() instanceof ServerPlayer sp) {
                double v = event.getEntity().distanceToSqr(
                        this.worldPosition.getX() + 0.5D,
                        this.worldPosition.getY() + 0.5D,
                        this.worldPosition.getZ() + 0.5D
                );
                if (v < 100) {
                    AdvancementsInit.APPROACH_TOWN_TRIGGER.trigger(sp, ApproachTownTrigger.Triggers.FirstVisit);
                }
            }
        });
    }

    @Override
    public KnowledgeHolder<ResourceLocation, MCHeldItem, MCTownItem> getKnowledgeHandle() {
        return knowledgeHandle;
    }

    @Override
    public QuestsHolder getQuestHandle() {
        return questsHandle;
    }

    @Override
    public RoomsHolder getRoomHandle() {
        return roomsHandle;
    }

    @Override
    public Optional<ResourceLocation> roomRecipeHolding(BlockPos pos) {
        Collection<RoomRecipeMatch<MCRoom>> matches = roomsHandle.getMatches(m -> true);
        Optional<MCRoom> room = ca.bradj.questown.town.rooms.RoomDoors.roomHolding(
                matches.stream().map(RoomRecipeMatch::getRoom).toList(), pos);
        return room.flatMap(r -> matches.stream()
                                        .filter(m -> m.getRoom().equals(r))
                                        .findFirst()
                                        .flatMap(m -> getTopMatch(this::recipesFromLevel, m)));
    }

    void setUpQuestsForNewlyPlacedFlag() {
        TownQuests.setUpQuestsForNewlyPlacedFlag(this, quests);
        this.isInitializedQuests = true;
        setChanged();
    }

    /** Pauses the requests storage holds enough of. */
    void refreshStockedRequests(ImmutableList<MCTownItem> stacks) {
        java.util.Map<WorkRequest, Integer> stock = new java.util.LinkedHashMap<>();
        for (WorkRequest r : workHandle.getRequestedResults()) {
            stock.put(r, countInStorage(stacks, r));
        }
        ImmutableMap<WorkRequest, Integer> counts = ImmutableMap.copyOf(stock);
        workHandle.setStock(counts);
        workHandle.setStocked(BoardRequests.stocked(
                workHandle.getRequestedResults(),
                r -> counts.getOrDefault(r, 0),
                quests.requestsOpenQuestsNeed(),
                workHandle::limitOf
        ));
    }

    private static int countInStorage(ImmutableList<MCTownItem> stacks, WorkRequest r) {
        Ingredient requested = r.asIngredient();
        return stacks.stream().filter(s -> requested.test(s.toQTItemStack())).mapToInt(MCTownItem::quantity).sum();
    }

    public ImmutableList<Quest<ResourceLocation, MCRoom>> getAllQuests() {
        return quests.getAll();
    }

    /** A new town's starting wood: a line of fallen logs and two trees near the flag. Once, on a new flag's first tick. */
    void seedStartingTimber() {
        ServerLevel l = getServerLevel();
        if (l == null || l.isClientSide()) {
            return;
        }
        BlockPos flag = getBlockPos();
        int placed = 0;
        // Eight fallen logs, clear of the arrival ring and the first room.
        BlockState log = Blocks.OAK_LOG.defaultBlockState()
                                       .setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        for (int i = 0; i < 8; i++) {
            BlockPos p = flag.offset(-1 + i, 0, 6);
            if (l.getBlockState(p).isAir() && !l.getBlockState(p.below()).isAir()) {
                l.setBlock(p, log, 3);
                placed++;
            }
        }
        // The same tree grows at a given position every time.
        MinecraftWorldAccess world = MinecraftWorldAccess.silent(l);
        if (world.growTreeAt(flag.offset(8, 0, 6), new ItemStack(Items.OAK_SAPLING))) {
            placed++;
        }
        if (world.growTreeAt(flag.offset(-8, 0, -6), new ItemStack(Items.OAK_SAPLING))) {
            placed++;
        }
        if (placed > 0) {
            QT.FLAG_LOGGER.info("Seeded starting timber at new flag {} ({} placements)", flag, placed);
        }
    }

    /** Gives the player who placed the flag a filled map centred on it. */
    public void grantStarterMap(net.minecraft.server.level.ServerPlayer placer) {
        ServerLevel sl = getServerLevel();
        if (sl == null) {
            return;
        }
        BlockPos flag = getTownFlagBasePos();
        ItemStack filledMap = net.minecraft.world.item.MapItem.create(
                sl, flag.getX(), flag.getZ(), (byte) 1, true, false
        );
        net.minecraft.world.item.MapItem.renderBiomePreviewMap(sl, filledMap);
        if (!placer.addItem(filledMap)) {
            placer.drop(filledMap, false);
        }
        QT.FLAG_LOGGER.info(
                "Granted starter map (zoom 1, flag-centered at {}) to {}",
                flag,
                placer.getGameProfile().getName()
        );
    }

    @Override
    public void roomRecipeCreated(
            MCRoom room,
            RoomRecipeMatch<MCRoom> match
    ) {
        roomRecipeCreated(room, match, true);
    }

    /** As {@link #roomRecipeCreated}, without the chat line; block rooms announce themselves once. */
    void roomRecipeCreated(
            MCRoom room,
            RoomRecipeMatch<MCRoom> match,
            boolean broadcast
    ) {
        ServerLevel l = getServerLevel();
        world.swapBlocks(l, match);
        if (broadcast) {
            messages.roomRecipeCreated(room, getTopMatch(this::recipesFromLevel, match));
        }
        BlockPos pos = Positions.ToBlock(room.doorPos, room.yCoord);
        if (match.anyMatch(SpecialQuests.JOB_BOARD)) {
            AdvancementsInit.ROOM_TRIGGER.triggerForNearestPlayer(l, RoomTrigger.Triggers.FirstJobBoard, pos);
        }
        if (match.anyMatch(Questown.ResourceLocation("store_room"))) {
            AdvancementsInit.ROOM_TRIGGER.triggerForNearestPlayer(l, RoomTrigger.Triggers.FirstStoreRoom, pos);
        }
        // TODO: get room for rendering effect
//        handleRoomChange(room, ParticleTypes.HAPPY_VILLAGER);

        runForTopMatch(this::recipesFromLevel, match, r -> quests.markQuestAsComplete(room, r));
    }

    /** A block room (job board, welcome mat, mineshaft) at {@code block}, announced at the block. */
    void blockRoomCreated(
            MCRoom room,
            RoomRecipeMatch<MCRoom> match,
            BlockPos block
    ) {
        roomRecipeCreated(room, match, false);
        messages.roomRecipeCreated(Positions.FromBlockPos(block), getTopMatch(this::recipesFromLevel, match));
    }


    private Map<ResourceLocation, RoomRecipe> recipesFromLevel() {
        return RoomRecipes.hydrate(getServerLevel().getRecipeManager(), true);
    }

    @Override
    public void roomRecipeChanged(
            MCRoom oldRoom,
            RoomRecipeMatch<MCRoom> oldMatch,
            MCRoom newRoom,
            RoomRecipeMatch<MCRoom> newMatch
    ) {
        ServerLevel l = getServerLevel();
        // Turn signs into job boards on changes too, not only on creation.
        world.swapBlocks(l, newMatch);
        Optional<ResourceLocation> oldMatchID = getTopMatch(this::recipesFromLevel, oldMatch);
        Optional<ResourceLocation> newMatchID = getTopMatch(this::recipesFromLevel, newMatch);
        if (oldMatchID.isPresent() && newMatchID.isPresent() && !oldMatchID.equals(newMatchID)) {
            messages.roomRecipeChanged(oldMatchID.get(), newMatchID.get(), newRoom);
            quests.roomRecipeChanged(oldRoom, oldMatch, newRoom, newMatch);
            TownRooms.addParticles(l, newRoom, ParticleTypes.HAPPY_VILLAGER);
        }
        setChanged();
    }

    @Override
    public void roomRecipeDestroyed(
            MCRoom roomDoorPos,
            RoomRecipeMatch<MCRoom> oldRecipeId
    ) {
        ServerLevel l = getServerLevel();
        messages.roomRecipeDestroyed(roomDoorPos, getTopMatch(this::recipesFromLevel, oldRecipeId).orElse(null));
        quests.roomRecipeDestroyed(roomDoorPos, oldRecipeId);
        TownRooms.addParticles(l, roomDoorPos, ParticleTypes.SMOKE);
        setChanged();
    }

    @Override
    public void addRandomJobQuestForVisitor(UUID visitorUUID) {
        TownQuests.addJobQuest(this, quests, VillagerUUID.from(visitorUUID));
        setChanged();
        // TODO: Town should have owners who all get the cheevo
        BlockPos bp = getBlockPos();
        AdvancementsInit.VISITOR_TRIGGER.triggerForNearestPlayer(
                getServerLevel(),
                VisitorTrigger.Triggers.FirstJobQuest,
                bp
        );
    }

    @Override
    public boolean changeJobForVisitorFromBoard(
            UUID ownerUUID,
            JobID currentJob
    ) {
        long tick = Util.getTick(getServerLevel());
        VillagerUUID ownerVUID = VillagerUUID.from(ownerUUID);
        // Every exit logs its branch.
        BiConsumer<String, JobID> changeJob = (branch, id) -> {
            getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.JOB_POSSIBILITIES_COMPUTE).log(
                    "[board] {} {} -> {} via {}", ownerVUID, currentJob, id, branch
            );
            villagerHandle.changeJobForVillager(ownerVUID, id, false);
        };

        VisitorMobEntity villager = villagerHandle.getEntity(ownerVUID);
        if (villager == null) {
            return true;
        }
        float damagePercent = villagerHandle.getDamagePercent(ownerVUID);
        if (damagePercent > 0) {
            // The more damaged they are, the more likely they are to rest.
            if (level.getRandom().nextFloat() < damagePercent) {
                changeJob.accept("damaged-rest", ResterWork.getIdForRoot(currentJob.rootId()));
                return true;
            }
        }

        boolean carrying = carriesBlockOfProgress(villager);
        // A carried Block of Progress always goes to the flag.
        if (villagerHandle.hasBlockOfProgress(ownerVUID) || carrying) {
            // Hand one out only when the villager isn't already carrying one.
            if (!carrying) {
                // Record the job that earned it.
                ItemStack bopStack = new ItemStack(ItemsInit.BLOCK_OF_PROGRESS.get());
                BlockOfProgress.markEarnedBy(bopStack, currentJob);
                MCHeldItem bop = MCHeldItem.fromTown(bopStack);
                villager.tryGiveItem(bop, InventoryFullStrategy.REMOVE_FROM_WORLD);
            }
            changeJob.accept("bop-deposit", BOPDepositorWork.getIdForRoot(currentJob.rootId()));
            return true;
        }

        if (villagerHandle.isReadyForDowntime(ownerVUID, tick) && !DowntimeWork.matches(currentJob)
                && DiningStages.downtimeMayInterrupt(currentJob, villagerHandle.isStarving(ownerVUID))) {
            changeJob.accept("ready-for-downtime", DowntimeWork.getIdForRoot(currentJob.rootId()));
            return true;
        }

        // Store carried work output before eating; storing it is what counts it as made.
        if (depositCarriedWorkOutput(villager)) {
            return true;
        }

        // A starving villager that hasn't just given up on dining starts the diner chain.
        if (villagerHandle.isStarving(ownerVUID) && !villagerHandle.gaveUpDiningRecently(ownerVUID)) {
            String rid = currentJob.rootId();
            ResourceLocation diningRoom = DinerWork.asWork(rid).baseRoom;
            boolean hasDiningRoom = !roomsHandle.getRoomsMatching(diningRoom).isEmpty();
            // Still starving on the way out of a diner: try the next one.
            JobID diner = DiningStages.next(currentJob, hasDiningRoom);
            if (diner != null) {
                changeJob.accept("starving-diner-stage", diner);
                return true;
            }
        }

        // The player's "after this trip" switch, applied at the next job pick.
        JobID chosen = villagerHandle.takeSwitchAfterTrip(ownerVUID);
        if (chosen != null) {
            changeJob.accept("player-after-trip", chosen);
            return true;
        }

        BoardRequests requestedResults = workHandle.getBoardRequests();
        WorksBehaviour.TownData td = getTownData();
        Predicate<JobID> canFit = p -> {
            if (ServerJobsRegistry.canFit(uuid, p, Util.getDayTime(getServerLevel()))) {
                return true;
            }
            getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.JOB_POSSIBILITIES_COMPUTE).log(
                    "Villager will not do {} because there is not enough time left in the day", p
            );
            return false;
        };
        Predicate<JobID> canAlwaysStart = p -> ServerJobsRegistry.canAlwaysStart(uuid, p);
        JobID work = possibleWork.nextForVillager(
                ownerUUID,
                villager.getJobId(),
                canAlwaysStart,
                canFit,
                requestedResults,
                td
        );
        if (work != null) {
            // Taking real work earns a later rest.
            villagerHandle.markWorked(VillagerUUID.from(ownerUUID), tick);
            changeJob.accept("work-roll", work);
            return true;
        }

        // Nothing to do: rest, if the last rest is old enough.
        if (villagerHandle.isReadyForDowntimeIfIdle(ownerVUID, tick) && !DowntimeWork.matches(currentJob)) {
            changeJob.accept("idle-escape", DowntimeWork.getIdForRoot(currentJob.rootId()));
            return true;
        }
        // A diner that has eaten and has no work relaxes.
        if (DiningStages.isDiner(currentJob) && !villagerHandle.isStarving(ownerVUID)) {
            changeJob.accept("diner-ate", DowntimeWork.getIdForRoot(currentJob.rootId()));
            return true;
        }
        if (BoardIdleExits.depositFinished(currentJob)) {
            changeJob.accept("bop-deposited", DowntimeWork.getIdForRoot(currentJob.rootId()));
            return true;
        }
        getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.JOB_POSSIBILITIES_COMPUTE).log(
                "[board] {} {} stays: no branch applied", ownerVUID, currentJob
        );
        return false;
    }

    private static boolean carriesBlockOfProgress(VisitorMobEntity villager) {
        Container inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(ItemsInit.BLOCK_OF_PROGRESS.get())) {
                return true;
            }
        }
        return false;
    }

    /** Stores the villager's carried work output before it eats. @return true when the town has no chest, so the villager waits here */
    private boolean depositCarriedWorkOutput(VisitorMobEntity villager) {
        Container inventory = villager.getInventory();
        List<MCTownItem> carried = new ArrayList<>();
        List<Integer> carriedSlots = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || stack.is(ItemsInit.BLOCK_OF_PROGRESS.get())) {
                continue;
            }
            carried.add(MCTownItem.fromMCItemStack(stack));
            carriedSlots.add(slot);
        }
        if (carried.isEmpty()) {
            return false;
        }
        ContainerTarget<MCContainer, MCTownItem> chest = TownContainers.findClosestMatching(
                this, MCTownItem::isEmpty, villager.blockPosition(), carried);
        if (chest == null) {
            return true;
        }
        int deposited = 0;
        for (MCTownItem item : carried) {
            ContainerTarget<MCContainer, MCTownItem> updated = chest.withItemAdded(item);
            if (updated == null) {
                break;
            }
            chest = updated;
            deposited++;
        }
        for (int i = 0; i < deposited; i++) {
            inventory.setItem(carriedSlots.get(i), ItemStack.EMPTY);
        }
        if (deposited > 0) {
            inventory.setChanged();
        }
        return false;
    }

    public WorksBehaviour.TownData getTownData() {
        return new WorksBehaviour.TownData(
                getServerLevel(),
                prefix -> knowledgeHandle.getAllKnownGatherResults(
                        biomes.getAllInTown(),
                        prefix
                )
        );
    }

    /** {@link #getTownData} for what players see: a trip job only claims what its own loot tables can drop. */
    public WorksBehaviour.TownData getTownDataForDisplay() {
        ServerLevel level = getServerLevel();
        return new WorksBehaviour.TownData(
                level,
                prefix -> {
                    ImmutableSet<String> drops = Loots.previewForPrefix(level.getServer().getResourceManager(), prefix);
                    return knowledgeHandle.getAllKnownGatherResults(biomes.getAllInTown(), prefix).stream()
                            .filter(item -> drops.contains(Compat.getItemId(item.get()).toString()))
                            .collect(ImmutableSet.toImmutableSet());
                }
        );
    }

    @Override
    public @Nullable UUID getRandomVillager() {
        if (getVillagers().isEmpty()) {
            return null;
        }
        List<UUID> villagers = ImmutableList.copyOf(getVillagers());
        return villagers.get(getServerLevel().getRandom().nextInt(villagers.size()));
    }

    @Override
    public boolean isVillagerMissing(UUID uuid) {
        return !getVillagers().contains(uuid);
    }

    @Override
    public Vec3 getVisitorJoinPos() {
        return pois.getVisitorJoinPos(getServerLevel(), getBlockPos());
    }

    @Override
    public Collection<BlockPos> getTownAnchors() {
        BlockPos flagPos = getBlockPos();
        List<BlockPos> anchors = new ArrayList<>();
        anchors.add(flagPos);
        anchors.addAll(pois.getWelcomeMats());
        roomsHandle.getAllRegisteredDoors().forEach(d -> anchors.add(new BlockPos(d.x, flagPos.getY() + d.scanLevel, d.z)));
        return anchors;
    }

    // Rate-limits the wander-failure warning per stuck villager.
    private final Map<BlockPos, Long> wanderFailLogWindows = new HashMap<>();
    private static final long WANDER_FAIL_LOG_INTERVAL_TICKS = 200;

    @Override
    public BlockPos getRandomWanderTarget(BlockPos avoiding) {
        if (!isInitialized()) {
            return null;
        }
        Collection<MCRoom> allRooms;
        ServerLevel sl = getServerLevel();
        if (sl == null) {
            return null;
        }

        boolean night = sl.isNight();
        if (night) {
            allRooms = roomsHandle.getRegisteredRooms().getAllRooms();
        } else {
            allRooms = roomsHandle.getAllRoomsIncludingMetaAndFarms();
        }
        BlockPos townPos = pois.getWanderTarget(
                sl, allRooms, (p, r) -> {
                    BlockPos pos = Positions.ToBlock(p, r.yCoord);
                    double dist = pos.distSqr(avoiding);
                    if (dist > 5) {
                        getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.VILLAGER_NAVIGATION).log(
                                "Target is {} blocks away from {}", dist, avoiding
                        );
                        return true;
                    }
                    return false;
                }, (p, r) -> Positions.ToBlock(p, r.yCoord),
                new TownPois.WanderDiagnostics() {
                    @Override
                    public void noRooms() {
                        logWanderFailureOnce(sl, avoiding, "no wanderable rooms ({})", night ? "night" : "day");
                    }

                    @Override
                    public void noCandidateInRoom(int roomCount, int enclosedCount) {
                        logWanderFailureOnce(
                                sl, avoiding,
                                "all {} room(s) filtered out ({} enclosed position(s) checked; {})",
                                roomCount, enclosedCount, night ? "night" : "day"
                        );
                    }
                }
        );
        return townPos;
    }

    private void logWanderFailureOnce(
            ServerLevel sl,
            BlockPos avoiding,
            String reason,
            Object... reasonArgs
    ) {
        long now = sl.getGameTime();
        Long windowStart = wanderFailLogWindows.get(avoiding);
        if (windowStart != null && now - windowStart < WANDER_FAIL_LOG_INTERVAL_TICKS) {
            return;
        }
        wanderFailLogWindows.put(avoiding, now);
        Object[] args = new Object[reasonArgs.length + 1];
        System.arraycopy(reasonArgs, 0, args, 0, reasonArgs.length);
        args[reasonArgs.length] = avoiding;
        QT.FLAG_LOGGER.warn("Could not find wander target: " + reason + " [avoiding {}]", args);
    }

    public ImmutableSet<UUID> getVillagers() {
        return ImmutableSet.copyOf(villagerHandle.stream().map(Entity::getUUID).collect(Collectors.toSet()));
    }

    @Override
    public TownHealingHandle getHealHandle() {
        return healing;
    }

    @SuppressWarnings("removal") // Already using recommended alternative
    @Override
    public @Nullable ContainerTarget<MCContainer, MCTownItem> findMatchingContainer(ContainerTarget.CheckFn<MCTownItem> c) {
        return TownContainers.findMatching(this, c);
    }

    @Override
    public WorkStatusHandle<BlockPos, MCHeldItem> getWorkStatusHandle(
            @Nullable UUID ownerIDOrNullForGlobal
    ) {
        return getRealWorkStatusHandle(ownerIDOrNullForGlobal);
    }

    @Override
    public WorkHandle getWorkHandle() {
        return workHandle;
    }

    @Override
    public Collection<String> getAvailableRootJobs() {
        // TODO: Scan villagers to make this decision
        Set<String> allJobs = ServerJobsRegistry.getAllJobs().stream().map(JobID::rootId).collect(Collectors.toSet());
        Set<String> allFilledJobs = villagerHandle.stream().filter(v -> v instanceof VisitorMobEntity)
                                                  .map(v -> (VisitorMobEntity) v).map(VisitorMobEntity::getJobId)
                                                  .map(JobID::rootId).collect(Collectors.toSet());
        Set<String> allNewJobs = allJobs.stream().filter(v -> !allFilledJobs.contains(v)).collect(Collectors.toSet());
        if (allNewJobs.isEmpty()) {
            allNewJobs = allJobs;
        }
        return allNewJobs;
    }

    @Override
    public boolean hasEnoughBeds() {
        long numVillagers = villagerHandle.size();
        return roomsHandle.hasEnoughBeds(numVillagers);
    }

    @Override
    public int missingBeds() {
        long numVillagers = villagerHandle.size();
        return roomsHandle.missingBeds(numVillagers);
    }

    @Override
    public int missingBedsWithArrivals(int arrivals) {
        return roomsHandle.missingBeds(villagerHandle.size() + arrivals);
    }

    @Override
    public boolean isInitialized() {
        return isInitializedQuests && biomes.isInitialized() && initializers.isEmpty();
    }

    public boolean isInitializing() {
        return !initializers.isEmpty();
    }

    public ImmutableList<HashMap.SimpleEntry<MCQuest, MCReward>> getAllQuestsWithRewards() {
        return quests.questBatches.getAllWithRewards();
    }

    @Override
    public void campfireFound(BlockPos bp) {
        Position pos = Positions.FromBlockPos(bp);
        MCRoom room = new MCRoom(pos, ImmutableList.of(InclusiveSpace.from(pos).to(pos)), bp.getY());
        quests.markQuestAsComplete(room, SpecialQuests.CAMPFIRE);
    }

    @Override
    public void townGateFound(BlockPos bp) {
        Position pos = Positions.FromBlockPos(bp);
        MCRoom room = new MCRoom(pos, ImmutableList.of(InclusiveSpace.from(pos).to(pos)), bp.getY());
        quests.markQuestAsComplete(room, SpecialQuests.TOWN_GATE);
    }

    @Override
    public UUID getUUID() {
        return uuid;
    }

    /** Takes on a moved town's UUID. Call before the flag finishes loading. */
    void adoptRelocatedIdentity(UUID carried) {
        this.uuid = carried;
    }

    public void registerWelcomeMat(BlockPos welcomeMatBlock) {
        pois.registerWelcomeMat(welcomeMatBlock);
        roomsHandle.registerBlockAsRoom(SpecialQuests.TOWN_GATE, welcomeMatBlock);
        // A registered welcome mat completes the town gate quest.
        Position gatePos = Positions.FromBlockPos(welcomeMatBlock);
        MCRoom gateRoom = new MCRoom(gatePos, ImmutableList.of(InclusiveSpace.from(gatePos).to(gatePos)), welcomeMatBlock.getY());
        quests.markQuestAsComplete(gateRoom, SpecialQuests.TOWN_GATE);
        setChanged();
        AdvancementsInit.ROOM_TRIGGER.triggerForNearestPlayer(
                getServerLevel(),
                RoomTrigger.Triggers.FirstWelcomeMat,
                welcomeMatBlock
        );
    }


    /** Places a welcome mat at {@code pos} and registers it with this flag, as a player placing one does. */
    public void placeWelcomeMat(BlockPos pos) {
        ServerLevel level = getServerLevel();
        if (level == null) {
            return;
        }
        net.minecraft.world.level.block.state.BlockState state =
                ca.bradj.questown.core.init.BlocksInit.WELCOME_MAT_BLOCK.get().defaultBlockState();
        level.setBlockAndUpdate(pos, state);
        registerWelcomeMat(pos);
    }

    public List<BlockPos> getWelcomeMats() {
        return pois.getWelcomeMats();
    }


    public void registerJobsBoard(BlockPos matPos) {
        this.workHandle.registerJobBoard(matPos);
        this.setChanged();
    }

    public void removeJobsBoard(BlockPos matPos) {
        this.workHandle.removeJobBoard(matPos);
        this.setChanged();
    }

    /** Places a job board at {@code pos} and registers it with this flag, as a player placing one does. */
    public void placeJobBoard(BlockPos pos) {
        ServerLevel level = getServerLevel();
        if (level == null) {
            return;
        }
        net.minecraft.world.level.block.state.BlockState state =
                ca.bradj.questown.core.init.BlocksInit.JOB_BOARD_BLOCK.get().defaultBlockState();
        level.setBlockAndUpdate(pos, state);
        registerJobsBoard(pos);
    }

    // Dev-only: the empty cells inside this flag's registered rooms. A block placed in one is inside the room.
    public java.util.List<BlockPos> interiorCells() {
        ServerLevel level = getServerLevel();
        java.util.List<BlockPos> cells = new java.util.ArrayList<>();
        if (level == null) {
            return cells;
        }
        for (MCRoom room : roomsHandle.getRegisteredRooms().getAllRooms()) {
            int y = room.yCoord;
            for (InclusiveSpace space : room.getSpaces()) {
                for (int x = space.getWestX(); x <= space.getEastX(); x++) {
                    for (int z = space.getNorthZ(); z <= space.getSouthZ(); z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (level.getBlockState(pos).isAir()) {
                            cells.add(pos);
                        }
                    }
                }
            }
        }
        return cells;
    }

    // Dev-only: place a block in the indexed interior cell. Returns where it landed, or null.
    public @Nullable BlockPos placeBlockInInterior(int index, Block block) {
        ServerLevel level = getServerLevel();
        if (level == null) {
            return null;
        }
        java.util.List<BlockPos> cells = interiorCells();
        if (index < 0 || index >= cells.size()) {
            return null;
        }
        BlockPos pos = cells.get(index);
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        return pos;
    }

    public void buildRoom(BlockPos doorPos) {
        ServerLevel level = getServerLevel();
        if (level == null) {
            return;
        }
        // A registered door already has a room; another room needs another door.
        int scanLevel = doorPos.getY() - getBlockPos().getY();
        if (getRoomHandle().getAllRegisteredDoors().contains(
                new ca.bradj.questown.town.rooms.TownPosition(doorPos.getX(), doorPos.getZ(), scanLevel)
        )) {
            QT.FLAG_LOGGER.warn(
                    "[build_room] door {} already has a room built around it; a quest that needs its "
                            + "own room needs a new door, not this one",
                    doorPos
            );
            return;
        }

        // Place a door if there isn't one, or the plan walls the doorway up.
        if (!(level.getBlockState(doorPos).getBlock() instanceof net.minecraft.world.level.block.DoorBlock)) {
            if (!level.getBlockState(doorPos).isAir() || !level.getBlockState(doorPos.above()).isAir()) {
                QT.FLAG_LOGGER.warn(
                        "[build_room] door cell at {} is blocked and cannot hold a door",
                        doorPos
                );
                return;
            }
            placeDoor(doorPos);
        }

        int baseY = getBlockPos().getY() + (doorPos.getY() - getBlockPos().getY());

        // Check first, so a room that can't close is never started.
        java.util.function.Predicate<BlockPos> isOccupied = p -> !level.getBlockState(p).isAir();
        java.util.function.Predicate<BlockPos> isOccupiedIgnoringDoorCell = p -> {
            if (p.equals(doorPos) || p.equals(doorPos.above())) {
                return false;
            }
            return isOccupied.test(p);
        };
        RoomShellPlan.Outcome feasible = RoomShellPlan.feasibility(
                doorPos, baseY, isOccupiedIgnoringDoorCell
        );
        if (!feasible.clear()) {
            QT.FLAG_LOGGER.warn(
                    "[build_room] refusing to build a room at {}: {} - pick a clear door spot",
                    doorPos, feasible.reason()
            );
            return;
        }
        RoomShellPlan.DoorFrame frame = RoomShellPlan.frameFor(doorPos, baseY, isOccupied);
        RoomShellPlan.Plan plan = RoomShellPlan.plan(frame, isOccupied, java.util.Set.of());
        int laid = 0;
        for (RoomShellPlan.BlockStep step : plan.steps()) {
            if (level.getBlockState(step.pos()).isAir()) {
                level.setBlockAndUpdate(step.pos(), net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState());
                laid++;
            }
        }
        // Register the door after the walls, so the scan sees the finished room.
        getRoomHandle().registerDoor(doorPos);
        QT.FLAG_LOGGER.info(
                "[build_room] laid {} shell blocks around door {} for flag {} (door registered; the room "
                        + "is detected by the scan on a later tick)",
                laid, doorPos, getBlockPos()
        );
    }

    /**
     * Builds a 5x5 room with a south door, plank walls, a dirt floor and a roof around {@code doorPos},
     * registers the door, and places the recipe's blocks inside.
     *
     * @param recipeBlocks the recipe's blocks, one per ingredient; a bed takes two
     * @return the door position, or null when the spot is not clear
     */
    public @Nullable BlockPos buildRoomForRecipe(BlockPos doorPos, List<net.minecraft.world.level.block.Block> recipeBlocks) {
        ServerLevel level = getServerLevel();
        if (level == null) return null;

        if (!level.getBlockState(doorPos).isAir()
                || !level.getBlockState(doorPos.above()).isAir()
                || level.getBlockState(doorPos.below()).isAir()) {
            return null;
        }

        int s = 4;
        int cornerX = doorPos.getX() - s / 2;
        int cornerZ = doorPos.getZ() - s;
        BlockPos corner = new BlockPos(cornerX, doorPos.getY(), cornerZ);

        for (int dx = 0; dx <= s; dx++) {
            for (int dz = 0; dz <= s; dz++) {
                BlockPos at = corner.offset(dx, 0, dz);
                level.setBlockAndUpdate(at.below(), net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
                boolean edge = dx == 0 || dx == s || dz == 0 || dz == s;
                if (edge) {
                    boolean isDoor = dx == s / 2 && dz == s;
                    for (int dy = 0; dy <= 2; dy++) {
                        BlockPos wall = at.above(dy);
                        if (isDoor && dy <= 1) {
                            var half = dy == 0
                                    ? net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                                    : net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER;
                            level.setBlockAndUpdate(wall, net.minecraft.world.level.block.Blocks.OAK_DOOR.defaultBlockState()
                                    .setValue(net.minecraft.world.level.block.DoorBlock.HALF, half));
                        } else {
                            level.setBlockAndUpdate(wall, net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState());
                        }
                    }
                }
                level.setBlockAndUpdate(at.above(3), net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState());
            }
        }

        getRoomHandle().registerDoor(doorPos);

        List<net.minecraft.world.level.block.Block> toPlace = ca.bradj.questown.logic.BedHalves.oneEntryPerBed(
                recipeBlocks,
                b -> b.defaultBlockState().hasProperty(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART)
        );

        // The recipe blocks go in the 3x3 inside, on two levels. A bed takes two cells.
        int[][] interior = {{2, 2}, {1, 1}, {3, 3}, {1, 3}, {3, 1}, {2, 1}, {1, 2}, {3, 2}, {2, 3}};
        int levels = 2;
        java.util.List<BlockPos> cells = new java.util.ArrayList<>();
        for (int lvl = 0; lvl < levels; lvl++) {
            for (int[] c : interior) {
                cells.add(new BlockPos(cornerX + c[0], doorPos.getY() + lvl, cornerZ + c[1]));
            }
        }
        java.util.Set<BlockPos> cellSet = new java.util.HashSet<>(cells);
        int capacity = cells.size();
        if (recipeBlocks.size() > capacity) {
            QT.FLAG_LOGGER.warn(
                    "[buildRoomForRecipe] recipe needs {} blocks but the room holds {} "
                            + "({} cells x {} levels); the room will NOT match the recipe",
                    recipeBlocks.size(), capacity, interior.length, levels
            );
        }
        java.util.Set<BlockPos> used = new java.util.HashSet<>();
        int placed = 0;
        for (net.minecraft.world.level.block.Block block : toPlace) {
            net.minecraft.world.level.block.state.BlockState st = block.defaultBlockState();
            if (!st.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART)) {
                for (BlockPos at : cells) {
                    if (used.contains(at)) {
                        continue;
                    }
                    level.setBlockAndUpdate(at, st);
                    used.add(at);
                    placed++;
                    break;
                }
                continue;
            }
            // A bed needs a free cell and a free cell in front of it; without both, it is skipped.
            int placedBefore = placed;
            placedBed:
            for (BlockPos at : cells) {
                if (used.contains(at)) {
                    continue;
                }
                for (net.minecraft.core.Direction facing : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    BlockPos head = at.relative(facing);
                    if (!cellSet.contains(head) || used.contains(head)) {
                        continue;
                    }
                    net.minecraft.world.level.block.state.BlockState footState = st.setValue(
                            net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART,
                            net.minecraft.world.level.block.state.properties.BedPart.FOOT)
                            .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, facing);
                    level.setBlockAndUpdate(at, footState);
                    level.setBlockAndUpdate(head, footState.setValue(
                            net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART,
                            net.minecraft.world.level.block.state.properties.BedPart.HEAD));
                    used.add(at);
                    used.add(head);
                    placed++;
                    break placedBed;
                }
            }
            if (placed == placedBefore) {
                QT.FLAG_LOGGER.warn(
                        "[buildRoomForRecipe] no free adjacent cell pair left for a bed of {} "
                                + "({} of {} interior cells used); bed left unplaced",
                        block, used.size(), cells.size()
                );
            }
        }

        QT.FLAG_LOGGER.info(
                "[buildRoomForRecipe] built 5x5 room at corner {} for door {}, placed {} of {} inside, door registered",
                corner.toShortString(), doorPos.toShortString(), placed, toPlace.size(), toPlace
        );
        return doorPos;
    }

    /** Places a whole oak door at {@code pos}. Register it with {@code _qtdev registerroom}. */
    public void placeDoor(BlockPos pos) {
        ServerLevel level = getServerLevel();
        if (level == null) {
            return;
        }
        net.minecraft.world.level.block.state.BlockState door =
                net.minecraft.world.level.block.Blocks.OAK_DOOR.defaultBlockState();
        level.setBlockAndUpdate(pos, door.setValue(
                net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
        ));
        level.setBlockAndUpdate(pos.above(), door.setValue(
                net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
        ));
    }

    public void openJobsMenu(
            ServerPlayer sender,
            boolean skipStraightToAdd
    ) {
        workHandle.openMenuRequested(sender, skipStraightToAdd);
    }

    /** For tests: saves the live town state now, so a warp runs on current data. */
    public void publishStateToTileForTest() {
        CompoundTag tag = Compat.getBlockStoredTagData(this);
        writeTownData(tag);
        state.putStateOnTile(tag, getUUID(), getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.TOWN_STATE_CHANGES));
    }

    public MCTownState warpTime(int ticks) {
        return state.warp(this, Compat.getBlockStoredTagData(this), getServerLevel(), ticks);
    }

    public void freezeWarpReferenceTick() {
        CompoundTag tag = Compat.getBlockStoredTagData(this);
        tag.putLong(NBT_TIME_WARP_REFERENCE_TICK, getServerLevel().getDayTime());
    }

    public @Nullable MCTownState captureCurrentState() {
        return state.captureState();
    }

    public VillagerHolder getVillagerHandle() {
        return TownVillagerHandles.asVillagerHolder(villagerHandle);
    }

    public TownVillagerHandle getTownVillagerHandle() {
        return villagerHandle;
    }

    /** The jobs this town knows that make {@code result}, from what it has found. */
    public ImmutableMap<JobID, ResourceLocation> getKnownProducersOf(Ingredient result) {
        ImmutableSet<JobID> townKnown = villagerHandle.getTownKnownJobs();
        ImmutableMap.Builder<JobID, ResourceLocation> producers = ImmutableMap.builder();
        ServerJobsRegistry.getJobsKnownToProduce(getTownDataForDisplay(), result).forEach((jobId, icon) -> {
            if (townKnown.contains(jobId)) {
                producers.put(jobId, icon);
            }
        });
        return producers.build();
    }

    /** Starts the town shutdown: villagers come back and are taken into the flag, which goes dormant. Only for an active flag; false otherwise. */
    public boolean beginTownShutdown() {
        ServerLevel level = getServerLevel();
        if (level == null) {
            return false;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(TownFlagBlock.PHASE) || state.getValue(TownFlagBlock.PHASE) != FlagPhase.ACTIVE) {
            return false;
        }
        shutdownController.begin(level, getBlockPos(), state, this);
        return true;
    }

    /** Stops a shutdown in progress: the villagers come back and the flag is active again. */
    public boolean cancelTownShutdown() {
        ServerLevel level = getServerLevel();
        if (level == null || !shutdownController.isRunning()) {
            return false;
        }
        shutdownController.cancel(level, getBlockPos(), this);
        return true;
    }

    private boolean isDormant() {
        BlockState state = getBlockState();
        return state.hasProperty(TownFlagBlock.PHASE)
                && state.getValue(TownFlagBlock.PHASE) == FlagPhase.DORMANT;
    }

    public boolean isDeedAvailable() {
        BlockState state = getBlockState();
        return state.hasProperty(TownFlagBlock.DEED_AVAILABLE) && state.getValue(TownFlagBlock.DEED_AVAILABLE);
    }

    /** A relocation deed is waiting above this dormant flag; the player collects it by using the flag. */
    void makeDeedAvailable() {
        setDeedAvailable(true);
    }

    private void setDeedAvailable(boolean value) {
        ServerLevel level = getServerLevel();
        if (level == null || !getBlockState().hasProperty(TownFlagBlock.DEED_AVAILABLE)) {
            return;
        }
        level.setBlock(getBlockPos(), getBlockState().setValue(TownFlagBlock.DEED_AVAILABLE, value), Block.UPDATE_ALL);
    }

    /** Gives the waiting deed to the player, or drops it at the flag if their inventory is full. */
    public void collectDeed(ServerPlayer recipient) {
        ServerLevel level = getServerLevel();
        if (!isDeedAvailable() || level == null) {
            return;
        }
        setDeedAvailable(false);
        BlockPos pos = getBlockPos();
        ItemStack deed = RelocationDeedItem.forReference(getUUID(), pos, level.dimension().location());
        if (recipient.getInventory().add(deed)) {
            recipient.getInventory().setChanged();
            recipient.inventoryMenu.broadcastChanges();
        } else {
            level.addFreshEntity(new ItemEntity(
                    level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, deed
            ));
        }
        recipient.sendSystemMessage(Compat.translatable("message.questown.relocation.deed_collected"));
    }

    /** Issues a new deed for this dormant flag. */
    public boolean reissueDeed() {
        if (!isDormant() || getServerLevel() == null) {
            return false;
        }
        makeDeedAvailable();
        return true;
    }

    /** Wakes this dormant town where it is. */
    public boolean wakeInPlace() {
        ServerLevel level = getServerLevel();
        if (level == null || !isDormant()) {
            return false;
        }
        // A woken town has no deed waiting.
        setDeedAvailable(false);
        shutdownController.wake(level, getBlockPos(), this);
        return true;
    }

    /** Saves a villager as it is taken in, so it comes back with its job and items. */
    void rememberAbsorbedJournal(VisitorMobEntity absorbed) {
        CompoundTag villagerTag = TownStateSerializer.storeVillager(TownFlagState.captureVillager(this, absorbed));
        AbsorbedJournals.remember(Compat.getBlockStoredTagData(this), absorbed.getUUID(), villagerTag);
        setChanged();
    }

    /** Villagers taken in by a shutdown and not back yet. */
    List<UUID> absorbedVillagers() {
        return AbsorbedJournals.absorbed(Compat.getBlockStoredTagData(this));
    }

    @Override
    public Optional<ImmutableSnapshot<MCHeldItem, ?>> takeRespawnJournal(VillagerUUID villager) {
        UUID uuid = UUID.fromString(VillagerUUID.getStringUUID(villager));
        Optional<CompoundTag> tag = AbsorbedJournals.take(Compat.getBlockStoredTagData(this), uuid);
        setChanged();
        return tag.map(t -> TownStateSerializer.loadVillager(t).journal);
    }

    /** For tests: claims every queued immediate reward now. */
    public void claimImmediateRewardsForTest() {
        while (asapRewards.tick()) {
        }
    }

    public int getY() {
        return getTownFlagBasePos().getY();
    }

    public void startDebugTask(Supplier<Boolean> debugTask) {
        if (ticker.startDebugTask(debugTask)) {
            return;
        }
        messages.startDebugFailed();
    }

    public void toggleDebugMode() {
        this.ticker.toggleDebugMode();
        messages.debugToggled(this.ticker.isDebugEnabled());
    }

    TownFlagInitialization initializer() {
        return initializer;
    }

    public FlagTabsEmbedding.FlagInfo getInfo() {
        boolean hasIncomplete = quests.getAll().stream().anyMatch(q -> !q.isComplete());
        boolean questsTabAttention = hasIncomplete && seenQuests.hasUnseen(quests.incompleteQuestKeys());
        // The villagers and economics tabs appear with the first villager.
        boolean showVillagers = !getVillagers().isEmpty();
        boolean showEcon = showVillagers;
        return new FlagTabsEmbedding.FlagInfo(
                getBlockPos(),
                showVillagers,
                true,
                showEcon,
                getBopCount() > 0,
                questsTabAttention,
                isBopFull(),
                !givenBonusFood,
                FlagTabsEmbedding.FlagInfo.villagersTabAttention(showVillagers, villagersTabSeen),
                !bopWithdrawn
        );
    }

    /** Whether the flag holds as many Blocks of Progress as it can. */
    public boolean isBopFull() {
        BlockState state = getBlockState();
        return state.hasProperty(TownFlagBlock.BOP_FULL) && state.getValue(TownFlagBlock.BOP_FULL);
    }

    /** Brings the full-flag block state in line with the deposited Blocks of Progress. */
    public void syncBopFull() {
        setBopFull(getBopCount() >= TownFlagBOPItemHandler.CAP);
    }

    private void setBopFull(boolean value) {
        ServerLevel level = getServerLevel();
        if (level == null || !getBlockState().hasProperty(TownFlagBlock.BOP_FULL)) {
            return;
        }
        level.setBlock(getBlockPos(), getBlockState().setValue(TownFlagBlock.BOP_FULL, value), Block.UPDATE_ALL);
    }

    public void ejectBlockOfProgress(ServerPlayer sender) {
        TownFlagBOPItemHandler.eject(this, sender);
    }

    void setChangedMC(
            ServerLevel sl,
            BlockPos blockEntityPos,
            BlockState state
    ) {
        setChanged(sl, blockEntityPos, state);
    }

    /** Puts a player's Block of Progress back on the flag. False when it isn't one or the flag is full. */
    public boolean returnBopFromPlayer(ServerPlayer player, ItemStack held) {
        if (!held.is(ItemsInit.BLOCK_OF_PROGRESS.get()) || getBopCount() >= TownFlagBOPItemHandler.CAP) {
            return false;
        }
        TownFlagBOPItemHandler.depositOne(held, false, bopStacks::add);
        setChanged();
        syncBopFull();
        player.sendSystemMessage(Compat.translatable("message.questown.bop.returned", getBopCount()));
        return true;
    }

    public void grantBlockOfProgress(int amount) {
        for (int i = 0; i < amount; i++) {
            bopStacks.add(new ItemStack(ItemsInit.BLOCK_OF_PROGRESS.get()));
        }
        setChanged();
        syncBopFull();
    }

    public boolean isBoardAutoRequest() {
        return boardAutoRequest;
    }

    /** Unlocks a skill for the villager and posts its first board request. */
    public void unlockSkillFromPlayer(UUID villagerUUID, JobID id) {
        getVillagerHandle().unlockJob(villagerUUID, id);
        quests.markSkillUnlocked(id);
        postStarterRequests(List.of(id));
    }

    /** Open skill quests no villager is levelled for yet, with the level each needs. */
    public Map<ResourceLocation, Integer> lockedSkillQuestLevels() {
        Map<ResourceLocation, JobID> named = new HashMap<>();
        for (MCQuest q : quests.questBatches.getAll()) {
            if (q.getType() != Quest.QuestType.SKILL_UNLOCK || q.isComplete()) {
                continue;
            }
            MCQuest.namedSkill(q.getWantedId()).ifPresent(skill -> named.put(q.getWantedId(), skill));
        }
        if (named.isEmpty()) {
            return Map.of();
        }
        return SkillQuestLocks.lockedLevels(named, this::skillLevelGate, highestLevelByRoot());
    }

    /** Job root to the highest villager level in it. */
    public Map<String, Integer> highestLevelByRoot() {
        Map<String, Integer> highest = new HashMap<>();
        for (LivingEntity e : getVillagerHandle().entities()) {
            if (!(e instanceof VisitorMobEntity v) || v.getVUID() == null) {
                continue;
            }
            int level = villagerHandle.getLevel(VillagerUUID.get(v.getVUID()));
            highest.merge(v.getJobId().rootId(), level, Math::max);
        }
        return highest;
    }

    private int skillLevelGate(JobID skill) {
        if (!Works.isInitialized() || !Works.ids().contains(skill)) {
            return 0;
        }
        return Works.get(skill).get().getAvailableAtLevel();
    }

    public void requestWorkFromPlayer(WorkRequest request) {
        workHandle.requestWorkFromPlayer(request);
        quests.markFirstWorkRequestCompleteIfActive();
    }

    public void postStarterRequests(JobID root) {
        postStarterRequests(ServerJobsRegistry.getDefaultWork(root));
    }

    private void postStarterRequests(Collection<JobID> works) {
        if (!isBoardAutoRequest()) {
            return;
        }
        List<WorkRequest> declared = new ArrayList<>();
        for (JobID work : works) {
            Ingredient request = ServerJobsRegistry.getInitialRequest(getServerLevel(), work);
            if (request == null || request.isEmpty()) {
                continue;
            }
            declared.add(Ingredients.asWorkRequest(request));
        }
        List<WorkRequest> onBoard = workHandle.getRequestedResults();
        for (WorkRequest r : StarterRequests.toPost(declared, onBoard, WorkRequest::covers)) {
            QT.FLAG_LOGGER.info("Starter request for {}: {}", works, r);
            workHandle.requestWork(r);
        }
    }

    /** The town's top missing item as a readable name, or null. */
    public @Nullable String topUnmetNeedName() {
        economics.update();
        var items = economics.getAggregatedItems(null);
        if (items == null || items.isEmpty()) {
            return null;
        }
        String topKey = null;
        int topCount = -1;
        for (ca.bradj.questown.gui.ItemEconomicsData d : items) {
            if (d.timesNeeded() > topCount) {
                topCount = d.timesNeeded();
                topKey = d.ingredientKey();
            }
        }
        if (topKey == null || topKey.isEmpty()) {
            return null;
        }
        try {
            return ca.bradj.questown.gui.Ingredients
                    .getName(ca.bradj.questown.gui.Ingredients.fromString(topKey))
                    .getString();
        } catch (Exception e) {
            QT.FLAG_LOGGER.debug("topUnmetNeedName: could not resolve need key {}", topKey, e);
            return topKey;
        }
    }

    public void setBoardAutoRequest(boolean v) {
        this.boardAutoRequest = v;
    }

    public boolean isVillagersTabSeen() {
        return villagersTabSeen;
    }

    public boolean isBopWithdrawn() {
        return bopWithdrawn;
    }

    void markBopWithdrawn() {
        if (bopWithdrawn) {
            return;
        }
        bopWithdrawn = true;
        setChanged();
    }

    /** The quests tab was opened: its open quests count as seen. */
    public void markQuestsSeen() {
        seenQuests.markSeen(quests.incompleteQuestKeys());
        setChanged();
    }

    public void markVillagersTabSeen() {
        if (villagersTabSeen) {
            return;
        }
        villagersTabSeen = true;
        setChanged();
    }

    public boolean isFirstHunterLootGuaranteeFired() {
        return firstHunterLootGuaranteeFired;
    }

    public void setFirstHunterLootGuaranteeFired(boolean v) {
        this.firstHunterLootGuaranteeFired = v;
    }

    public static final int BONUS_FOOD_COUNT = 20;

    /** Puts the free food in the first container with room. Returns the carrots placed; 0 with no container. */
    public int depositBonusCarrots() {
        ServerLevel sl = getServerLevel();
        for (ContainerTarget<MCContainer, MCTownItem> target : TownContainers.getAllContainers(this, sl)) {
            if (sl.getBlockEntity(target.getBlockPos()) instanceof Container c) {
                ItemStack stack = new ItemStack(Items.CARROT, BONUS_FOOD_COUNT);
                for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
                    if (c.getItem(i).isEmpty()) {
                        c.setItem(i, stack.copy());
                        stack.setCount(0);
                    }
                }
                c.setChanged();
                return BONUS_FOOD_COUNT;
            }
        }
        return 0;
    }

    /** The economics tab's free food: into a container if there is one, else to the player. Counted as given only once placed. */
    public boolean claimBonusFood(@Nullable ServerPlayer sp) {
        if (givenBonusFood) {
            if (sp != null) {
                Compat.sendMessage(sp, Component.translatable("message.questown.bonus_food_only_once"));
            }
            return false;
        }
        int deposited = depositBonusCarrots();
        if (deposited > 0) {
            givenBonusFood = true;
            if (sp != null) {
                Compat.sendMessage(sp, Component.translatable("message.questown.bonus_food.claimed_container"));
            }
            QT.FLAG_LOGGER.info("Bonus food claimed: {} carrots deposited into a registered container at flag {}",
                    deposited, getBlockPos());
            return true;
        }
        if (sp != null) {
            sp.getInventory().add(new ItemStack(Items.CARROT, BONUS_FOOD_COUNT));
            givenBonusFood = true;
            Compat.sendMessage(sp, Component.translatable("message.questown.bonus_food.claimed_inventory"));
            QT.FLAG_LOGGER.info("Bonus food claimed: {} carrots given to the player (no registered container yet) at flag {}",
                    BONUS_FOOD_COUNT, getBlockPos());
            return true;
        }
        QT.FLAG_LOGGER.warn("No registered container to deposit bonus food at flag {}", getBlockPos());
        return false;
    }

    public boolean getGivenBonusFood() {
        return givenBonusFood;
    }

    /** Marks the free food as given. False if it already was. */
    public boolean markBonusFoodGiven() {
        if (givenBonusFood) {
            return false;
        }
        givenBonusFood = true;
        return true;
    }

    public void toggleDebugLog(String logId) {
        logToggles.compute(logId, (k, v) -> v == null || !v);
    }
    public void setDebugLog(String logId, boolean b) {
        logToggles.put(logId, b);
    }

    @Override
    public DebugLogger getDebugLogger(
            QT.QTLogger logger,
            String logId
    ) {
        Marker marker = MarkerManager.getMarker(logId);
        Logger wrapped = logger.unwrap();
        if (isDebugLogEnabled(logId)) {
            return (m, p) -> wrapped.info(marker, m, p);
        }
        String logLevel = System.getenv("INVISIBLE_LOG_LEVEL");
        if ("trace".equalsIgnoreCase(logLevel)) {
            return (m, p) -> wrapped.trace(marker, m, p);
        }
        return (m, p) -> wrapped.debug(marker, m, p);
    }

    private boolean isDebugLogEnabled(String logId) {
        return logToggles.getOrDefault(logId, false);
    }

    public boolean isDebugLogConsuming(String logId) {
        if (isDebugLogEnabled(logId)) {
            return true;
        }
        return "trace".equalsIgnoreCase(System.getenv("INVISIBLE_LOG_LEVEL"));
    }

    /** Forgets a villager's job-block states. */
    void resetWorkStatusFor(UUID villagerUUID) {
        if (jobHandles.remove(villagerUUID) != null) {
            QT.FLAG_LOGGER.info("Cleared work spot states for {} after a job root change", villagerUUID);
            setChanged();
        }
    }

    public AbstractWorkStatusStore<BlockPos, MCHeldItem, MCRoom, ServerLevel> getRealWorkStatusHandle(UUID ownerIDOrNullForGlobal) {
        if (ownerIDOrNullForGlobal == null) {
            return jobHandle;
        }
        TownWorkStatusStore jh = jobHandles.get(ownerIDOrNullForGlobal);
        if (jh != null) {
            return jh;
        }
        jh = new TownWorkStatusStore(
                (m, p) -> this.getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.WORK_STATUS).log(m, p)
        );
        jobHandles.put(ownerIDOrNullForGlobal, jh);
        // Initialize the new store immediately so work states are available
        ServerLevel sl = getServerLevel();
        if (sl != null) {
            Collection<MCRoom> allRooms = roomsHandle.getAllRoomsIncludingMetaAndFarms();
            jh.tick(sl, allRooms, 1);
        }
        return jh;
    }
}
