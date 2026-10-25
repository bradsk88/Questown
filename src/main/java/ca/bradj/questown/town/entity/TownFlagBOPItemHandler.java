package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import ca.bradj.questown.loader.ItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class TownFlagBOPItemHandler implements ItemHandler {

    /** The flag holds at most this many Blocks of Progress; a full flag silently drops further ones. */
    public static final int CAP = 64;

    private final TownFlagBlockEntity town;

    public TownFlagBOPItemHandler(TownFlagBlockEntity town) {
        this.town = town;
    }

    public static void eject(
            TownFlagBlockEntity flag,
            ServerPlayer recipient
    ) {
        // FIFO: the player collects the oldest deposit first (see bopStacks on the entity).
        if (flag.bopStacks.isEmpty()) {
            return;
        }
        ItemStack v = flag.bopStacks.remove(0);
        flag.markBopWithdrawn();
        flag.setChanged();
        flag.syncBopFull();
        BlockPos bp = flag.getTownFlagBasePos();
        flag.messages.broadcastMessage(
                "messages.player.took_bop",
                recipient.getName(),
                Compat.getItemName(v),
                Util.getTinyString(bp)
        );
        if (recipient.getInventory().add(v)) {
            recipient.getInventory().setChanged();
            recipient.inventoryMenu.broadcastChanges();
            return;
        }
        ServerLevel level = flag.getServerLevel();
        bp = bp.relative(Compat.getRandomHorizontal(level));
        ItemEntity item = new ItemEntity(level, bp.getX(), bp.getY(), bp.getZ(), v);
        level.addFreshEntity(item);
    }

    @Override
    public int getSlots() {
        return CAP;
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int i) {
        if (i >= town.getBopCount()) {
            return ItemStack.EMPTY;
        }
        return town.bopStacks.get(i);
    }

    @Override
    public @NotNull ItemStack insertItem(
            int i,
            @NotNull ItemStack itemStack,
            boolean simulate
    ) {
        if (itemStack.isEmpty()) {
            // A consumed/empty stack is not a deposit. 1.20.6 copy() of an empty stack hands
            // back the global ItemStack.EMPTY singleton, and setCount() on it does not reset
            // the item reference, so storing it would poison the shared EMPTY and make the
            // BE save throw "Cannot encode empty ItemStack" (1.19.2 never hit this; its
            // save() tolerated empties).
            return itemStack;
        }
        if (!isItemValid(i, itemStack)) {
            return itemStack;
        }
        return depositOne(itemStack, simulate, stored -> {
            town.bopStacks.add(stored);
            town.syncBopFull();
            QT.FLAG_LOGGER.debug("Flag now contains {} BOPs", town.getBopCount());
            // Two lines in one message (r17 #18, dev: "that message should be two lines"): the lang
            // value carries the line break, since two broadcasts are paced 100 ticks apart.
            town.messages.broadcastMessage("messages.bop.earned");
        });
    }

    /**
     * Takes one item off {@code from} and hands {@code store} a 1-count snapshot of it (bless
     * NBT included). Returns what is left of {@code from}.
     */
    static ItemStack depositOne(
            ItemStack from,
            boolean simulate,
            Consumer<ItemStack> store
    ) {
        // Snapshot BEFORE shrinking: a villager carries one BOP, so shrink(1) empties the
        // stack and copy() would hand back ItemStack.EMPTY (agent playtest 2026-10-22 r3:
        // earned BOPs were stored as EMPTY, counted but never saved or collectable).
        ItemStack stored = from.copyWithCount(1);
        if (simulate) {
            return from.copyWithCount(from.getCount() - 1);
        }
        from.shrink(1);
        store.accept(stored);
        return from;
    }

    @Override
    public @NotNull ItemStack extractItem(
            int i,
            int i1,
            boolean b
    ) {
        // Extraction is not currently supported
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int i) {
        return 1;
    }

    @Override
    public boolean isItemValid(
            int i,
            @NotNull ItemStack itemStack
    ) {
        if (i >= getSlots()) {
            return false;
        }
        if (i < town.getBopCount()) {
            return false;
        }
        return itemStack.is(ItemsInit.BLOCK_OF_PROGRESS.get());
    }
    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        throw new UnsupportedOperationException("BOP handler is deposit-only; setStackInSlot is not supported.");
    }

    @Override
    public void setChanged() {
        // No-op: the flag owns the bopStacks list and marks itself changed on deposit/eject.
    }
}
