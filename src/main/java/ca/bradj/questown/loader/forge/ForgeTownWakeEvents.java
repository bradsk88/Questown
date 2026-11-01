package ca.bradj.questown.loader.forge;

import ca.bradj.questown.Questown;
import ca.bradj.questown.town.entity.TownFlags;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.level.SleepFinishedTimeEvent;

/**
 * Loader binding for the morning-wake signal (the logic is {@code TownFlags.onSleepFinished}).
 */
@EventBusSubscriber(modid = Questown.MODID, bus = EventBusSubscriber.Bus.FORGE)
public class ForgeTownWakeEvents {

    @SubscribeEvent
    public static void onWake(SleepFinishedTimeEvent event) {
        TownFlags.onSleepFinished(event.getNewTime());
    }
}
