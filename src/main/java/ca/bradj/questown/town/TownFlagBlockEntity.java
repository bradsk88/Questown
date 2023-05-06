package ca.bradj.questown.town;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.logic.TownCycle;
import ca.bradj.questown.town.activerecipes.ActiveRecipes;
import ca.bradj.questown.town.activerecipes.MCActiveRecipes;
import ca.bradj.questown.town.activerooms.ActiveRooms;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.*;
import ca.bradj.questown.town.rewards.AddBatchOfRandomQuestsForVisitorReward;
import ca.bradj.questown.town.rewards.SpawnVisitorReward;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.DoorDetection;
import ca.bradj.roomrecipes.recipes.RecipeDetection;
import ca.bradj.roomrecipes.recipes.RecipesInit;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import ca.bradj.roomrecipes.render.RoomEffects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class TownFlagBlockEntity extends BlockEntity implements TownInterface, TownCycle.BlockChecker, DoorDetection.DoorChecker, ActiveRecipes.ChangeListener<ResourceLocation>, QuestBatch.ChangeListener<MCQuest>, ActiveRooms.ChangeListener {

    public static final String ID = "flag_base_block_entity";
    public static final String NBT_QUEST_BATCHES = String.format("%s_quest_batches", Questown.MODID);
    public static final String NBT_ACTIVE_RECIPES = String.format("%s_active_recipes", Questown.MODID);
    private static int radius = 20; // TODO: Move to config
    private final ActiveRooms activeRooms = new ActiveRooms();
    private final MCActiveRecipes activeRecipes = new MCActiveRecipes();
    private final MCQuestBatches questBatches = new MCQuestBatches();
    private final UUID uuid = UUID.randomUUID();
    private boolean isInitializedQuests = false;


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
        if (level == null || level.isClientSide()) {
            return;
        }

        long gameTime = level.getGameTime();
        long l = gameTime % 10;
        if (l != 0) {
            return;
        }

        ImmutableMap<Position, Optional<Room>> rooms = TownCycle.findRooms(
                Positions.FromBlockPos(e.getBlockPos()), e
        );
        e.activeRooms.update(rooms);

        e.activeRooms.getAll().forEach(room -> {
            Optional<RoomRecipe> recipe = RecipeDetection.getActiveRecipe(level, room, e, blockPos.getY());
            e.activeRecipes.update(room, recipe.map(RoomRecipe::getId));
        });
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
            MCActiveRecipes.SERIALIZER.deserializeNBT(data, this.activeRecipes);
        }
        if (tag.contains(NBT_QUEST_BATCHES)) {
            CompoundTag data = tag.getCompound(NBT_QUEST_BATCHES);
            MCQuestBatches.SERIALIZER.deserializeNBT(this, data, this.questBatches);
            this.isInitializedQuests = true;
        }
    }

    private void writeTownData(CompoundTag tag) {
        tag.put(NBT_ACTIVE_RECIPES, MCActiveRecipes.SERIALIZER.serializeNBT(activeRecipes));
        tag.put(NBT_QUEST_BATCHES, MCQuestBatches.SERIALIZER.serializeNBT(questBatches));
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
        informPlayersOnApproach();
        if (!this.isInitializedQuests) {
            this.initializeQuests(sl);
        }
        this.questBatches.addChangeListener(this);
        this.activeRecipes.addChangeListener(this);
        this.activeRooms.addChangeListener(this);
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2);
    }

    private void informPlayersOnApproach() {
        MinecraftForge.EVENT_BUS.addListener((EntityEvent.EnteringSection event) -> {
            if (event.getEntity() instanceof Player) {
                double v = event.getEntity().distanceToSqr(
                        this.worldPosition.getX() + 0.5D,
                        this.worldPosition.getY() + 0.5D,
                        this.worldPosition.getZ() + 0.5D
                );
                Questown.LOGGER.debug("Player detected at distance: " + v);
                if (v < 113) {
                    Player player = (Player) event.getEntity();
                    // TODO: bring back but only send once per minute(?)
//                    player.sendMessage(new TranslatableComponent("messages.town_flag.click_to_view_quests"), null);
                }
            }
        });
    }

    private void initializeQuests(ServerLevel level) {
        List<RoomRecipe> recipes = level.getRecipeManager().getAllRecipesFor(RecipesInit.ROOM);
        final List<RoomRecipe> recipesCopy = Lists.reverse(ImmutableList.sortedCopyOf(recipes));

        List<? extends String> initialQuests = Config.village_start_quests.get();

        UUID visitorUUID = UUID.randomUUID();
        MCRewardList reward = new MCRewardList(this,
            new SpawnVisitorReward(this, visitorUUID),
            new AddBatchOfRandomQuestsForVisitorReward(this, visitorUUID)
        );
        MCQuestBatch qb = new MCQuestBatch(reward);
        initialQuests.forEach(iq -> {
            Optional<RoomRecipe> match = recipesCopy.stream().filter(v -> v.getId().toString().equals(iq)).findFirst();
            if (match.isEmpty()) {
                Questown.LOGGER.error("Unrecognized recipe ID provided for {}: {}", Config.VILLAGE_START_QUESTS, iq);
                return;
            }
            qb.addNewQuest(match.get().getId());
        });
        questBatches.add(qb);
        this.isInitializedQuests = true;
    }

    @Override
    public boolean IsEmpty(Position dp) {
        BlockPos bp = Positions.ToBlock(dp, this.getBlockPos().getY());
        return level.isEmptyBlock(bp) || level.isEmptyBlock(bp.above());
    }

    @Override
    public boolean IsDoor(Position dp) {
        return level.getBlockState(Positions.ToBlock(dp, this.getBlockPos().getY())).getBlock() instanceof DoorBlock;
    }

    private void broadcastMessage(TranslatableComponent msg) {
        level.getServer().getPlayerList().broadcastMessage(msg, ChatType.CHAT, null);
    }

    private void handleRoomChange(
            Room room,
            ParticleOptions pType
    ) {
        RoomEffects.renderParticlesBetween(room.getSpace(), (x, z) -> {
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

    public ImmutableList<MCQuest> getAllQuests() {
        return questBatches.getAll();
    }

    public void generateRandomQuest(ServerLevel level) {
        RoomRecipe recipe = getRandomQuest(level);
        MCQuestBatch qb = new MCQuestBatch(new SpawnVisitorReward(this));
        questBatches.add(qb);
        setChanged();
        broadcastQuestToChat(level, recipe);
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
            Room room,
            ResourceLocation recipeId
    ) {
        broadcastMessage(new TranslatableComponent(
                "messages.building.room_created",
                new TranslatableComponent("room." + recipeId.getPath()),
                room.getDoorPos().getUIString()
        ));
        handleRoomChange(room, ParticleTypes.HAPPY_VILLAGER);
        questBatches.markRecipeAsComplete(recipeId);
    }

    @Override
    public void roomRecipeChanged(
            Room room,
            ResourceLocation oldRecipeId,
            ResourceLocation newRecipeId
    ) {
        broadcastMessage(new TranslatableComponent(
                "messages.building.room_changed",
                new TranslatableComponent("room." + oldRecipeId.getPath()),
                new TranslatableComponent("room." + newRecipeId.getPath()),
                room.getDoorPos().getUIString()
        ));
        handleRoomChange(room, ParticleTypes.HAPPY_VILLAGER);
        questBatches.markRecipeAsComplete(newRecipeId);
        // TODO: Mark removed recipe as lost?
    }

    @Override
    public void roomRecipeDestroyed(
            Room room,
            ResourceLocation oldRecipeId
    ) {
        broadcastMessage(new TranslatableComponent(
                "messages.building.room_destroyed",
                new TranslatableComponent("room." + oldRecipeId.getPath()),
                room.getDoorPos().getUIString()
        ));
        handleRoomChange(room, ParticleTypes.LARGE_SMOKE);
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
    }

    @Override
    public void roomAdded(
            Position doorPos,
            Room room
    ) {
        handleRoomChange(room, ParticleTypes.HAPPY_VILLAGER);
        Optional<RoomRecipe> recipe = RecipeDetection.getActiveRecipe(level, room, this, this.getBlockPos().getY());
        this.activeRecipes.update(room, recipe.map(RoomRecipe::getId));
        broadcastMessage(new TranslatableComponent(
                "messages.building.room_created",
                RoomRecipes.getName(recipe),
                doorPos.getUIString()
        ));
    }

    @Override
    public void roomResized(
            Position doorPos,
            Room oldRoom,
            Room newRoom
    ) {
        handleRoomChange(newRoom, ParticleTypes.HAPPY_VILLAGER);
        this.activeRecipes.update(oldRoom, Optional.empty());
        Optional<RoomRecipe> recipe = RecipeDetection.getActiveRecipe(level, newRoom, this, this.getBlockPos().getY());
        this.activeRecipes.update(newRoom, recipe.map(RoomRecipe::getId));
        broadcastMessage(new TranslatableComponent(
                "messages.building.room_size_changed",
                RoomRecipes.getName(recipe),
                doorPos.getUIString()
        ));
    }

    @Override
    public void roomDestroyed(
            Position doorPos,
            Room room
    ) {
        Optional<RoomRecipe> recipe = RecipeDetection.getActiveRecipe(level, room, this, this.getBlockPos().getY());
        broadcastMessage(new TranslatableComponent(
                "messages.building.room_destroyed",
                RoomRecipes.getName(recipe),
                doorPos.getUIString()
        ));
        handleRoomChange(room, ParticleTypes.SMOKE);
        this.activeRecipes.update(room, Optional.empty());
    }

    @Override
    public void addBatchOfRandomQuestsForVisitor(UUID visitorUUID) {
        if (level == null) {
            throw new IllegalCallerException("Cannot add reward to null level");
        }
        if (!(level instanceof ServerLevel sl)) {
            throw new IllegalCallerException("Cannot add reward to client level");
        }
        int numNewQuests = 3; // TODO: Determine this based on town "progress"
        UUID nextVisitorUUID = UUID.randomUUID();
        MCQuestBatch qb = new MCQuestBatch(new MCRewardList(this,
           new SpawnVisitorReward(this, nextVisitorUUID),
            new AddBatchOfRandomQuestsForVisitorReward(this, nextVisitorUUID)
        ));
        for (int i = 0; i < numNewQuests; i++) {
            qb.addNewQuest(getRandomQuest(sl).getId());
        }
        this.questBatches.add(qb);
    }
}