package ca.bradj.questown.gui.town.status;

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * Where the villagers tab puts the "...and one more in the morning" card: right after the
 * villagers, but never past the last slot of page 1, so a coming villager is always on page 1.
 */
final class ArrivalCard {

    private ArrivalCard() {
    }

    static <T> ImmutableList<T> order(List<T> villagers, T arrivalCard, boolean arriving, int cardsPerPage) {
        if (!arriving) {
            return ImmutableList.copyOf(villagers);
        }
        int at = Math.max(0, Math.min(villagers.size(), cardsPerPage - 1));
        return ImmutableList.<T>builder()
                .addAll(villagers.subList(0, at))
                .add(arrivalCard)
                .addAll(villagers.subList(at, villagers.size()))
                .build();
    }
}
