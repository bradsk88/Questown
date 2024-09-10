package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.integration.SpecialRulesRegistry;
import ca.bradj.questown.integration.jobs.BeforeTickEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static ca.bradj.questown.jobs.declarative.PrePostHooks.processMulti;

public class PreTickHook {

    public static void run(
            Collection<String> rules,
            ImmutableList<MCHeldItem> heldItems,
            Consumer<Function<Supplier<Map<Integer, Collection<MCRoom>>>, Supplier<Map<Integer, Collection<MCRoom>>>>> roomsReplacer,
            Function<BlockPos, @NotNull State> state
    ) {
        ImmutableList<JobPhaseModifier> appliers = SpecialRulesRegistry.getRuleAppliers(rules);
        BeforeTickEvent bxEvent = new BeforeTickEvent(heldItems, roomsReplacer, state);
        processMulti(false, appliers, (o, a) -> {
            a.beforeTick(bxEvent);
            return true;
        });
    }
}
