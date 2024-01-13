package ca.bradj.questown.jobs;

public class SpecialRules {
    public static final String REMOVE_FROM_WORLD = "remove_from_world";

    // By default, if a job block generates more results than a villager can hold,
    // they will simply drop those result on the ground. For jobs like "gatherer",
    // villagers will only be "able to carry N items home", so there's no reason
    // to drop excess results on the ground. Using this rule will help with that
    // scenario (gatherer).
    public static final String NULLIFY_EXCESS_RESULTS = "nullify_excess_results";

    // By default, each villager will "see" a job block as being in a different state.
    // This can be useful for jobs like gathering, where the villager takes their supplies
    // to the job block (town gate) and then "leave" through it, waiting for their own
    // personal timer to expire.
    // For production jobs, it makes more sense to share the block state. This helps to
    // solidify the illusion that a villager is "using" a block. It also allows a different
    // villager to finish the work started by another.
    public static final String SHARED_WORK_STATUS = "shared_work_status";

    // i.e. finished products will be grabbed, rather than working on blocks that
    // have a lower state. This is typically the most efficient option for production
    // jobs that have an element of "time".
    public static final String PRIORITIZE_EXTRACTION = "prioritize_extraction";

    // If this rule is enabled, only one villager will be allowed to use the job block
    // after the first ingredient has been inserted and until the product is extracted.
    // E.g. For the "dining job" a plate can only be filled by one villager, who will
    // "work" on that spot until they have finished their work, and then they will
    // "release" to plate for others to use. Other villagers will try to claim other
    // plates - ignoring claimed ones.
    public static final String CLAIM_SPOT = "claim_spot";

    // If this rule is enabled, the villager will scan the blocks BELOW the job block
    // first, if there are any valid interaction spots on that level, they will navigate
    // toward that spot. If this rule is not enabled, or there are no interaction spots
    // on the layer below the job block, they will navigate to a spot on the same level
    // as the job block.
    public static final String PREFER_INTERACTION_BELOW = "claim_spot";

    // If this rule is enabled, the villager will continue working into the evening until
    // it is time for them to go to bed. If this rule is disabled, villagers will become
    // idle in the evening.
    public static final String WORK_IN_EVENING = "work_in_evening";
}
