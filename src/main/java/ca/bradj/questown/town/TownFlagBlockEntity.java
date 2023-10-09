package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.ScheduledBlock;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.advancements.ApproachTownTrigger;
import ca.bradj.questown.core.advancements.VisitorTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.integration.minecraft.*;
import ca.bradj.questown.jobs.JobsRegistry;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.mobs.visitor.ContainerTarget;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.*;
import ca.bradj.questown.town.rooms.TownRoomsMap;
import ca.bradj.questown.town.rooms.TownRoomsMapSerializer;
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
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

import static ca.bradj.questown.town.TownFlagState.NBT_LAST_TICK;
import static ca.bradj.questown.town.TownFlagState.NBT_TOWN_STATE;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL;

public class TownFlagBlockEntity extends BlockEntity implements TownInterface, ActiveRecipes.ChangeListener<MCRoom, RoomRecipeMatch<MCRoom>>, QuestBatch.ChangeListener<MCQuest>, TownPois.Listener {

    public static final String ID = "flag_base_block_entity";
    // TODO: Extract serialization
    public static final String NBT_QUEST_BATCHES = String.format("%s_quest_batches", Questown.MODID);
    public static final String NBT_MORNING_REWARDS = String.format("%s_morning_rewards", Questown.MODID);
    public static final String NBT_ASAP_QUESTS = String.format("%s_asap_quests", Questown.MODID);
    public static final String NBT_WELCOME_MATS = String.format("%s_welcome_mats", Questown.MODID);
    public static final String NBT_ROOMS = String.format("%s_rooms", Questown.MODID);
    private final TownRoomsMap roomsMap = new TownRoomsMap(this);
    private final TownQuests quests = new TownQuests();
    private final TownPois pois = new TownPois();
    private final MCMorningRewards morningRewards = new MCMorningRewards(this);
    private final MCAsapRewards asapRewards = new MCAsapRewards();
    private final Stack<PendingQuests> asapRandomAwdForVisitor = new Stack<>();
    private final UUID uuid = UUID.randomUUID();
    private final TownFlagState state = new TownFlagState(this);
    public long advancedTimeOnTick = -1;
    List<LivingEntity> entities = new ArrayList<>();
    private boolean isInitializedQuests = false;
    private boolean everScanned = false;
    private boolean changed = false;
    private final ArrayList<Biome> nearbyBiomes = new ArrayList<>();

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


