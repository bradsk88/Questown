package ca.bradj.questown.blocks;

import com.mojang.serialization.MapCodec;

/**
 * The armory's work block: the blacksmith's table with blue metal. Its own block so the armory
 * (armorer's table + chests) is no longer a smithy (blacksmith's table + light) plus extras (dev
 * playtest 2026-10-28: "an armory IS a smithy").
 */
public class ArmorersTableBlock extends BlacksmithsTableBlock {

    public static final MapCodec<ArmorersTableBlock> CODEC =
            MapCodec.unit(ArmorersTableBlock::new);

    public static final String ITEM_ID = "armorers_table";

    }
