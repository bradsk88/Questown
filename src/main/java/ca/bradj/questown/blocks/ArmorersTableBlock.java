package ca.bradj.questown.blocks;

import com.mojang.serialization.MapCodec;

/**
 * The armory's work block: the blacksmith's table with blue metal. Its own block, so the armory
 * (armorer's table + chests) is not a smithy (blacksmith's table + light) plus extras.
 */
public class ArmorersTableBlock extends BlacksmithsTableBlock {

    public static final MapCodec<ArmorersTableBlock> CODEC =
            MapCodec.unit(ArmorersTableBlock::new);

    public static final String ITEM_ID = "armorers_table";

    }
