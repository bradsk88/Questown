package ca.bradj.questown.town.entity;

import com.google.common.collect.ImmutableSet;

import java.util.Collection;

/**
 * The open quests a player has seen on the flag's quests tab. The tab's red dot shows while an
 * open quest is not among them (dev playtest 2026-10-30: "The quest tab should stop showing red
 * once the player has seen the latest quests"). Keys are {@code batch UUID | wanted id}.
 */
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
