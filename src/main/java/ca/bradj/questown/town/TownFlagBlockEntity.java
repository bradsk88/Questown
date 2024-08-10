package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.TownFlagSubBlocks;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.advancements.ApproachTownTrigger;
import ca.bradj.questown.core.advancements.RoomTrigger;
import ca.bradj.questown.core.advancements.VisitorTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.integration.minecraft.*;
import ca.bradj.questown.items.QTNBT;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.JobsRegistry;
import ca.bradj.questown.jobs.WorksBehaviour;
import ca.bradj.questown.jobs.declarative.DinerNoTableWork;
import ca.bradj.questown.jobs.declarative.DinerWork;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.*;
import ca.bradj.questown.town.quests.*;
import ca.bradj.questown.town.rooms.TownRoomsMapSerializer;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import ca.bradj.roomrecipes.recipes.ActiveRecipes;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.*;
import java.util.stream.Collectors;

import static ca.bradj.questown.town.TownFlagState.NBT_TIME_WARP_REFERENCE_TICK;
import static ca.bradj.questown.town.TownFlagState.NBT_TOWN_STATE;

public class TownFlagBlockEntity extends BlockEntity implements TownInterface,
        ActiveRecipes.ChangeListener<MCRoom, RoomRecipeMatch<MCRoom>>, QuestBatch.ChangeListener<MCQuest>,
        TownPois.Listener {

    private final TownKnownBiomes biomes = new TownKnownBiomes();

    public TownKnownBiomes getBiomesHandle() {
        return biomes;
    }

    private record InitPair(
            BiFunction<CompoundTag, TownFlagBlockEntity, Boolean> fromTag,
            Consumer<TownFlagBlockEntity> onFlagPlace
    ) {
    }

    private static Map<String, InitPair> initPairs;

    public static void staticInitialize() {
        ImmutableMap.Builder<String, InitPair> b = ImmutableMap.builder();
        b.put(
                NBT_ROOMS, new InitPair(
                        (CompoundTag tag, TownFlagBlockEntity t) -> {
                            TownRoomsMapSerializer.INSTANCE.deserialize(
                                    tag, t, t.roomsHandle.getRegisteredRooms());
                            QT.FLAG_LOGGER.debug("Initialized rooms from {}", tag);
                            return true;
                        },
                        t -> {
                            t.roomsHandle.initializeNew(t);
                            QT.FLAG_LOGGER.debug("Initialized rooms for new flag");
                        }
                )
        );
        b.put(
                NBT_QUEST_BATCHES, new InitPair(
                        (tag, t) -> {
                            CompoundTag data = tag.getCompound(NBT_QUEST_BATCHES);
                            boolean inited = MCQuestBatches.SERIALIZER.deserializeNBT(t, data, t.quests.questBatches);
                            if (!inited) {
                                t.setUpQuestsForNewlyPlacedFlag();
                            }
                            t.isInitializedQuests = true;
                            QT.FLAG_LOGGER.debug("Initialized quests from {}", tag);
                            return true;
                        },
                        t -> {
                            t.questsHandle.initialize(t);
                            QT.FLAG_LOGGER.debug("Initialized quests for new flag");
                        }
                )
        );
        b.put(
                NBT_MORNING_REWARDS, new InitPair(
                        (tag, t) -> {
                            CompoundTag data = tag.getCompound(NBT_MORNING_REWARDS);
                            t.morningRewards.deserializeNbt(t, data);
                            QT.FLAG_LOGGER.debug("Initialized morning rewards from {}", tag);
                            return true;
                        },
                        t -> {
                            QT.FLAG_LOGGER.debug("Initialized morning rewards for new flag");
                        }
                )
        );
        b.put(
                NBT_WELCOME_MATS, new InitPair(
                        (tag, t) -> {
                            ListTag data = tag.getList(NBT_WELCOME_MATS, Tag.TAG_COMPOUND);
                            Collection<BlockPos> l = WelcomeMatsSerializer.INSTANCE.deserializeNBT(data);
                            l.forEach(t.pois::registerWelcomeMat);
                            QT.FLAG_LOGGER.debug("Initialized welcome mats from {}", tag);
                            return true;
                        },
                        t -> QT.FLAG_LOGGER.debug("Initialized welcome mats for new flag")
                )
        );
        b.put(
                NBT_JOBS, new InitPair(
                        (tag, t) -> {
                            TownWorkHandleSerializer.INSTANCE.deserializeNBT(tag, t.workHandle);
                            QT.FLAG_LOGGER.debug("Initialized jobs from {}", tag);
                            return true;
                        },
                        t -> QT.FLAG_LOGGER.debug("Initialized jobs for new flag")
                )
        );
        b.put(
                NBT_KNOWLEDGE, new InitPair(
                        (tag, t) -> {
                            if (!t.knowledgeHandle.isInitialized()) {
                                return false;
                            }
                            TownKnowledgeStoreSerializer.INSTANCE.deserializeNBT(
                                    QTNBT.getCompound(tag, NBT_KNOWLEDGE),
                                    t.knowledgeHandle
                            );
                            QT.FLAG_LOGGER.debug("Initialized knowledge from {}", tag);
                            return true;
                        },
                        t -> {
                            t.knowledgeHandle.initialize(t);
                            QT.FLAG_LOGGER.debug("Initialized knowledge for new flag");
                        }
                )
        );
        b.put(
                NBT_VILLAGERS, new InitPair(
                        (tag, t) -> {
                            long currentTick = Util.getTick(t.getServerLevel());
                            TownVillagerHandle.SERIALIZER.deserialize(tag, t.villagerHandle, currentTick);
                            QT.FLAG_LOGGER.debug("Initialized villagers from {}", tag);
                            return true;
                        },
                        t -> {
                            t.villagerHandle.associate(t);
                            t.villagerHandle.addHungryListener(e -> {
                                if (t.getVillagerHandle()
                                        .isDining(e.getUUID())) {
                                    return;
                                }
                                if (!t.getVillagerHandle()
                                        .canDine(e.getUUID())) {
                                    return;
                                }
                                String rid = e.getJobId()
                                        .rootId();
                                ResourceLocation diningRoom = DinerWork.asWork(rid)
                                        .baseRoom();
                                Collection<RoomRecipeMatch<MCRoom>> diningRooms = t.roomsHandle.getRoomsMatching(
                                        diningRoom);
                                if (diningRooms.isEmpty()) {
                                    t.changeJobForVisitor(
                                            e.getUUID(), DinerNoTableWork.getIdForRoot(rid));
                                } else {
                                    t.changeJobForVisitor(
                                            e.getUUID(), DinerWork.getIdForRoot(rid));
                                }
                            });
                            t.villagerHandle.addStatsListener(s -> t.setChanged());
                            QT.FLAG_LOGGER.debug("Initialized villagers for new flag");
                        }
                )
        );
        initPairs = b.build();
    }

    public static final String ID = "flag_base_block_entity";
    // TODO: Extract serialization
    public static final String NBT_QUEST_BATCHES = String.format("%s_quest_batches", Questown.MODID);
    public static final String NBT_MORNING_REWARDS = String.format("%s_morning_rewards", Questown.MODID);
    public static final String NBT_WELCOME_MATS = String.format("%s_welcome_mats", Questown.MODID);
    public static final String NBT_ROOMS = String.format("%s_rooms", Questown.MODID);
    public static final String NBT_JOBS = String.format("%s_jobs", Questown.MODID);
    private static final String NBT_KNOWLEDGE = "knowledge";
    private static final String NBT_VILLAGERS = "villagers";
    private boolean stopped = true;
    final TownQuests quests = new TownQuests();
    private final TownFlagSubBlocks subBlocks = new TownFlagSubBlocks(getBlockPos());
    private final TownPois pois = new TownPois(subBlocks);
    private final MCMorningRewards morningRewards = new MCMorningRewards(this);
    private final MCAsapRewards asapRewards = new MCAsapRewards();
    private final UUID uuid = UUID.randomUUID();
    private final TownFlagState state = new TownFlagState(this);
    public long advancedTimeOnTick = -1;
    private boolean isInitializedQuests = false;
    private boolean everScanned = false;
    private boolean changed = false;

    // Farmer specific stuff
    private final ArrayList<UUID> assignedFarmers = new ArrayList<>();
    private final ArrayList<BlockPos> blocksWithWeeds = new ArrayList<>();

    private final ArrayList<Integer> times = new ArrayList<>();

    private final TownWorkStatusStore jobHandle = new TownWorkStatusStore();
    private final Map<UUID, TownWorkStatusStore> jobHandles = new HashMap<>();

    final TownWorkHandle workHandle = new TownWorkHandle(subBlocks, getBlockPos());
    private final Stack<Long> mornings = new Stack<>();
    private final LinkedBlockingQueue<Function<TownFlagBlockEntity, Boolean>> initializers = new LinkedBlockingQueue<>();

    // TODO: Move all quest-related stuff into the handle
    private final TownKnowledgeStore knowledgeHandle = new TownKnowledgeStore();
    private final TownQuestsHandle questsHandle = new TownQuestsHandle();
    private final TownRoomsHandle roomsHandle = new TownRoomsHandle();
    final TownMessages messages = new TownMessages();

    private final TownVillagerHandle villagerHandle = new TownVillagerHandle();
    private @Nullable Supplier<Boolean> debugTask;
    private boolean debugMode;

    public TownFlagBlockEntity(
            BlockPos p_155229_,
            BlockState p_155230_
    ) {
        super(TilesInit.TOWN_FLAG.get(), p_155229_, p_155230_);
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

        Player nearestPlayer = level.getNearestPlayer(
                blockEntityPos.getX(), blockEntityPos.getY(), blockEntityPos.getZ(), -1, null);
        if (nearestPlayer == null) {
            QT.FLAG_LOGGER.error("No players detected in world");
            return;
        }
        double distToPlayer = nearestPlayer.blockPosition().distSqr(e.worldPosition);
        if (distToPlayer > Config.TOWN_TICK_RADIUS.get()) {
            if (!e.stopped) {
                QT.FLAG_LOGGER.info(
                        "Town flag at {} stopped ticking because closest player is further away than limit {}: {}",
                        blockEntityPos, Config.TOWN_TICK_RADIUS.get(), distToPlayer
                );
            }
            e.stopped = true;
            return;
        }

        e.stopped = false;

        long start = System.currentTimeMillis();

        if (!e.initializers.isEmpty()) {
            QT.FLAG_LOGGER.info("Running initializer ({} left) @ {}", e.initializers.size() - 1, e.getBlockPos());
            Function<TownFlagBlockEntity, Boolean> initr = e.initializers.remove();
            if (!initr.apply(e)) {
                e.initializers.add(initr);
            }
            return;
        }

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

        e.workHandle.tick(sl);
        e.quests.tick(e);
        e.biomes.tick();

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

        e.pois.tick(sl, blockEntityPos);

        e.villagerHandle.tick(Util.getTick(sl));

        e.everScanned = true;

        profileTick(e, start);
    }

    private void morningTick(Long newTime) {
        this.assignedFarmers.clear();
        for (MCReward r : this.morningRewards.getChildren()) {
            this.asapRewards.push(r);
        }
        this.setChanged();
        villagerHandle.forEach(LivingEntity::stopSleeping);
        villagerHandle.makeAllTotallyHungry();
        Compat.getBlockStoredTagData(this)
                .putLong(NBT_TIME_WARP_REFERENCE_TICK, newTime);
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
                        e.times.stream()
                                .mapToInt(Integer::intValue)
                                .average()
                                .getAsDouble()
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

        loadNextTick(initializers);
    }

    private static void loadNextTick(Queue<Function<TownFlagBlockEntity, Boolean>> initializers) {
        // TODO: Store active rooms. Otherwise they get re-announced on each startup.

        initPairs.forEach(
                (key, pair) -> {
                    initializers.add(t -> {
                        CompoundTag tag = Compat.getBlockStoredTagData(t);
                        if (tag.contains(key)) {
                            return pair.fromTag.apply(tag.getCompound(key), t);
                        }
                        QT.FLAG_LOGGER.info("No data for {}. Skipping deserialization.", key);
                        return true;
                    });
                }
        );
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
        tag.put(NBT_QUEST_BATCHES, MCQuestBatches.SERIALIZER.serializeNBT(quests.questBatches));
        tag.put(NBT_MORNING_REWARDS, this.morningRewards.serializeNbt());
        tag.put(NBT_WELCOME_MATS, WelcomeMatsSerializer.INSTANCE.serializeNBT(pois.getWelcomeMats()));
        tag.put(NBT_ROOMS, TownRoomsMapSerializer.INSTANCE.serializeNBT(roomsHandle.getRegisteredRooms()));
        tag.put(NBT_JOBS, TownWorkHandleSerializer.INSTANCE.serializeNBT(workHandle));
        QTNBT.put(tag, NBT_KNOWLEDGE, TownKnowledgeStoreSerializer.INSTANCE.serializeNBT(knowledgeHandle));
        QTNBT.put(
                tag, NBT_VILLAGERS,
                TownVillagerHandle.SERIALIZER.serialize(villagerHandle, Util.getTick(getServerLevel()))
        );
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
                v.onFlagPlace.accept(this);
            });
        }
        initializers.add(t -> {
            t.messages.initialize(t.getServerLevel());
            return true;
        });
        initializers.add(t -> {
            t.biomes.initialize(t);
            return true;
        });
        initializers.add(t -> {
            if (!this.isInitializedQuests) {
                t.setUpQuestsForNewlyPlacedFlag();
            }
            t.quests.setChangeListener(t);
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
        villagerHandle.stream()
                .filter(v -> v instanceof VisitorMobEntity)
                .map(v -> (VisitorMobEntity) v)
                .filter(e -> {
                    for (WorkRequest r : workHandle.getRequestedResults()) {
                        if (JobsRegistry.canSatisfy(td, e.getJobId(), r.asIngredient())) {
                            if (e.getStatusForServer()
                                    .isBusy()) {
                                return false;
                            }
                        }
                    }
                    return true;
                })
                .forEach(e -> changeJobForVisitor(e.getUUID(), WorkSeekerJob.getIDForRoot(e.getJobId())));
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
                double v = event.getEntity()
                        .distanceToSqr(
                                this.worldPosition.getX() + 0.5D,
                                this.worldPosition.getY() + 0.5D,
                                this.worldPosition.getZ() + 0.5D
                        );
                if (v < 100) {
                    AdvancementsInit.APPROACH_TOWN_TRIGGER.trigger(
                            sp, ApproachTownTrigger.Triggers.FirstVisit
                    );
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

    private void setUpQuestsForNewlyPlacedFlag() {
        TownQuests.setUpQuestsForNewlyPlacedFlag(this, quests);
        this.isInitializedQuests = true;
        setChanged();
    }

    public ImmutableList<Quest<ResourceLocation, MCRoom>> getAllQuests() {
        return quests.getAll();
    }

    @Override
    public void roomRecipeCreated(
            MCRoom roomDoorPos,
            RoomRecipeMatch<MCRoom> match
    ) {
        ServerLevel l = getServerLevel();
        swapBlocks(l, match);
        messages.roomRecipeCreated(roomDoorPos, match);;
        BlockPos pos = Positions.ToBlock(roomDoorPos.doorPos, roomDoorPos.yCoord);
        AdvancementsInit.ROOM_TRIGGER.triggerForNearestPlayer(l, RoomTrigger.Triggers.FirstJobBoard, pos);
        // TODO: get room for rendering effect
//        handleRoomChange(room, ParticleTypes.HAPPY_VILLAGER);
        quests.markQuestAsComplete(roomDoorPos, match.getRecipeID());
    }

    private void swapBlocks(
            ServerLevel level,
            RoomRecipeMatch<MCRoom> match
    ) {
        ImmutableMap<ResourceLocation, BiFunction<ServerLevel, RoomRecipeMatch<MCRoom>, Void>> swaps = ImmutableMap.of(
                Questown.ResourceLocation("job_board"), this::swapJobBoardSign
        );
        BiFunction<ServerLevel, RoomRecipeMatch<MCRoom>, Void> swap = swaps.get(match.getRecipeID());
        if (swap != null) {
            swap.apply(level, match);
        }
    }

    private Void swapJobBoardSign(
            ServerLevel level,
            RoomRecipeMatch<MCRoom> room
    ) {
        BlockPredicate predicate = BlockPredicate.Builder.block()
                .of(BlockTags.SIGNS)
                .build();
        for (Map.Entry<BlockPos, Block> e : room.getContainedBlocks()
                .entrySet()) {
            if (!predicate.matches(level, e.getKey())) {
                continue;
            }
            Direction value = Util.rotationToDirection(
                    level.getBlockState(e.getKey()).getValue(StandingSignBlock.ROTATION)
            );
            level.setBlockAndUpdate(
                    e.getKey(), BlocksInit.JOB_BOARD_BLOCK.get()
                            .defaultBlockState()
                            .setValue(HorizontalDirectionalBlock.FACING, value)
            );
            registerJobsBoard(e.getKey());
            jobHandle.setJobBlockState(e.getKey(), State.freshAtState(WorkSeekerJob.MAX_STATE));
        }
        return null;
    }

    @Override
    public void roomRecipeChanged(
            MCRoom oldRoom,
            RoomRecipeMatch oldMatch,
            MCRoom newRoom,
            RoomRecipeMatch newMatch
    ) {
        ResourceLocation oldMatchID = oldMatch.getRecipeID();
        ResourceLocation newMatchID = newMatch.getRecipeID();
        messages.roomRecipeChanged(oldMatch, newMatch, newRoom);
        TownRooms.addParticles(getServerLevel(), newRoom, ParticleTypes.HAPPY_VILLAGER);
        if (oldMatch == null && newMatch != null) {
            quests.markQuestAsComplete(newRoom, newMatchID);
            return;
        }
        if (oldMatchID.equals(newMatchID)) {
            if (!oldRoom.equals(newRoom)) {
                quests.changeRoomOnly(oldRoom, newRoom);
            }
        }
        if (!oldMatchID.equals(newMatchID)) {
            // TODO: Add quests as a listener instead of doing these calls
            if (quests.canBeUpgraded(oldMatchID, newMatchID)) {
                quests.markAsConverted(newRoom, oldMatchID, newMatchID);
            } else {
                quests.markQuestAsLost(oldRoom, oldMatchID);
                quests.markQuestAsComplete(newRoom, newMatchID);
            }
        }
    }

    @Override
    public void roomRecipeDestroyed(
            MCRoom roomDoorPos,
            RoomRecipeMatch oldRecipeId
    ) {
        messages.roomRecipeDestroyed(roomDoorPos, oldRecipeId);
        TownRooms.addParticles(getServerLevel(), roomDoorPos, ParticleTypes.SMOKE);
        quests.markQuestAsLost(roomDoorPos, oldRecipeId.getRecipeID());
    }

    @Override
    public void questCompleted(MCQuest quest) {
        messages.questCompleted(quest);
        setChanged();
        FireworkRocketEntity firework = new FireworkRocketEntity(
                level,
                getBlockPos().getX(),
                getBlockPos().getY() + 10,
                getBlockPos().getZ(),
                new ItemStack(Items.FIREWORK_ROCKET.getDefaultInstance()
                        .getItem(), 3)
        );
        level.addFreshEntity(firework);
    }

    @Override
    public void questLost(MCQuest quest) {
        messages.questLost(quest);
        setChanged();
    }

    @Override
    public void questBatchCompleted(QuestBatch<?, ?, ?, ?> quest) {
        // TODO: Handle this by informing the user, etc.
        setChanged();
    }

    @Override
    public void addBatchOfRandomQuestsForVisitor(@Nullable UUID visitorUUID) {
        TownQuests.addRandomBatchForVisitor(this, quests, visitorUUID);
        setChanged();
    }

    @Override
    public void addRandomUpgradeQuestForVisitor(UUID visitorUUID) {
        TownQuests.addUpgradeQuest(this, quests, visitorUUID);
        setChanged();
    }

    @Override
    public void addRandomJobQuestForVisitor(UUID visitorUUID) {
        TownQuests.addJobQuest(this, quests, visitorUUID);
        setChanged();
        // TODO: Town should have owners who all get the cheevo
        BlockPos bp = getBlockPos();
        AdvancementsInit.VISITOR_TRIGGER.triggerForNearestPlayer(
                getServerLevel(), VisitorTrigger.Triggers.FirstJobQuest, bp
        );
    }

    @Override
    public boolean alreadyHasQuest(ResourceLocation resourceLocation) {
        return quests.alreadyRequested(resourceLocation);
    }

    @Override
    public boolean changeJobForVisitorFromBoard(UUID ownerUUID) {
        VisitorMobEntity villager = villagerHandle.getEntity(ownerUUID);
        if (villager == null) {
            return true;
        }
        ImmutableList<WorkRequest> requestedResults = workHandle.getRequestedResults();
        WorksBehaviour.TownData td = getTownData();
        Predicate<JobID> canFit = p -> JobsRegistry.canFit(this, uuid, p, Util.getDayTime(getServerLevel()));
        JobID work = TownVillagers.getPreferredWork(villager.getJobId(), canFit, requestedResults, td);
        if (work != null) {
            changeJobForVisitor(ownerUUID, work);
            return true;
        }
        return false;
    }

    WorksBehaviour.TownData getTownData() {
        return new WorksBehaviour.TownData(
                prefix -> knowledgeHandle.getAllKnownGatherResults(biomes.getAllInTown(), prefix)
        );
    }

    @Override
    public void changeJobForVisitor(
            UUID visitorUUID,
            JobID jobID
    ) {
        this.changeJobForVisitor(visitorUUID, jobID, false);
    }

    public void changeJobForVisitor(
            UUID visitorUUID,
            JobID jobID,
            boolean announce
    ) {
        Optional<VisitorMobEntity> f = villagerHandle.stream()
                .filter(v -> v instanceof VisitorMobEntity)
                .map(v -> (VisitorMobEntity) v)
                .filter(v -> v.getUUID()
                        .equals(visitorUUID))
                .findFirst();
        if (f.isEmpty()) {
            QT.FLAG_LOGGER.error("Could not find entity {} to apply job change: {}", visitorUUID, jobID);
        } else {
            doSetJob(visitorUUID, jobID, f.get());
            setChanged();
            if (announce) {
                messages.jobChanged(jobID, visitorUUID);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void doSetJob(
            UUID visitorUUID,
            JobID jobName,
            VisitorMobEntity f
    ) {
        f.setJob(JobsRegistry.getInitializedJob(this, jobName, f.getJobJournalSnapshot()
                .items(), visitorUUID));
    }

    @Override
    public @Nullable UUID getRandomVillager() {
        if (getVillagers().isEmpty()) {
            return null;
        }
        List<UUID> villagers = ImmutableList.copyOf(getVillagers());
        return villagers.get(getServerLevel().getRandom()
                .nextInt(villagers.size()));
    }

    @Override
    public boolean isVillagerMissing(UUID uuid) {
        return !getVillagers().contains(uuid);
    }

    @Override
    public Collection<UUID> getUnemployedVillagers() {
        return villagerHandle.stream()
                .filter(v -> v instanceof VisitorMobEntity)
                .map(v -> (VisitorMobEntity) v)
                .filter(VisitorMobEntity::canAcceptJob)
                .map(Entity::getUUID)
                .toList();
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
        return pois.getWanderTarget(getServerLevel(), allRooms, (p, r) -> {
            BlockPos pos = Positions.ToBlock(p, r.yCoord);
            double dist = pos.distSqr(avoiding);
            if (dist > 5) {
                QT.FLAG_LOGGER.trace("Target is {} blocks away from {}", dist, avoiding);
                return true;
            }
            return false;
        }, (p, r) -> Positions.ToBlock(p, r.yCoord));
    }

    @Override
    public Collection<MCQuest> getQuestsForVillager(UUID uuid) {
        return this.quests.getAllForVillager(uuid);
    }

    @Override
    public List<AbstractMap.SimpleEntry<MCQuest, MCReward>> getQuestsWithRewardsForVillager(UUID uuid) {
        return this.quests.getAllForVillagerWithRewards(uuid);
    }

    @Override
    public void addBatchOfQuests(
            MCQuestBatch batch
    ) {
        this.quests.addBatch(batch);
    }

    @Override
    public ImmutableSet<UUID> getVillagers() {
        return ImmutableSet.copyOf(villagerHandle.stream()
                .map(Entity::getUUID)
                .collect(Collectors.toSet()));
    }

    @Override
    public void removeEntity(VisitorMobEntity visitorMobEntity) {
        villagerHandle.remove(visitorMobEntity);
        setChanged();
    }

    @Override
    public ImmutableSet<UUID> getVillagersWithQuests() {
        return TownQuests.getVillagers(quests);
    }

    @Override
    public @Nullable ContainerTarget<MCContainer, MCTownItem> findMatchingContainer(ContainerTarget.CheckFn<MCTownItem> c) {
        return TownContainers.findMatching(this, c);
    }

    @Override
    public void registerEntity(VisitorMobEntity vEntity) {
        QT.FLAG_LOGGER.debug("Registered entity with town {}: {}", uuid, vEntity);
        villagerHandle.add(vEntity);
        vEntity.addChangeListener(() -> {
            QT.FLAG_LOGGER.trace("Entity requests flag to be marked changed");
            this.setChanged();
        });
        this.setChanged();
    }

    @Override
    public BlockPos getEnterExitPos() {
        @Nullable BlockPos eePos = pois.getWelcomeMatPos((ServerLevel) level);
        if (eePos != null) {
            return eePos;
        }
        BlockPos fallback = getTownFlagBasePos().relative(
                Direction.Plane.HORIZONTAL.getRandomDirection(level.random),
                10
        );
        QT.FLAG_LOGGER.trace("No welcome mats found, falling back to {}", fallback);
        return fallback;
    }

    @Override
    public @Nullable BlockPos getClosestWelcomeMatPos(BlockPos reference) {
        List<BlockPos> welcomeMats = getWelcomeMats();
        if (welcomeMats.isEmpty()) {
            return null;
        }
        // TODO: Find closest
        return welcomeMats.get(0);
    }

    @Override
    public void markBlockWeeded(BlockPos p) {
        this.blocksWithWeeds.remove(p);
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
    public Optional<MCRoom> assignToFarm(UUID ownerUUID) {
        int idx = this.assignedFarmers.indexOf(ownerUUID);
        List<MCRoom> farms = ImmutableList.copyOf(roomsHandle.getFarms());
        if (idx >= 0) {
            return Optional.of(farms.get(this.assignedFarmers.indexOf(ownerUUID)));
        }

        if (this.assignedFarmers.size() < farms.size()) {
            this.assignedFarmers.add(ownerUUID);
            return Optional.of(farms.get(this.assignedFarmers.indexOf(ownerUUID)));
        }

        return Optional.empty();
    }

    @Override
    public Optional<MCRoom> getBiggestFarm() {
        return roomsHandle.getFarms()
                .stream()
                .max(Comparator.comparingInt(
                        v -> v.getSpaces()
                                .stream()
                                .map(InclusiveSpaces::calculateArea)
                                .mapToInt(Double::intValue)
                                .sum()
                ));
    }

    @Override
    public Collection<String> getAvailableRootJobs() {
        // TODO: Scan villagers to make this decision
        Set<String> allJobs = JobsRegistry.getAllJobs()
                .stream()
                .map(JobID::rootId)
                .collect(Collectors.toSet());
        Set<String> allFilledJobs = villagerHandle.stream()
                .filter(v -> v instanceof VisitorMobEntity)
                .map(v -> (VisitorMobEntity) v)
                .map(VisitorMobEntity::getJobId)
                .map(JobID::rootId)
                .collect(Collectors.toSet());
        Set<String> allNewJobs = allJobs.stream()
                .filter(v -> !allFilledJobs.contains(v))
                .collect(Collectors.toSet());
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

    @Override
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
        MCRoom room = new MCRoom(pos, ImmutableList.of(new InclusiveSpace(pos, pos)), bp.getY());
        quests.markQuestAsComplete(room, SpecialQuests.CAMPFIRE);
    }

    @Override
    public void townGateFound(BlockPos bp) {
        Position pos = Positions.FromBlockPos(bp);
        MCRoom room = new MCRoom(pos, ImmutableList.of(new InclusiveSpace(pos, pos)), bp.getY());
        quests.markQuestAsComplete(room, SpecialQuests.TOWN_GATE);
    }

    public void assumeStateFromTown(
            VisitorMobEntity visitorMobEntity,
            ServerLevel sl
    ) {
        if (!Compat.getBlockStoredTagData(this)
                .contains(NBT_TOWN_STATE)) {
            QT.FLAG_LOGGER.error(
                    "Villager entity exists but town state is missing. This is a bug and may cause unexpected behaviour.");
            return;
        }
        MCTownState state = TownStateSerializer.INSTANCE.load(
                Compat.getBlockStoredTagData(this)
                        .getCompound(NBT_TOWN_STATE),
                sl, bp -> this.pois.getWelcomeMats()
                        .contains(bp)
        );
        Optional<TownState.VillagerData<MCHeldItem>> match = state.villagers.stream()
                .filter(v -> v.uuid.equals(
                        visitorMobEntity.getUUID()))
                .findFirst();
        if (match.isEmpty()) {
            QT.FLAG_LOGGER.error(
                    "Villager entity exists but is not present on town state. This is a bug and may cause unexpected behaviour.");
            return;
        }
        registerEntity(visitorMobEntity);
        TownState.VillagerData<MCHeldItem> m = match.get();
        visitorMobEntity.initialize(
                this, m.uuid,
                m.xPosition, m.yPosition, m.zPosition,
                m.journal
        );
    }

    @Override
    public UUID getUUID() {
        return uuid;
    }

    public void registerWelcomeMat(BlockPos welcomeMatBlock) {
        pois.registerWelcomeMat(welcomeMatBlock);
        setChanged();
        AdvancementsInit.ROOM_TRIGGER.triggerForNearestPlayer(
                getServerLevel(), RoomTrigger.Triggers.FirstJobBlock, welcomeMatBlock
        );
    }

    public List<BlockPos> getWelcomeMats() {
        return pois.getWelcomeMats();
    }


    public void registerJobsBoard(BlockPos matPos) {
        this.workHandle.registerJobBoard(matPos);
        this.setChanged();
    }

    public void openJobsMenu(ServerPlayer sender) {
        workHandle.openMenuRequested(sender);
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
}
