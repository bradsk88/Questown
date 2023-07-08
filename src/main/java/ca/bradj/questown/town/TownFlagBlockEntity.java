package ca.bradj.questown.town;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.advancements.ApproachTownTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.TownStateSerializer;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.mobs.visitor.ContainerTarget;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.*;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.recipes.ActiveRecipes;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.lwjgl.glfw.GLFW.GLFW_CURSOR;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL;

public class TownFlagBlockEntity extends BlockEntity implements TownInterface, ActiveRecipes.ChangeListener<RoomRecipeMatch>, QuestBatch.ChangeListener<MCQuest>, TownPois.Listener {

    public static final String ID = "flag_base_block_entity";
    // TODO: Extract serialization
    public static final String NBT_QUEST_BATCHES = String.format("%s_quest_batches", Questown.MODID);
    public static final String NBT_ACTIVE_RECIPES = String.format("%s_active_recipes", Questown.MODID);
    public static final String NBT_MORNING_REWARDS = String.format("%s_morning_rewards", Questown.MODID);
    public static final String NBT_ASAP_QUESTS = String.format("%s_asap_quests", Questown.MODID);
    public static final String NBT_LAST_TICK = String.format("%s_last_tick", Questown.MODID);
    private static final String NBT_TOWN_STATE = String.format("%s_town_state", Questown.MODID);
    private final TownRoomsMap roomsMap = new TownRoomsMap(this);
    private final TownQuests quests = new TownQuests();
    private final TownPois pois = new TownPois();
    private final MCMorningRewards morningRewards = new MCMorningRewards(this);
    private final MCAsapRewards asapRewards = new MCAsapRewards();
    private final Stack<PendingQuests> asapRandomAwdForVisitor = new Stack<>();
    private final UUID uuid = UUID.randomUUID();
    private boolean isInitializedQuests = false;
    private List<LivingEntity> entities = new ArrayList<>();
    private final Stack<Function<ServerLevel, TownState<MCTownItem>>> townInit = new Stack<>();
    private boolean hasPlayerEverBeenNear;
    private long lastTick = -1;
    private boolean changed = false;

    public TownFlagBlockEntity(
            BlockPos p_155229_,
            BlockState p_155230_
    ) {
        super(TilesInit.TOWN_FLAG.get(), p_155229_, p_155230_);
    }

    public void setChanged() {
        super.setChanged();
        this.changed = true;
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

        if (e.changed) {
            e.putStateOnTile();
            e.changed = false;
        }

        // TODO: Use this for recovering mobs
        long lastTick = e.getTileData().getLong(NBT_LAST_TICK);
        long timeSinceWake = level.getGameTime() - lastTick;
        boolean waking = timeSinceWake > 10;
        e.getTileData().putLong(NBT_LAST_TICK, level.getGameTime());

        if (waking) {
            Questown.LOGGER.debug("Recovering villagers due to player return (last near {} ticks ago)", timeSinceWake);
            recoverMobs(level, e, sl);
        }

        e.quests.tick(sl);

        long gameTime = level.getGameTime();
        long l = gameTime % 10;
        if (l != 0) {
            return;
        }

        e.roomsMap.tick(sl, blockPos);

        e.asapRewards.tick();

        e.pois.tick(sl, blockPos);
    }

