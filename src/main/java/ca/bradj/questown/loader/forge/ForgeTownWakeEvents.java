package ca.bradj.questown.loader.forge;

import ca.bradj.questown.Questown;
import ca.bradj.questown.town.entity.TownFlags;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.level.SleepFinishedTimeEvent;

/**
 * Loader binding for the morning-wake signal (G6, 2026-10-13): moved out of
 * {@code town/entity/TownFlags}, which keeps the domain logic in
 * {@code TownFlags.onSleepFinished} and no longer names the loader bus.
 */
@EventBusSubscriber(modid = Questown.MODID, bus = EventBusSubscriber.Bus.FORGE)
public class ForgeTownWakeEvents {

    @SubscribeEvent
    public static void onWake(SleepFinishedTimeEvent event) {
        TownFlags.onSleepFinished(event.getNewTime());
    }
}
