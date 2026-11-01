package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

    // Dev-only: dump every known flag's position.
    public static java.util.Collection<TownFlagBlockEntity> all() {
        return java.util.Collections.unmodifiableCollection(flags.values());
    }

    /**
     * The morning-wake domain step (the loader's wake event binding calls it). {@code newTime} is
     * the wake's new daytime and is only logged: the morning pass runs on game time.
     */
    public static void onSleepFinished(long newTime) {
        // Diagnostic: the definitive "did morning come" signal. If it fires but the server then goes
        // silent, the stall is in the wake path (onMorning / onWake), not the night-skip itself.
        QT.JOB_LOGGER.info(
                "[campfire-sleep] SleepFinishedTimeEvent (morning) newTime={}",
                newTime
        );
        for (TownFlagBlockEntity e : flags.values()) {
            e.ticker.runSleptMorning(TownFlagTicker.TickData.fromFlag(e));
        }
    }

}
