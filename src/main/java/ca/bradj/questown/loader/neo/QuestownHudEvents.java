package ca.bradj.questown.loader.neo;

import ca.bradj.questown.Questown;
import ca.bradj.questown.gui.AbstractPagedCardScreen;
import ca.bradj.questown.gui.InventoryAndStatusScreen;
import ca.bradj.questown.gui.VillagerStatsScreen;
import ca.bradj.questown.gui.villager.advancements.VillagerAdvancementsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Draws the HUD after Questown's menus, as 1.19.2 did, without the crosshair. */
@EventBusSubscriber(modid = Questown.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class QuestownHudEvents {

    private QuestownHudEvents() {
    }

    // True only during the post-screen HUD redraw.
    private static boolean reRenderingHud = false;

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractPagedCardScreen
                || screen instanceof InventoryAndStatusScreen
                || screen instanceof VillagerStatsScreen
                || screen instanceof VillagerAdvancementsScreen)) {
            return;
        }
        reRenderingHud = true;
        try {
            Minecraft.getInstance().gui.render(event.getGuiGraphics(), event.getPartialTick());
        } finally {
            reRenderingHud = false;
        }
    }

    // No crosshair during the redraw.
    @SubscribeEvent
    public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        if (reRenderingHud && VanillaGuiLayers.CROSSHAIR.equals(event.getName())) {
            event.setCanceled(true);
        }
    }
}
