package ca.bradj.questown.integration;

import ca.bradj.questown.Questown;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.mc.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;

public class SpecialRules {

    private static ImmutableMap.Builder<ResourceLocation, JobPhaseModifier> registry = ImmutableMap.builder();
    private static ImmutableMap<ResourceLocation, JobPhaseModifier> listeners = ImmutableMap.of();

    public static void finalizeForServer() {
        SpecialRules.listeners = SpecialRules.registry.build();
    }

    public static void registerSpecialRule(
            ResourceLocation id,
            JobPhaseModifier result
    ) {
        registry.put(id, result);
    }

    public static ImmutableList<JobPhaseModifier> getRuleAppliers(Collection<String> ruleIDs) {
        List<ResourceLocation> rules = ruleIDs.stream()
                                            .map(v -> v.contains(":") ? new ResourceLocation(v) : Questown.ResourceLocation(
                                                    v)).toList();
        ImmutableList.Builder<JobPhaseModifier> b = ImmutableList.builder();
        rules.forEach(v -> b.add(Util.getOrDefault(listeners, v, JobPhaseModifier.NO_OP)));
        return b.build();
    }
}
