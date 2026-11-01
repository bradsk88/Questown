package ca.bradj.questown.mc;

import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;

/**
 * A block's map colour (1.19.2: a {@link MaterialColor}, carried with the {@link Material} a
 * 1.19.2 block needs to be built at all; 1.20 has map colour alone). Leaves name only this enum.
 */
public enum MapC {

    TERRACOTTA_BROWN(Material.STONE, MaterialColor.TERRACOTTA_BROWN),
    TERRACOTTA_WHITE(Material.STONE, MaterialColor.TERRACOTTA_WHITE),
    COLOR_BROWN(Material.WOOD, MaterialColor.COLOR_BROWN),
    COLOR_GRAY(Material.STONE, MaterialColor.COLOR_GRAY),
    WOOD(Material.WOOD, MaterialColor.WOOD),
    WOOL(Material.WOOL, MaterialColor.WOOL),
    BLACK(Material.WOOD, MaterialColor.COLOR_BLACK),
    SNOW(Material.GLASS, MaterialColor.SNOW);

    private final Material material;
    private final MaterialColor vanilla;

    MapC(Material material, MaterialColor vanilla) {
        this.material = material;
        this.vanilla = vanilla;
    }

    public MaterialColor vanilla() {
        return vanilla;
    }

    public Material material() {
        return material;
    }
}
