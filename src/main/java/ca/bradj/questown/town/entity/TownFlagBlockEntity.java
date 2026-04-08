package ca.bradj.questown.town.entity;

import ca.bradj.questown.InventoryFullStrategy;
import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.TownFlagSubBlocks;
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
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.WorksBehaviour;
import ca.bradj.questown.jobs.declarative.BOPDepositorWork;
import ca.bradj.questown.jobs.declarative.DowntimeWork;
import ca.bradj.questown.jobs.declarative.ResterWork;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.items.IItemHandler;
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
    private final TownVillagerData.FallbackSelector fallbackSelector = new TownVillagerData.FallbackSelector();
    private final NoMCEconomics economics = new NoMCEconomics();
    final TownFlagTicker ticker = new TownFlagTicker();

    int bopCount = 0;
    boolean tutorialBopGranted = false;
    int completedProceduralBatches = 0;
    private boolean flagpoleBuilt = false;

    private final LazyOptional<IItemHandler> itemHandler = LazyOptional.of(() -> new TownFlagBOPItemHandler(this));
    boolean givenBonusFood;

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap) {
        if (!ForgeCapabilities.ITEM_HANDLER.equals(cap)) {
            return super.getCapability(cap);
        }
        return this.itemHandler.cast();
    }

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
        return bopCount;
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
    private final UUID uuid = UUID.randomUUID();
    final TownFlagState state = new TownFlagState(this);
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
    public CompoundTag serializeNBT() {
        return super.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        super.deserializeNBT(nbt);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
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
    public void load(@NotNull CompoundTag tag) {
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

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        if (this.isInitialized()) {
            this.writeTownData(tag);
        }
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
            return;
        }
        initializeFreshFlag(false);
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
                    if (e.getStatusForServer().isBusy()) {
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
            MCHeldItem bop = MCHeldItem.fromTown(ItemsInit.BLOCK_OF_PROGRESS.get());
            villager.tryGiveItem(bop, InventoryFullStrategy.REMOVE_FROM_WORLD);
            changeJob.accept(BOPDepositorWork.getIdForRoot(currentJob.rootId()));
            return true;
        }

        if (villagerHandle.isReadyForDowntime(ownerVUID, tick) && !DowntimeWork.matches(currentJob)) {
            changeJob.accept(DowntimeWork.getIdForRoot(currentJob.rootId()));
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

        if (sl.isNight()) {
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
                }, (p, r) -> Positions.ToBlock(p, r.yCoord)
        );
        if (townPos == null) {
            QT.FLAG_LOGGER.warn("Could not find wander target avoiding {}", avoiding);
            return townPos;
        }
        return townPos;
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

    public void registerWelcomeMat(BlockPos welcomeMatBlock) {
        pois.registerWelcomeMat(welcomeMatBlock);
        roomsHandle.registerBlockAsRoom(SpecialQuests.TOWN_GATE, welcomeMatBlock);
        setChanged();
        AdvancementsInit.ROOM_TRIGGER.triggerForNearestPlayer(
                getServerLevel(),
                RoomTrigger.Triggers.FirstWelcomeMat,
                welcomeMatBlock
        );
    }

    public List<BlockPos> getWelcomeMats() {
        return pois.getWelcomeMats();
    }


    public void registerJobsBoard(BlockPos matPos) {
        this.workHandle.registerJobBoard(matPos);
        this.setChanged();
    }

    public void openJobsMenu(
            ServerPlayer sender,
            boolean skipStraightToAdd
    ) {
        workHandle.openMenuRequested(sender, skipStraightToAdd);
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
        return FlagTabsEmbedding.FlagInfo.withQuestNotification(getBlockPos(), bopCount > 0, hasIncomplete);
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
