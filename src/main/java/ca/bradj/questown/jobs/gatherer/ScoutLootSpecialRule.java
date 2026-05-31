package ca.bradj.questown.jobs.gatherer;

import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.jobs.AfterExtractEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.jobs.QTNativeRule;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.items.GathererMap;
import com.google.common.collect.ImmutableList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The explorer's "scouting" outcome: when a gatherer map is extracted, learn one
 * loot drop available in that map's biome and record it as town knowledge — without
 * taking the item. Replaces the {@code KnowledgeMetaItem} the explorer used to emit
 * (see ADR-0004). Reads the just-extracted map so the learned loot's biome always
 * matches the map the same trip produced; runs in realtime and warp alike.
 */
public class ScoutLootSpecialRule extends JobPhaseModifier implements QTNativeRule {

    @Override
    public <CONTEXT> @Nullable CONTEXT afterExtract(
            CONTEXT ctxInput,
            AfterExtractEvent<CONTEXT> event
    ) {
        MCHeldItem extracted = event.extractedItem();
        if (extracted == null || !ItemsInit.GATHERER_MAP.get().equals(extracted.get().get())) {
            return ctxInput;
        }
        ServerLevel level = event.world().asServerLevel();
        if (level == null) {
            return ctxInput;
        }
        ResourceLocation biome = GathererMap.getBiome(extracted.get().toQTItemStack());
        if (biome == null) {
            return ctxInput;
        }
        MCHeldItem scouted = rollScoutedLoot(level, biome);
        if (scouted == null) {
            return ctxInput;
        }
        return event.knowledgeUpdater().apply(ctxInput, scouted);
    }

    private static @Nullable MCHeldItem rollScoutedLoot(
            ServerLevel level,
            ResourceLocation biome
    ) {
        List<GathererTools.LootTableParameters> all = NewLeaverWork.getAllParameters();
        if (all.isEmpty()) {
            all = ImmutableList.of(new GathererTools.LootTableParameters(
                    GathererTools.NO_TOOL_TABLE_PREFIX,
                    GathererTools.NO_TOOL_LOOT_TABLE_DEFAULT
            ));
        }
        GathererTools.LootTableParameters params = all.get(level.getRandom().nextInt(all.size()));
        List<MCHeldItem> rolled = Loots.getFromLootTables(level, 1, 1, params, biome);
        if (rolled.isEmpty()) {
            return null;
        }
        // Items from a loot table already carry their (prefix, biome) for registerFoundLoots.
        return rolled.get(0);
    }
}
