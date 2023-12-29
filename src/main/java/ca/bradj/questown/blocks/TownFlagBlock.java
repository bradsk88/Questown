package ca.bradj.questown.blocks;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.ModItemGroup;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.core.materials.WallType;
import ca.bradj.questown.town.TownFlagBlockEntity;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.questown.town.rewards.AddBatchOfRandomQuestsForVisitorReward;
import ca.bradj.questown.town.rewards.AddRandomUpgradeQuest;
import ca.bradj.questown.town.rewards.SpawnVisitorReward;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
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
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.Nullable;

import java.util.*;


public class TownFlagBlock extends BaseEntityBlock {

    public static final String ITEM_ID = "flag_base";
    public static final Item.Properties ITEM_PROPS = new Item.Properties().
            tab(ModItemGroup.QUESTOWN_GROUP);
    private Map<Player, Long> informedPlayers = new HashMap<>();

    public TownFlagBlock() {
        super(
                BlockBehaviour.Properties.of(Material.STONE, MaterialColor.COLOR_GRAY)
                        .strength(10.0F, 1200.0F)
                        .noOcclusion()
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
            QT.ITEM_LOGGER.error("Missing tag");
            return null;
        }
        int x, y, z;
        String xTag = String.format("%s.parent_pos_x", Questown.MODID);
        if (!itemInHand.getTag().contains(xTag)) {
            QT.ITEM_LOGGER.error("Missing X tag from {}", itemInHand.getTag());
            return null;
        }
        String yTag = String.format("%s.parent_pos_y", Questown.MODID);
        if (!itemInHand.getTag().contains(yTag)) {
            QT.ITEM_LOGGER.error("Missing Y tag from {}", itemInHand.getTag());
            return null;
        }
        String zTag = String.format("%s.parent_pos_z", Questown.MODID);
        if (!itemInHand.getTag().contains(zTag)) {
            QT.ITEM_LOGGER.error("Missing Z tag from {}", itemInHand.getTag());
            return null;
        }
        x = itemInHand.getOrCreateTag().getInt(xTag);
        y = itemInHand.getOrCreateTag().getInt(yTag);
        z = itemInHand.getOrCreateTag().getInt(zTag);


        BlockPos bp = new BlockPos(x, y, z);
        BlockEntity oEntity = level.getBlockEntity(bp);
        if (oEntity instanceof TownFlagBlockEntity e) {
            return e;
        }
        return null;

    }
    public static void CopyParentFromNBT(
            ItemStack input,
            ItemStack output
    ) {
        if (input.getTag() == null) {
            QT.ITEM_LOGGER.error("No parent stored on item: {}", input.getTag());
            return;
        }
        int x, y, z;
        String xTag = String.format("%s.parent_pos_x", Questown.MODID);
        if (!input.getTag().contains(xTag)) {
            QT.ITEM_LOGGER.error("No parent X coord stored on item: {}", input.getTag());
            return;
        }
        String yTag = String.format("%s.parent_pos_y", Questown.MODID);
        if (!input.getTag().contains(yTag)) {
            QT.ITEM_LOGGER.error("No parent Y coord stored on item: {}", input.getTag());
            return;
        }
        String zTag = String.format("%s.parent_pos_z", Questown.MODID);
        if (!input.getTag().contains(zTag)) {
            QT.ITEM_LOGGER.error("No parent Z coord stored on item: {}", input.getTag());
            return;
        }
        output.getOrCreateTag().putInt(xTag, input.getTag().getInt(xTag));
        output.getOrCreateTag().putInt(yTag, input.getTag().getInt(yTag));
        output.getOrCreateTag().putInt(zTag, input.getTag().getInt(zTag));
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

        if (itemInHand.getItem().equals(Items.DIAMOND)) {
            for (UUID uuid : entity.getVillagersWithQuests()) {
                entity.addImmediateReward(
                        new AddRandomUpgradeQuest(entity, uuid)
                );
                return InteractionResult.sidedSuccess(false);
            }
        }

        if (itemInHand.getItem().equals(Items.GOLD_BLOCK)) {
            UUID randomVillager = entity.getRandomVillager();
            entity.addImmediateReward(
                    new AddBatchOfRandomQuestsForVisitorReward(entity, randomVillager)
            );
            return InteractionResult.sidedSuccess(false);
        }

        if (itemInHand.getItem().equals(Items.OAK_LOG)) {
            List<String> qss = entity.getAllQuests().stream().map(Quest::toShortString).toList();
            QT.FLAG_LOGGER.debug("Town UUID: {}", entity.getUUID());
            QT.FLAG_LOGGER.debug("Quests:\n{}", Strings.join(qss, '\n'));
            QT.FLAG_LOGGER.debug("Villagers:\n{}", Strings.join(entity.getVillagers(), '\n'));
//            QT.FLAG_LOGGER.debug("Villager Jobs:\n{}", Strings.join(entity.getJobs(), '\n'));
            QT.FLAG_LOGGER.debug("Room Recipes:\n{}", Strings.join(entity.getMatches(), '\n'));
            return InteractionResult.sidedSuccess(false);
        }

        if (itemInHand.getItem().equals(Items.POTATO)) {
            entity.recallVillagers();
            return InteractionResult.sidedSuccess(false);
        }

        // TODO: Consider making a new recipe type so any item can be cnverted to
        //  any other item and with the parent NBT stored on the new item.
        ItemStack converted = null;

        if (itemInHand.getItem().equals(ItemsInit.WELCOME_MAT_BLOCK.get())) {
            converted = itemInHand;
        }
        if (Ingredient.of(ItemTags.SIGNS).test(itemInHand)) {
            converted = ItemsInit.JOB_BOARD_BLOCK.get().getDefaultInstance();
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
        if (itemInHand.getItem().equals(ItemsInit.TOWN_DOOR.get())) {
            converted = ItemsInit.TOWN_DOOR.get().getDefaultInstance();
        }
        if (itemInHand.getItem().equals(Items.COBBLESTONE)) {
            converted = ItemsInit.FALSE_DOOR.get().getDefaultInstance();
        }
        if (itemInHand.getItem().equals(ItemsInit.FALSE_DOOR.get())) {
            converted = ItemsInit.FALSE_WALL_BLOCK.get().getDefaultInstance();
        }
        if (itemInHand.getItem().equals(ItemsInit.FALSE_WALL_BLOCK.get())) {
            converted = ItemsInit.FALSE_DOOR.get().getDefaultInstance();
        }
        if (Ingredient.of(Tags.Items.FENCE_GATES).test(itemInHand)) {
            converted = ItemsInit.TOWN_FENCE_GATE.get().getDefaultInstance();
            // TODO: Advancement
        }
        if (itemInHand.getItem().equals(ItemsInit.TOWN_FENCE_GATE.get())) {
            converted = ItemsInit.TOWN_FENCE_GATE.get().getDefaultInstance();
        }

        if (converted != null) {
            StoreFlagInputOnOutputNBT(itemInHand, converted);
            player.setItemInHand(hand, converted);
            QT.FLAG_LOGGER.debug(
                    "{} created at {}",
                    ForgeRegistries.ITEMS.getKey(converted.getItem()),
                    entity.getTownFlagBasePos()
            );
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
            QT.FLAG_LOGGER.debug(
                    "{} has been paired with {} at {}",
                    ForgeRegistries.ITEMS.getKey(converted.getItem()), entity.getUUID(), entity.getTownFlagBasePos()
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
    public void attack(
            BlockState p_60499_,
            Level level,
            BlockPos p_60501_,
            Player player
    ) {
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

        oEntity.get().getQuestHandle().showQuestsUI((ServerPlayer) player);
        return InteractionResult.sidedSuccess(false);
    }

}
