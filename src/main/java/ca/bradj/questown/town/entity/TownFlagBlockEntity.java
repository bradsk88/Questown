package ca.bradj.questown.town.entity;

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
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.WorksBehaviour;
import ca.bradj.questown.jobs.declarative.BOPDepositorWork;
import ca.bradj.questown.jobs.declarative.DinerNoTableWork;
import ca.bradj.questown.jobs.declarative.DinerWork;
import ca.bradj.questown.jobs.declarative.DowntimeWork;
import ca.bradj.questown.jobs.declarative.ResterWork;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.helperchicken.ChickenRoomShellPlan;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.*;
import ca.bradj.questown.town.econ.NoMCEconomics;
import ca.bradj.questown.town.interfaces.*;
import ca.bradj.questown.town.quests.*;
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
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import ca.bradj.questown.town.rooms.DeadDoorBubbles;
import ca.bradj.questown.town.rooms.DoorTrouble;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityEvent;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
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

    /**
     * Registered doors with no room, and why (ADR-0011). Written server-side by
     * {@code TownRoomsMap} whenever the scan changes it; synced to clients via the update
     * tag so the dead-door bubble can be drawn. Live read, never latched: a door leaves the
     * set the moment its scan attaches it to a room.
     */
    private Map<BlockPos, DoorTrouble> deadDoors = ImmutableMap.of();

    public Map<BlockPos, DoorTrouble> getDeadDoors() {
        return deadDoors;
    }

    /**
     * The position range of a registered door's room, for the door-trouble
     * guidance (ADR-0011): a {@code NO_RECIPE} door needs the player to place
     * a recipe block inside the room, so the agent must know where the room is.
     * Read from the live scan (the room may be un-matched), not the matched
     * set. Empty when the door is not enclosed at all (NOT_ENCLOSED) or the
     * door is not registered.
     */
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
    public void handleUpdateTag(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.handleUpdateTag(tag, provider);
        // Unlike the town-data initializers (see the warning on load), this payload only
        // ever feeds client-side bubble rendering, so a plain field write is safe here.
        readDeadDoors(tag);
    }
    private final TownVillagerData.FallbackSelector fallbackSelector = new TownVillagerData.FallbackSelector();
    private final NoMCEconomics economics = new NoMCEconomics();
    final TownFlagTicker ticker = new TownFlagTicker();

    // Deposited Blocks of Progress. Stored as real stacks (not a count) because each deposit
    // may be blessed: the item's NBT carries the depositor's job, which a blessed BOP guarantees
    // on a root job change. FIFO: eject() gives the player the oldest deposit first.
    final NonNullList<ItemStack> bopStacks = NonNullList.create();
    boolean tutorialBopGranted = false;
    int completedProceduralBatches = 0;
    private boolean flagpoleBuilt = false;

    public ItemHandler bopItemHandler() {
        return new TownFlagBOPItemHandler(this);
    }

    boolean givenBonusFood;

    // Helper-chicken onboarding arc state (U1). Storage only — logic lives in ChickenArcController (U3+).
    boolean chickenEverSpawned = false;
    ca.bradj.questown.mobs.helperchicken.ChickenBeatState chickenBeatState =
            ca.bradj.questown.mobs.helperchicken.ChickenBeatState.WAITING_FOR_STICK;
    boolean chickenFirstGatherWorldlySeedsFired = false;
    boolean chickenArcForfeit = false;
    /**
     * Persisted: true once the first hunter has delivered a raw-meat that closes the
     * raw-food LOOT (ADR-0014's hunter equivalent). Gates
     * {@link ca.bradj.questown.jobs.declarative.FirstHunterLootGuarantee#shouldPrepend}
     * and the LOOT-extract flip hook.
     */
    boolean firstHunterLootGuaranteeFired = false;
    net.minecraft.world.level.block.Rotation chickenStructureRotation =
            net.minecraft.world.level.block.Rotation.NONE;
    boolean chickenRotationDetected = false;
    /**
     * Persisted: has the SUNSET_AND_MAP beat already had its "chicken pecks the
     * ground and spawns a map+axe chest" stage fire? Once true, the bubble
     * switches from the chest icon to the sunset texture and the chicken stops
     * being driven toward a fresh peck point.
     */
    boolean chickenSunsetChestSpawned = false;

    // Ephemeral observation flags consumed by ChickenArcController (U4). These are
    // intentionally NOT persisted — they fire once during a single server session
    // and are cleared by the controller after driving the matching transition.
    // A save/quit mid-observation resets them, which is acceptable: the player
    // can always reproduce the trigger (sleep again, open the UI again).
    private transient boolean chickenObservedSleepSinceSunset = false;
    private transient boolean chickenObservedVillagerUiOpen = false;
    private transient boolean chickenObservedFlagUiOpen = false;
    private transient boolean chickenObservedSeedsGiven = false;


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

    /**
     * The root job ids the deposited BOPs were blessed with, in deposit (FIFO) order. A plain
     * (unblessed) BOP contributes "". Feeds the flag's BOP menu "Earned by: ..." line, which is
     * the player's answer to "why does the town have BOPs?" (day-1 playtest: the deposit notice
     * announced the amount but not the cause).
     */
    public ImmutableList<String> depositedBopRootJobs() {
        return bopStacks.stream()
                .map(s -> BlockOfProgress.getBlessedJob(s)
                        .map(j -> j.rootId())
                        .orElse(""))
                .collect(ImmutableList.toImmutableList());
    }

    public enum JobChangeResult {
        REQUESTED, NO_BOP, NO_VILLAGER
    }

    /**
     * Dev-only: the change-job-tab job change a player performs by spending a
     * Block of Progress on the villager's change job tab (JobRootChangeMessage),
     * driven from the console. Removes the oldest deposited BOP from the flag's
     * store (the order the player collects them in) - the spend the player makes
     * in the tab - and schedules the root change through the same handle call
     * the message handler uses, overnight (not instant). A blessed BOP's
     * guaranteed root is carried the way the handler carries it. The new root is
     * chosen by the usual rules (random unless the spent BOP was blessed); this
     * does not pick a job, it only performs the player's spend-and-request.
     */
    public JobChangeResult spendBopToRequestJobChange(UUID villagerUUID) {
        boolean inTown = getVillagerHandle().entities().stream()
                .anyMatch(e -> e.getUUID().equals(villagerUUID));
        if (!inTown) {
            return JobChangeResult.NO_VILLAGER;
        }
        if (bopStacks.isEmpty()) {
            return JobChangeResult.NO_BOP;
        }
        net.minecraft.world.item.ItemStack consumed = bopStacks.remove(0);
        setChanged();
        syncBopFull();
        getVillagerHandle().scheduleJobRootChange(
                villagerUUID, false,
                ca.bradj.questown.blocks.BlockOfProgress.getBlessedJob(consumed)
        );
        return JobChangeResult.REQUESTED;
    }

    /**
     * Dev/test only: fill the flag's deposited BOPs directly, mirroring the tutorial
     * {@code grantBop}. Lets a scenario pre-fill the flag (e.g. to 63, one below the 64-slot cap)
     * without having to drive 63 level-ups through the real deposit path. Grants plain
     * (unblessed) BOPs.
     */
    public void grantBlockOfProgressForTest(int amount) {
        for (int i = 0; i < amount; i++) {
            bopStacks.add(new ItemStack(ItemsInit.BLOCK_OF_PROGRESS.get()));
        }
        setChanged();
        syncBopFull();
    }

    public boolean isFlagpoleBuilt() {
        return flagpoleBuilt;
    }

    public void setFlagpoleBuilt(boolean built) {
        this.flagpoleBuilt = built;
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
        // Gate chicken-arc on isInitialized: until the load queue drains, the
        // arc fields (chickenEverSpawned, chickenRotationDetected, ...) still
        // hold their default values. Running detector/spawner here pre-init
        // would re-detect rotation and writeTownData() the defaults back over
        // the saved persistent tag — losing chickenEverSpawned=true and
        // re-spawning a fresh helper chicken on every world load.
        if (level instanceof ServerLevel && e.isInitialized()) {
            ca.bradj.questown.town.HelperChickenRotationDetector.detectIfNeeded(e);
            ca.bradj.questown.town.HelperChickenSpawnController.tick(e);
            ca.bradj.questown.mobs.helperchicken.ChickenArcController.tick(e);
        }
    }


    void morningTick(Long newTime) {
        for (MCReward r : this.morningRewards.popChildren()) {
            this.asapRewards.push(r);
        }
        this.setChanged();
        villagerHandle.handleMorning(newTime);
        roomsHandle.handleMorning();
        CompoundTag tag = Compat.getBlockStoredTagData(this);
        tag.putLong(NBT_TIME_WARP_REFERENCE_TICK, newTime);
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
    protected void saveAdditional(@NotNull CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        //////////////////////////
        //       WARNING        //
        //////////////////////////
        // Don't use this. Data gets stored on the tick. This function
        // NEVER works the way it claims to (the data saved here is NOT
        // present on the tag that gets passed to load())
        // Use writeTownData
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);

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

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        if (this.isInitialized()) {
            this.writeTownData(tag);
        }
        writeDeadDoors(tag);
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
            // Client side: make this flag's synced dead-door set findable by the bubble
            // focus. Guarded so the client-only class is never touched on a server.
            if (level != null && level.isClientSide()) {
                DeadDoorBubbles.register(this);
            }
            return;
        }
        initializeFreshFlag(false);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide()) {
            DeadDoorBubbles.unregister(this);
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
        Stream<VisitorMobEntity> relevantWorkers = villagerHandle.stream().filter(Objects::nonNull).filter(e -> {
            for (WorkRequest r : workHandle.getRequestedResults()) {
                if (ServerJobsRegistry.canSatisfy(td, e.getJobId(), r.asIngredient())) {
                    // A downtime townie has no server status; null counts as not busy.
                    if (IStatus.isBusy(e.getStatusForServer())) {
                        return false;
                    }
                }
            }
            return true;
        });
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

    public boolean hasVillagerArrivingInMorning() {
        return morningRewards.hasPendingSpawnVisitor();
    }

    /**
     * Sends a titled tutorial toast to every online player (the chicken-arc
     * day-one reward's "your camp is ready" announcement). Public so the
     * rewards package can reach it without package-private access to
     * {@code TownMessages}.
     */
    public void broadcastTutorialToast(String titleKey, String descriptionKey) {
        this.messages.broadcastTutorialToast(titleKey, descriptionKey);
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
        NeoForge.EVENT_BUS.addListener((EntityEvent.EnteringSection event) -> {
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

    void setUpQuestsForNewlyPlacedFlag() {
        TownQuests.setUpQuestsForNewlyPlacedFlag(this, quests);
        this.isInitializedQuests = true;
        setChanged();
    }

    public ImmutableList<Quest<ResourceLocation, MCRoom>> getAllQuests() {
        return quests.getAll();
    }

    @Override
    public void roomRecipeCreated(
            MCRoom room,
            RoomRecipeMatch<MCRoom> match
    ) {
        ServerLevel l = getServerLevel();
        world.swapBlocks(l, match);
        messages.roomRecipeCreated(room, getTopMatch(this::recipesFromLevel, match));
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
        // A sign converts to a job board only in the created/changed events, not in the
        // quest-completion path; run the swap on changed too so a sign in a room whose
        // top match is another recipe (e.g. a store room) still converts when it joins the
        // match. swapBlocks is idempotent (it only swaps sign blocks, not boards).
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
        Consumer<JobID> changeJob = id -> villagerHandle.changeJobForVillager(ownerVUID, id, false);

        VisitorMobEntity villager = villagerHandle.getEntity(ownerVUID);
        if (villager == null) {
            return true;
        }
        float damagePercent = villagerHandle.getDamagePercent(ownerVUID);
        if (damagePercent > 0) {
            // The more damaged they are, the more likely they are to rest.
            if (level.getRandom().nextFloat() < damagePercent) {
                changeJob.accept(ResterWork.getIdForRoot(currentJob.rootId()));
                return true;
            }
        }

        if (villagerHandle.hasBlockOfProgress(ownerVUID)) {
            // Bless the BOP with the job that earned it: a "gold" BOP guarantees that root job
            // when spent on a job change (see BlockOfProgress.bless / getBlessedJob).
            ItemStack bopStack = new ItemStack(ItemsInit.BLOCK_OF_PROGRESS.get());
            BlockOfProgress.bless(bopStack, currentJob);
            MCHeldItem bop = MCHeldItem.fromTown(bopStack);
            villager.tryGiveItem(bop, InventoryFullStrategy.REMOVE_FROM_WORLD);
            changeJob.accept(BOPDepositorWork.getIdForRoot(currentJob.rootId()));
            return true;
        }

        if (villagerHandle.isReadyForDowntime(ownerVUID, tick) && !DowntimeWork.matches(currentJob)) {
            changeJob.accept(DowntimeWork.getIdForRoot(currentJob.rootId()));
            return true;
        }

        // Deposit carried work output before the townie dines: depositing is what counts the
        // output as produced (and completes the onboarding mutton quest). If the town has no
        // storage, this is a deliberate blocker state.
        if (depositCarriedWorkOutput(villager)) {
            return true;
        }

        // A starving townie that has not just given up on dining enters the diner chain here,
        // after its work output has been deposited. (Only reached for jobs that cannot
        // instantly switch to eating; see addHungryListener.)
        if (villagerHandle.isStarving(ownerVUID) && !villagerHandle.gaveUpDiningRecently(ownerVUID)) {
            String rid = currentJob.rootId();
            ResourceLocation diningRoom = DinerWork.asWork(rid).baseRoom;
            boolean hasDiningRoom = !roomsHandle.getRoomsMatching(diningRoom).isEmpty();
            changeJob.accept(hasDiningRoom
                    ? DinerWork.getIdForRoot(rid)
                    : DinerNoTableWork.getIdForRoot(rid));
            return true;
        }

        ImmutableList<WorkRequest> requestedResults = workHandle.getRequestedResults();
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
            // The townie is being adopted into a **real work job** (not a meta-job like
            // DowntimeWork — those were handled by the earlier cascade gates). This is
            // their "I've earned a rest by working" signal — see DowntimeState (ca.bradj.questown.town.entity)
            // and STATE.md § Mission. markWorked records the last-work tick, so a rest becomes so a rest becomes
            // available next once another NOON/downtime/sleep event anchors a "last rest"
            // tick and it is `maxTicksBeforeDowntime` ticks old.
            villagerHandle.markWorked(VillagerUUID.from(ownerUUID), tick);
            changeJob.accept(work);
            return true;
        }

        JobID fallback = fallbackSelector.tryFallback(
                1, villager.getJobId(), canFit, canAlwaysStart, requestedResults, td,
                possibleWork.getFor(villager.getJobId()),
                jobs -> Compat.shuffle(jobs.iterator(), getServerLevel())
        );
        if (fallback != null) {
            changeJob.accept(fallback);
            return true;
        }

        // Escape hook: the board has no work and no fallback for this townie (the cycler and the
        // fallback selector both found nothing), so they would otherwise idle without a job. If they
        // have rested long enough, rest instead of waiting forever — rule (b)'s "worked since the
        // last rest" gate does not apply when there is truly nothing to do. Guarded on
        // !DowntimeWork so a townie already resting on DowntimeWork is not re-adopted onto it.
        if (villagerHandle.isReadyForDowntimeIfIdle(ownerVUID, tick) && !DowntimeWork.matches(currentJob)) {
            changeJob.accept(DowntimeWork.getIdForRoot(currentJob.rootId()));
            return true;
        }
        return false;
    }

    /**
     * Deposit the townie's carried work output into the nearest chest so it counts as produced
     * (and completes the onboarding mutton quest) before the townie dines. BOPs are tracked
     * separately (the {@code hasBlockOfProgress} flag) and are not deposited here.
     *
     * @return true if the board should hold the townie here — there is no chest in the town to
     * store the work output, a deliberate blocker state. Returns false when there was nothing
     * to carry or the output was deposited, so the board continues.
     */
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
            // No chest in the town to store work output: a deliberate blocker state.
            return true;
        }
        int deposited = 0;
        for (MCTownItem item : carried) {
            ContainerTarget<MCContainer, MCTownItem> updated = chest.withItemAdded(item);
            if (updated == null) {
                break; // chest full; the rest stays carried until space frees up
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

    // Rate-limits the wander-failure warning so a stuck villager logs once per
    // window instead of every tick. Keyed by the avoiding position (a proxy for
    // the stuck villager), value is the game tick when the window opened.
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

    /**
     * Adopt a relocated town's identity (ADR-0009, #199). A freshly placed flag gets a random UUID,
     * but relocation copies a dormant town's data onto a new flag, so it must also carry the original
     * UUID — {@link TownFlags} is keyed by it, and per-villager/knowledge data is keyed against it.
     * Must be called before the init queue drains so registration uses the carried identity.
     */
    void adoptRelocatedIdentity(UUID carried) {
        this.uuid = carried;
    }

    public void registerWelcomeMat(BlockPos welcomeMatBlock) {
        pois.registerWelcomeMat(welcomeMatBlock);
        roomsHandle.registerBlockAsRoom(SpecialQuests.TOWN_GATE, welcomeMatBlock);
        // Fixture completion path for the day-one gate quest (.scratch/mat-gated-arrival,
        // decision 1): a welcome mat registered with the flag completes TOWN_GATE. The
        // structure path (townGateFound) and room-recipe path still complete it too; this
        // one is the canonical one and fires the moment the mat registers, so the
        // gate batch's reward (first villager in the morning + the basics batch) hands
        // over without needing a flanked-gate structure or a recipe-matched room.
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

    /**
     * Place a welcome mat block at {@code pos} and register it with this flag, the
     * way a player placing the item does (the block's {@code onUse} registers it).
     * Used where the placement cannot go through the item-use path (e.g. a console
     * command that has no player hand to place it with). The placed block carries
     * the flag-linking NBT so it is associated with this flag.
     */
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

    /**
     * Place a job-board block at {@code pos} and register it with this flag, the way
     * a player placing the item does, but driven from the console (no player hand to
     * place it with). Mirrors {@link #placeWelcomeMat}. Placing the JOB_BOARD_BLOCK
     * directly is equivalent to the sign to job-board conversion
     * (isSignConvertedToJobBoard checks for the job-board block itself), and the
     * explicit register re-runs the placement side effect setBlockAndUpdate bypasses.
     */
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

    /**
     * Lay a room shell around a door at {@code pos}, the way the helper chicken does in its
     * room-build beat, but driven from the console (no chicken to lay it). A room quest needs
     * its own room, and the only hand that builds a room is the chicken's beat, which a held run
     * with no live player cannot trigger; this is the agent's standing-in for that beat. The
     * layout is the same pure plan the chicken uses ({@link ca.bradj.questown.mobs.helperchicken
     * .ChickenRoomShellPlan}), so the perimeter it lays is the one RoomRecipes' wall walk accepts
     * (a closed box, not a plus). The door is registered first so the room scan attaches the shell
     * to it, then the perimeter is laid at feet and head ({@code WallDetection.IsWall} reads a
     * wall when solid at both levels), leaving the interior open so a villager can stand in it.
     */
    // Dev-only: the empty interior cells of this flag's registered rooms, the way a player
    // would see a room's open floor. A block placed at one of these cells is inside a room,
    // so the room re-detection picks it up as a contained block (a chest here becomes a
    // registered container). Returns air cells only, in a stable order, so an agent can index
    // one without guessing a coordinate. No permission gate; a held run has no player to look
    // into a room, so this is the agent's eyes for "where is the floor".
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

    // Dev-only: place a block at the indexed interior cell, the way a player placing a block
    // on the floor would. Thin wrapper over {@link #interiorCells()} + a vanilla setBlock;
    // the agent picks the spot by index (a position it could not see) and the block id.
    // Returns the real cell the block landed on, so a caller that places a chest or board
    // can record the exact position without re-deriving it (interiorCells() is recomputed
    // per call and its indices can shift between the two). Null if nothing was placed.
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
        // A door that is already registered already has a room built around it, so building
        // around it again would lay a second shell over the first - two rooms sharing a wall
        // with no way out. A quest that needs its own room needs a new door, not this one.
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

        // Place a door if none exists. Without a door block the shell plan treats the door
        // cell as an open perimeter position and fills it with cobblestone, leaving solid walls
        // with no way in.
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

        // Pre-flight: do not lay a shell that can not close. The feasibility check treats the
        // door cell as open so it does not reject an already-placed door; every other cell is
        // checked the normal way.
        java.util.function.Predicate<BlockPos> isOccupied = p -> !level.getBlockState(p).isAir();
        java.util.function.Predicate<BlockPos> isOccupiedIgnoringDoorCell = p -> {
            if (p.equals(doorPos) || p.equals(doorPos.above())) {
                return false;
            }
            return isOccupied.test(p);
        };
        ChickenRoomShellPlan.Outcome feasible = ChickenRoomShellPlan.feasibility(
                doorPos, baseY, isOccupiedIgnoringDoorCell
        );
        if (!feasible.clear()) {
            QT.FLAG_LOGGER.warn(
                    "[build_room] refusing to build a room at {}: {} - pick a clear door spot",
                    doorPos, feasible.reason()
            );
            return;
        }
        ChickenRoomShellPlan.DoorFrame frame = ChickenRoomShellPlan.frameFor(doorPos, baseY, isOccupied);
        ChickenRoomShellPlan.Plan plan = ChickenRoomShellPlan.plan(frame, isOccupied, java.util.Set.of());
        int laid = 0;
        for (ChickenRoomShellPlan.BlockStep step : plan.steps()) {
            if (level.getBlockState(step.pos()).isAir()) {
                level.setBlockAndUpdate(step.pos(), net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState());
                laid++;
            }
        }
        // The shell is in place; now register the door so the scan detects the room. Registering
        // after the shell (not before) is what the chicken's goal effectively does - it builds the
        // shell around an already-registered door and re-reads it each tick - and it avoids
        // registering a door a scan has already finished over (which would leave it undetected
        // until the next scan cycle). The scan is resumable, so the room appears on a later tick.
        getRoomHandle().registerDoor(doorPos);
        QT.FLAG_LOGGER.info(
                "[build_room] laid {} shell blocks around door {} for flag {} (door registered; the room "
                        + "is detected by the scan on a later tick)",
                laid, doorPos, getBlockPos()
        );
    }

    /**
     * Build a complete 5x5 rectangular room (3x3 interior, single south door,
     * plank walls, dirt floor, flat roof) around a clear door spot, register the
     * door so the room scan detects it, and place the recipe block inside.
     * <p>
     * This is the reliable room-build for the self-sufficiency watchtower:
     * the seed's {@code buildOneBuilding} shape (which the wall walk closes
     * cleanly, unlike {@link #buildRoom}'s chicken shell at some footprints).
     * The door spot must be on flat ground with air above and below; the method
     * computes the building's southwest corner from the door position (the door
     * sits at the south wall, two cells in from the west corner).
     *
     * @param doorPos      the clear ground-level cell for the door
     * @param recipeBlocks the recipe's blocks to place inside the room, one per
     *                     ingredient slot (a multi-ingredient recipe like
     *                     bedroom = light + 2 beds lists every block it needs)
     * @return the door position if the room was built, or null if the spot was
     *         not clear
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

        // Place the recipe blocks inside the 3x3 interior footprint (dx/dz 1..3 from the
        // corner), on up to two stacked levels (floor and floor+1) — the room matcher
        // (RecipeDetection) reads both y and y+1 of every room cell. The room matcher
        // counts each cell, so each placed block contributes one entry to the room's
        // block multiset. Beds reserve two adjacent cells each (foot + head half), so a
        // recipe heavy in beds (inn = light + 12 beds + sign needs 26 cells vs the 18
        // interior cells) is only partially placeable; the surplus beds are skipped
        // with a warning rather than placed as lone feet.
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
        for (net.minecraft.world.level.block.Block block : recipeBlocks) {
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
            // A bed is a two-block item: the default state is the FOOT half only, and vanilla
            // places the HEAD at foot.relative(FACING) (BedBlock.setPlacedBy) — the same
            // lone-half bug placeDoor's javadoc documents for doors. Find a free cell whose
            // forward neighbour is free too and reserve both; a lone foot never passes the
            // bed lookup (the isHead filter), so when no pair fits (e.g. an inn's 12 beds
            // vs 18 cells) the bed is left unplaced rather than placed as a useless half.
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
                corner.toShortString(), doorPos.toShortString(), placed, recipeBlocks.size(), recipeBlocks
        );
        return doorPos;
    }

    /**
     * Place a full (both-halves) oak door at {@code pos}, the way a player's door item
     * does, but driven from the console (no player hand to place it with). A dev/agent
     * placing a door with vanilla {@code setblock} easily places only the LOWER half
     * (leaving a one-block gap where the UPPER half belongs, which then gets roofed over
     * and breaks the door); this places both halves so the door is whole. Registration
     * for room detection is a separate step ({@code _qtdev registerroom}), mirroring the
     * player flow of placing a door then right-clicking it with the town wand.
     */
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

    /**
     * Test seam: publish the live town state to the tile now, the way the ticker does when the town
     * changes. {@link #warpTime} advances the state stored on the tile, <em>not</em> the live
     * handles, so a test that mutates a villager and then warps must publish first or the warp runs
     * against stale data and silently measures the wrong thing.
     */
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

    /**
     * Begin the town-shutdown ritual (ADR-0009): recall every townie to the flag, absorb them, and
     * flip the flag dormant once the roster is in and the minimum-duration floor has elapsed. Only
     * an {@link FlagPhase#ACTIVE} flag (realtime, player-present, not already shutting down) is
     * eligible. Returns false if the flag is not currently eligible.
     */
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

    /**
     * Abort an in-progress (not yet completed) shutdown: re-spawn the townies absorbed so far and
     * return the flag to {@link FlagPhase#ACTIVE}. Loss-proof — the town's data never left the flag.
     */
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

    /**
     * Mark that a relocation deed is ready above this (dormant) flag. The flag renders it spinning and
     * hands it over on interaction ({@link #collectDeed}) — chosen over dropping a {@link ItemEntity}
     * so it can't be culled or picked up by the wrong entity before the player sees it. Stored as a
     * blockstate property so it auto-syncs to the client (the flag has no client-side BE ticker).
     */
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

    /**
     * Hand the waiting relocation deed to the player (mirrors {@code TownFlagBOPItemHandler.eject}):
     * straight into the inventory, or dropped at the flag as a fallback if the inventory is full.
     */
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

    /**
     * Re-issue a relocation deed for this dormant flag (loss-protection). Only valid while DORMANT.
     */
    public boolean reissueDeed() {
        if (!isDormant() || getServerLevel() == null) {
            return false;
        }
        makeDeedAvailable();
        return true;
    }

    /**
     * Wake this dormant town in place — re-spawn its roster and return to ACTIVE. Only valid while
     * DORMANT; the other loss-protection path beside re-issuing the deed (ADR-0009).
     */
    public boolean wakeInPlace() {
        ServerLevel level = getServerLevel();
        if (level == null || !isDormant()) {
            return false;
        }
        // A woken town is live again, so any deed still waiting on the flag would hand out a reference
        // to a town that is no longer dormant (and would shadow the flag menu on every interaction).
        setDeedAvailable(false);
        shutdownController.wake(level, getBlockPos(), this);
        return true;
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
        // Day-1 playtest: the villagers tab is a dead-end with zero townies (its copy
        // even reads "one more in the morning" for the first arrival). Gate it on first
        // arrival. Economics is gated on the SAME condition: the evening playtest (day 2,
        // pre-arrival) showed the procedural-batch gate reveals it too early - the player's
        // bar is "not until we've met a villager", so economics and the villagers tab reveal
        // together.
        boolean showVillagers = !getVillagers().isEmpty();
        boolean showEcon = showVillagers;
        return new FlagTabsEmbedding.FlagInfo(
                getBlockPos(),
                showVillagers,
                true,
                showEcon,
                getBopCount() > 0,
                hasIncomplete,
                isBopFull()
        );
    }

    /**
     * Whether the flag's deposited BOPs have reached the cap. Read from the client-synced
     * {@link TownFlagBlock#BOP_FULL} blockstate (mirrors {@link #isDeedAvailable()}) so the client
     * render and the server interaction see the same value.
     */
    public boolean isBopFull() {
        BlockState state = getBlockState();
        return state.hasProperty(TownFlagBlock.BOP_FULL) && state.getValue(TownFlagBlock.BOP_FULL);
    }

    /**
     * Reconcile the {@link TownFlagBlock#BOP_FULL} blockstate with the deposited stacks. Called at
     * every bop-stacks-change site (deposit/collect, tutorial grant, test pre-fill) and after
     * load, so the red-BOP render and the menu warning track the count. The blockstate persists in
     * the block state, so it can be stale against the freshly-loaded stacks; this reconciles it.
     */
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

    public boolean getChickenEverSpawned() {
        return chickenEverSpawned;
    }

    public void setChickenEverSpawned(boolean v) {
        this.chickenEverSpawned = v;
    }

    public ca.bradj.questown.mobs.helperchicken.ChickenBeatState getChickenBeatState() {
        return chickenBeatState;
    }

    public void setChickenBeatState(ca.bradj.questown.mobs.helperchicken.ChickenBeatState v) {
        this.chickenBeatState = v;
    }

    public boolean getChickenFirstGatherWorldlySeedsFired() {
        return chickenFirstGatherWorldlySeedsFired;
    }

    public void setChickenFirstGatherWorldlySeedsFired(boolean v) {
        this.chickenFirstGatherWorldlySeedsFired = v;
    }

    public boolean isFirstHunterLootGuaranteeFired() {
        return firstHunterLootGuaranteeFired;
    }

    public void setFirstHunterLootGuaranteeFired(boolean v) {
        this.firstHunterLootGuaranteeFired = v;
    }

    public boolean getChickenArcForfeit() {
        return chickenArcForfeit;
    }

    public void setChickenArcForfeit(boolean v) {
        this.chickenArcForfeit = v;
    }

    public net.minecraft.world.level.block.Rotation getChickenStructureRotation() {
        return chickenStructureRotation;
    }

    public void setChickenStructureRotation(net.minecraft.world.level.block.Rotation v) {
        this.chickenStructureRotation = v;
    }

    public boolean getChickenRotationDetected() {
        return chickenRotationDetected;
    }

    public void setChickenRotationDetected(boolean v) {
        this.chickenRotationDetected = v;
    }

    public boolean getChickenSunsetChestSpawned() {
        return chickenSunsetChestSpawned;
    }

    public void setChickenSunsetChestSpawned(boolean v) {
        this.chickenSunsetChestSpawned = v;
    }

    public boolean getChickenObservedSleepSinceSunset() {
        return chickenObservedSleepSinceSunset;
    }

    public void setChickenObservedSleepSinceSunset(boolean v) {
        this.chickenObservedSleepSinceSunset = v;
    }

    public boolean getChickenObservedVillagerUiOpen() {
        return chickenObservedVillagerUiOpen;
    }

    public void setChickenObservedVillagerUiOpen(boolean v) {
        this.chickenObservedVillagerUiOpen = v;
    }

    public boolean getChickenObservedFlagUiOpen() {
        return chickenObservedFlagUiOpen;
    }

    public void setChickenObservedFlagUiOpen(boolean v) {
        this.chickenObservedFlagUiOpen = v;
    }

    public boolean getChickenObservedSeedsGiven() {
        return chickenObservedSeedsGiven;
    }

    public void setChickenObservedSeedsGiven(boolean v) {
        this.chickenObservedSeedsGiven = v;
    }

    public boolean giveBonusFood(ServerPlayer sp) {
        if (givenBonusFood) {
            Compat.sendMessage(sp, Component.translatable("message.questown.bonus_food_only_once"));
            return false;
        }
        BlockPos pos = getBlockPos();
        ItemStack stack = new ItemStack(Items.CARROT, 10);
        level.addFreshEntity(new ItemEntity(level, pos.getX(), pos.getY(), pos.getZ(), stack));
        givenBonusFood = true;
        return true;
    }

    public boolean getGivenBonusFood() {
        return givenBonusFood;
    }

    /**
     * Set the once-only bonus-food bit without dropping anything at the flag, so
     * the {@code free_food} command can put the food in a registered container
     * (where a villager can actually eat it) instead of a carrot ItemEntity at the
     * flag that feeds no one. Returns false if already given, mirroring
     * {@link #giveBonusFood(ServerPlayer)}'s once-only rule.
     */
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

    /** True if a log for logId would actually be written anywhere (not silently discarded). */
    public boolean isDebugLogConsuming(String logId) {
        if (isDebugLogEnabled(logId)) {
            return true;
        }
        return "trace".equalsIgnoreCase(System.getenv("INVISIBLE_LOG_LEVEL"));
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
