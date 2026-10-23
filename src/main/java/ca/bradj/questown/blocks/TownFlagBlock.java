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

    @Override
    public com.mojang.serialization.MapCodec<TownFlagBlock> codec() {
        return CODEC;
    }


    public static final String ITEM_ID = "flag_base";
    public static final Item.Properties ITEM_PROPS = new Item.Properties();
    public static final Property<Boolean> INACTIVE = BooleanProperty.create("inactive");
    public static final EnumProperty<FlagPhase> PHASE = EnumProperty.create("phase", FlagPhase.class);
    // A relocation deed is waiting above this flag. A blockstate (not BE) property so it auto-syncs to
    // the client, which the flag has no ticker to hydrate BE data on. The "" model variant ignores it.
    public static final Property<Boolean> DEED_AVAILABLE = BooleanProperty.create("deed_available");
    // The flag's deposited BOPs have reached the cap. Rendered as a red BOP above the flag and surfaced
    // as a warning in the BOP menu, so a full flag (which silently loses further BOPs) is visible.
    // A blockstate (not BE) property so it auto-syncs to the client, like DEED_AVAILABLE.
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
        net.minecraft.advancements.AdvancementHolder adv = player.getServer().getAdvancements().get(advId);
        if (adv == null) {
            return true;
        }
        return !player.getAdvancements().getOrStartProgress(adv).isDone();
    }


    /**
     * A flag-linked block placed from an item with no (or a dead) flag link drops back as an item.
     * Tell the placer why instead of failing silently (2026-10-21 agent playtest).
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
            net.minecraft.core.HolderLookup.Provider ctx,
            ItemStack item
    ) {
        String key = String.format("%s.flag_input_itemstack", Questown.MODID);
        CompoundTag serialized = Compat.itemDataTag(item).getCompound(key);
        if (serialized.isEmpty()) {
            return null;
        }
        return ItemStack.parse(ctx, serialized).orElse(null);
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
        // The town flag is unbreakable in survival: it owns the town's authoritative data and
        // there is no loot/drop path, so mining it would destroy the town irrecoverably. The
        // only sanctioned removal is the shutdown ritual (relocation deed) or the creative-only
        // `qt flag destroy` dev command.
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
        // F1 (2026-10-13 walkthrough): a player-placed flag grants the placer a
        // flag-centered filled map (the flag's area becomes unknowable within
        // one coal-search of day 1 — `.scratch/day1-playtest-2026-10-11/flag-map-when-lost.md`).
        // Only a player placement grants: the console `/flag` command and
        // relocation go through setBlock* without a placer, so held runs and
        // dev worlds never pick up starter maps.
        if (!(level instanceof ServerLevel) || !(placer instanceof ServerPlayer player)) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof TownFlagBlockEntity flag) {
            flag.grantStarterMap(player);
        }
    }

    @Override
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

    @Override
    public net.minecraft.world.ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState blockState,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        InteractionResult r = useFlag(level, pos, player, hand);
        if (r == InteractionResult.PASS) {
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return net.minecraft.world.ItemInteractionResult.sidedSuccess(true);
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

        // No item-on-flag conversions (removed 2026-10-19, dev): everything that matters is
        // crafted from the flag's crafting tab (FlagCraftMessage).
        if (entity.isInitialized()) {
            // A full flag opens the BOP menu unconditionally (not gated on first_bop_view), so the
            // warning shows even once the player has seen the tab. Otherwise an unseen BOP tab
            // outranks the quests tab (day-1 playtest: opening on the multi-status overview shows
            // nothing selected, so open the tab with "stuff to see").
            FlagTabsEmbedding.FlagInfo info = entity.getInfo();
            String tab = FlagTabs.initialFlagTab(
                    entity.isBopFull(),
                    info.villagersTabAttention(),
                    info.bopTabAttention() && shouldPreSelectBopTab(entity, (ServerPlayer) player),
                    info.hasIncompleteQuests(),
                    info.showEconTab() && info.bonusFoodAvailable()
            );
            if (tab == null) {
                entity.getVillagerHandle().showMultiStatusUI((ServerPlayer) player);
            } else {
                entity.menus.showUI((ServerPlayer) player, tab, info, entity.getBlocksOfProgress());
            }
        } else {
            if (!entity.isInitializing()) {
                entity.initializeFreshFlag(true);
            }
            player.displayClientMessage(Compat.translatable("messages.town_flag.loading"), false);
        }
        return InteractionResult.sidedSuccess(false);
    }

}
