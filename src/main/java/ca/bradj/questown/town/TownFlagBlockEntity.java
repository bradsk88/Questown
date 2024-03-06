package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.TownFlagSubBlocks;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.advancements.ApproachTownTrigger;
import ca.bradj.questown.core.advancements.VisitorTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.minecraft.*;
import ca.bradj.questown.items.GathererMap;
import ca.bradj.questown.items.QTNBT;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.JobsRegistry;
import ca.bradj.questown.jobs.WorksBehaviour;
import ca.bradj.questown.jobs.declarative.DinerWork;
import ca.bradj.questown.jobs.declarative.WorkSeekerJob;
import ca.bradj.questown.jobs.gatherer.Loots;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.*;
import ca.bradj.questown.town.quests.*;
import ca.bradj.questown.town.rooms.TownRoomsMapSerializer;
import ca.bradj.questown.town.special.SpecialQuests;
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
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ca.bradj.questown.town.TownFlagState.NBT_TIME_WARP_REFERENCE_TICK;
import static ca.bradj.questown.town.TownFlagState.NBT_TOWN_STATE;

public class TownFlagBlockEntity extends BlockEntity implements TownInterface, ActiveRecipes.ChangeListener<MCRoom, RoomRecipeMatch<MCRoom>>, QuestBatch.ChangeListener<MCQuest>, TownPois.Listener {

    public static final String ID = "flag_base_block_entity";
    // TODO: Extract serialization
    public static final String NBT_QUEST_BATCHES = String.format("%s_quest_batches", Questown.MODID);
    public static final String NBT_MORNING_REWARDS = String.format("%s_morning_rewards", Questown.MODID);
    public static final String NBT_WELCOME_MATS = String.format("%s_welcome_mats", Questown.MODID);
    public static final String NBT_ROOMS = String.format("%s_rooms", Questown.MODID);
    public static final String NBT_JOBS = String.format("%s_jobs", Questown.MODID);
    private static final String NBT_KNOWLEDGE = "knowledge";
    private static final String NBT_VILLAGERS = "villagers";
    private static boolean stopped;
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
    private final ArrayList<Biome> nearbyBiomes = new ArrayList<>();

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

