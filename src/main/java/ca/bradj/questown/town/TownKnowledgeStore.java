package ca.bradj.questown.town;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.items.KnowledgeMetaItem;
import ca.bradj.questown.jobs.gatherer.GathererTools;
import com.google.common.collect.ImmutableSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class TownKnowledgeStore extends KnowledgeStore<ResourceLocation, MCHeldItem, MCTownItem> {
    @Nullable
    private UnsafeTown town = new UnsafeTown();

    public void initialize(TownFlagBlockEntity t) {
        this.town.initialize(t);
    }

    public boolean isInitialized() {
        return town != null;
    }

    public TownKnowledgeStore() {
        super(
                ImmutableSet.of(MCTownItem.fromMCItemStack(Items.WHEAT_SEEDS.getDefaultInstance())),
                (i) -> {
                    MCTownItem unwrapped = KnowledgeMetaItem.unwrap(i);
                    if (unwrapped != null) {
                        return unwrapped;
                    }
                    return i.get();
                },
                (GathererTools.LootTablePrefix tool, ResourceLocation biome, MCTownItem i) -> {
                    if (biome == null) {
                        return MCHeldItem.fromTown(i);
                    }
                    return MCHeldItem.fromLootTable(i, tool, biome);
                },
                ResourceLocation::new
        );
    }

    @Override
    public void registerFoundLoots(Collection<MCHeldItem> items) {
        super.registerFoundLoots(items);
        town.getUnsafe().setChanged();
    }
}
