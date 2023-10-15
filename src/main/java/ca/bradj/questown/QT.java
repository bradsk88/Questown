package ca.bradj.questown;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class QT {

    /**
     * @deprecated Use another QT._LOGGER
     */
    public static final Logger LOGGER = LogManager.getLogger();
    public static final Logger VILLAGER_LOGGER = LogManager.getLogger("Villagers");
    public static final Logger FLAG_LOGGER = LogManager.getLogger("Flag");
    public static final Logger QUESTS_LOGGER = LogManager.getLogger("Quests");
}
