package ca.bradj.questown.items;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.gatherer.GathererTools;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

public class KnowledgeMetaItem extends Item {
    public static final String ITEM_ID = "knowledge_meta_item";

    public KnowledgeMetaItem() {
        super(Questown.DEFAULT_ITEM_PROPS);
    }

    public static MCHeldItem wrap(
            MCHeldItem mcHeldItem, GathererTools.LootTablePrefix prefix, ResourceLocation biome
    ) {
        Item inner = mcHeldItem.get().get();
        @Nullable ResourceLocation innerKey = ForgeRegistries.ITEMS.getKey(inner);

        ItemStack knowledge = ItemsInit.KNOWLEDGE.get().getDefaultInstance();
        QTNBT.put(knowledge.getOrCreateTag(), "item_knowledge", innerKey);
        return MCHeldItem.fromLootTable(MCTownItem.fromMCItemStack(knowledge), prefix, biome);
    }

    public static @Nullable MCTownItem unwrap(MCHeldItem itemStack) {
        if (!ItemsInit.KNOWLEDGE.get().equals(itemStack.get().get())) {
            return null;
        }

        ItemStack stack = itemStack.get().toItemStack();
        ResourceLocation actualItem = QTNBT.getResourceLocation(stack.getOrCreateTag(), "item_knowledge");

        Item value = ForgeRegistries.ITEMS.getValue(actualItem);
        if (value == null) {
            QT.ITEM_LOGGER.error("Unknown item {} being used as knowledge", actualItem);
            return null;
        }
        return MCTownItem.fromMCItemStack(value.getDefaultInstance());
    }
}
