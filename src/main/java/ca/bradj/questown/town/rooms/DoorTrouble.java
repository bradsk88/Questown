package ca.bradj.questown.town.rooms;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Why a registered door has no room, surfaced as a world-space bubble over the door
 * (ADR-0011). The door counterpart of {@code TownieNeed}: the scan knows *that* the door
 * is dead and *why*, and the player is the one who can fix it.
 *
 * <p>Both values share one icon (a door) — the icon answers "which door needs me"; the
 * distinction lives only in the close-up words, because the fix differs: walls/roof vs.
 * room contents.
 *
 * <p>A door whose block is gone from the world is NOT here — that zombie registration is
 * simply discarded (ADR-0013), there is nothing to point a bubble at.
 */
public enum DoorTrouble {
    /** The scan found no enclosed space at the door: walls or roof are incomplete. */
    NOT_ENCLOSED,
    /** The scan found an enclosed space, but it matches no room recipe: contents are wrong. */
    NO_RECIPE;

    public static DoorTrouble fromName(String name) {
        return DoorTrouble.valueOf(name);
    }

    /** One shared door icon for both troubles; the words carry the distinction. */
    public ItemStack icon() {
        return sharedIcon();
    }

    /** The icon every door trouble shares — renderers need no per-trouble branch. */
    public static ItemStack sharedIcon() {
        return Items.OAK_DOOR.getDefaultInstance();
    }

    /**
     * Plain words naming the fix, shown when the player walks up and looks squarely at the
     * door — the same second gesture as the townie need hints.
     */
    public String hintKey() {
        return "message.questown.door_need." + name().toLowerCase();
    }
}
