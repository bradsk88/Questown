package ca.bradj.questown.loader;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** A menu container factory: {@code (windowId, playerInventory, extraData) -> container}. */
@FunctionalInterface
public interface MenuFactory<T extends AbstractContainerMenu> {
    T create(int windowId, Inventory playerInventory, FriendlyByteBuf extraData);
}
