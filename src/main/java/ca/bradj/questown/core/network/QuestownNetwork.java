package ca.bradj.questown.core.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod network.
 *
 * <p>The 31 message classes in this package are loader-neutral (vanilla {@code FriendlyByteBuf}).
 * The channel setup and dispatch are the loader-specific part, migrated on top of NeoForge's
 * payload/stream-codec networking via the neutral {@code loader.network} seam in a dedicated
 * slice (strangler plan). Until it lands the mod boots and everything except these GUI menu-sync
 * flows works (jobs, towns, economy, and vanilla {@code /tick rate}).
 */
public class QuestownNetwork {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuestownNetwork.class);

    public static final String NETWORK_VERSION = "0.0.1";

    public static void init() {
        LOGGER.info("[questown] network init: NeoForge payload/stream-codec migration pending; GUI menu-sync messages are disabled this run.");
    }
}
