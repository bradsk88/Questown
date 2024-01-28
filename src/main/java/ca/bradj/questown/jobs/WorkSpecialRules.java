package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.ProductionStatus;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public record WorkSpecialRules(
        ImmutableMap<ProductionStatus, String> specialStatusRules,
        ImmutableList<String> specialGlobalRules
) {
    public boolean containsGlobal(String rule) {
        return specialGlobalRules.contains(rule);
    }
}
