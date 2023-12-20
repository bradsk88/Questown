package ca.bradj.questown.jobs.gatherer;

import ca.bradj.questown.integration.minecraft.MCTownItem;

import java.util.Optional;
import java.util.function.Predicate;

public record ToolRequirement(
        Predicate<MCTownItem> isCorrectTool,
        Optional<NewLeaverWork.TagsCriteria> tagCriteria
) implements Predicate<MCTownItem> {
    @Override
    public boolean test(MCTownItem item) {
        if (!isCorrectTool.test(item)) {
            return false;
        }
        return tagCriteria
                .map(tagsCriteria -> tagsCriteria.atLeastOneFullCriteriaMatch(item.getTags()))
                .orElse(true);
    }
}
