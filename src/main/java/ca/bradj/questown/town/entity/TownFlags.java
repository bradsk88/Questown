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

    public static java.util.Collection<TownFlagBlockEntity> all() {
        return java.util.Collections.unmodifiableCollection(flags.values());
    }

    /** The morning after a sleep. {@code newTime} is only logged: the morning runs on game time. */
    public static void onSleepFinished(long newTime) {
        // Dev diagnostics: confirms the morning came.
        QT.JOB_LOGGER.info(
                "[campfire-sleep] SleepFinishedTimeEvent (morning) newTime={}",
                newTime
        );
        for (TownFlagBlockEntity e : flags.values()) {
            e.ticker.runSleptMorning(TownFlagTicker.TickData.fromFlag(e));
        }
    }

}
