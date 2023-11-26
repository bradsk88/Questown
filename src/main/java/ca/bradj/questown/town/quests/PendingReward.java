package ca.bradj.questown.town.quests;

import java.util.UUID;

public record PendingReward (
        UUID owner,
        MCRewardList reward
) {
}
