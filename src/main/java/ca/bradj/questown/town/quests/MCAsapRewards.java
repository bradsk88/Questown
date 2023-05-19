package ca.bradj.questown.town.quests;

import java.util.Stack;

public class MCAsapRewards {

    public static final Serializer SERIALIZER = new Serializer();

    private final Stack<MCReward> rewards = new Stack<>();

    public boolean popClaim() {
        if (this.rewards.empty()) {
            return false;
        }
        this.rewards.pop().claim();
        return true;
    }

    public void push(MCReward r) {
        this.rewards.push(r);
    }

    public static class Serializer {
        // TODO: Implement and use
    }

}
