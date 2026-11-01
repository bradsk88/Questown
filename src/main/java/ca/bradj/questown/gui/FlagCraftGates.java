package ca.bradj.questown.gui;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Whether a flag crafting row shows: always, or once its unlocking quest has been posted (open or
 * completed), like the mineshaft and fishing station rows. The screen hides the row and
 * {@code FlagCraftMessage} refuses it by index. Pinned by {@code FlagCraftGatesTest}.
 */
public final class FlagCraftGates {

    private FlagCraftGates() {
    }

    public static boolean visible(@Nullable ResourceLocation unlockedByQuest, Collection<ResourceLocation> postedQuests) {
        if (unlockedByQuest == null) {
            return true;
        }
        return postedQuests.contains(unlockedByQuest);
    }
}
