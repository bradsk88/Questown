package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.InventoryFullStrategy;
import ca.bradj.questown.integration.SpecialRulesRegistry;
import ca.bradj.questown.integration.jobs.BeforeExtractEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.mobs.visitor.ItemAcceptor;
import ca.bradj.questown.world.QTWorldAccess;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.BiFunction;

import static ca.bradj.questown.jobs.declarative.PrePostHooks.processMulti;

public class PreExtractHook {

    public static <TOWN> TOWN run(
            TOWN town,
            Collection<String> rules,
            QTWorldAccess world,
            TriFunction<TOWN, MCHeldItem, InventoryFullStrategy, TOWN> tryGiveItem,
            BlockPos position,
            Item lastInsertedItem,
            Runnable clearPoses
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
        BeforeExtractEvent<TOWN> bxEvent = new BeforeExtractEvent<>(
                world, itemAcceptor, position, lastInsertedItem, clearPoses
        );
        return processMulti(town, appliers, (o, a) -> a.beforeExtract(o, bxEvent));
    }
}
