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

    // If this rule is enabled, the villager will attempt to find a chest containing any
    // one of the work results defined in the "Works" array. (See class: Works)
    // They will ignore whatever was specified as the "ingredient" for the corresponding
    // work state.
    public static final String INGREDIENT_ANY_VALID_WORK_OUTPUT = "ingredient_any_valid_work_output";

    // If this rule is enabled, the villager will take N items from the town to satisfy the
    // "quantity" rule of their job for the current state, but only if there are N items of
    // the same type. This rule is only relevant when tags or special rules are being used
    // for the job ingredients.
    // If specific items are defined on the job, this will have no effect.
    public static final String INGREDIENTS_MUST_BE_SAME = "ingredients_must_be_same";

    // If this rule is enabled, the villager will drop the ingredients from the previous
    // stage in town as a stack using the ingredient quantity from the previous stage to
    // determine the size of the stack.
    //
    // Questown villagers are not supposed to be able to stack items. This rule was added
    // to support the special "organizer" villager type - who has this unique capability.
    //
    // NOTE: This can only be used when INGREDIENTS_MUST_BE_SAME is also enabled.
    public static final String DROP_LOOT_AS_STACK = "drop_loot_as_stack";

    // If this rule is enabled, the villager will only take items from storage if they
    // have a quantity lower than the quantity specified on the job's "ingredient quantity
    // parameter.
    // FIXME: Implement
    public static final String TAKE_ONLY_LESS_THAN_QUANTITY = "take_only_less_than_quantity";

    // If this rule is enabled, the villager will assume the "dropping_loot"
    // status at the corresponding production state.
    public static final String FORCE_DROP_LOOT = "force_drop_loot";
}