    private static void recoverMobs(
            Level level,
            TownFlagBlockEntity e,
            ServerLevel sl
    ) {
        ImmutableList<LivingEntity> entitiesSnapshot = ImmutableList.copyOf(e.entities);
        for (LivingEntity entity : entitiesSnapshot) {
            e.entities.remove(entity);
            entity.remove(Entity.RemovalReason.DISCARDED);
        }

        if (e.getTileData().contains(NBT_TOWN_STATE)) {
            TownState<MCTownItem> storedState = TownStateSerializer.INSTANCE.load(
                    e.getTileData().getCompound(NBT_TOWN_STATE),
                    sl
            );
            Set<UUID> uuids = entitiesSnapshot.stream().map(Entity::getUUID).collect(Collectors.toSet());
            for (TownState.VillagerData<MCTownItem> v : storedState.villagers) {
                VisitorMobEntity recovered = new VisitorMobEntity(sl, e);
                recovered.initialize(v.uuid, new BlockPos(v.position.x, v.yPosition, v.position.z), v.journal);
                level.addFreshEntity(recovered);
                e.registerEntity(recovered);
            }
            Questown.LOGGER.debug("Loaded state from NBT: {}", storedState);
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
        this.writeTownData(tag);
        if (!level.isClientSide()) {
            TownState<MCTownItem> state = captureState();
            if (state == null) {
                return;
            }
            tag.put(NBT_TOWN_STATE, TownStateSerializer.INSTANCE.store(state));
        }
    }

    private void putStateOnTile() {
        @Nullable TownState<MCTownItem> state = captureState();
        if (state == null) {
            Questown.LOGGER.warn("TownState was null. Will not store.");
            return;
        }
        Questown.LOGGER.debug("Storing state on {}: {}", uuid, state);
        CompoundTag cereal = TownStateSerializer.INSTANCE.store(state);
        getTileData().put(NBT_TOWN_STATE, cereal);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        // TODO: Store active rooms?
        if (tag.contains(NBT_ACTIVE_RECIPES)) {
//            CompoundTag data = tag.getCompound(NBT_ACTIVE_RECIPES); TODO: Bring back
//            ActiveRecipes<ResourceLocation> ars = ActiveRecipesSerializer.INSTANCE.deserializeNBT(data);
//            this.roomsMap.initialize(this, ImmutableMap.of(0, ars)); // TODO: Support more levels
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
        if (tag.contains(NBT_LAST_TICK)) {
            this.lastTick = tag.getLong(NBT_LAST_TICK);
        }
        if (tag.contains(NBT_TOWN_STATE)) {
            CompoundTag stateTag = tag.getCompound(NBT_TOWN_STATE);
            this.townInit.push((level) -> TownStateSerializer.INSTANCE.load(stateTag, level));
        }

    }

    private void writeTownData(CompoundTag tag) {
        if (roomsMap.numRecipes() > 0) {
//            tag.put(NBT_ACTIVE_RECIPES, ActiveRecipesSerializer.INSTANCE.serializeNBT(roomsMap.getRecipes(0)));
        }
        tag.put(NBT_QUEST_BATCHES, MCQuestBatches.SERIALIZER.serializeNBT(quests.questBatches));
        tag.put(NBT_MORNING_REWARDS, this.morningRewards.serializeNbt());
        tag.put(NBT_ASAP_QUESTS, PendingQuestsSerializer.INSTANCE.serializeNBT(this.asapRandomAwdForVisitor));
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
    public void onChunkUnloaded() {
        if (level == null) {
            throw new IllegalStateException("level is null");
        }
        if (level.isClientSide()) {
            return;
        }

        this.hasPlayerEverBeenNear = false;

    }

    private @Nullable TownState<MCTownItem> captureState() {
        ImmutableList.Builder<TownState.VillagerData<MCTownItem>> vB = ImmutableList.builder();
        for (LivingEntity entity : entities) {
            if (entity instanceof VisitorMobEntity) {
                if (!((VisitorMobEntity) entity).isInitialized()) {
                    return null;
                }
                TownState.VillagerData<MCTownItem> data = new TownState.VillagerData<>(
                        Positions.FromBlockPos(entity.blockPosition()),
                        entity.blockPosition().getY(),
                        ((VisitorMobEntity) entity).getJobJournalSnapshot(),
                        entity.getUUID()
                );
                vB.add(data);
            }
        }

        TownState<MCTownItem> ts = new TownState<>(
                vB.build(),
                TownContainers.findAllMatching(this, item -> true).toList(),
                level.getDayTime()
        );
        return ts;
    }

    public static boolean debuggerReleaseControl() {
        GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().getWindow(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        return true;
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
        level.getServer().getPlayerList().broadcastMessage(msg, ChatType.CHAT, null);
    }

    public ImmutableList<Quest<ResourceLocation>> getAllQuests() {
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
            Position roomDoorPos,
            RoomRecipeMatch recipeId
    ) {
        broadcastMessage(new TranslatableComponent(
                "messages.building.recipe_created",
                roomDoorPos.getUIString()
        ));
        // TODO: get room for rendering effect
//        handleRoomChange(room, ParticleTypes.HAPPY_VILLAGER);
        quests.markQuestAsComplete(recipeId.getRecipeID());
    }

    @Override
    public void roomRecipeChanged(
            Position roomDoorPos,
            RoomRecipeMatch oldRecipeId,
            RoomRecipeMatch newRecipeId
    ) {
        broadcastMessage(new TranslatableComponent(
                "messages.building.room_changed",
                new TranslatableComponent("room." + oldRecipeId.getRecipeID().getPath()),
                new TranslatableComponent("room." + newRecipeId.getRecipeID().getPath()),
                roomDoorPos.getUIString()
        ));
        // TODO: Get room
//        handleRoomChange(room, ParticleTypes.HAPPY_VILLAGER);
        if (!oldRecipeId.equals(newRecipeId)) { // TODO: Add quests as a listener instead of doing this call
            quests.markQuestAsComplete(newRecipeId.getRecipeID());
        }
        // TODO: Mark removed recipe as lost?
    }

    @Override
    public void roomRecipeDestroyed(
            Position roomDoorPos,
            RoomRecipeMatch oldRecipeId
    ) {
        broadcastMessage(new TranslatableComponent(
                "messages.building.room_destroyed",
                new TranslatableComponent("room." + oldRecipeId.getRecipeID().getPath()),
                roomDoorPos.getUIString()
        ));
        // TODO: Get room
//        handleRoomChange(, ParticleTypes.LARGE_SMOKE);
    }

    @Override
    public void questCompleted(MCQuest quest) {
        broadcastMessage(new TranslatableComponent(
                "messages.town_flag.quest_completed",
                RoomRecipes.getName(quest.getId())
        )); // TODO: Do this in a different quest listener (specialized in "messaging")
        setChanged();
        FireworkRocketEntity firework = new FireworkRocketEntity(
                level,
                getBlockPos().getX(),
                getBlockPos().getY() + 10,
                getBlockPos().getZ(),
                Items.FIREWORK_ROCKET.getDefaultInstance()
        );
        level.addFreshEntity(firework);
    }

    @Override
    public void questBatchCompleted(QuestBatch<?, ?, ?> quest) {
        // TODO: Handle this by informing the user, etc.
        setChanged();
    }

    @Override
    public void addBatchOfRandomQuestsForVisitor(UUID visitorUUID) {
        TownQuests.addRandomBatchForVisitor(this, quests, visitorUUID);
        setChanged();
    }

    @Override
    public Vec3 getVisitorJoinPos() {
        return pois.getVisitorJoinPos(getServerLevel(), getBlockPos());
    }

    @Override
    public BlockPos getRandomWanderTarget() {
        Position wt = getWanderTargetPosition();
        BlockPos bp = getBlockPos();
        if (wt != null) {
            bp = Positions.ToBlock(wt, getBlockPos().getY());
        }
        return bp;
    }

    @Override
    public Collection<MCQuest> getQuestsForVillager(UUID uuid) {
        return this.quests.getAllForVillager(uuid);
    }

    @Override
    public void addBatchOfQuests(
            MCQuestBatch batch
    ) {
        this.quests.addBatch(batch);
    }

    @Override
    public Set<UUID> getVillagers() {
        return TownQuests.getVillagers(quests);
    }

    @Override
    public ContainerTarget findMatchingContainer(ContainerTarget.CheckFn c) {
        return TownContainers.findMatching(this, c);
    }

    @Override
    public void registerEntity(VisitorMobEntity vEntity) {
        this.entities.add(vEntity);
        vEntity.addChangeListener(() -> {
            Questown.LOGGER.debug("Setting changed");
            this.setChanged();
        });
        this.setChanged();
    }

    private @Nullable Position getWanderTargetPosition() {
        Collection<Room> all = roomsMap.getAllRooms();
        return pois.getWanderTarget(getServerLevel(), all);
    }

    void onMorning() {
        for (MCReward r : this.morningRewards.getChildren()) {
            this.asapRewards.push(r);
        }
    }

    @Override
    public void campfireFound() {
        quests.markQuestAsComplete(SpecialQuests.CAMPFIRE);
    }

    public Collection<RoomRecipeMatch> getMatches() {
        return this.roomsMap.getAllMatches();
    }

    public void loadEntity(VisitorMobEntity visitorMobEntity,
                           ServerLevel sl
    ) {
        if (!getTileData().contains(NBT_TOWN_STATE)) {
            Questown.LOGGER.error("Villager entity exists but town state is missing. This is a bug and may cause unexpected behaviour.");
            return;
        }
        TownState<MCTownItem> state = TownStateSerializer.INSTANCE.load(getTileData().getCompound(NBT_TOWN_STATE), sl);
        Optional<TownState.VillagerData<MCTownItem>> match = state.villagers.stream()
                .filter(v -> v.uuid.equals(visitorMobEntity.getUUID()))
                .findFirst();
        if (match.isEmpty()) {
            Questown.LOGGER.error("Villager entity exists but is not present on town state. This is a bug and may cause unexpected behaviour.");
            return;
        }
        registerEntity(visitorMobEntity);
        visitorMobEntity.initialize(
                match.get().uuid,
                Positions.ToBlock(match.get().position, match.get().yPosition),
                match.get().journal
        );
    }
}
