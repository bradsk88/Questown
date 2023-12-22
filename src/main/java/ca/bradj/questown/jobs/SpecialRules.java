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
}
