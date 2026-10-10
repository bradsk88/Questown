package ca.bradj.questown.blocks;

import ca.bradj.questown.core.init.TilesInit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The bread oven's item store: slot 0 = wheat (input), slot 1 = coal (fuel),
 * slot 2 = bread (output). Bakes 1 wheat + 1 coal into 1 bread over
 * {@link BreadOvenBake#BAKE_TICKS} ticks, mirroring the cook's furnace slot
 * idiom — the cook-shaped job family inserts one item per job cycle into a
 * slot the job gate requires to be empty, and the extract job takes the
 * product from the output slot.
 *
 * <p>All bake arithmetic lives in {@link BreadOvenBake}, which this entity
 * and the warp lane ({@code WarpWorldAccess.advanceBreadOven}) share, so the
 * warp can never drift from real time. This entity owns the container
 * surface, NBT, and the {@code bake_state} visual property (kept in sync
 * with the slot contents by {@link #syncVisualState()}, not by the
 * townie's work-state index).
 */
public class BreadOvenBlockEntity extends BaseContainerBlockEntity {

    public static final String ID = "bread_oven_block";

    private final NonNullList<ItemStack> items =
            NonNullList.withSize(3, ItemStack.EMPTY);
    private final BreadOvenBake bake = new BreadOvenBake(items);

    public BreadOvenBlockEntity(BlockPos pos, BlockState state) {
        super(TilesInit.BREAD_OVEN.get(), pos, state);
    }

    /** Static ticker entry point wired via {@code BreadOvenBlock.getTicker}. */
    public static void tick(
            Level level,
            BlockPos pos,
            BlockState state,
            BreadOvenBlockEntity e
    ) {
        if (level.isClientSide()) {
            return;
        }
        e.advanceBakeOneTick();
    }

    /** One bake step; runs each server tick. */
    public void advanceBakeOneTick() {
        bake.advanceOneTick();
        setChanged();
        syncVisualState();
    }

    /**
     * Advance the bake by up to {@code ticks} ticks (warp lane). Mirrors the
     * furnace loop shape: one bake step per tick, stop once no further bake
     * is possible.
     */
    public void advanceBake(int ticks) {
        bake.advance(ticks);
        setChanged();
        syncVisualState();
    }

    /** Bread taken from the output slot, or null when the slot is empty. */
    public @Nullable ItemStack takeBread(int count) {
        ItemStack taken = bake.takeBread(count);
        if (taken != null) {
            setChanged();
            syncVisualState();
        }
        return taken;
    }

    /** Keep the {@code bake_state} visual property in sync with the slot contents. */
    private void syncVisualState() {
        Level level = getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = level.getBlockState(getBlockPos());
        if (!(state.getBlock() instanceof BreadOvenBlock)) {
            return;
        }
        int visual = visualState();
        if (state.getValue(BreadOvenBlock.BAKE_STATE) == visual) {
            return;
        }
        level.setBlockAndUpdate(getBlockPos(), state.setValue(BreadOvenBlock.BAKE_STATE, visual));
    }

    private int visualState() {
        ItemStack bread = bake.getItem(BreadOvenBake.SLOT_BREAD);
        if (!bread.isEmpty()) {
            return BreadOvenBlock.BAKE_STATE_BAKED;
        }
        ItemStack wheat = bake.getItem(BreadOvenBake.SLOT_WHEAT);
        ItemStack coal = bake.getItem(BreadOvenBake.SLOT_COAL);
        if (BreadOvenBake.isWheat(wheat) && BreadOvenBake.isCoal(coal)) {
            return bake.progress() > 0
                    ? BreadOvenBlock.BAKE_STATE_BAKING
                    : BreadOvenBlock.BAKE_STATE_FILLED;
        }
        if (BreadOvenBake.isWheat(wheat)) {
            return BreadOvenBlock.BAKE_STATE_HALF_FILLED;
        }
        return BreadOvenBlock.BAKE_STATE_EMPTY;
    }

    // --- Container ---

    @Override
    public int getContainerSize() {
        return 3;
    }

    @Override
    protected @NotNull NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> newItems) {
        this.items.clear();
        for (int i = 0; i < newItems.size() && i < items.size(); i++) {
            this.items.set(i, newItems.get(i));
        }
    }

    @Override
    protected @Nullable net.minecraft.world.inventory.AbstractContainerMenu createMenu(
            int containerId,
            @NotNull net.minecraft.world.entity.player.Inventory playerInventory
    ) {
        // The oven has no GUI; the job slot rules and the player right-click
        // are the only access paths.
        return null;
    }

    @Override
    public boolean canPlaceItem(int index, @NotNull ItemStack stack) {
        return switch (index) {
            case BreadOvenBake.SLOT_WHEAT -> BreadOvenBake.isWheat(stack);
            case BreadOvenBake.SLOT_COAL -> BreadOvenBake.isCoal(stack);
            case BreadOvenBake.SLOT_BREAD -> Items.BREAD == stack.getItem();
            default -> false;
        };
    }

    @Override
    protected @NotNull net.minecraft.network.chat.Component getDefaultName() {
        return net.minecraft.network.chat.Component.literal("Bread Oven");
    }

    // --- NBT ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("bake_progress", bake.progress());
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        bake.loadProgress(tag.getInt("bake_progress"));
        ContainerHelper.loadAllItems(tag, items, registries);
    }
}
