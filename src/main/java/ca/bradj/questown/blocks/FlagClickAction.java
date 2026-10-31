package ca.bradj.questown.blocks;

/**
 * What a right-click on the town flag does. Pure so the phase rule is unit-testable
 * ({@code FlagClickActionTest}); {@link TownFlagBlock} acts on the answer.
 */
public enum FlagClickAction {
    /** A relocation deed is waiting above the flag: hand it over. */
    COLLECT_DEED,
    /**
     * Dormant with no deed waiting: offer the way back (wake the town in place, or get the deed
     * back). ADR-0009's loss-protection paths, which were command-only before 2026-10-23.
     */
    DORMANT_CHOICES,
    /** Anything else: the flag menu. */
    FLAG_MENU;

    public static FlagClickAction of(boolean deedAvailable, FlagPhase phase) {
        if (deedAvailable) {
            return COLLECT_DEED;
        }
        if (phase == FlagPhase.DORMANT) {
            return DORMANT_CHOICES;
        }
        return FLAG_MENU;
    }
}