    private final TownVillagerHandle villagerHandle = new TownVillagerHandle();

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
            BlockPos blockPos,
            BlockState state,
            TownFlagBlockEntity e
    ) {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }

        Player nearestPlayer = level.getNearestPlayer(blockPos.getX(), blockPos.getY(), blockPos.getZ(), -1, null);
        if (nearestPlayer == null) {
            QT.FLAG_LOGGER.error("No players detected in world");
            return;
        }
        double distToPlayer = nearestPlayer.blockPosition().distSqr(e.worldPosition);
        if (distToPlayer > Config.TOWN_TICK_RADIUS.get()) {
            if (!stopped) {
                QT.FLAG_LOGGER.info(
                        "Town flag at {} stopped ticking because closest player is further away than limit {}: {}",
                        blockPos, Config.TOWN_TICK_RADIUS.get(), distToPlayer
                );
            }
            stopped = true;
            return;
        }

        stopped = false;

        long start = System.currentTimeMillis();

        if (!e.initializers.isEmpty()) {
            QT.FLAG_LOGGER.info("Running initializer ({} left)", e.initializers.size() - 1);
            Function<TownFlagBlockEntity, Boolean> initr = e.initializers.remove();
            if (!initr.apply(e)) {
                e.initializers.add(initr);
            }
            return;
        }

        e.subBlocks.parentTick(sl);

        if (!e.mornings.empty()) {
            e.morningTick(e.mornings.pop());
        }

        CompoundTag tag = Util.getBlockStoredTagData(e);
        boolean stateChanged = e.state.tick(e, tag, sl);

        if ((stateChanged || e.changed) && e.everScanned) {
            e.state.putStateOnTile(tag, e.uuid);
            e.writeTownData(tag);
            e.changed = false;
            setChanged(level, blockPos, state);
        }

        e.workHandle.tick(sl);
        e.quests.tick(e);

        long gameTime = level.getGameTime();
        long l = gameTime % 10;
        if (l != 0) {
            return;
        }

        if (e.nearbyBiomes.isEmpty()) {
            computeNearbyBiomes(level, blockPos, e);
        }

        e.roomsHandle.tick(sl, blockPos);

        Collection<MCRoom> allRooms = e.roomsHandle.getAllRoomsIncludingMeta();
        e.jobHandle.tick(sl, allRooms);
        e.jobHandles.forEach((k, v) -> v.tick(sl, allRooms));

        e.asapRewards.tick();

        e.pois.tick(sl, blockPos);

        e.villagerHandle.tick(Util.getTick(sl));

        e.everScanned = true;

        profileTick(e, start);
    }

    private static void computeNearbyBiomes(
            Level level,
            BlockPos blockPos,
            TownFlagBlockEntity e
    ) {
        ChunkPos here = new ChunkPos(blockPos);
        Biome value = level.getBiome(blockPos).value();
        e.nearbyBiomes.add(value);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int i = 0; i < Config.BIOME_SCAN_RADIUS.get(); i++) {
                ChunkPos there = new ChunkPos(here.x + d.getStepX() * i, here.z + d.getStepZ() * i);
                Biome biome = level.getBiome(there.getMiddleBlockPosition(blockPos.getY())).value();
                e.nearbyBiomes.add(biome);
            }
        }
    }

    private void morningTick(Long newTime) {
        this.assignedFarmers.clear();
        for (MCReward r : this.morningRewards.getChildren()) {
            this.asapRewards.push(r);
        }
        this.setChanged();
        villagerHandle.forEach(LivingEntity::stopSleeping);
        villagerHandle.makeAllTotallyHungry();
        Util.getBlockStoredTagData(this).putLong(NBT_TIME_WARP_REFERENCE_TICK, newTime);
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
                        e.times.stream().mapToInt(Integer::intValue).average()
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
        // TODO: Store active rooms? (Cost to re-compute is low, I think)
        initializers.add(t -> {
            CompoundTag tag = Util.getBlockStoredTagData(t);
            if (tag.contains(NBT_ROOMS)) {
                TownRoomsMapSerializer.INSTANCE.deserialize(tag.getCompound(NBT_ROOMS), t, t.roomsHandle.getRegisteredRooms());
            }
            return true;
        });

        initializers.add(t -> {
            CompoundTag tag = Util.getBlockStoredTagData(t);
            if (tag.contains(NBT_QUEST_BATCHES)) {
                CompoundTag data = tag.getCompound(NBT_QUEST_BATCHES);
                MCQuestBatches.SERIALIZER.deserializeNBT(t, data, t.quests.questBatches);
                t.isInitializedQuests = true;
            }
            return true;
        });

        initializers.add(t -> {
            CompoundTag tag = Util.getBlockStoredTagData(t);
            if (tag.contains(NBT_MORNING_REWARDS)) {
                CompoundTag data = tag.getCompound(NBT_MORNING_REWARDS);
                t.morningRewards.deserializeNbt(t, data);
            }
            return true;
        });

        initializers.add(t -> {
            CompoundTag tag = Util.getBlockStoredTagData(t);
            if (tag.contains(NBT_WELCOME_MATS)) {
                ListTag data = tag.getList(NBT_WELCOME_MATS, Tag.TAG_COMPOUND);
                Collection<BlockPos> l = WelcomeMatsSerializer.INSTANCE.deserializeNBT(data);
                l.forEach(t.pois::registerWelcomeMat);
            }
            return true;
        });

        initializers.add(t -> {
            CompoundTag tag = Util.getBlockStoredTagData(t);
            if (tag.contains(NBT_JOBS)) {
                TownWorkHandleSerializer.INSTANCE.deserializeNBT(tag.getCompound(NBT_JOBS), t.workHandle);
            }
            return true;
        });

        initializers.add(t -> {
            CompoundTag tag = Util.getBlockStoredTagData(t);
            if (!t.knowledgeHandle.isInitialized()) {
                return false;
            }
            if (QTNBT.contains(tag, NBT_KNOWLEDGE)) {
                TownKnowledgeStoreSerializer.INSTANCE.deserializeNBT(
                        QTNBT.getCompound(tag, NBT_KNOWLEDGE),
                        t.knowledgeHandle
                );
            }
            return true;
        });

        initializers.add(t -> {
            CompoundTag tag = Util.getBlockStoredTagData(t);
            if (QTNBT.contains(tag, NBT_VILLAGERS)) {
                CompoundTag data = QTNBT.getCompound(tag, NBT_VILLAGERS);
                long currentTick = Util.getTick(t.getServerLevel());
                TownVillagerHandle.SERIALIZER.deserialize(data, t.villagerHandle, currentTick);
            }
            t.villagerHandle.addHungryListener(e -> {
                if (t.getVillagerHandle().isDining(e.getUUID())) {
                    return;
                }
                t.changeJobForVisitor(e.getUUID(), DinerWork.getIdForRoot(e.getJobId().rootId()));
            });
            t.villagerHandle.addStatsListener(s -> t.setChanged());
            return true;
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
        tag.put(NBT_QUEST_BATCHES, MCQuestBatches.SERIALIZER.serializeNBT(quests.questBatches));
        tag.put(NBT_MORNING_REWARDS, this.morningRewards.serializeNbt());
        tag.put(NBT_WELCOME_MATS, WelcomeMatsSerializer.INSTANCE.serializeNBT(pois.getWelcomeMats()));
        tag.put(NBT_ROOMS, TownRoomsMapSerializer.INSTANCE.serializeNBT(roomsHandle.getRegisteredRooms()));
        tag.put(NBT_JOBS, TownWorkHandleSerializer.INSTANCE.serializeNBT(workHandle));
        QTNBT.put(tag, NBT_KNOWLEDGE, TownKnowledgeStoreSerializer.INSTANCE.serializeNBT(knowledgeHandle));
        QTNBT.put(tag, NBT_VILLAGERS, TownVillagerHandle.SERIALIZER.serialize(villagerHandle, Util.getTick(getServerLevel())));
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
        grantAdvancementOnApproach();

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
            t.questsHandle.initialize(t);
            t.roomsHandle.initialize(t);
            t.knowledgeHandle.initialize(t);
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
        WorksBehaviour.TownData td = new WorksBehaviour.TownData(prefix -> knowledgeHandle.getAllKnownGatherResults(
                getKnownBiomes(),
                prefix
        ));
        villagerHandle.stream()
                .filter(v -> v instanceof VisitorMobEntity)
                .map(v -> (VisitorMobEntity) v)
                .filter(e -> {
                    for (WorkRequest r : workHandle.getRequestedResults()) {
                        if (JobsRegistry.canSatisfy(td, e.getJobId(), r.asIngredient())) {
                            if (e.getStatusForServer().isBusy()) {
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
                double v = event.getEntity().distanceToSqr(
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

    void broadcastMessage(
            String key,
            Object... args
    ) {
        QT.FLAG_LOGGER.info("Broadcasting message: {} {}", key, args);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            p.displayClientMessage(Util.translatable(key, args), false);
        }
    }

    public ImmutableList<Quest<ResourceLocation, MCRoom>> getAllQuests() {
        return quests.getAll();
    }

    @Override
    public void roomRecipeCreated(
            MCRoom roomDoorPos,
            RoomRecipeMatch<MCRoom> match
    ) {
        swapBlocks(getServerLevel(), match);
        broadcastMessage(
                "messages.building.recipe_created",
                RoomRecipes.getName(match.getRecipeID()),
                roomDoorPos.getDoorPos().getUIString()
        );
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
        BlockPredicate predicate = BlockPredicate.Builder.block().of(BlockTags.SIGNS).build();
        for (Map.Entry<BlockPos, Block> e : room.getContainedBlocks().entrySet()) {
            if (!predicate.matches(level, e.getKey())) {
                continue;
            }
            // TODO: Get direction
//            Direction value = level.getBlockState(e.getKey()).getValue(HorizontalDirectionalBlock.FACING);
            Direction value = Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom());
            level.setBlockAndUpdate(e.getKey(), BlocksInit.JOB_BOARD_BLOCK.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, value)
            );
            registerJobsBoard(e.getKey());
            jobHandle.setJobBlockState(e.getKey(), AbstractWorkStatusStore.State.freshAtState(WorkSeekerJob.MAX_STATE));
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
        broadcastMessage(
                "messages.building.room_changed",
                Util.translatable("room." + oldMatchID.getPath()),
                Util.translatable("room." + newMatchID.getPath()),
                newRoom.getDoorPos().getUIString()
        );
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
        broadcastMessage(
                "messages.building.room_destroyed",
                Util.translatable("room." + oldRecipeId.getRecipeID().getPath()),
                roomDoorPos.getDoorPos().getUIString()
        );
        TownRooms.addParticles(getServerLevel(), roomDoorPos, ParticleTypes.SMOKE);
        quests.markQuestAsLost(roomDoorPos, oldRecipeId.getRecipeID());
    }

    @Override
    public void questCompleted(MCQuest quest) {
        broadcastMessage(
                "messages.town_flag.quest_completed",
                RoomRecipes.getName(quest.getWantedId())
        ); // TODO: Do this in a different quest listener (specialized in "messaging")
        setChanged();
        FireworkRocketEntity firework = new FireworkRocketEntity(
                level,
                getBlockPos().getX(),
                getBlockPos().getY() + 10,
                getBlockPos().getZ(),
                new ItemStack(Items.FIREWORK_ROCKET.getDefaultInstance().getItem(), 3)
        );
        level.addFreshEntity(firework);
    }

    @Override
    public void questLost(MCQuest quest) {
        broadcastMessage(
                "messages.town_flag.quest_lost",
                RoomRecipes.getName(quest.getWantedId())
        ); // TODO: Do this in a different quest listener (specialized in "messaging")
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
        // TODO: Town should have owners?
        BlockPos bp = getBlockPos();
        ServerPlayer p = (ServerPlayer) level.getNearestPlayer(
                bp.getX(),
                bp.getY(),
                bp.getZ(),
                100.0,
                false
        );
        AdvancementsInit.VISITOR_TRIGGER.trigger(
                p, VisitorTrigger.Triggers.FirstJobQuest
        );
    }

    @Override
    public boolean alreadyHasQuest(ResourceLocation resourceLocation) {
        return quests.alreadyRequested(resourceLocation);
    }

    @Override
    public void changeJobForVisitorFromBoard(UUID ownerUUID) {
        JobID work = getVillagerPreferredWork(ownerUUID, workHandle.getRequestedResults());
        if (work != null) {
            changeJobForVisitor(ownerUUID, work);
        }
    }

    private JobID getVillagerPreferredWork(
            UUID uuid,
            Collection<WorkRequest> requestedResults
    ) {
        Optional<LivingEntity> f = villagerHandle.stream().filter(v -> uuid.equals(v.getUUID())).findFirst();
        if (f.isEmpty()) {
            QT.BLOCK_LOGGER.error("No entities found for UUID: {}", uuid);
            return null;
        }
        LivingEntity ff = f.get();
        if (!(ff instanceof VisitorMobEntity v)) {
            QT.BLOCK_LOGGER.error("Entity is wrong type: {}", ff);
            return null;
        }

        Collection<ResourceLocation> mapBiomes = getKnownBiomes();
        WorksBehaviour.TownData data = new WorksBehaviour.TownData(prefix -> knowledgeHandle.getAllKnownGatherResults(
                mapBiomes, prefix
        ));

        List<JobID> preference = new ArrayList<>(JobsRegistry.getPreferredWorkIds(v.getJobId()));

        // TODO[TEST]: Allow work to be "claimed" so that if there are multiple
        //  requests that can be satisfied by one job, the villagers with that
        //  job will distribute themselves across those requests.

        // For now, we use randomization to give work requests a fair chance of being selected
        Collections.shuffle(preference);

        // TODO[ASAP]: Use a job attempt counter to determine which preference they choose
        //  With full random, the villager could theoretically never choose a job that
        //  is possible with the items currently in town. Under true random, they could
        //  potentially just keep choosing "gather with axe" over and over while there
        //  are no axes in town, without trying "gather with shovel" while there IS a
        //  shovel in town. Using a counter would allow the villager to consider every
        //  job option.

        for (JobID p : preference) {
            List<Ingredient> i = requestedResults.stream().map(WorkRequest::asIngredient).toList();
            for (Ingredient requestedResult : i) {
                // TODO: Think about how work chains work.
                //  E.g. If a blacksmith needs iron ingots to do a requested job,
                //  but none of the other villagers produce that resource, the
                //  blacksmith should light up red to indicate a broken chain and
                //  that the player will need to contribute in order for the
                //  blacksmith to work, rather than everything being automated.
                if (JobsRegistry.canSatisfy(data, p, requestedResult)) {
                    return p;
                }
            }
        }
        return null;
    }

    @Override
    public void changeJobForVisitor(
            UUID visitorUUID,
            JobID jobID
    ) {
        Optional<VisitorMobEntity> f = villagerHandle.stream()
                .filter(v -> v instanceof VisitorMobEntity)
                .map(v -> (VisitorMobEntity) v)
                .filter(v -> v.getUUID().equals(visitorUUID))
                .findFirst();
        if (f.isEmpty()) {
            QT.FLAG_LOGGER.error("Could not find entity {} to apply job change: {}", visitorUUID, jobID);
        } else {
            doSetJob(visitorUUID, jobID, f.get());
            setChanged();
        }
    }

    @SuppressWarnings("deprecation")
    private void doSetJob(
            UUID visitorUUID,
            JobID jobName,
            VisitorMobEntity f
    ) {
        f.setJob(JobsRegistry.getInitializedJob(this, jobName, f.getJobJournalSnapshot().items(), visitorUUID));
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
        ImmutableList<MCRoom> allRooms = roomsHandle.getAllRoomsIncludingMeta();
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
        return ImmutableSet.copyOf(villagerHandle.stream().map(Entity::getUUID).collect(Collectors.toSet()));
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
        return roomsHandle.getFarms().stream().max(Comparator.comparingInt(
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
        Set<String> allJobs = JobsRegistry.getAllJobs().stream()
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
    public ResourceLocation getRandomNearbyBiome() {
        if (nearbyBiomes.isEmpty()) {
            computeNearbyBiomes(level, getBlockPos(), this);
        }
        Biome biome = nearbyBiomes.get(getServerLevel().getRandom().nextInt(nearbyBiomes.size()));
        return ForgeRegistries.BIOMES.getKey(biome);
    }

    @Override
    public boolean isInitialized() {
        return isInitializedQuests && !nearbyBiomes.isEmpty() && initializers.isEmpty();
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
        if (!Util.getBlockStoredTagData(this).contains(NBT_TOWN_STATE)) {
            QT.FLAG_LOGGER.error(
                    "Villager entity exists but town state is missing. This is a bug and may cause unexpected behaviour.");
            return;
        }
        MCTownState state = TownStateSerializer.INSTANCE.load(
                Util.getBlockStoredTagData(this).getCompound(NBT_TOWN_STATE),
                sl, bp -> this.pois.getWelcomeMats().contains(bp)
        );
        Optional<TownState.VillagerData<MCHeldItem>> match = state.villagers.stream()
                .filter(v -> v.uuid.equals(visitorMobEntity.getUUID()))
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
    }

    public List<BlockPos> getWelcomeMats() {
        return pois.getWelcomeMats();
    }


    @Override
    public void validateEntity(VisitorMobEntity visitorMobEntity) {
        if (villagerHandle.exists(visitorMobEntity)) {
            return;
        }
        QT.FLAG_LOGGER.error("Visitor mob's parent has no record of entity. Removing visitor");
        visitorMobEntity.remove(Entity.RemovalReason.DISCARDED);
    }

    public void recallVillagers() {
        villagerHandle.forEach(v -> {
            BlockPos visitorJoinPos = getTownFlagBasePos();
            QT.FLAG_LOGGER.debug("Moving {} to {}", v, visitorJoinPos);
            v.setPos(visitorJoinPos.getX(), visitorJoinPos.getY(), visitorJoinPos.getZ());
            v.setHealth(v.getMaxHealth());
        });
    }

    public void registerJobsBoard(BlockPos matPos) {
        this.workHandle.registerJobBoard(matPos);
        this.setChanged();
    }

    public boolean hasJobBoard() {
        return workHandle.hasAtLeastOneBoard();
    }

    public void openJobsMenu(ServerPlayer sender) {
        workHandle.openMenuRequested(sender);
    }

    public Collection<ResourceLocation> getKnownBiomes() {
        ImmutableSet.Builder<ResourceLocation> b = ImmutableSet.builder();
        List<ContainerTarget<MCContainer, MCTownItem>> cs = TownContainers.getAllContainers(this, getServerLevel());
        cs.forEach(v -> {
            v.getItems()
                    .stream()
                    .filter(i -> ItemsInit.GATHERER_MAP.get().equals(i.get()))
                    .map(i -> GathererMap.getBiome(i.toItemStack()))
                    .filter(Objects::nonNull)
                    .forEach(b::add);
        });
        nearbyBiomes.forEach(v -> {
            ResourceLocation key = ForgeRegistries.BIOMES.getKey(v);
            if (key == null) {
                return;
            }
            b.add(key);
        });
        b.add(Loots.fallbackBiome);
        return b.build();
    }

    public void warpTime(int ticks) {
        state.warp(this, Util.getBlockStoredTagData(this), getServerLevel(), ticks);
    }

    public void freezeVillagers(Integer ticks) {
        villagerHandle.stream()
                .filter(VisitorMobEntity.class::isInstance)
                .map(VisitorMobEntity.class::cast)
                .forEach(v -> v.freeze(ticks));
    }

    public VillagerHolder getVillagerHandle() {
        return villagerHandle;
    }

    public int getY() {
        return getTownFlagBasePos().getY();
    }
}
