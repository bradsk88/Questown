package ca.bradj.questown.jobs.declarative;

/** Whether an extraction fires the LOOT quest check. */
public final class LootExtractGuard {

    private LootExtractGuard() {}

    /**
     * Any non-empty extraction fires the LOOT quest check, whatever the job's own extraction rules.
     *
     * @param jobDeclaresExtractingProductRule not consulted
     */
    public static boolean questCheckFires(
            boolean itemNonEmpty,
            boolean jobDeclaresExtractingProductRule
    ) {
        return itemNonEmpty;
    }
}
