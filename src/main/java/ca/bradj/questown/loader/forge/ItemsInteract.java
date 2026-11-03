package ca.bradj.questown.loader.forge;

import ca.bradj.questown.core.init.items.ItemsInit;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

public class ItemsInteract {
    @SubscribeEvent
    public void onRightClickBlock_qii10(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getSide().isServer() || !(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl
                && ItemsInit.onInteractRightClick(sl, sp, event.getPos(), event.getItemStack())) {
            event.setCanceled(true);
        }
    }
}
