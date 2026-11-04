package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import net.minecraft.core.BlockPos;

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
        QT.FLAG_LOGGER.dev(
                "[findflag] flag {} registered at {}",
                uuid,
                entity.getBlockPos()
        );
    }

    public static java.util.Collection<TownFlagBlockEntity> all() {
        return java.util.Collections.unmodifiableCollection(flags.values());
    }

    /** Whether a loaded flag owns the welcome mat at {@code pos}. */
    public static boolean ownsWelcomeMat(BlockPos pos) {
        return flags.values().stream().anyMatch(f -> f.getWelcomeMats().contains(pos));
    }

    /** The morning after a sleep. {@code newTime} is only logged: the morning runs on game time. */
    public static void onSleepFinished(long newTime) {
        // Dev diagnostics: confirms the morning came.
        QT.JOB_LOGGER.dev(
                "[campfire-sleep] SleepFinishedTimeEvent (morning) newTime={}",
                newTime
        );
        for (TownFlagBlockEntity e : flags.values()) {
            e.ticker.runSleptMorning(TownFlagTicker.TickData.fromFlag(e));
        }
    }

}
