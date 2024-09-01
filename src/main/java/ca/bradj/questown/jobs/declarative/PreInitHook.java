package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.integration.SpecialRulesRegistry;
import ca.bradj.questown.integration.jobs.BeforeInitEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.logic.PredicateCollection;
import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static ca.bradj.questown.jobs.declarative.PrePostHooks.processMulti;

public class PreInitHook {

    public static void run(
            Collection<String> rules,
            Supplier<ImmutableList<MCHeldItem>> heldItems,
            Consumer<Function<PredicateCollection<MCHeldItem>, PredicateCollection<MCHeldItem>>> ingrReplacer
    ) {
        ImmutableList<JobPhaseModifier> appliers = SpecialRulesRegistry.getRuleAppliers(rules);
        BeforeInitEvent bxEvent = new BeforeInitEvent(heldItems, ingrReplacer);
        processMulti(false, appliers, (o, a) -> {
            a.beforeInit(bxEvent);
            return true;
        });
    }
}
