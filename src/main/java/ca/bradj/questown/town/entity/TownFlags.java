package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@net.neoforged.fml.common.EventBusSubscriber(modid = Questown.MODID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.GAME)
public class TownFlags {

    private static final Map<UUID, TownFlagBlockEntity> flags = new HashMap<>();

    static void register(
            UUID uuid,
            TownFlagBlockEntity entity
    ) {
        flags.put(uuid, entity);
        QT.FLAG_LOGGER.info(
                "[findflag] flag {} registered at {}",
                uuid,
                entity.getBlockPos()
        );
    }

    // Dev-only: dump every known flag's position (used to locate the incident flag).
    public static java.util.Collection<TownFlagBlockEntity> all() {
        return java.util.Collections.unmodifiableCollection(flags.values());
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
