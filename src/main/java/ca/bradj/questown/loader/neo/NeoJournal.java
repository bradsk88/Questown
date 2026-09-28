package ca.bradj.questown.loader.neo;

import ca.bradj.questown.QT;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * NeoForge journal target (client side). The 1.20.6 classpath has no journal;
 * opening an entry is a no-op that notes where it would have gone, so callers'
 * journal-intent stays visible in the log while the feature is absent.
 */
public final class NeoJournal {

    private NeoJournal() {
    }

    public static void openBookEntry(
            @Nullable Player player,
            ResourceLocation bookId,
            ResourceLocation entry,
            int page
    )
    {
        QT.LOGGER.info(
                "[journal] no journal on this target; would open {}.{} page {}",
                bookId, entry, page
        );
    }
}
