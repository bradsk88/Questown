package ca.bradj.questown.loader.neo;

import ca.bradj.questown.core.init.items.ItemsInit;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Per-loader binder: forwards right-click interactions to the neutral core in {@link ItemsInit}. */
public class ItemsInteract {
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getSide().isServer() || !(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl
                && ItemsInit.onInteractRightClick(sl, sp, event.getPos(), event.getItemStack())) {
            event.setCanceled(true);
        }
    }
}
