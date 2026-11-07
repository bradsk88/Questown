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

/** The bread oven's slots (0 wheat, 1 coal, 2 bread) and its bake progress. */
public class BreadOvenBlockEntity extends BaseContainerBlockEntity {

    public static final String ID = "bread_oven_block";

    private final NonNullList<ItemStack> items =
            NonNullList.withSize(3, ItemStack.EMPTY);
    private final BreadOvenBake bake = new BreadOvenBake(items);

    public BreadOvenBlockEntity(BlockPos pos, BlockState state) {
        super(TilesInit.BREAD_OVEN.get(), pos, state);
    }

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

    public void advanceBakeOneTick() {
        bake.advanceOneTick();
        setChanged();
        syncVisualState();
    }

    /** Bakes for up to {@code ticks} ticks at once. */
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

    @Override
    public int getContainerSize() {
        return 3;
    }

    protected @NotNull NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public @NotNull ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public @NotNull ItemStack removeItem(int slot, int amount) {
        ItemStack out = ContainerHelper.removeItem(items, slot, amount);
        if (!out.isEmpty()) {
            setChanged();
        }
        return out;
    }

    @Override
    public @NotNull ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean stillValid(@NotNull net.minecraft.world.entity.player.Player player) {
        return false;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    protected @Nullable net.minecraft.world.inventory.AbstractContainerMenu createMenu(
            int containerId,
            @NotNull net.minecraft.world.entity.player.Inventory playerInventory
    ) {
        // The oven has no screen.
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

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("bake_progress", bake.progress());
        ContainerHelper.saveAllItems(tag, items);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        bake.loadProgress(tag.getInt("bake_progress"));
        ContainerHelper.loadAllItems(tag, items);
    }
}
