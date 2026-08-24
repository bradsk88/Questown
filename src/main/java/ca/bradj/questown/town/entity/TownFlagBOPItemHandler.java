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
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

public class TownFlagBOPItemHandler implements IItemHandler {

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
        flag.bopCount--;
        flag.setChanged();
        flag.syncBopFull();
        ItemStack v = ItemsInit.BLOCK_OF_PROGRESS.get().getDefaultInstance();
        BlockPos bp = flag.getTownFlagBasePos();
        flag.messages.broadcastMessage(
                "messages.player.took_bop",
                recipient.getName(),
                ItemsInit.BLOCK_OF_PROGRESS.get().getDefaultInstance(),
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
        if (i >= town.bopCount) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(ItemsInit.BLOCK_OF_PROGRESS.get());
    }

    @Override
    public @NotNull ItemStack insertItem(
            int i,
            @NotNull ItemStack itemStack,
            boolean simulate
    ) {
        if (!isItemValid(i, itemStack)) {
            return itemStack;
        }
        itemStack.shrink(1);
        if (!simulate) {
            town.bopCount++;
            town.syncBopFull();
            QT.FLAG_LOGGER.debug("Flag now contains {} BOPs", town.bopCount);
            town.messages.broadcastMessage("messages.bop.earned");
        }
        return itemStack;
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
        if (i < town.bopCount) {
            return false;
        }
        return itemStack.is(ItemsInit.BLOCK_OF_PROGRESS.get());
    }
}
