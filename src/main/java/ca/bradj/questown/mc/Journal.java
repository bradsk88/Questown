package ca.bradj.questown.mc;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import ca.bradj.questown.loader.Loader;

/**
 * Neutral journal access. The 1.19 build opened Patchouli entries from here; the
 * NeoForge 1.20.6 target ships without a journal on the classpath (see build.gradle),
 * so this currently has no effect. Callers keep their intent (chat notification,
 * entry open) without a journal dependency in main code.
 */
public final class Journal {

    private Journal() {
    }

    /**
     * @param player   who should see the entry (may be null on the client-only path)
     * @param bookId   the book to open
     * @param entry    the entry path within the book
     * @param page     the page to land on
     * @return true when an entry was opened
     */
    public static boolean openBookEntry(
            @Nullable Player player,
            ResourceLocation bookId,
            ResourceLocation entry,
            int page
    )
    {
        boolean[] opened = { false };
        Loader.runClient(() -> {
            ca.bradj.questown.loader.neo.NeoJournal.openBookEntry(player, bookId, entry, page);
            opened[0] = true;
        });
        return opened[0];
    }
}
