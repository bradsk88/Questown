package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableMap;

public record JobDefinition(
        JobID jobId,
        int maxState,
        ImmutableMap<Integer, String> ingredientsRequiredAtStates,
        ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates,
        ImmutableMap<Integer, String> toolsRequiredAtStates,
        ImmutableMap<Integer, Integer> workRequiredAtStates,
        ImmutableMap<Integer, Integer> timeRequiredAtStates,
        String result

) {
}
