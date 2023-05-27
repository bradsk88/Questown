package ca.bradj.questown.core;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.InMemoryFormat;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeConfigSpec;

public class RecipeItemConfig {

    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final String ITEM_WEIGHTS = "Item weights";
    private static final Config defaultItemWeights = Config.of(InMemoryFormat.defaultInstance());
    public static final ForgeConfigSpec.ConfigValue<Config> itemWeights;

    static {
        // TODO: Build a cost calculator that works like calculator.usesRecipe([
        //    MinedResource(Items.COAL, Rarity.MEDIUM),
        //    CraftedResource(Items.Stick, [CraftedResource(Items.OAK_PLANKS, [RawResource(Items.OAK_WOOD)])])
        //  ]).calculate()
        defaultItemWeights.add(Items.TORCH.getRegistryName().toString(), 20);
        defaultItemWeights.add(Items.LANTERN.getRegistryName().toString(), 30);
        defaultItemWeights.add(Items.CHEST.getRegistryName().toString(), 40);
        defaultItemWeights.add(Items.CRAFTING_TABLE.getRegistryName().toString(), 20);
        defaultItemWeights.add(Items.FURNACE.getRegistryName().toString(), 40);
        defaultItemWeights.add(Items.ENCHANTING_TABLE.getRegistryName().toString(), 250);
        defaultItemWeights.add(Items.BOOKSHELF.getRegistryName().toString(), 135);
        defaultItemWeights.add(String.format("#%s", ItemTags.BEDS.location()), 10);
        defaultItemWeights.add(String.format("#%s", ItemTags.SIGNS.location()), 50);
    }

    public static final String FILENAME = "questown-item-weights-server.toml";

    static {
        BUILDER.push(FILENAME);

        itemWeights = BUILDER.define(ITEM_WEIGHTS, defaultItemWeights);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
