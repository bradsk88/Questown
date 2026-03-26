package ca.bradj.questown.integration;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.jobs.QTNativeRule;
import ca.bradj.questown.mc.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SpecialRulesRegistry {

    private static ImmutableMap.Builder<ResourceLocation, JobPhaseModifier> registry = ImmutableMap.builder();
    private static ImmutableMap<ResourceLocation, JobPhaseModifier> listeners = ImmutableMap.of();

    public static void finalizeForServer() {
        SpecialRulesRegistry.listeners = SpecialRulesRegistry.registry.build();
        logTier2Rules();
    }

    private static void logTier2Rules() {
        List<ResourceLocation> tier2 = new ArrayList<>();
        for (var entry : listeners.entrySet()) {
            if (!(entry.getValue() instanceof QTNativeRule)) {
                tier2.add(entry.getKey());
            }
        }
        if (tier2.isEmpty()) {
            return;
        }
        QT.JOB_LOGGER.info(
                "[SpecialRulesRegistry] {} Tier 2 (MC-native) rule(s) registered. " +
                "These rules call asServerLevel() and may degrade during time warp: {}",
                tier2.size(), tier2
        );
    }

    public static void registerSpecialRule(
            ResourceLocation id,
            JobPhaseModifier result
    ) {
        registry.put(id, result);
    }

    public static void resetForTesting() {
        registry = ImmutableMap.builder();
        listeners = ImmutableMap.of();
    }

    public static ImmutableList<JobPhaseModifier> getAllInstances() {
        return ImmutableList.copyOf(listeners.values());
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

