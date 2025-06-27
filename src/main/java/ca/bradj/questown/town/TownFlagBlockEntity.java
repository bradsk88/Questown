package ca.bradj.questown.town;

import ca.bradj.questown.InventoryFullStrategy;
import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.TownFlagSubBlocks;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.advancements.ApproachTownTrigger;
import ca.bradj.questown.core.advancements.RoomTrigger;
import ca.bradj.questown.core.advancements.VisitorTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.gui.FlagTabsEmbedding;
import ca.bradj.questown.integration.minecraft.*;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.Signals;
import ca.bradj.questown.jobs.WorksBehaviour;
import ca.bradj.questown.jobs.declarative.BOPDepositorWork;
import ca.bradj.questown.jobs.declarative.ResterWork;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
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
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static ca.bradj.questown.roomrecipes.Matches.getTopMatch;
import static ca.bradj.questown.roomrecipes.Matches.runForTopMatch;
import static ca.bradj.questown.town.TownFlagState.NBT_TIME_WARP_REFERENCE_TICK;
import static ca.bradj.questown.town.TownFlagState.NBT_TOWN_STATE;

public class TownFlagBlockEntity extends BlockEntity implements TownInterface,
        ActiveRecipes.ChangeListener<MCRoom, RoomRecipeMatch<MCRoom>>, TownPois.Listener {

    private final TownKnownBiomes biomes = new TownKnownBiomes();
    TownHealingHandle healing = new TownHealingHandle();
    private final TownFlagInitialization initializer;
    private int preferredBuffer;
    private boolean isMorning = false;
    private final NoMCEconomics economics = new NoMCEconomics();
    private int ticksWithoutQuests;

    int bopCount = 0;

    private LazyOptional<IItemHandler> itemHandler = LazyOptional.of(() -> new IItemHandler() {

        @Override
        public int getSlots() {
            return 64;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int i) {
            if (i >= bopCount) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(ItemsInit.BLOCK_OF_PROGRESS.get());
        }

        @Override
        public @NotNull ItemStack insertItem(
                int i,
                @NotNull ItemStack itemStack,
                boolean simulate
        ) {
            if (!isItemValid(i, itemStack)) {
                return itemStack;
            }
            itemStack.shrink(1);
            if (!simulate) {
                bopCount++;
                QT.FLAG_LOGGER.debug("Flag now contains {} BOPs", bopCount);
            }
            return itemStack;
        }

        @Override
        public @NotNull ItemStack extractItem(
                int i,
                int i1,
                boolean b
        ) {
            // Extraction is not currently supported
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int i) {
            return 1;
        }

        @Override
        public boolean isItemValid(
                int i,
                @NotNull ItemStack itemStack
        ) {
            if (i >= getSlots()) {
                return false;
            }
            if (i < bopCount) {
                return false;
            }
            return itemStack.is(ItemsInit.BLOCK_OF_PROGRESS.get());
        }
    });

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap) {
        if (!Compat.getItemHandlerCapability().equals(cap)) {
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

    private static Map<String, InitPair> initPairs;

    public static void staticInitialize() {
        initPairs = TownFlagTileData.initialize();
    }

    public static final String ID = "flag_base_block_entity";

    private boolean stopped = true;
    final TownQuests quests = new TownQuests();
    final TownFlagSubBlocks subBlocks = new TownFlagSubBlocks(getBlockPos());
    final TownPois pois = new TownPois(subBlocks);
    final MCMorningRewards morningRewards = new MCMorningRewards(this);
    private final MCAsapRewards asapRewards = new MCAsapRewards();
    private final UUID uuid = UUID.randomUUID();
    private final TownFlagState state = new TownFlagState(this);
    public long advancedTimeOnTick = -1;
    boolean isInitializedQuests = false;
    private boolean everScanned = false;
    private boolean changed = false;

    // Farmer specific stuff
    private final ArrayList<UUID> assignedFarmers = new ArrayList<>();

    private final ArrayList<Integer> times = new ArrayList<>();

    final TownWorkStatusStore jobHandle = new TownWorkStatusStore();
    private final Map<UUID, TownWorkStatusStore> jobHandles = new HashMap<>();

    final TownWorkHandle workHandle = new TownWorkHandle(subBlocks, getBlockPos());
    private final Stack<Long> mornings = new Stack<>();
    private final LinkedBlockingQueue<Function<TownFlagBlockEntity, Boolean>> initializers = new LinkedBlockingQueue<>();

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

    private @Nullable Supplier<Boolean> debugTask;
    private boolean debugMode;

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
        if (!(level instanceof ServerLevel sl)) {
            return;
        }

        if (!e.initializers.isEmpty()) {
            QT.FLAG_LOGGER.info("Running initializer ({} left) @ {}", e.initializers.size() - 1, e.getBlockPos());
            Function<TownFlagBlockEntity, Boolean> initr = e.initializers.remove();
            if (!initr.apply(e)) {
                e.initializers.add(initr);
            }
            return;
        }

        e.villagerHandle.entities().stream().findFirst().ifPresent(v -> {
            if (!isMissingCompletableQuests(e)) {
                e.ticksWithoutQuests = 0;
                return;
            }
            if (e.ticksWithoutQuests < 500) {
                e.ticksWithoutQuests++;
                return;
            }

            QT.FLAG_LOGGER.warn("No quests found. This is a bug. Adding a batch for {}", v.getUUID());
            e.questsHandle.addBatchOfRandomQuestsForVisitor(e.uuid);
            e.setChanged();
            e.ticksWithoutQuests = 0;
        });

        Player nearestPlayer = level.getNearestPlayer(
                blockEntityPos.getX(),
                blockEntityPos.getY(),
                blockEntityPos.getZ(),
                -1,
                null
        );
        if (nearestPlayer == null) {
            return;
        }
        double distToPlayer = nearestPlayer.blockPosition().distSqr(e.worldPosition);
        if (distToPlayer > Config.TOWN_TICK_RADIUS.get()) {
            if (!e.stopped) {
                QT.FLAG_LOGGER.info(
                        "Town flag at {} stopped ticking because closest player is further away than limit {}: {}",
                        blockEntityPos,
                        Config.TOWN_TICK_RADIUS.get(),
                        distToPlayer
                );
                e.subBlocks.parentUnloaded();
            }
            e.stopped = true;
            return;
        }

        e.stopped = false;

        long start = System.currentTimeMillis();

        // Must tick sub-blocks even with debug mode enabled,
        // because non-ticked sub-blocks will self-destruct.
        e.subBlocks.parentTick(sl);

        if (e.debugMode) {
            if (e.debugTask != null) {
                boolean done = e.debugTask.get();
                if (done) {
                    e.debugTask = null;
                }
            }
            return;
        }


        Signals.DayTime dayTime = Util.getDayTime(sl);
        Signals signals = Signals.fromDayTime(dayTime);
        if (signals == Signals.MORNING) {
            if (!e.isMorning) {
                e.isMorning = true;
                e.onMorning(Util.getTick(sl));
            }
        } else {
            if (e.isMorning) {
                e.isMorning = false;
            }
        }

        if (!e.mornings.empty()) {
            e.morningTick(e.mornings.pop());
        }

        CompoundTag tag = Compat.getBlockStoredTagData(e);
        boolean stateChanged = e.state.tick(e, tag, sl);

        if ((stateChanged || e.changed) && e.everScanned) {
            e.writeTownData(tag);
            e.state.putStateOnTile(tag, e.uuid);
            e.changed = false;
            setChanged(level, blockEntityPos, state);
        }

        if (stateChanged) {
            e.possibleWork.invalidate();
        }

        e.workHandle.tick(sl);
        e.quests.tick(e);
        e.biomes.tick();
        e.healing.tick();
        e.possibleWork.tick();

        e.roomsHandle.tick(sl, blockEntityPos);

        long gameTime = level.getGameTime();
        long l = gameTime % Config.FLAG_TICK_INTERVAL.get();
        if (l != 0) {
            return;
        }

        Collection<MCRoom> allRooms = e.roomsHandle.getAllRoomsIncludingMetaAndFarms();
        e.jobHandle.tick(sl, allRooms, Config.FLAG_TICK_INTERVAL.get());
        e.jobHandles.forEach((k, v) -> v.tick(sl, allRooms, Config.FLAG_TICK_INTERVAL.get()));

        e.asapRewards.tick();

        e.pois.tick(sl, blockEntityPos, (int) e.villagerHandle.stream().count());
        if ((signals == Signals.NIGHT || signals == Signals.EVENING) && !e.getVillagerHandle().entities().isEmpty()) {
            AdvancementsInit.VISITOR_TRIGGER.triggerForNearestPlayer(
                    sl,
                    VisitorTrigger.Triggers.FirstNightFall,
                    e.getBlockPos()
            );
        }

        e.villagerHandle.tick(Util.getTick(sl), signals);

        e.economics.tick();

        e.everScanned = true;

        profileTick(e, start);
    }

    private static boolean isMissingCompletableQuests(TownFlagBlockEntity e) {
        ImmutableList<AbstractMap.SimpleEntry<MCQuest, MCReward>> all = e.questsHandle.getAllQuestsWithRewards();
        if (all.stream().anyMatch(v -> !v.getKey().isComplete())) {
            return false;
        }
        if (e.morningRewards.children.stream().anyMatch(MCReward::addsQuestsWhenApplied)) {
            return false;
        }
        return true;
    }

    private void morningTick(Long newTime) {
        this.assignedFarmers.clear();
        for (MCReward r : this.morningRewards.popChildren()) {
            this.asapRewards.push(r);
        }
        this.setChanged();
        villagerHandle.handleMorning();
        roomsHandle.handleMorning();
        Compat.getBlockStoredTagData(this).putLong(NBT_TIME_WARP_REFERENCE_TICK, newTime);
    }

    private static void profileTick(
            TownFlagBlockEntity e,
            long start
    ) {
        if (Config.TICK_SAMPLING_RATE.get() > 0) {
            long end = System.currentTimeMillis();
            e.times.add((int) (end - start));

            if (e.times.size() > Config.TICK_SAMPLING_RATE.get()) {
                QT.PROFILE_LOGGER.debug(
                        "Average tick length: {}",
                        e.times.stream().mapToInt(Integer::intValue).average().getAsDouble()
                );
                e.times.clear();
            }
        }
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
        // TODO: Serialization for ASAPss
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
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2);
            if (!level.isClientSide()) {
                TownFlags.register(uuid, t);
            }
            return true;
        });
    }

    // TODO: Precompute ideal jobs regularly, ahead of time.
    //  When a change happens to the job board, or to the town state,
    //  detwermine the best possible use of people (this can be unit
    //  tested thoroughly. And then filter out any work that is already
    //  being done, and assign the rest to people who are idle or
    //  cannot complete their current work (e.g. no supplies)
    private void updateWorkersAfterRequestChange() {
        WorksBehaviour.TownData td = getTownData();
        villagerHandle.stream().filter(v -> v instanceof VisitorMobEntity).map(v -> (VisitorMobEntity) v).filter(e -> {
            for (WorkRequest r : workHandle.getRequestedResults()) {
                if (ServerJobsRegistry.canSatisfy(td, e.getJobId(), r.asIngredient())) {
                    if (e.getStatusForServer().isBusy()) {
                        return false;
                    }
                }
            }
            return true;
        }).forEach(e -> villagerHandle.changeJobForVillager(
                e.getUUID(),
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

    @Override
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
        TownQuests.addJobQuest(this, quests, visitorUUID);
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
        VisitorMobEntity villager = villagerHandle.getEntity(ownerUUID);
        if (villager == null) {
            return true;
        }
        float damagePercent = villagerHandle.getDamagePercent(ownerUUID);
        if (damagePercent > 0) {
            // The more damaged they are, the more likely they are to rest.
            if (level.getRandom().nextFloat() < damagePercent) {
                villagerHandle.changeJobForVillager(ownerUUID, ResterWork.getIdForRoot(currentJob.rootId()), false);
                return true;
            }
        }

        if (villagerHandle.hasBlockOfProgress(ownerUUID)) {
            MCHeldItem bop = MCHeldItem.fromTown(ItemsInit.BLOCK_OF_PROGRESS.get());
            villager.tryGiveItem(bop, InventoryFullStrategy.REMOVE_FROM_WORLD);
            JobID depositor = BOPDepositorWork.getIdForRoot(currentJob.rootId());
            villagerHandle.changeJobForVillager(ownerUUID, depositor, false);
            return true;
        }

        ImmutableList<WorkRequest> requestedResults = workHandle.getRequestedResults();
        WorksBehaviour.TownData td = getTownData();
        Predicate<JobID> canFit = p -> ServerJobsRegistry.canFit(uuid, p, Util.getDayTime(getServerLevel()));
        Predicate<JobID> canAlwaysStart = p -> ServerJobsRegistry.canAlwaysStart(uuid, p);
        JobID work = TownVillagers.chooseFromList(
                canFit,
                canAlwaysStart,
                requestedResults,
                td,
                possibleWork.getFor(villager.getJobId())
        );
        if (work != null) {
            villagerHandle.changeJobForVillager(ownerUUID, work, false);
            return true;
        }

        if (preferredBuffer < 100) {
            preferredBuffer++;
            return false;
        }
        preferredBuffer = 0;

        work = TownVillagers.getPreferredWork(villager.getJobId(), canFit, canAlwaysStart, requestedResults, td);
        if (work != null) {
            villagerHandle.changeJobForVillager(ownerUUID, work, false);
            return true;
        }

        return false;
    }

    public WorksBehaviour.TownData getTownData() {
        return new WorksBehaviour.TownData(prefix -> knowledgeHandle.getAllKnownGatherResults(
                biomes.getAllInTown(),
                prefix
        ));
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
        ImmutableList<MCRoom> allRooms = roomsHandle.getAllRoomsIncludingMetaAndFarms();
        return pois.getWanderTarget(
                getServerLevel(), allRooms, (p, r) -> {
                    BlockPos pos = Positions.ToBlock(p, r.yCoord);
                    double dist = pos.distSqr(avoiding);
                    if (dist > 5) {
                        QT.FLAG_LOGGER.trace("Target is {} blocks away from {}", dist, avoiding);
                        return true;
                    }
                    return false;
                }, (p, r) -> Positions.ToBlock(p, r.yCoord)
        );
    }

    public ImmutableSet<UUID> getVillagers() {
        return ImmutableSet.copyOf(villagerHandle.stream().map(Entity::getUUID).collect(Collectors.toSet()));
    }

    @Override
    public TownHealingHandle getHealHandle() {
        return healing;
    }

    @Override
    public @Nullable ContainerTarget<MCContainer, MCTownItem> findMatchingContainer(ContainerTarget.CheckFn<MCTownItem> c) {
        return TownContainers.findMatching(this, c);
    }

    @Override
    public WorkStatusHandle<BlockPos, MCHeldItem> getWorkStatusHandle(
            @Nullable UUID ownerIDOrNullForGlobal
    ) {
        if (ownerIDOrNullForGlobal == null) {
            return jobHandle;
        }
        TownWorkStatusStore jh = jobHandles.get(ownerIDOrNullForGlobal);
        if (jh != null) {
            return jh;
        }
        jh = new TownWorkStatusStore();
        jobHandles.put(ownerIDOrNullForGlobal, jh);
        return jh;
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

    void onMorning(long newTime) {
        this.mornings.push(newTime);
        // IMPORTANT: DO NOTHING ELSE IN THIS FUNCTION
        // Adding logic here may cause the game to lock up.
        // Do morning logic via morningTick().
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

    public void assumeStateFromTown(
            VisitorMobEntity visitorMobEntity,
            ServerLevel sl
    ) {
        if (!Compat.getBlockStoredTagData(this).contains(NBT_TOWN_STATE)) {
            QT.FLAG_LOGGER.error(
                    "Villager entity exists but town state is missing. This is a bug and may cause unexpected behaviour.");
            return;
        }
        MCTownState state = TownStateSerializer.INSTANCE.load(
                Compat.getBlockStoredTagData(this)
                      .getCompound(NBT_TOWN_STATE),
                sl,
                bp -> this.pois.getWelcomeMats().contains(bp)
        );
        Optional<TownState.VillagerData<MCHeldItem>> match = state.villagers.stream()
                                                                            .filter(v -> v.uuid.equals(visitorMobEntity.getUUID()))
                                                                            .findFirst();
        if (match.isEmpty()) {
            QT.FLAG_LOGGER.error(
                    "Villager entity exists but is not present on town state. This is a bug and may cause unexpected behaviour.");
            return;
        }
        villagerHandle.register(visitorMobEntity);
        TownState.VillagerData<MCHeldItem> m = match.get();
        visitorMobEntity.initialize(this, m.uuid, m.xPosition, m.yPosition, m.zPosition, m.journal);
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

    public void warpTime(int ticks) {
        state.warp(this, Compat.getBlockStoredTagData(this), getServerLevel(), ticks);
    }

    public VillagerHolder getVillagerHandle() {
        return villagerHandle;
    }

    public int getY() {
        return getTownFlagBasePos().getY();
    }

    public void startDebugTask(Supplier<Boolean> debugTask) {
        if (!this.debugMode) {
            messages.startDebugFailed();
            return;
        }
        this.debugTask = debugTask;
    }

    public void toggleDebugMode() {
        this.debugMode = !this.debugMode;
        messages.debugToggled(debugMode);
    }

    TownFlagInitialization initializer() {
        return initializer;
    }

    public FlagTabsEmbedding.FlagInfo getInfo() {
        return new FlagTabsEmbedding.FlagInfo(getBlockPos(), bopCount > 0);
    }

    public void ejectBlockOfProgress(ServerPlayer sender) {
        bopCount--;
        setChanged();
        ItemStack v = ItemsInit.BLOCK_OF_PROGRESS.get().getDefaultInstance();
        BlockPos bp = getTownFlagBasePos();
        messages.broadcastMessage(
                "messages.player.took_bop",
                sender.getName(),
                ItemsInit.BLOCK_OF_PROGRESS.get().getDefaultInstance(),
                Util.getTinyString(bp)
        );
        if (sender.getInventory().add(v)) {
            sender.getInventory().setChanged();
            sender.inventoryMenu.broadcastChanges();
            return;
        }
        bp = bp.relative(Compat.getRandomHorizontal(getServerLevel()));
        ItemEntity item = new ItemEntity(level, bp.getX(), bp.getY(), bp.getZ(), v);
        level.addFreshEntity(item);
    }


}
