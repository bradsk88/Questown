package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.QT;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

/** Something a villager needs the player to fix, shown as a bubble. Only for a villager that has actually stopped. */
public enum TownieNeed {
    NONE,
    /** Gave up walking somewhere. The key names the room it was trying to reach, if any. */
    CANT_REACH,
    /** No job it can do, because the town lacks an item the job needs. */
    UNMET_ITEM,
    /** Holding work output with every town chest full. */
    NO_STORAGE,
    /** Every job it could do needs a room the town lacks. */
    MISSING_ROOM;

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

    /** Whether the need carries a key: the missing item or room, or the room it couldn't reach. */
    public boolean carriesKey() {
        return this == UNMET_ITEM || this == MISSING_ROOM || this == CANT_REACH;
    }

    public ItemStack icon() {
        return switch (this) {
            case CANT_REACH -> Items.BARRIER.getDefaultInstance();
            case UNMET_ITEM -> Items.BUNDLE.getDefaultInstance();
            case NO_STORAGE -> ItemStack.EMPTY;
            case MISSING_ROOM -> Items.CRAFTING_TABLE.getDefaultInstance();
            case NONE -> ItemStack.EMPTY;
        };
    }

    /** A texture to draw instead of {@link #icon()}, or null. */
    public @Nullable ResourceLocation iconTexture() {
        return this == NO_STORAGE ? NO_SPACE_TEXTURE : null;
    }

    public static final ResourceLocation NO_SPACE_TEXTURE =
            new ResourceLocation("questown", "textures/menu/gatherer/no_space.png");

    /** What the bubble means, in words, when the player looks straight at the villager. */
    public String hintKey() {
        return "message.questown.townie_need." + name().toLowerCase();
    }
}
