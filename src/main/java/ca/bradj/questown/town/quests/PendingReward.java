package ca.bradj.questown.town.quests;

import ca.bradj.questown.core.VillagerUUID;

public record PendingReward (
        VillagerUUID owner,
        MCRewardList reward
) {
}
