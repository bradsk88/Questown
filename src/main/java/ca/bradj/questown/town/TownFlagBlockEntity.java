package ca.bradj.questown.town;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.advancements.ApproachTownTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.logic.TownCycle;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.*;
import ca.bradj.questown.town.rewards.AddBatchOfRandomQuestsForVisitorReward;
import ca.bradj.questown.town.rewards.SpawnVisitorReward;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.recipes.ActiveRecipes;
import ca.bradj.roomrecipes.recipes.RecipeDetection;
import ca.bradj.roomrecipes.recipes.RecipesInit;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import ca.bradj.roomrecipes.render.RoomEffects;
import ca.bradj.roomrecipes.serialization.ActiveRecipesSerializer;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class TownFlagBlockEntity extends BlockEntity implements TownInterface, ActiveRecipes.ChangeListener<ResourceLocation>, QuestBatch.ChangeListener<MCQuest> {

    public static final String ID = "flag_base_block_entity";
    public static final String NBT_QUEST_BATCHES = String.format("%s_quest_batches", Questown.MODID);
    public static final String NBT_ACTIVE_RECIPES = String.format("%s_active_recipes", Questown.MODID);
    public static final String NBT_MORNING_REWARDS = String.format("%s_morning_rewards", Questown.MODID);
    public static final String NBT_ASAP_QUESTS = String.format("%s_asap_quests", Questown.MODID);
    private static int radius = 20; // TODO: Move to config
    private final Map<Integer, TownRooms> activeRooms = new HashMap<>();
    private final Map<Integer, ActiveRecipes<ResourceLocation>> activeRecipes = new HashMap<>();
    private final MCQuestBatches questBatches = new MCQuestBatches();
    private final MCMorningRewards morningRewards = new MCMorningRewards(this);
    private final MCAsapRewards asapRewards = new MCAsapRewards();
    private final Stack<PendingQuests> asapRandomAwdForVisitor = new Stack<>();
    private final UUID uuid = UUID.randomUUID();
    private boolean isInitializedQuests = false;
    private BlockPos visitorSpot = null;

    private int scanLevel = 0;
    private int scanBuffer = 0;


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

        if (!e.asapRandomAwdForVisitor.isEmpty()) {
            PendingQuests pop = e.asapRandomAwdForVisitor.pop();
            Optional<MCQuestBatch> o = pop.grow(sl);
            o.ifPresentOrElse(
                    e.questBatches::add,
                    () -> e.asapRandomAwdForVisitor.push(pop)
            );
        }

        long gameTime = level.getGameTime();
        long l = gameTime % 10;
        if (l != 0) {
            return;
        }

        e.scanBuffer = (e.scanBuffer + 1) % 2;
        int scanLevel = 0;
        if (e.scanBuffer == 0) {
            e.scanLevel = (e.scanLevel + 1) % 5;
            scanLevel = e.scanLevel + 1;
        }

        e.asapRewards.popClaim();

        // TODO: Consider adding non-room town "features" as quests
        // TODO: Don't check this so often - maybe add fireside seating that can be paired to flag
        Optional<BlockPos> fire = TownCycle.findCampfire(blockPos, level);
        if (e.visitorSpot == null) {
            fire.ifPresent((bp) -> e.questBatches.markRecipeAsComplete(SpecialQuests.CAMPFIRE));
        }
        e.visitorSpot = fire.orElse(null);

        updateActiveRooms(level, blockPos, e, 0);

        if (scanLevel != 0) {
            int y = 2 * scanLevel;
            updateActiveRooms(level, blockPos.offset(0, y, 0), e, scanLevel);
        }
    }

    private static void updateActiveRooms(
            Level level,
            BlockPos blockPos,
            TownFlagBlockEntity e,
            int scanLevel
    ) {
       TownRooms ars = e.getOrCreateRooms(scanLevel, blockPos.getY());

        ImmutableMap<Position, Optional<Room>> rooms = TownCycle.findRooms(
                Positions.FromBlockPos(blockPos), ars
        );
        ars.update(rooms);

        ars.getAll().forEach(room -> {
            Optional<RoomRecipe> recipe = RecipeDetection.getActiveRecipe(level, room, ars, blockPos.getY());
            e.activeRecipes.get(scanLevel).update(room.getDoorPos(), recipe.map(RoomRecipe::getId).orElse(null));
        });
    }

    private TownRooms getOrCreateRooms(int scanLevel, int yCoord) {
        if (!activeRecipes.containsKey(scanLevel)) {
            ActiveRecipes<ResourceLocation> v = new ActiveRecipes<>();
            activeRecipes.put(scanLevel, v);
            v.addChangeListener(this);
        }

        if (!activeRooms.containsKey(scanLevel)) {
            TownRooms v = new TownRooms(scanLevel, this);
            activeRooms.put(scanLevel, v);
        }

        return activeRooms.get(scanLevel);
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
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        // TODO: Store active rooms?
        if (tag.contains(NBT_ACTIVE_RECIPES)) {
            CompoundTag data = tag.getCompound(NBT_ACTIVE_RECIPES);
            ActiveRecipes<ResourceLocation> ars = ActiveRecipesSerializer.INSTANCE.deserializeNBT(data);
            this.activeRecipes.put(0, ars); // TODO: Support more levels
        }
        if (tag.contains(NBT_QUEST_BATCHES)) {
            CompoundTag data = tag.getCompound(NBT_QUEST_BATCHES);
            MCQuestBatches.SERIALIZER.deserializeNBT(this, data, this.questBatches);
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
    }

    private void writeTownData(CompoundTag tag) {
        tag.put(NBT_ACTIVE_RECIPES, ActiveRecipesSerializer.INSTANCE.serializeNBT(activeRecipes.get(0)));
        tag.put(NBT_QUEST_BATCHES, MCQuestBatches.SERIALIZER.serializeNBT(questBatches));
        tag.put(NBT_MORNING_REWARDS, this.morningRewards.serializeNbt());
        tag.put(NBT_ASAP_QUESTS, PendingQuestsSerializer.INSTANCE.serializeNBT(this.asapRandomAwdForVisitor));
        // TODO: Serialization for ASAPs
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
            this.setUpQuestsForNewlyPlacedFlag(sl);
        }
        this.questBatches.addChangeListener(this);
        this.getOrCreateRooms(0, getBlockPos().getY());
        this.activeRecipes.get(0).addChangeListener(this);
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
                Questown.LOGGER.debug("Distance {}", v);
                if (v < 100) {
                    AdvancementsInit.APPROACH_TOWN_TRIGGER.trigger(
                            sp, ApproachTownTrigger.Triggers.FirstVisit
                    );
                }
            }
        });
    }

    private void setUpQuestsForNewlyPlacedFlag(ServerLevel sl) {
        UUID visitorUUID = UUID.randomUUID();
        MCRewardList reward = new MCRewardList(
                this,
                new SpawnVisitorReward(this, visitorUUID),
                new AddBatchOfRandomQuestsForVisitorReward(this, visitorUUID)
        );

        MCQuestBatch fireQuest = new MCQuestBatch(null, new MCDelayedReward(this, reward));
        fireQuest.addNewQuest(SpecialQuests.CAMPFIRE);

        questBatches.add(fireQuest);
        this.isInitializedQuests = true;
        setChanged();
    }

    void broadcastMessage(TranslatableComponent msg) {
        level.getServer().getPlayerList().broadcastMessage(msg, ChatType.CHAT, null);
    }

    private void handleRoomChange(
            Room room,
            ParticleOptions pType
    ) {
        for (InclusiveSpace space : room.getSpaces()) {
            RoomEffects.renderParticlesBetween(space, (x, z) -> {
                int y = this.getBlockPos().getY();
                BlockPos bp = new BlockPos(x, y, z);
                if (!(level instanceof ServerLevel)) {
                    return;
                }
                if (!level.isEmptyBlock(bp)) {
                    return;
                }
                ((ServerLevel) level).sendParticles(pType, x, y, z, 2, 0, 1, 0, 1);
                ((ServerLevel) level).sendParticles(pType, x, y + 1, z, 2, 0, 1, 0, 1);
            });
        }
    }

    public ImmutableList<Quest<ResourceLocation>> getAllQuests() {
        return ImmutableList.copyOf(questBatches.getAll().stream().map(v -> (Quest<ResourceLocation>) v).toList());
    }

    private static RoomRecipe getRandomQuest(ServerLevel level) {
        // TODO: Take "difficulty" as input
        List<RoomRecipe> recipes = level.getRecipeManager().getAllRecipesFor(RecipesInit.ROOM);
        return recipes.get(level.getRandom().nextInt(recipes.size()));
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
            ResourceLocation recipeId
    ) {
        broadcastMessage(new TranslatableComponent(
                "messages.building.room_created",
                new TranslatableComponent("room." + recipeId.getPath()),
                roomDoorPos.getUIString()
        ));
        // TODO: get room
//        handleRoomChange(room, ParticleTypes.HAPPY_VILLAGER);
        questBatches.markRecipeAsComplete(recipeId);
    }

    @Override
    public void roomRecipeChanged(
            Position roomDoorPos,
            ResourceLocation oldRecipeId,
            ResourceLocation newRecipeId
    ) {
        broadcastMessage(new TranslatableComponent(
                "messages.building.room_changed",
                new TranslatableComponent("room." + oldRecipeId.getPath()),
                new TranslatableComponent("room." + newRecipeId.getPath()),
                roomDoorPos.getUIString()
        ));
        // TODO: Get room
//        handleRoomChange(room, ParticleTypes.HAPPY_VILLAGER);
        if (!oldRecipeId.equals(newRecipeId)) {
            questBatches.markRecipeAsComplete(newRecipeId);
        }
        // TODO: Mark removed recipe as lost?
    }

    @Override
    public void roomRecipeDestroyed(
            Position roomDoorPos,
            ResourceLocation oldRecipeId
    ) {
        broadcastMessage(new TranslatableComponent(
                "messages.building.room_destroyed",
                new TranslatableComponent("room." + oldRecipeId.getPath()),
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
        ));
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
        int minItems = 40 + (100 * getVillagers().size())/2;

        UUID nextVisitorUUID = UUID.randomUUID();
        MCRewardList reward = new MCRewardList(
                this,
                new SpawnVisitorReward(this, nextVisitorUUID),
                new AddBatchOfRandomQuestsForVisitorReward(this, nextVisitorUUID)
        );
        this.asapRandomAwdForVisitor.push(new PendingQuests(
                minItems, visitorUUID, new MCDelayedReward(this, reward)
        ));
        setChanged();
    }

    @Override
    public Vec3 getVisitorJoinPos() {
        if (this.visitorSpot == null) {
            return new Vec3(getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ());
        }
        BlockPos vs = this.visitorSpot.relative(Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom()), 2);
        while (!level.isUnobstructed(level.getBlockState(vs.below()), vs, CollisionContext.empty())) {
            vs = this.visitorSpot.relative(Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom()), 2);
        }
        return new Vec3(vs.getX(), vs.getY(), vs.getZ());
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
        return this.questBatches.getAllBatches()
                .stream()
                .filter(b -> uuid.equals(b.getOwner()))
                .flatMap(v -> v.getAll().stream())
                .toList();
    }

    @Override
    public void addBatchOfQuests(
            MCQuestBatch batch
    ) {
        this.questBatches.add(batch);
    }

    @Override
    public Set<UUID> getVillagers() {
        return this.questBatches.getAllBatches().stream().map(MCQuestBatch::getOwner).collect(Collectors.toSet());
    }

    private @Nullable Position getWanderTargetPosition() {
        Collection<Room> all = this.activeRooms.get(0).getAll();
        for (Room r : all) {
            if (level.getRandom().nextInt(all.size()) == 0) {
                Position ac = r.getSpace().getCornerA();
                Position bc = r.getSpace().getCornerB();
                if (level.getRandom().nextBoolean()) {
                    return new Position((ac.x + bc.x) / 2, (ac.z + bc.z) / 2);
                }
                if (level.getRandom().nextBoolean()) {
                    return ac.offset(1, 1);
                }
                if (level.getRandom().nextBoolean()) {
                    return bc.offset(-1, -1);
                }
                return r.getDoorPos();
            }
        }
        return null;
    }

    void onMorning() {
        for (MCReward r : this.morningRewards.getChildren()) {
            this.asapRewards.push(r);
        }
    }

    public void updateActiveRecipe(
            int scanLevel,
            Position roomDoorPos,
            @Nullable ResourceLocation resourceLocation
    ) {
        activeRecipes.get(scanLevel).update(roomDoorPos, resourceLocation);
    }
}
