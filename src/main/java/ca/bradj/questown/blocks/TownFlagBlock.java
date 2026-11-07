package ca.bradj.questown.blocks;
import ca.bradj.questown.loader.Loader;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.entity.BlockAsRoomEntity;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.core.advancements.RoomTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.core.init.ModItemGroup;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.core.materials.WallType;
import ca.bradj.questown.core.network.OpenDormantFlagMessage;
import ca.bradj.questown.core.network.OpenFlagMenuMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.gui.FlagTabs;
import ca.bradj.questown.gui.FlagTabsEmbedding;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.MapC;
import ca.bradj.questown.mobs.helperchicken.ChickenArcUiObservations;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.questown.town.rewards.AddBatchOfQuestsForVisitorReward;
import ca.bradj.questown.town.rewards.AddRandomUpgradeQuest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;


public class TownFlagBlock extends BaseEntityBlock {

    public static final com.mojang.serialization.MapCodec<TownFlagBlock> CODEC =
            com.mojang.serialization.MapCodec.unit(TownFlagBlock::new);

    public static final String ITEM_ID = "flag_base";
    public static final Item.Properties ITEM_PROPS = new Item.Properties();
    public static final Property<Boolean> INACTIVE = BooleanProperty.create("inactive");
    public static final EnumProperty<FlagPhase> PHASE = EnumProperty.create("phase", FlagPhase.class);
    // A relocation deed waits above this flag.
    public static final Property<Boolean> DEED_AVAILABLE = BooleanProperty.create("deed_available");
    // The flag holds as many Blocks of Progress as it can; more would be lost.
    public static final Property<Boolean> BOP_FULL = BooleanProperty.create("bop_full");
    private Map<Player, Long> informedPlayers = new HashMap<>();

    public TownFlagBlock() {
        super(
                Compat.P.of().mapColor(MapC.COLOR_GRAY)
                                         .strength(10.0F, 1200.0F)
                                         .noOcclusion().impl()
        );
        this.registerDefaultState(this.stateDefinition.any()
                                                       .setValue(INACTIVE, false)
                                                       .setValue(PHASE, FlagPhase.ACTIVE)
                                                       .setValue(DEED_AVAILABLE, false)
                                                       .setValue(BOP_FULL, false));
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_51385_) {
        p_51385_.add(INACTIVE, PHASE, DEED_AVAILABLE, BOP_FULL);
    }

    public static String itemId(WallType wallType) {
        return String.format("%s_%s", wallType.asString(), ITEM_ID);
    }

    public static TownFlagBlockEntity GetParentFromNBT(
            ServerLevel level,
            ItemStack itemInHand
    ) {
        BlockPos bp = GetParentPosFromNBT(itemInHand);
        if (bp == null) return null;
        BlockEntity oEntity = level.getBlockEntity(bp);
        if (oEntity instanceof TownFlagBlockEntity e) {
            return e;
        }
        return null;

    }

    public static @Nullable BlockPos GetParentPosFromNBT(ItemStack itemInHand) {
        net.minecraft.nbt.CompoundTag data = Compat.itemDataTag(itemInHand);
        if (data.isEmpty()) {
            QT.ITEM_LOGGER.error("Missing NBT tag on {}", itemInHand);
            return null;
        }
        int x, y, z;
        String xTag = String.format("%s.parent_pos_x", Questown.MODID);
        if (!data.contains(xTag)) {
            QT.ITEM_LOGGER.error("Missing X tag from {}", data);
            return null;
        }
        String yTag = String.format("%s.parent_pos_y", Questown.MODID);
        if (!data.contains(yTag)) {
            QT.ITEM_LOGGER.error("Missing Y tag from {}", data);
            return null;
        }
        String zTag = String.format("%s.parent_pos_z", Questown.MODID);
        if (!data.contains(zTag)) {
            QT.ITEM_LOGGER.error("Missing Z tag from {}", data);
            return null;
        }
        x = data.getInt(xTag);
        y = data.getInt(yTag);
        z = data.getInt(zTag);


        BlockPos bp = new BlockPos(x, y, z);
        return bp;
    }

