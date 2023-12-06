package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.ScheduledBlock;
import ca.bradj.questown.blocks.TownFlagSubBlocks;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.advancements.ApproachTownTrigger;
import ca.bradj.questown.core.advancements.VisitorTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.integration.minecraft.*;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.JobsRegistry;
import ca.bradj.questown.jobs.declarative.WorkSeekerJob;
import ca.bradj.questown.jobs.gatherer.GathererTools;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.QuestsHolder;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import ca.bradj.questown.town.quests.*;
import ca.bradj.questown.town.rooms.TownRoomsMap;
import ca.bradj.questown.town.rooms.TownRoomsMapSerializer;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import ca.bradj.roomrecipes.recipes.ActiveRecipes;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
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
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static ca.bradj.questown.town.TownFlagState.NBT_LAST_TICK;
import static ca.bradj.questown.town.TownFlagState.NBT_TOWN_STATE;

public class TownFlagBlockEntity extends BlockEntity implements TownInterface, ActiveRecipes.ChangeListener<MCRoom, RoomRecipeMatch<MCRoom>>, QuestBatch.ChangeListener<MCQuest>, TownPois.Listener {

    public static final String ID = "flag_base_block_entity";
    // TODO: Extract serialization
    public static final String NBT_QUEST_BATCHES = String.format("%s_quest_batches", Questown.MODID);
    public static final String NBT_MORNING_REWARDS = String.format("%s_morning_rewards", Questown.MODID);
    public static final String NBT_WELCOME_MATS = String.format("%s_welcome_mats", Questown.MODID);
    public static final String NBT_ROOMS = String.format("%s_rooms", Questown.MODID);
    public static final String NBT_JOBS = String.format("%s_jobs", Questown.MODID);
    private final TownRoomsMap roomsMap = new TownRoomsMap(this);
    final TownQuests quests = new TownQuests();
    private final TownFlagSubBlocks subBlocks = new TownFlagSubBlocks(getBlockPos());
    private final TownPois pois = new TownPois(subBlocks);
    private final MCMorningRewards morningRewards = new MCMorningRewards(this);
    private final MCAsapRewards asapRewards = new MCAsapRewards();
    private final UUID uuid = UUID.randomUUID();
    private final TownFlagState state = new TownFlagState(this);
    public long advancedTimeOnTick = -1;
    final List<LivingEntity> entities = new ArrayList<>();
    private boolean isInitializedQuests = false;
    private boolean everScanned = false;
    private boolean changed = false;
    private final ArrayList<Biome> nearbyBiomes = new ArrayList<>();

    // Farmer specific stuff
    private final ArrayList<UUID> assignedFarmers = new ArrayList<>();
    private final ArrayList<BlockPos> blocksWithWeeds = new ArrayList<>();

    private final ArrayList<Integer> times = new ArrayList<>();
    private final MCRoom flagMetaRoom = new MCRoom(
            Positions.FromBlockPos(getBlockPos().offset(1, 0, 0)),
            ImmutableList.of(new InclusiveSpace(
                    // TODO: Add 2 to config?
                    Positions.FromBlockPos(getBlockPos()).offset(-2, -2),
                    Positions.FromBlockPos(getBlockPos()).offset(2, 2)
            )),
            getBlockPos().getY()
    );
    private final TownWorkStatusStore jobHandle = new TownWorkStatusStore();
    final TownWorkHandle workHandle = new TownWorkHandle(subBlocks, getBlockPos());
    private final Stack<Long> mornings = new Stack<>();
    private final LinkedBlockingQueue<Consumer<TownFlagBlockEntity>> initializers = new LinkedBlockingQueue<>();

    // TODO: Move all quest-related stuff into the handle
    private TownQuestsHandle questsHandle = new TownQuestsHandle();

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

        long start = System.currentTimeMillis();

        if (!e.initializers.isEmpty()) {
            QT.FLAG_LOGGER.info("Running initializer ({} left)", e.initializers.size() - 1);
            e.initializers.remove().accept(e);
            return;
        }

        e.subBlocks.parentTick(sl);

        if (!e.mornings.empty()) {
            e.morningTick(e.mornings.pop());
        }

        boolean stateChanged = e.state.tick(e, e.getPersistentData(), sl);

