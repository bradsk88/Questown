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

/**
 * In 1.19.2 the HUD (hearts, hunger, XP, hotbar, chat) was drawn after the current
 * screen, so Questown's menus showed it on top of the menu background. In 1.20.6
 * GameRenderer draws the HUD before the screen, so an opaque screen background hides
 * it. This handler restores the 1.19.2 layering for the screen families that match
 * the 1.19.2 golden captures by re-rendering the HUD after the screen.
 *
 * <p>The re-render draws the full {@link net.minecraft.client.gui.Gui} layer stack, which
 * in 1.20.6 includes the crosshair - and the crosshair would otherwise pop back on top of
 * the menu (the day-1 playtest's "crosshair renders above menus"). The {@link
 * #onRenderGuiLayerPre} hook drops just the crosshair layer during the re-render pass so
 * the HUD is restored but the crosshair stays suppressed.
 */
@EventBusSubscriber(modid = Questown.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class QuestownHudEvents {

    private QuestownHudEvents() {
    }

    // Set only while the post-screen HUD re-render is in flight (client render thread,
    // single-threaded), so the layer hook can tell a re-render pass from the normal
    // pre-screen HUD draw (where the crosshair should render as usual and get covered).
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

    // During the post-screen HUD re-render, skip the crosshair layer so it doesn't draw on
    // top of the menu. In the normal pre-screen pass reRenderingHud is false, so the
    // crosshair renders as vanilla would (and the opaque screen background covers it).
    @SubscribeEvent
    public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        if (reRenderingHud && VanillaGuiLayers.CROSSHAIR.equals(event.getName())) {
            event.setCanceled(true);
        }
    }
}
