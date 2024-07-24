package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.InventoryFullStrategy;
import ca.bradj.questown.integration.SpecialRulesRegistry;
import ca.bradj.questown.integration.jobs.BeforeExtractEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.mobs.visitor.ItemAcceptor;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static ca.bradj.questown.jobs.declarative.PrePostHooks.processMulti;

public class PreExtractHook {

    public static <TOWN> TOWN run(
            TOWN town,
            Collection<String> rules,
            ServerLevel level,
            TriFunction<TOWN, MCHeldItem, InventoryFullStrategy, TOWN> tryGiveItem,
            BlockPos position
    ) {
        ImmutableList<JobPhaseModifier> appliers = SpecialRulesRegistry.getRuleAppliers(rules);
        ItemAcceptor<TOWN> itemAcceptor = new ItemAcceptor<>() {

            @Override
            public @Nullable TOWN tryGiveItem(
                    TOWN ctx,
                    MCHeldItem item,
                    InventoryFullStrategy inventoryFullStrategy
            ) {
                return tryGiveItem.apply(ctx, item, inventoryFullStrategy);
            }
        };
        BeforeExtractEvent<TOWN> bxEvent = new BeforeExtractEvent<>(level, itemAcceptor, position);
        return processMulti(town, appliers, (o, a) -> a.beforeExtract(o, bxEvent));
    }
}
