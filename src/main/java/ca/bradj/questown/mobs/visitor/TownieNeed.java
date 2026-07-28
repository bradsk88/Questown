package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.QT;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Something a townie needs the player to fix, surfaced as a world-space bubble (ADR-0011).
 *
 * <p>The set is deliberately small and closed: a bubble is worth looking at precisely because it
 * is rare, so a need belongs here only when the townie has genuinely stopped and the player is the
 * one who can unblock it. "Working, but slowly" is not a need.
 */
public enum TownieNeed {
    /** Nothing wrong. Silence is the signal that the townie is fine. */
    NONE,
    /** Gave up walking somewhere: no viable path, or wedged despite one. */
    CANT_REACH;

    public static TownieNeed fromName(String name) {
        for (TownieNeed v : values()) {
            if (v.name().equals(name)) {
                return v;
            }
        }
        QT.VILLAGER_LOGGER.error("Unknown townie need {}. Treating as NONE.", name);
        return NONE;
    }

    public boolean isNeeded() {
        return this != NONE;
    }

    /**
     * What the bubble shows. The barrier is vanilla's own "you cannot go here" glyph, so it reads
     * without a legend; a townie under it is asking the player to open a way through.
     */
    public ItemStack icon() {
        return switch (this) {
            case CANT_REACH -> Items.BARRIER.getDefaultInstance();
            case NONE -> ItemStack.EMPTY;
        };
    }

    /**
     * Plain words for the same thing the icon says, shown when the player walks up and looks
     * straight at the townie. The icon answers "who needs me"; this answers "what for", so it says
     * what the townie cannot do — not which internal state it is in.
     */
    public String hintKey() {
        return "message.questown.townie_need." + name().toLowerCase();
    }
}
