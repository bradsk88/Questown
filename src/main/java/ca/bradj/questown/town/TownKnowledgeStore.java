package ca.bradj.questown.town;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.gatherer.GathererTools;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TownKnowledgeStore extends KnowledgeStore<ResourceLocation, MCHeldItem, MCTownItem> {
    @Nullable
    private TownFlagBlockEntity town;

    public void initialize(TownFlagBlockEntity t) {
        this.town = t;
    }

    public boolean isInitialized() {
        return town != null;
    }

    /**
     * Only safe to call after initialize
     */
    private @NotNull TownFlagBlockEntity unsafeGetTown() {
        if (town == null) {
            throw new IllegalStateException("Town has not been initialized on quest handle yet");
        }
        return town;
    }

    public TownKnowledgeStore() {
        super(
                ImmutableSet.of(MCTownItem.fromMCItemStack(Items.WHEAT_SEEDS.getDefaultInstance())),
                MCHeldItem::get,
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
    public void registerFoundLoots(ImmutableList<MCHeldItem> items) {
        super.registerFoundLoots(items);
        unsafeGetTown().setChanged();
    }
}
