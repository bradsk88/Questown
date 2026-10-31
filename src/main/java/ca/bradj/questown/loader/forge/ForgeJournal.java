package ca.bradj.questown.loader.forge;

import ca.bradj.questown.QT;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Journal target (client side). As on the 1.20.6 branch there is no journal: opening an entry is
 * a no-op that notes where it would have gone.
 */
public final class ForgeJournal {

    private ForgeJournal() {
    }

    public static void openBookEntry(
            @Nullable Player player,
            ResourceLocation bookId,
            ResourceLocation entry,
            int page
    ) {
        QT.LOGGER.info("[journal] no journal on this target; would open {}.{} page {}", bookId, entry, page);
    }
}
