package ca.bradj.questown.town.entity;

import com.google.common.collect.ImmutableSet;

import java.util.Collection;

/** The open quests a player has seen on the quests tab; the red dot shows while one isn't among them. */
final class SeenQuests {

    private ImmutableSet<String> seen = ImmutableSet.of();

    void markSeen(Collection<String> openKeys) {
        seen = ImmutableSet.copyOf(openKeys);
    }

    boolean hasUnseen(Collection<String> openKeys) {
        return !seen.containsAll(openKeys);
    }

    ImmutableSet<String> keys() {
        return seen;
    }
}
