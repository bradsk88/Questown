package ca.bradj.questown;

import com.google.common.collect.ImmutableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("removal")
public class QT {

    public interface QTLogger {
        void error(
                String s,
                Object... args
        );

        /**
         * @deprecated Use TownFlagBlockEntity.getLogger
         */
        @Deprecated(forRemoval = true)
        void debug(
                String s,
                Object... args
        );

        void warn(
                String s,
                Object... args
        );

        void info(
                String s,
                Object... args
        );

        Logger unwrap();
    }

    /**
     * @deprecated Use another QT._LOGGER
     */
    public static final Logger LOGGER = LogManager.getLogger();
    public static final QTLogger VILLAGER_LOGGER = wrap(LogManager.getLogger("Questown:Villagers"));

    /**
     * @deprecated Use TownFlagBlockEntity.getLogger
     */
    @Deprecated(forRemoval = true)
    private static QTLogger wrap(Logger logger) {
        return new QTLogger() {
            @Override
            public void error(
                    String s,
                    Object... args
            ) {
                logger.error(s, args);
            }

            @Override
            public void debug(
                    String s,
                    Object... args
            ) {
                logger.debug(s, args);
            }

            @Override
            public void warn(
                    String s,
                    Object... args
            ) {
                logger.warn(s, args);
            }

            @Override
            public void info(
                    String s,
                    Object... args
            ) {
                logger.info(s, args);
            }

            @Override
            public Logger unwrap() {
                return logger;
            }
        };
    }

    public static final QTLogger JOB_LOGGER = wrap(LogManager.getLogger("Questown:Jobs"));
    public static final QTLogger BLOCK_LOGGER = wrap(LogManager.getLogger("Questown:Blocks"));
    public static final QTLogger ITEM_LOGGER = wrap(LogManager.getLogger("Questown:Items"));
    public static final QTLogger INIT_LOGGER = wrap(LogManager.getLogger("Questown:Init"));
    public static final QTLogger FLAG_LOGGER = wrap(LogManager.getLogger("Questown:Flag"));
    public static final QTLogger QUESTS_LOGGER = wrap(LogManager.getLogger("Questown:Quests"));
    public static final QTLogger PROFILE_LOGGER = wrap(LogManager.getLogger("Questown:Profiling"));
    public static final QTLogger GUI_LOGGER = wrap(LogManager.getLogger("Questown:GUI"));

    public static void logBug(
            String s,
            Object... flagPos
    ) {
        // Add all of flagPos to c
        List<Object> c = new ArrayList<>(ImmutableList.of(flagPos));
        c.add("https://github.com/bradsk88/questown/issues");
        LOGGER.error(s + " (This is a bug; please report it to the mod author) at {}", c.toArray());
    }
}
