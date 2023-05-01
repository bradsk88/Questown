package ca.bradj.questown.town;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.logic.TownCycle;
import ca.bradj.questown.town.activerecipes.ActiveRecipes;
import ca.bradj.questown.town.activerecipes.MCActiveRecipes;
import ca.bradj.questown.town.activerooms.ActiveRooms;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuests;
import ca.bradj.questown.town.quests.Quests;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
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

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class TownFlagBlockEntity extends BlockEntity implements TownCycle.BlockChecker, DoorDetection.DoorChecker, ActiveRecipes.ChangeListener<ResourceLocation>, Quests.ChangeListener<MCQuest>, ActiveRooms.ChangeListener {

    private static int radius = 20; // TODO: Move to config

    public static final String ID = "flag_base_block_entity";
    public static final String NBT_QUESTS = String.format("%s_quests", Questown.MODID);
    public static final String NBT_ACTIVE_RECIPES = String.format("%s_active_recipes", Questown.MODID);

    private final ActiveRooms activeRooms = new ActiveRooms();
    private final MCActiveRecipes activeRecipes = new MCActiveRecipes();
    private final MCQuests quests = new MCQuests();


    public TownFlagBlockEntity(
            BlockPos p_155229_,
            BlockState p_155230_
    ) {
        super(TilesInit.TOWN_FLAG.get(), p_155229_, p_155230_);
    }

    @Override
    public void load(CompoundTag p_155245_) {
        this.deserializeNBT(p_155245_);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        tag.put(NBT_ACTIVE_RECIPES, MCActiveRecipes.SERIALIZER.serializeNBT(activeRecipes));
        tag.put(NBT_QUESTS, MCQuests.SERIALIZER.serializeNBT(quests));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level.isClientSide()) {
            return;
        }
        informPlayersOnApproach();
        this.initializeActiveRooms();
        this.initializeActiveRecipes();
        this.initializeQuests();
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

    private void initializeActiveRooms() {
        // TODO: Store on block entity
        this.activeRooms.addChangeListener(this);
    }

    private void initializeActiveRecipes() {
        if (getTileData().contains(NBT_ACTIVE_RECIPES)) {
            CompoundTag data = getTileData().getCompound(NBT_ACTIVE_RECIPES);
            MCActiveRecipes.SERIALIZER.deserializeNBT(data, this.activeRecipes);
            return;
        }
        this.activeRecipes.addChangeListener(this);
    }

    private void initializeQuests() {
        if (getTileData().contains(NBT_QUESTS)) {
            CompoundTag data = getTileData().getCompound(NBT_ACTIVE_RECIPES);
            MCQuests.SERIALIZER.deserializeNBT(data, this.quests);
            return;
        }
        this.quests.addChangeListener(this);
    }

    public static void tick(
            Level level,
            BlockPos blockPos,
            BlockState state,
            TownFlagBlockEntity e
    ) {
        if (level.isClientSide()) {
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

    private void handleRoomChange(Room room, ParticleOptions pType) {
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
        return quests.getAll();
    }

    public void generateRandomQuest(ServerLevel level) {
        List<RoomRecipe> recipes = level.getRecipeManager().getAllRecipesFor(RecipesInit.ROOM);
        RoomRecipe recipe = recipes.get(level.getRandom().nextInt(recipes.size()));
        quests.addNewQuest(recipe.getId());
        broadcastQuestToChat(level, recipe);
    }

    private void broadcastQuestToChat(ServerLevel level, RoomRecipe recipe) {
        Component recipeName = RoomRecipes.getName(recipe.getId());
        TranslatableComponent questName = new TranslatableComponent("quests.build_a", recipeName);
        TranslatableComponent questAdded = new TranslatableComponent("messages.town_flag.quest_added", questName);
        level.getServer().getPlayerList().broadcastMessage(questAdded, ChatType.CHAT, null);
    }

    @Override
    public void roomRecipeCreated(Room room, ResourceLocation recipeId) {
        broadcastMessage(new TranslatableComponent(
                "messages.building.room_created",
                new TranslatableComponent("room." + recipeId.getPath()),
                room.getDoorPos().getUIString()
        ));
        quests.markRecipeAsComplete(recipeId);
    }

    @Override
    public void roomRecipeChanged(
            Room room, ResourceLocation oldRecipeId, ResourceLocation newRecipeId
    ) {
        broadcastMessage(new TranslatableComponent(
                "messages.building.room_changed",
                new TranslatableComponent("room." + oldRecipeId.getPath()),
                new TranslatableComponent("room." + newRecipeId.getPath()),
                room.getDoorPos().getUIString()
        ));
        quests.markRecipeAsComplete(newRecipeId);
        // TODO: Mark removed recipe as lost?
    }

    @Override
    public void roomRecipeDestroyed(Room room, ResourceLocation oldRecipeId) {
        broadcastMessage(new TranslatableComponent(
                "messages.building.room_destroyed",
                new TranslatableComponent("room." + oldRecipeId.getPath()),
                room.getDoorPos().getUIString()
        ));
    }

    @Override
    public void questCompleted(MCQuest quest) {
        broadcastMessage(new TranslatableComponent(
                "messages.town_flag.quest_completed",
                RoomRecipes.getName(quest.getId())
        ));
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
}
