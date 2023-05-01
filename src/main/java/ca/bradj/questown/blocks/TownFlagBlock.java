package ca.bradj.questown.blocks;

import ca.bradj.questown.core.init.ModItemGroup;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.core.materials.WallType;
import ca.bradj.questown.gui.TownQuestsContainer;
import ca.bradj.questown.gui.UIQuest;
import ca.bradj.questown.town.Quest;
import ca.bradj.questown.town.TownFlagBlockEntity;
import ca.bradj.roomrecipes.recipes.RecipesInit;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class TownFlagBlock extends BaseEntityBlock {

    public static String itemId(WallType wallType) {
        return String.format("%s_%s", wallType.name(), ITEM_ID);
    }
    public static final String ITEM_ID = "flag_base";
    private TownFlagBlockEntity entity;
    public static final Item.Properties ITEM_PROPS = new Item.Properties().
            tab(ModItemGroup.QUESTOWN_GROUP);

    public TownFlagBlock() {
        super(BlockBehaviour.Properties.copy(Blocks.COBWEB));
    }

    @Override
    public RenderShape getRenderShape(BlockState blockState) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
        this.entity = TilesInit.TOWN_FLAG.get().create(pos, state);
        return this.entity;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState blockState,
            BlockEntityType<T> entityType
    ) {
        return level.isClientSide ? null : createTickerHelper(
                entityType, TilesInit.TOWN_FLAG.get(), TownFlagBlockEntity::tick
        );
    }

    @Override
    public InteractionResult use(
            BlockState p_60503_,
            Level level,
            BlockPos p_60505_,
            Player player,
            InteractionHand hand,
            BlockHitResult p_60508_
    ) {
        if (level.isClientSide()) {
            return InteractionResult.sidedSuccess(true);
        }

        if (player.isCrouching()) {
            entity.generateRandomQuest((ServerLevel) level);
            return InteractionResult.sidedSuccess(true);
        }

        // TODO: Store quest state in block (or world?)
        ImmutableMap.Builder<ResourceLocation, RoomRecipe> rMapB = ImmutableMap.builder();
        level.getRecipeManager().getAllRecipesFor(RecipesInit.ROOM).forEach(v -> rMapB.put(v.getId(), v));
        ImmutableMap<ResourceLocation, RoomRecipe> rMap = rMapB.build();

        ImmutableList<Quest> aQ = entity.getAllQuests();
        List<UIQuest> quests = aQ.stream().map(v -> {
            RoomRecipe q = rMap.get(v.getId());
            if (q == null) {
                return null;
            }
            int recipeStrength = 1; // TODO: Add getter to RoomRecipes
            return new UIQuest(new RoomRecipe(v.getId(), q.getIngredients(), recipeStrength), v.getStatus());
        }).toList();

        NetworkHooks.openGui((ServerPlayer) player, new MenuProvider() {
            @Override
            public @NotNull Component getDisplayName() {
                return TextComponent.EMPTY;
            }

            @Override
            public @NotNull AbstractContainerMenu createMenu(
                    int windowId,
                    @NotNull Inventory inv,
                    @NotNull Player p
            ) {
                return new TownQuestsContainer(windowId, quests);
            }
        }, data -> {
            UIQuest.Serializer ser = new UIQuest.Serializer();
            data.writeInt(quests.size());
            data.writeCollection(quests, (buf, recipe) -> {
                ResourceLocation id = recipe.getRecipeId();
                buf.writeResourceLocation(id);
                ser.toNetwork(buf, recipe);
            });
        });
        return InteractionResult.sidedSuccess(true);
    }

}
