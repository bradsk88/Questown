package ca.bradj.questown.mc;

import net.minecraft.world.level.material.MapColor;

/**
 * 1.20 block map-colour value aliases - the seam names for the map colours a leaf needs.
 *
 * <p>On 1.20.x a block's map colour is a static {@code MapColor} (1.20.2+ block properties) or a
 * per-state function into one; 1.19.2 set a {@code MaterialColor} (constant or function) via
 * {@code Properties#color}. A leaf names only the alias here, never the versioned colour type -
 * the compat layer carries whichever the active branch uses (see {@code Compat.P#mapColor}).
 */
public enum MapC {

    TERRACOTTA_BROWN(MapColor.TERRACOTTA_BROWN),
    TERRACOTTA_WHITE(MapColor.TERRACOTTA_WHITE),
    COLOR_BROWN(MapColor.COLOR_BROWN),
    COLOR_GRAY(MapColor.COLOR_GRAY),
    WOOD(MapColor.WOOD),
    WOOL(MapColor.WOOL),
    BLACK(MapColor.COLOR_BLACK),
    SNOW(MapColor.SNOW);

    private final MapColor vanilla;

    MapC(MapColor vanilla) {
        this.vanilla = vanilla;
    }

    public MapColor vanilla() {
        return vanilla;
    }
}