    private static net.minecraft.nbt.CompoundTag customData(ItemStack stack) {
        return Compat.itemDataTag(stack);
    }

    public static void CopyParentFromNBT(
            ItemStack input,
            ItemStack output
    ) {
        net.minecraft.nbt.CompoundTag in = customData(input);
        if (in.isEmpty()) {
            QT.ITEM_LOGGER.error("No parent stored on item: {}", in);
            return;
        }
        int x, y, z;
        String xTag = String.format("%s.parent_pos_x", Questown.MODID);
        if (!in.contains(xTag)) {
            QT.ITEM_LOGGER.error("No parent X coord stored on item: {}", in);
            return;
        }
        String yTag = String.format("%s.parent_pos_y", Questown.MODID);
        if (!in.contains(yTag)) {
            QT.ITEM_LOGGER.error("No parent Y coord stored on item: {}", in);
            return;
        }
        String zTag = String.format("%s.parent_pos_z", Questown.MODID);
        if (!in.contains(zTag)) {
            QT.ITEM_LOGGER.error("No parent Z coord stored on item: {}", in);
            return;
        }
        CompoundTag inTag = in;
        Compat.updateItemDataTag(output, out -> {
            out.putInt(xTag, inTag.getInt(xTag));
            out.putInt(yTag, inTag.getInt(yTag));
            out.putInt(zTag, inTag.getInt(zTag));
        });
    }

    @Nullable
    private static boolean shouldPreSelectBopTab(TownFlagBlockEntity entity, ServerPlayer player) {
        if (entity.getBlocksOfProgress() <= 0) {
            return false;
        }
        // Pre-select BOP tab if the player hasn't viewed it yet
        ResourceLocation advId = new ResourceLocation(Questown.MODID, "first_bop_view");
        net.minecraft.advancements.Advancement adv = player.getServer().getAdvancements().getAdvancement(advId);
        if (adv == null) {
            return true;
        }
        return !player.getAdvancements().getOrStartProgress(adv).isDone();
    }


    /**
     * A flag-linked block placed from an item with no (or a dead) flag link drops back as an item.
     * Tell the placer why instead of failing silently.
     */
    public static void tellPlacerNotLinked(
            net.minecraft.world.item.context.BlockPlaceContext ctx,
            String msgKey
    ) {
        if (ctx.getPlayer() instanceof ServerPlayer sp) {
            ca.bradj.questown.mc.Util.onScreenText(() -> sp, msgKey);
        }
    }

    public static void StoreParentOnNBT(
            ItemStack itemInHand,
            BlockPos p
    ) {
        getParentData(p).forEach((key, value) -> Compat.updateItemDataTag(itemInHand, tag -> tag.putInt(key, value)));
    }

    public static ImmutableMap<String, Integer> getParentData(
            BlockPos parentPos
    ) {
        return ImmutableMap.of(
                String.format("%s.parent_pos_x", Questown.MODID), parentPos.getX(),
                String.format("%s.parent_pos_y", Questown.MODID), parentPos.getY(),
                String.format("%s.parent_pos_z", Questown.MODID), parentPos.getZ()
        );
    }


    public static @Nullable ItemStack GetFlagInputFromItemNBT(
            net.minecraft.core.RegistryAccess ctx,
            ItemStack item
    ) {
        String key = String.format("%s.flag_input_itemstack", Questown.MODID);
        CompoundTag serialized = Compat.itemDataTag(item).getCompound(key);
        if (serialized.isEmpty()) {
            return null;
        }
        ItemStack parsed = ItemStack.of(serialized);
        return parsed.isEmpty() ? null : parsed;
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
        Compat.sendMessage((ServerPlayer) player, Compat.translatable("messages.town_flag.damaged"));
        informedPlayers.put(player, level.getGameTime());
    }

    @Override
    public RenderShape getRenderShape(BlockState blockState) {
        return RenderShape.MODEL;
    }

