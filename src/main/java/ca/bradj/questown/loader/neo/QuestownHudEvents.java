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
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * In 1.19.2 the HUD (hearts, hunger, XP, hotbar, chat) was drawn after the current
 * screen, so Questown's menus showed it on top of the menu background. In 1.20.6
 * GameRenderer draws the HUD before the screen, so an opaque screen background hides
 * it. This handler restores the 1.19.2 layering for the screen families that match
 * the 1.19.2 golden captures by re-rendering the HUD after the screen.
 */
@EventBusSubscriber(modid = Questown.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class QuestownHudEvents {

    private QuestownHudEvents() {
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractPagedCardScreen
                || screen instanceof InventoryAndStatusScreen
                || screen instanceof VillagerStatsScreen
                || screen instanceof VillagerAdvancementsScreen)) {
            return;
        }
        Minecraft.getInstance().gui.render(event.getGuiGraphics(), event.getPartialTick());
    }
}