        if ((stateChanged || e.changed) && e.everScanned) {
            e.state.putStateOnTile(e.getPersistentData(), e.uuid);
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

        e.roomsMap.tick(sl, blockPos);

        // TODO: Do this less often?
        e.jobHandle.tick(sl, e.roomsMap.getAllRooms());

        advanceScheduledBlocks(sl, e.roomsMap, e.blocksWithWeeds);

        e.asapRewards.tick();

        e.pois.tick(sl, blockPos);

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
        for (LivingEntity e : entities) {
            e.stopSleeping();
        }
        getPersistentData().putLong(NBT_LAST_TICK, newTime);
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

    private static void advanceScheduledBlocks(
            ServerLevel level,
            TownRoomsMap roomsMap,
            List<BlockPos> blocksWithWeeds
    ) {
        for (RoomRecipeMatch<MCRoom> r : roomsMap.getAllMatches()) {
            for (Map.Entry<BlockPos, Block> b : r.getContainedBlocks().entrySet()) {
                if (b.getValue() instanceof ScheduledBlock sb) {
                    BlockState bs = sb.tryAdvance(level, level.getBlockState(b.getKey()));
                    if (bs != null) {
                        level.setBlock(b.getKey(), bs, 11);
                    }
                }
            }
        }

        for (MCRoom r : roomsMap.getFarms()) {
            // TODO: Handle rooms with multiple spaces
            for (Position p : InclusiveSpaces.getAllEnclosedPositions(r.getSpace())) {
                BlockPos bp = Positions.ToBlock(p, r.yCoord);
                BlockPos gp = bp.below();
                if (blocksWithWeeds.contains(gp)) {
                    continue;
                }
                if (level.getRandom().nextInt(Config.FARMER_WEEDS_RARITY.get()) == 0) {
                    blocksWithWeeds.add(gp);
                }
            }
        }
    }

//    public static boolean debuggerReleaseControl() {
//        GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().getWindow(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
//        return true;
//    }

    public void setChanged() {
        super.setChanged();
        this.changed = true;
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
        this.writeTownData(tag);
        if (!level.isClientSide() && everScanned) {
            MCTownState state = this.state.captureState();
            if (state == null) {
                return;
            }
            tag.put(NBT_TOWN_STATE, TownStateSerializer.INSTANCE.store(state));
        }
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

        loadNextTick(tag, initializers);
    }

    private static void loadNextTick(CompoundTag tag, Queue<Consumer<TownFlagBlockEntity>> initializers) {
        // TODO: Store active rooms? (Cost to re-compute is low, I think)
        if (!tag.contains("side")) {
            return;
        }
        if (!tag.getString("side").equals("server")) {
            return;
        }

        if (tag.contains(NBT_ROOMS)) {
            initializers.add(t ->
                    TownRoomsMapSerializer.INSTANCE.deserialize(tag.getCompound(NBT_ROOMS), t, t.roomsMap)
            );
        }
        if (tag.contains(NBT_QUEST_BATCHES)) {
            initializers.add(t -> {
                CompoundTag data = tag.getCompound(NBT_QUEST_BATCHES);
                MCQuestBatches.SERIALIZER.deserializeNBT(t, data, t.quests.questBatches);
                t.isInitializedQuests = true;
            });
        }
        if (tag.contains(NBT_MORNING_REWARDS)) {
            initializers.add(t -> {
                CompoundTag data = tag.getCompound(NBT_MORNING_REWARDS);
                t.morningRewards.deserializeNbt(t, data);
            });
        }
        if (tag.contains(NBT_WELCOME_MATS)) {
            initializers.add(t -> {
                ListTag data = tag.getList(NBT_WELCOME_MATS, Tag.TAG_COMPOUND);
                Collection<BlockPos> l = WelcomeMatsSerializer.INSTANCE.deserializeNBT(data);
                l.forEach(t.pois::registerWelcomeMat);
            });
        }
        if (tag.contains(NBT_JOBS)) {
            initializers.add(t ->
                    TownWorkHandleSerializer.INSTANCE.deserializeNBT(tag.getCompound(NBT_JOBS), t.workHandle)
            );
        }
//        state.load(tag);

    }

    private void writeTownData(CompoundTag tag) {
        if (level == null) {
            return;
        }
        if (level.isClientSide()) {
            tag.putString("side", "client");
            return;
        } else {
            tag.putString("side", "server");
        }
        if (roomsMap.numRecipes() > 0) {
//            tag.put(NBT_ACTIVE_RECIPES, ActiveRecipesSerializer.INSTANCE.serializeNBT(roomsMap.getRecipes(0)));
        }
        tag.put(NBT_QUEST_BATCHES, MCQuestBatches.SERIALIZER.serializeNBT(quests.questBatches));
        tag.put(NBT_MORNING_REWARDS, this.morningRewards.serializeNbt());
        tag.put(NBT_WELCOME_MATS, WelcomeMatsSerializer.INSTANCE.serializeNBT(pois.getWelcomeMats()));
        tag.put(NBT_ROOMS, TownRoomsMapSerializer.INSTANCE.serializeNBT(roomsMap));
        tag.put(NBT_JOBS, TownWorkHandleSerializer.INSTANCE.serializeNBT(workHandle));
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
            t.roomsMap.addChangeListener(t);
            t.pois.setListener(t);
            t.workHandle.addChangeListener(t::setChanged);
            t.questsHandle.initialize(t);
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2);
            if (!level.isClientSide()) {
                TownFlags.register(uuid, t);
            }
        });
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
    public QuestsHolder getQuestHandle() {
        return questsHandle;
    }

    private void setUpQuestsForNewlyPlacedFlag() {
        TownQuests.setUpQuestsForNewlyPlacedFlag(this, quests);
        this.isInitializedQuests = true;
        setChanged();
    }

    void broadcastMessage(TranslatableContents c) {

        QT.FLAG_LOGGER.info("Broadcasting message: {} {}", c.getKey(), c.getArgs());
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            p.displayClientMessage(MutableComponent.create(c), false);
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
        broadcastMessage(new TranslatableContents(
                "messages.building.recipe_created",
                RoomRecipes.getName(match.getRecipeID()),
                roomDoorPos.getDoorPos().getUIString()
        ));
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
            level.setBlockAndUpdate(e.getKey(), BlocksInit.JOB_BOARD_BLOCK.get().defaultBlockState());
            registerJobsBoard(e.getKey());
            jobHandle.setJobBlockState(e.getKey(), new TownWorkStatusStore.State(WorkSeekerJob.MAX_STATE, 0, 0));
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
        broadcastMessage(new TranslatableContents(
                "messages.building.room_changed",
                new TranslatableContents("room." + oldMatchID.getPath()),
                new TranslatableContents("room." + newMatchID.getPath()),
                newRoom.getDoorPos().getUIString()
        ));
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
        broadcastMessage(new TranslatableContents(
                "messages.building.room_destroyed",
                new TranslatableContents("room." + oldRecipeId.getRecipeID().getPath()),
                roomDoorPos.getDoorPos().getUIString()
        ));
        TownRooms.addParticles(getServerLevel(), roomDoorPos, ParticleTypes.SMOKE);
        quests.markQuestAsLost(roomDoorPos, oldRecipeId.getRecipeID());
    }

    @Override
    public void questCompleted(MCQuest quest) {
        broadcastMessage(new TranslatableContents(
                "messages.town_flag.quest_completed",
                RoomRecipes.getName(quest.getWantedId())
        )); // TODO: Do this in a different quest listener (specialized in "messaging")
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
        broadcastMessage(new TranslatableContents(
                "messages.town_flag.quest_lost",
                RoomRecipes.getName(quest.getWantedId())
        )); // TODO: Do this in a different quest listener (specialized in "messaging")
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

    private final Map<ResourceLocation, Map<GathererTools.LootTablePrefix, ImmutableSet<MCTownItem>>> knownGatherResults = new HashMap<>();

    @Override
    public ImmutableSet<ItemStack> getAllKnownGatherResults(
            GathererTools.LootTablePrefix ltPrefix
    ) {
        ImmutableSet.Builder<ItemStack> b = ImmutableSet.builder();
        knownGatherResults.values()
                .stream()
                .flatMap(v -> v.getOrDefault(ltPrefix, ImmutableSet.of()).stream())
                .forEach(v -> b.add(v.toItemStack()));
        return b.build();
    }

    @Override
    public void registerFoundLoots(ImmutableList<MCHeldItem> items) {
        items.forEach(item -> {
            if (item.foundInBiome() == null) {
                return;
            }
            String o = item.foundInBiome();
            ResourceLocation bId = new ResourceLocation(o);
            Map<GathererTools.LootTablePrefix, ImmutableSet<MCTownItem>> biome = knownGatherResults.get(bId);
            if (biome == null) {
                biome = new HashMap<>();
            }
            String lootPrefix = item.acquiredViaLootTablePrefix();
            if (lootPrefix == null) {
                QT.FLAG_LOGGER.error("Found item has no loot table prefix");
                return;
            }
            GathererTools.LootTablePrefix ltp = new GathererTools.LootTablePrefix(lootPrefix);
            Set<MCTownItem> known = new HashSet<>(biome.get(ltp));
            int sizeBefore = known.size();
            known.add(item.get());
            if (sizeBefore != known.size()) {
                QT.FLAG_LOGGER.debug("New item recorded as 'known': {}", item);
            }
            biome.put(ltp, ImmutableSet.copyOf(known));
        });
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
        Optional<LivingEntity> f = entities.stream().filter(v -> uuid.equals(v.getUUID())).findFirst();
        if (f.isEmpty()) {
            QT.BLOCK_LOGGER.error("No entities found for UUID: {}", uuid);
            return null;
        }
        LivingEntity ff = f.get();
        if (!(ff instanceof VisitorMobEntity v)) {
            QT.BLOCK_LOGGER.error("Entity is wrong type: {}", ff);
            return null;
        }

        JobsRegistry.TownData data = new JobsRegistry.TownData(this::getAllKnownGatherResults);

        Collection<JobID> preference = JobsRegistry.getPreferredWorkIds(v.getJobId());
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
        Optional<VisitorMobEntity> f = entities.stream()
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
        return entities.stream()
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
        Collection<MCRoom> all = roomsMap.getAllRooms();
        ImmutableList.Builder<MCRoom> b = ImmutableList.builder();
        b.addAll(roomsMap.getAllRooms());
        b.add(flagMetaRoom);

        return pois.getWanderTarget(getServerLevel(), b.build(), (p, r) -> {
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
        return ImmutableSet.copyOf(this.entities.stream().map(Entity::getUUID).collect(Collectors.toSet()));
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
        this.entities.add(vEntity);
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
    public Collection<RoomRecipeMatch<MCRoom>> getRoomsMatching(ResourceLocation recipeId) {
        return roomsMap.getRoomsMatching(recipeId);
    }

    @Override
    public Collection<MCRoom> getFarms() {
        return roomsMap.getFarms();
    }

    @Override
    public void markBlockWeeded(BlockPos p) {
        this.blocksWithWeeds.remove(p);
    }

    @Override
    public WorkStatusHandle getWorkStatusHandle() {
        return jobHandle;
    }

    @Override
    public WorkHandle getWorkHandle() {
        return workHandle;
    }

    @Override
    public Optional<MCRoom> assignToFarm(UUID ownerUUID) {
        int idx = this.assignedFarmers.indexOf(ownerUUID);
        List<MCRoom> farms = ImmutableList.copyOf(roomsMap.getFarms());
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
        return roomsMap.getFarms().stream().max(Comparator.comparingInt(
                v -> v.getSpaces()
                        .stream()
                        .map(InclusiveSpaces::calculateArea)
                        .mapToInt(Double::intValue)
                        .sum()
        ));
    }

    @Override
    public Collection<BlockPos> findMatchedRecipeBlocks(MatchRecipe mr) {
        ImmutableList.Builder<BlockPos> b = ImmutableList.builder();
        for (RoomRecipeMatch<MCRoom> i : roomsMap.getAllMatches()) {
            for (Map.Entry<BlockPos, Block> j : i.getContainedBlocks().entrySet()) {
                if (mr.doesMatch(j.getValue())) {
                    b.add(j.getKey());
                }
            }
        }
        return b.build();
    }

    @Override
    public Collection<String> getAvailableRootJobs() {
        // TODO: Scan villagers to make this decision
        Set<String> allJobs = JobsRegistry.getAllJobs().stream()
                .map(JobID::rootId)
                .collect(Collectors.toSet());
        Set<String> allFilledJobs = entities.stream()
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
        // TODO: This returns false positives if called before entities have been loaded from tile data
        long beds = roomsMap.getAllMatches().stream()
                .flatMap(v -> v.getContainedBlocks().values().stream())
                .filter(v -> Ingredient.of(ItemTags.BEDS).test(new ItemStack(v.asItem())))
                .count();
        if (beds == 0 && entities.isEmpty()) {
            return false;
        }
        return (beds / 2) >= entities.size();
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
        return isInitializedQuests && !nearbyBiomes.isEmpty();
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

    public Collection<RoomRecipeMatch<MCRoom>> getMatches() {
        return this.roomsMap.getAllMatches();
    }

    public void assumeStateFromTown(
            VisitorMobEntity visitorMobEntity,
            ServerLevel sl
    ) {
        if (!getPersistentData().contains(NBT_TOWN_STATE)) {
            QT.FLAG_LOGGER.error(
                    "Villager entity exists but town state is missing. This is a bug and may cause unexpected behaviour.");
            return;
        }
        TownState<MCContainer, MCTownItem, MCHeldItem> state = TownStateSerializer.INSTANCE.load(
                getPersistentData().getCompound(NBT_TOWN_STATE),
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

    public void registerDoor(BlockPos clickedPos) {
        roomsMap.registerDoor(Positions.FromBlockPos(clickedPos), clickedPos.getY() - getTownFlagBasePos().getY());
        setChanged();
    }

    @Override
    public void registerFenceGate(BlockPos clickedPos) {
        roomsMap.registerFenceGate(Positions.FromBlockPos(clickedPos), clickedPos.getY() - getTownFlagBasePos().getY());
        setChanged();
    }

    @Override
    public void validateEntity(VisitorMobEntity visitorMobEntity) {
        if (entities.contains(visitorMobEntity)) {
            return;
        }
        QT.FLAG_LOGGER.error("Visitor mob's parent has no record of entity. Removing visitor");
        visitorMobEntity.remove(Entity.RemovalReason.DISCARDED);
    }

    public void recallVillagers() {
        entities.forEach(v -> {
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
}
