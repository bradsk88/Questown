package ca.bradj.questown.town.rooms;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Why a registered door has no room, shown as a bubble over the door. Both share the door icon; the words differ. */
public enum DoorTrouble {
    /** Walls or roof are incomplete. */
    NOT_ENCLOSED,
    /** Enclosed, but the contents match no room recipe. */
    NO_RECIPE;

    public static DoorTrouble fromName(String name) {
        return DoorTrouble.valueOf(name);
    }

    public ItemStack icon() {
        return sharedIcon();
    }

    public static ItemStack sharedIcon() {
        return Items.OAK_DOOR.getDefaultInstance();
    }

    /** The fix in words, when the player looks straight at the door. */
    public String hintKey() {
        return "message.questown.door_need." + name().toLowerCase();
    }
}
