package ca.bradj.questown.core.materials;

import net.minecraft.util.StringRepresentable;

public enum WallType implements StringRepresentable {
    COBBLESTONE("cobblestone"),
    MOSSY_COBBLESTONE("mossy_cobblestone"),
    BRICK("brick"),
    SANDSTONE("sandstone"),
    RED_SANDSTONE("red_sandstone"),
    NETHER_BRICK("nether_brick"),
    STONE_BRICK("stone_brick"),
    END_STONE_BRICK("end_stone_brick"),
    PRISMARINE("prismarine"),
    RED_NETHER_BRICK("red_nether_brick"),
    REDSTONE("redstone"),
    QUARTZ("quartz");

    private final String name;

    WallType(String name) {
        this.name = name;
    }

    public String asString() {
        return this.name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
