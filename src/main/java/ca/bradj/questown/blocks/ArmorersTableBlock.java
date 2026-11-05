package ca.bradj.questown.blocks;

import com.mojang.serialization.MapCodec;

/** The armory's work block. */
public class ArmorersTableBlock extends BlacksmithsTableBlock {

    public static final MapCodec<ArmorersTableBlock> CODEC =
            MapCodec.unit(ArmorersTableBlock::new);

    public static final String ITEM_ID = "armorers_table";

    public MapCodec<ArmorersTableBlock> codec() {
        return CODEC;
    }
}
