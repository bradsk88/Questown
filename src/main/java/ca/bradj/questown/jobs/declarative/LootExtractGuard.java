package ca.bradj.questown.jobs.declarative;

/**
 * Forge-free guard for whether a job extraction fires the LOOT-quest check.
 *
 * <p>Extracted from the declarative-engine wiring (see
 * {@code RealtimeWorldInteraction.onLootExtracted}) so the decoupling contract
 * is unit-testable in {@link ca.bradj.questown.jobs.declarative.LootExtractGuardTest}
 * even though the live engine itself is registry-bound and not unit-loadable.
 */
public final class LootExtractGuard {

    private LootExtractGuard() {}

    /**
     * A LOOT quest check fires for any non-empty extraction on the live path, whether or not the
     * job declares an {@code EXTRACTING_PRODUCT} special rule: the raw-food LOOT quest must close when
     * a hunter hands over a raw-meat grant, and the sword-hunter job declares no such rule. The job's
     * other post-extract behaviours (hunger fill, take-to-container, etc.) stay gated on that rule.
     *
     * @param itemNonEmpty                       true if a non-empty item was just extracted.
     * @param jobDeclaresExtractingProductRule   true if the job registered a special rule
     *                                           under {@code ProductionStatus.EXTRACTING_PRODUCT}.
     *                                           Accepted for documentation; not consulted.
     * @return true only when there is a non-empty item.
     */
    public static boolean questCheckFires(
            boolean itemNonEmpty,
            boolean jobDeclaresExtractingProductRule
    ) {
        return itemNonEmpty;
    }
}
