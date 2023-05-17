package ca.bradj.questown.blocks;

import ca.bradj.questown.core.init.ModItemGroup;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.core.materials.WallType;
import ca.bradj.questown.gui.TownQuestsContainer;
import ca.bradj.questown.gui.UIQuest;
import ca.bradj.questown.town.TownFlagBlockEntity;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.questown.town.special.SpecialQuests;
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
import java.util.Optional;


public class TownFlagBlock extends BaseEntityBlock {

    public static String itemId(WallType wallType) {
        return String.format("%s_%s", wallType.asString(), ITEM_ID);
    }
    public static final String ITEM_ID = "flag_base";
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
        return new TownFlagBlockEntity(pos, state);
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
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult p_60508_
    ) {
        if (level.isClientSide()) {
            return InteractionResult.sidedSuccess(true);
        }

        Optional<TownFlagBlockEntity> oEntity = level.getBlockEntity(pos, TilesInit.TOWN_FLAG.get());
        if (oEntity.isEmpty()) {
            return InteractionResult.sidedSuccess(true);
        }
        TownFlagBlockEntity entity = oEntity.get();
        if (player.isCrouching()) {
//             TODO: This is throwaway code
            entity.generateRandomQuest((ServerLevel) level);
//            boolean spawned = spawnVisitorNearby((ServerLevel) level, pos);
//            Questown.LOGGER.debug("Spawned: " + spawned);
//            return InteractionResult.sidedSuccess(false);
        }

        ImmutableList<MCQuest> aQ = entity.getAllQuests();
        List<UIQuest> quests = UIQuest.fromLevel(level, aQ);

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
                ResourceLocation id;
                if (recipe == null) {
                    id = SpecialQuests.BROKEN;
                    recipe = new UIQuest(SpecialQuests.SPECIAL_QUESTS.get(id), Quest.QuestStatus.ACTIVE);
                } else {
                    id = recipe.getRecipeId();
                }
                buf.writeResourceLocation(id);
                ser.toNetwork(buf, recipe);
            });
        });
        return InteractionResult.sidedSuccess(false);
    }

}
