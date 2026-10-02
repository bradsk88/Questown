package ca.bradj.questown.loader.neo;

import ca.bradj.questown.Questown;
import ca.bradj.questown.town.entity.TownFlags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;

/**
 * Loader binding for the morning-wake signal (G6, 2026-10-13): moved out of
 * {@code town/entity/TownFlags}, which keeps the domain logic in
 * {@code TownFlags.onSleepFinished} and no longer names the loader bus.
 */
@EventBusSubscriber(modid = Questown.MODID, bus = EventBusSubscriber.Bus.GAME)
public class NeoTownWakeEvents {

    @SubscribeEvent
    public static void onWake(SleepFinishedTimeEvent event) {
        TownFlags.onSleepFinished(event.getNewTime());
    }
}
