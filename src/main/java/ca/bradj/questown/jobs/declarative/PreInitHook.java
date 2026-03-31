package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.integration.SpecialRulesRegistry;
import ca.bradj.questown.integration.jobs.*;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.world.QTWorldAccess;
import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.function.Supplier;

import static ca.bradj.questown.jobs.declarative.PrePostHooks.processMulti;

public class PreInitHook {

    public static void run(
            Collection<String> rules,
            Supplier<QTWorldAccess> world,
            ItemCheckReplacer<MCHeldItem> ingrReplacer,
            ItemCheckReplacer<MCTownItem> toolReplacer,
            JobCheckReplacer jobBlockCheckReplacer,
            SupplyRoomCheckReplacer supplyRoomCheckReplacer
    ) {
        ImmutableList<JobPhaseModifier> appliers = SpecialRulesRegistry.getRuleAppliers(rules);
        BeforeInitEvent bxEvent = new BeforeInitEvent(
                world,
                ingrReplacer,
                toolReplacer,
                jobBlockCheckReplacer,
                supplyRoomCheckReplacer
        );
        processMulti(
                false, appliers, (o, a) -> {
                    a.beforeInit(bxEvent);
                    return true;
                }
        );
    }
}
