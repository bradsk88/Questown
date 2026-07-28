package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.Questown;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Drives {@link NeedBubbleFocus} once per client tick. */
@Mod.EventBusSubscriber(modid = Questown.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class NeedBubbleClientEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        NeedBubbleFocus.tick(Minecraft.getInstance());
    }

    private NeedBubbleClientEvents() {
    }
}
