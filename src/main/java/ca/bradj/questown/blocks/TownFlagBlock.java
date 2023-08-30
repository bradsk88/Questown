package ca.bradj.questown.blocks;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.ModItemGroup;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.core.materials.WallType;
import ca.bradj.questown.gui.TownQuestsContainer;
import ca.bradj.questown.gui.UIQuest;
import ca.bradj.questown.town.TownFlagBlockEntity;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.questown.town.rewards.SpawnVisitorReward;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public class TownFlagBlock extends BaseEntityBlock {

    public static final String ITEM_ID = "flag_base";
    public static final Item.Properties ITEM_PROPS = new Item.Properties().
            tab(ModItemGroup.QUESTOWN_GROUP);
    private Map<Player, Long> informedPlayers = new HashMap<>();

    public TownFlagBlock() {
        super(
                BlockBehaviour.Properties.of(Material.STONE, MaterialColor.COLOR_GRAY)
                        .strength(10.0F, 1200.0F)
        );
    }

    public static String itemId(WallType wallType) {
        return String.format("%s_%s", wallType.asString(), ITEM_ID);
    }

    public static TownFlagBlockEntity GetParentFromNBT(
            ServerLevel level,
            ItemStack itemInHand
    ) {
            if (itemInHand.getTag() == null) {
                return null;
            }
            int x, y, z;
            String xTag = String.format("%s.parent_pos_x", Questown.MODID);
            if (!itemInHand.getTag().contains(xTag)) {
                return null;
            }
            String yTag = String.format("%s.parent_pos_y", Questown.MODID);
            if (!itemInHand.getTag().contains(yTag)) {
                return null;
            }
            String zTag = String.format("%s.parent_pos_z", Questown.MODID);
            if (!itemInHand.getTag().contains(zTag)) {
                return null;
            }
            x = itemInHand.getOrCreateTag().getInt(xTag);
            y = itemInHand.getOrCreateTag().getInt(yTag);
            z = itemInHand.getOrCreateTag().getInt(zTag);


            BlockPos bp = new BlockPos(x, y, z);
            Optional<TownFlagBlockEntity> oEntity = level.getBlockEntity(bp, TilesInit.TOWN_FLAG.get());
            return oEntity.orElse(null);

    }

    @Nullable
    private static InteractionResult convertItemInHand(
            Level level,
            Player player,
            InteractionHand hand,
            TownFlagBlockEntity entity
    ) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (itemInHand.getItem().equals(Items.APPLE)) {
            entity.addImmediateReward(new SpawnVisitorReward(entity));
            return InteractionResult.sidedSuccess(false);
        }

        // TODO: Consider making a new recipe type so any item can be cnverted to
        //  any other item and with the parent NBT stored on the new item.
        ItemStack converted = null;

        if (itemInHand.getItem().equals(ItemsInit.WELCOME_MAT_BLOCK.get())) {
            converted = itemInHand;
        }
        if (Ingredient.of(ItemTags.CARPETS).test(itemInHand)) {
            converted = ItemsInit.WELCOME_MAT_BLOCK.get().getDefaultInstance();
            player.giveExperiencePoints(100);
            // TODO: Advancement
        }
        if (Ingredient.of(ItemTags.DOORS).test(itemInHand)) {
            converted = ItemsInit.TOWN_DOOR.get().getDefaultInstance();
            // TODO: Advancement
        }

        if (converted != null) {
            StoreFlagInputOnOutputNBT(itemInHand, converted);
            player.setItemInHand(hand, converted);
            Questown.LOGGER.debug("{} created at {}", converted.getItem().getRegistryName(), entity.getTownFlagBasePos());
            ItemStack toDrop = null;
            if (itemInHand.getCount() > 1) {
                toDrop = itemInHand.copy();
                toDrop.shrink(1);
            }
            if (toDrop != null) {
                BlockPos flagPos = player.blockPosition();
                level.addFreshEntity(new ItemEntity(level, flagPos.getX(), flagPos.getY(), flagPos.getZ(), toDrop));
            }
            StoreParentOnNBT(converted, entity.getTownFlagBasePos());
            Questown.LOGGER.debug(
                    "{} has been paired with {} at {}",
                    converted.getItem().getRegistryName(), entity.getUUID(), entity.getTownFlagBasePos()
            );
            return InteractionResult.sidedSuccess(false);
        }
        return null;
    }

    public static void StoreParentOnNBT(
            ItemStack itemInHand,
            BlockPos p
    ) {
        itemInHand.getOrCreateTag().putInt(String.format("%s.parent_pos_x", Questown.MODID), p.getX());
        itemInHand.getOrCreateTag().putInt(String.format("%s.parent_pos_y", Questown.MODID), p.getY());
        itemInHand.getOrCreateTag().putInt(String.format("%s.parent_pos_z", Questown.MODID), p.getZ());
    }

    public static void StoreFlagInputOnOutputNBT(
            ItemStack input,
            ItemStack output
    ) {
        String key = String.format("%s.flag_input_itemstack", Questown.MODID);
        output.getOrCreateTag().put(key, input.serializeNBT());
    }

    public static @Nullable ItemStack GetFlagInputFromItemNBT(
            ItemStack item
    ) {
        String key = String.format("%s.flag_input_itemstack", Questown.MODID);
        CompoundTag serialized = item.getOrCreateTag().getCompound(key);
        if (serialized.isEmpty()) {
            return null;
        }
        return ItemStack.of(serialized);
    }

    @Override
    public void attack(BlockState p_60499_, Level level, BlockPos p_60501_, Player player) {
        super.attack(p_60499_, level, p_60501_, player);
        if (level.isClientSide()) {
            return;
        }
        if (informedPlayers.containsKey(player)) {
            if (level.getGameTime() - informedPlayers.get(player) < 1000L) {
                return;
            }
        }
        player.sendMessage(
                new TranslatableComponent("messages.town_flag.damaged"), null
        );
        informedPlayers.put(player, level.getGameTime());
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

        InteractionResult sidedSuccess = convertItemInHand(level, player, hand, entity);
        if (sidedSuccess != null) {
            return sidedSuccess;
        }

        ImmutableList<Quest<ResourceLocation, MCRoom>> aQ = entity.getAllQuests();
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