    @Override
    public float getDestroyProgress(
            BlockState state,
            Player player,
            BlockGetter level,
            BlockPos pos
    ) {
        // The town flag can't be mined: that would destroy the town. Moving it goes through the shutdown ritual.
        return 0.0F;
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
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            net.minecraft.world.entity.LivingEntity placer,
            ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // A player who places a flag gets a map centred on it.
        if (!(level instanceof ServerLevel) || !(placer instanceof ServerPlayer player)) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof TownFlagBlockEntity flag) {
            flag.grantStarterMap(player);
        }
    }

    public InteractionResult useWithoutItem(
            BlockState blockState,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        InteractionHand hand = player.getOffhandItem().isEmpty() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        return useFlag(level, pos, player, hand);
    }

    public ca.bradj.questown.mc.ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState blockState,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        // A Block of Progress used on the flag goes back into it.
        if (stack.is(ItemsInit.BLOCK_OF_PROGRESS.get())) {
            if (level instanceof ServerLevel && player instanceof ServerPlayer sp
                    && level.getBlockEntity(pos) instanceof TownFlagBlockEntity flag) {
                flag.returnBopFromPlayer(sp, stack);
            }
            return ca.bradj.questown.mc.ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        InteractionResult r = useFlag(level, pos, player, hand);
        if (r == InteractionResult.PASS) {
            return ca.bradj.questown.mc.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return ca.bradj.questown.mc.ItemInteractionResult.sidedSuccess(true);
    }

    private InteractionResult useFlag(
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand
    ) {
        if (!(level instanceof ServerLevel sl)) {
            return InteractionResult.sidedSuccess(true);
        }

        if (!(level.getBlockEntity(pos) instanceof TownFlagBlockEntity entity)) {
            return InteractionResult.sidedSuccess(true);
        }

        BlockState flagState = level.getBlockState(pos);
        FlagPhase phase = flagState.hasProperty(PHASE) ? flagState.getValue(PHASE) : FlagPhase.ACTIVE;
        switch (FlagClickAction.of(entity.isDeedAvailable(), phase)) {
            case COLLECT_DEED -> {
                entity.collectDeed((ServerPlayer) player);
                return InteractionResult.sidedSuccess(false);
            }
            case DORMANT_CHOICES -> {
                QuestownNetwork.sendToPlayer((ServerPlayer) player, new OpenDormantFlagMessage(pos));
                return InteractionResult.sidedSuccess(false);
            }
            case FLAG_MENU -> {
            }
        }

        if (entity.isInitialized()) {
            FlagTabsEmbedding.FlagInfo info = entity.getInfo();
            String tab = FlagTabs.initialFlagTab(
                    entity.isBopFull(),
                    info.villagersTabAttention(),
                    info.bopTabAttention() && shouldPreSelectBopTab(entity, (ServerPlayer) player),
                    info.questsTabAttention(),
                    info.showEconTab() && info.bonusFoodAvailable()
            );
            entity.menus.showUI((ServerPlayer) player, tab, info, entity.getBlocksOfProgress());
            // Any flag tab counts for the chicken's "look at the flag" lesson.
            ChickenArcUiObservations.markFlagUiOpened(level, pos);
        } else {
            if (!entity.isInitializing()) {
                entity.initializeFreshFlag(true);
            }
            player.displayClientMessage(Compat.translatable("messages.town_flag.loading"), false);
        }
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    public net.minecraft.world.InteractionResult use(
            net.minecraft.world.level.block.state.BlockState qtState,
            net.minecraft.world.level.Level qtLevel,
            net.minecraft.core.BlockPos qtPos,
            net.minecraft.world.entity.player.Player qtPlayer,
            net.minecraft.world.InteractionHand qtHand,
            net.minecraft.world.phys.BlockHitResult qtHit
    ) {
        ca.bradj.questown.mc.ItemInteractionResult qtItem = useItemOn(qtPlayer.getItemInHand(qtHand), qtState, qtLevel, qtPos, qtPlayer, qtHand, qtHit);
        if (qtItem.consumesAction()) {
            return qtItem.result();
        }
        if (qtItem != ca.bradj.questown.mc.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (qtHand == net.minecraft.world.InteractionHand.MAIN_HAND) {
            return useWithoutItem(qtState, qtLevel, qtPos, qtPlayer, qtHit);
        }
        return net.minecraft.world.InteractionResult.PASS;
    }
}
