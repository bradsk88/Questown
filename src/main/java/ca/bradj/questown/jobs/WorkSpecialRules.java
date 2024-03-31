package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.ProductionStatus;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Collection;

public record WorkSpecialRules(
        ImmutableMap<ProductionStatus, ? extends Collection<String>> specialStatusRules,
        ImmutableList<String> specialGlobalRules
) {
    public boolean containsGlobal(String rule) {
        return specialGlobalRules.contains(rule);
    }
}
