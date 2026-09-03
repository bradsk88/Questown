package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import net.minecraftforge.event.level.SleepFinishedTimeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Questown.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TownFlags {

    private static final Map<UUID, TownFlagBlockEntity> flags = new HashMap<>();

    static void register(
            UUID uuid,
            TownFlagBlockEntity entity
    ) {
        flags.put(uuid, entity);
    }

    @SubscribeEvent
    public static void OnWake(SleepFinishedTimeEvent event) {
        // Diagnostic: this is the definitive "did morning come" signal. In the
        // 2026-09-03 two-player-sleep soft-lock this never fired, so night
        // never skipped. If it fires but the server then goes silent, the bug
        // is in the wake path (onMorning / onWake), not the night-skip itself.
        QT.JOB_LOGGER.info(
                "[campfire-sleep] SleepFinishedTimeEvent (morning) newTime={}",
                event.getNewTime()
        );
        for (TownFlagBlockEntity e : flags.values()) {
            e.ticker.onMorning(TownFlagTicker.TickData.fromFlag(e), event.getNewTime());
        }
    }

}
