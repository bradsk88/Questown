package ca.bradj.questown.loader;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Neutral factory for a menu container. Matches the shape the active loader needs
 * (NeoForge {@code IContainerFactory}: {@code (windowId, playerInventory, extraData) -> container}).
 * A mod-owned container's static {@code ForClient(int, Inventory, RegistryFriendlyByteBuf)} method reference
 * targets this type, so a main-code {@code Container::ForClient} is loader-neutral.
 */
@FunctionalInterface
public interface MenuFactory<T extends AbstractContainerMenu> {
    T create(int windowId, Inventory playerInventory, RegistryFriendlyByteBuf extraData);
}
