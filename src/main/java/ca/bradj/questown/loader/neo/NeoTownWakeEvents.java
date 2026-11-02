package ca.bradj.questown.loader.neo;

import ca.bradj.questown.Questown;
import ca.bradj.questown.town.entity.TownFlags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;

@EventBusSubscriber(modid = Questown.MODID, bus = EventBusSubscriber.Bus.GAME)
public class NeoTownWakeEvents {

    @SubscribeEvent
    public static void onWake(SleepFinishedTimeEvent event) {
        TownFlags.onSleepFinished(event.getNewTime());
    }
}