    public TownFlagBlockEntity(
            BlockPos p_155229_,
            BlockState p_155230_
    ) {
        super(TilesInit.TOWN_FLAG.get(), p_155229_, p_155230_);
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

        boolean stateChanged = e.state.tick(e, e.getTileData(), sl);

        if ((stateChanged || e.changed) && e.everScanned) {
            e.state.putStateOnTile(e.getTileData(), e.uuid);
            e.changed = false;
            setChanged(level, blockPos, state);
        }

        e.quests.tick(e);

        long gameTime = level.getGameTime();
        long l = gameTime % 10;
        if (l != 0) {
            return;
        }

        if (e.nearbyBiomes.isEmpty()) {
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

        e.roomsMap.tick(sl, blockPos);

        advanceScheduledBlocks(sl, e.roomsMap);

        e.asapRewards.tick();

        e.pois.tick(sl, blockPos);

        e.everScanned = true;

        profileTick(e, start);
    }

    private static void profileTick(
            TownFlagBlockEntity e,
            long start
    ) {
        if (Config.TICK_SAMPLING_RATE.get() > 0) {
            long end = System.currentTimeMillis();
            e.times.add((int) (end - start));

            if (e.times.size() > Config.TICK_SAMPLING_RATE.get()) {
                Questown.LOGGER.debug(
                        "Average tick length: {}",
                        e.times.stream().mapToInt(Integer::intValue).average()
                );
                e.times.clear();
            }
        }
    }

    private static void advanceScheduledBlocks(
            ServerLevel level,
            TownRoomsMap roomsMap
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
    }

    public static boolean debuggerReleaseControl() {
        GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().getWindow(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        return true;
    }

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
        // TODO: Store active rooms? (Cost to re-compute is low, I think)
        if (tag.contains(NBT_ROOMS)) {
            TownRoomsMapSerializer.INSTANCE.deserialize(tag.getCompound(NBT_ROOMS), this, roomsMap);
        }
        if (tag.contains(NBT_QUEST_BATCHES)) {
            CompoundTag data = tag.getCompound(NBT_QUEST_BATCHES);
            MCQuestBatches.SERIALIZER.deserializeNBT(this, data, quests.questBatches); // TODO: Serialize "quests"
            this.isInitializedQuests = true;
        }
        if (tag.contains(NBT_MORNING_REWARDS)) {
            CompoundTag data = tag.getCompound(NBT_MORNING_REWARDS);
            this.morningRewards.deserializeNbt(this, data);
        }
        if (tag.contains(NBT_ASAP_QUESTS)) {
            CompoundTag data = tag.getCompound(NBT_ASAP_QUESTS);
            Collection<PendingQuests> l = PendingQuestsSerializer.INSTANCE.deserializeNBT(this, data);
            l.forEach(this.asapRandomAwdForVisitor::push);
        }
        if (tag.contains(NBT_WELCOME_MATS)) {
            ListTag data = tag.getList(NBT_WELCOME_MATS, Tag.TAG_COMPOUND);
            Collection<BlockPos> l = WelcomeMatsSerializer.INSTANCE.deserializeNBT(data);
            l.forEach(this.pois::registerWelcomeMat);
        }
//        state.load(tag);

    }

    private void writeTownData(CompoundTag tag) {
        if (roomsMap.numRecipes() > 0) {
//            tag.put(NBT_ACTIVE_RECIPES, ActiveRecipesSerializer.INSTANCE.serializeNBT(roomsMap.getRecipes(0)));
        }
        tag.put(NBT_QUEST_BATCHES, MCQuestBatches.SERIALIZER.serializeNBT(quests.questBatches));
        tag.put(NBT_MORNING_REWARDS, this.morningRewards.serializeNbt());
        tag.put(NBT_ASAP_QUESTS, PendingQuestsSerializer.INSTANCE.serializeNBT(this.asapRandomAwdForVisitor));
        tag.put(NBT_WELCOME_MATS, WelcomeMatsSerializer.INSTANCE.serializeNBT(pois.getWelcomeMats()));
        tag.put(NBT_ROOMS, TownRoomsMapSerializer.INSTANCE.serializeNBT(roomsMap));
        // TODO: Serialization for ASAPss
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        this.writeTownData(tag);
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
        if (!this.isInitializedQuests) {
            this.setUpQuestsForNewlyPlacedFlag();
        }
        this.quests.setChangeListener(this);
        this.roomsMap.addChangeListener(this);
        this.pois.setListener(this);
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2);
        if (!level.isClientSide()) {
            TownFlags.register(uuid, this);
        }
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
                Questown.LOGGER.trace("Distance {}", v);
                if (v < 100) {
                    AdvancementsInit.APPROACH_TOWN_TRIGGER.trigger(
                            sp, ApproachTownTrigger.Triggers.FirstVisit
                    );
                }
            }
        });
    }

    private void setUpQuestsForNewlyPlacedFlag() {
        TownQuests.setUpQuestsForNewlyPlacedFlag(this, quests);
        this.isInitializedQuests = true;
        setChanged();
    }

    void broadcastMessage(TranslatableComponent msg) {
        Questown.LOGGER.info("Broadcasting message: {} {}", msg.getKey(), msg.getArgs());
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            p.sendMessage(msg, ChatType.CHAT, p.getUUID());
        }
    }

    public ImmutableList<Quest<ResourceLocation, MCRoom>> getAllQuests() {
        return quests.getAll();
    }

    private void broadcastQuestToChat(
            ServerLevel level,
            RoomRecipe recipe
    ) {
        Component recipeName = RoomRecipes.getName(recipe.getId());
        TranslatableComponent questName = new TranslatableComponent("quests.build_a", recipeName);
        TranslatableComponent questAdded = new TranslatableComponent("messages.town_flag.quest_added", questName);
        level.getServer().getPlayerList().broadcastMessage(questAdded, ChatType.CHAT, null);
    }

    @Override
    public void roomRecipeCreated(
            MCRoom roomDoorPos,
            RoomRecipeMatch match
    ) {
        broadcastMessage(new TranslatableComponent(
                "messages.building.recipe_created",
                RoomRecipes.getName(match.getRecipeID()),
                roomDoorPos.getDoorPos().getUIString()
        ));
        // TODO: get room for rendering effect
//        handleRoomChange(room, ParticleTypes.HAPPY_VILLAGER);
        quests.markQuestAsComplete(roomDoorPos, match.getRecipeID());
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
        broadcastMessage(new TranslatableComponent(
                "messages.building.room_changed",
                new TranslatableComponent("room." + oldMatchID.getPath()),
                new TranslatableComponent("room." + newMatchID.getPath()),
                newRoom.getDoorPos().getUIString()
        ));
        TownRooms.addParticles(getServerLevel(), newRoom, ParticleTypes.FLASH);
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
        broadcastMessage(new TranslatableComponent(
                "messages.building.room_destroyed",
                new TranslatableComponent("room." + oldRecipeId.getRecipeID().getPath()),
                roomDoorPos.getDoorPos().getUIString()
        ));
        TownRooms.addParticles(getServerLevel(), roomDoorPos, ParticleTypes.SMOKE);
        quests.markQuestAsLost(roomDoorPos, oldRecipeId.getRecipeID());
    }

    @Override
    public void questCompleted(MCQuest quest) {
        broadcastMessage(new TranslatableComponent(
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
        broadcastMessage(new TranslatableComponent(
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
    public void changeJobForVisitor(
            UUID visitorUUID,
            String jobName
    ) {
        Optional<VisitorMobEntity> f = entities.stream()
                .filter(v -> v instanceof VisitorMobEntity)
                .map(v -> (VisitorMobEntity) v)
                .filter(v -> v.getUUID().equals(visitorUUID))
                .findFirst();
        if (f.isEmpty()) {
            Questown.LOGGER.error("Could not find entity {} to apply job change: {}", visitorUUID, jobName);
        } else {
            f.get().setJob(JobsRegistry.getInitializedJob(this, jobName, null, visitorUUID));
        }
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
                QT.LOGGER.debug("Target is {} blocks away from {}", dist, avoiding);
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
        QT.LOGGER.debug("Registered entity with town {}: {}", uuid, vEntity);
        this.entities.add(vEntity);
        vEntity.addChangeListener(() -> {
            Questown.LOGGER.debug("Setting changed");
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
        QT.LOGGER.debug("No welcome mats found, falling back to {}", fallback);
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
    public Collection<String> getAvailableJobs() {
        // TODO: Scan villagers to make this decision
        return ImmutableList.of("farmer", "baker");
    }

    @Override
    public boolean hasEnoughBeds() {
        long beds = roomsMap.getAllMatches().stream()
                .flatMap(v -> v.getContainedBlocks().values().stream())
                .filter(v -> Ingredient.of(ItemTags.BEDS).test(new ItemStack(v.asItem())))
                .count();
        return (beds / 2) >= entities.size();
    }

    @Override
    public ResourceLocation getRandomNearbyBiome() {
        return nearbyBiomes.get(getServerLevel().getRandom().nextInt(nearbyBiomes.size())).getRegistryName();
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
        for (MCReward r : this.morningRewards.getChildren()) {
            this.asapRewards.push(r);
        }
        this.setChanged();
        for (LivingEntity e : entities) {
            e.stopSleeping();
        }
        getTileData().putLong(NBT_LAST_TICK, newTime);
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
        if (!getTileData().contains(NBT_TOWN_STATE)) {
            Questown.LOGGER.error(
                    "Villager entity exists but town state is missing. This is a bug and may cause unexpected behaviour.");
            return;
        }
        TownState<MCContainer, MCTownItem, MCHeldItem> state = TownStateSerializer.INSTANCE.load(
                getTileData().getCompound(NBT_TOWN_STATE),
                sl, bp -> this.pois.getWelcomeMats().contains(bp)
        );
        Optional<TownState.VillagerData<MCHeldItem>> match = state.villagers.stream()
                .filter(v -> v.uuid.equals(visitorMobEntity.getUUID()))
                .findFirst();
        if (match.isEmpty()) {
            Questown.LOGGER.error(
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
        QT.LOGGER.error("Visitor mob's parent has no record of entity. Removing visitor");
        visitorMobEntity.remove(Entity.RemovalReason.DISCARDED);
    }

    public void recallVillagers() {
        entities.forEach(v -> {
            Vec3 visitorJoinPos = getVisitorJoinPos();
            QT.LOGGER.debug("Moving {} to {}", v, visitorJoinPos);
            v.setPos(visitorJoinPos);
        });
    }
}
