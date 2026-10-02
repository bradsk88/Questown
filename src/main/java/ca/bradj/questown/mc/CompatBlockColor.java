package ca.bradj.questown.mc;

import net.minecraft.world.level.material.MapColor;

/**
 * The block map colours questown requests, as a version-neutral token block ctors can name.
 *
 * <p>{@link MapColor} is a strangler marker and a *bare name* (unlike the {@code Button.builder}-style
 * call markers), so a leaf must not name it at all. This enum owns the concrete {@link MapColor} and
 * {@code Compat.blockProps(...)} owns the {@code BlockBehaviour.Properties.of().mapColor(...)} form;
 * re-porting to 1.19.2 ({@code Properties.of(Material, MaterialColor)}) then touches only this seam
 * family, not the block files. Constant names track the vanilla {@link MapColor} names so the call
 * site is a mechanical rename.
 */
public enum CompatBlockColor {
    SNOW(MapColor.SNOW),
    STONE(MapColor.STONE),
    WOOD(MapColor.WOOD),
    WOOL(MapColor.WOOL),
    COLOR_BROWN(MapColor.COLOR_BROWN),
    COLOR_GRAY(MapColor.COLOR_GRAY),
    COLOR_BLACK(MapColor.COLOR_BLACK),
    TERRACOTTA_BROWN(MapColor.TERRACOTTA_BROWN),
    TERRACOTTA_WHITE(MapColor.TERRACOTTA_WHITE);

    private final MapColor mapColor;

    CompatBlockColor(MapColor mapColor) {
        this.mapColor = mapColor;
    }

    /**
     * The version-bound vanilla {@link MapColor}. Seam-family only — a block leaf reaches it through
     * {@code Compat.blockProps(this)}.
     */
    public MapColor mapColor() {
        return mapColor;
    }
}
