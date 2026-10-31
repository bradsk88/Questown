package ca.bradj.questown.mc;

import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;

/**
 * The strangler token for a block's map colour (see {@link Compat#blockProps}). 1.19.2 builds a
 * block from a {@link Material} plus its {@link MaterialColor}; the material is the one the 1.19
 * golden used for the same colour (stone, wood, wool; glass for the snow-white blocks).
 */
public enum CompatBlockColor {
    SNOW(Material.GLASS, MaterialColor.SNOW),
    STONE(Material.STONE, MaterialColor.STONE),
    WOOD(Material.WOOD, MaterialColor.WOOD),
    WOOL(Material.WOOL, MaterialColor.WOOL),
    COLOR_BROWN(Material.WOOD, MaterialColor.COLOR_BROWN),
    COLOR_GRAY(Material.STONE, MaterialColor.COLOR_GRAY),
    COLOR_BLACK(Material.WOOD, MaterialColor.COLOR_BLACK),
    TERRACOTTA_BROWN(Material.STONE, MaterialColor.TERRACOTTA_BROWN),
    TERRACOTTA_WHITE(Material.STONE, MaterialColor.TERRACOTTA_WHITE);

    private final Material material;
    private final MaterialColor mapColor;

    CompatBlockColor(Material material, MaterialColor mapColor) {
        this.material = material;
        this.mapColor = mapColor;
    }

    public MaterialColor mapColor() {
        return mapColor;
    }

    public Material material() {
        return material;
    }
}
