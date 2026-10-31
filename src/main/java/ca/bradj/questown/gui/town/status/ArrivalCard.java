package ca.bradj.questown.gui.town.status;

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * Where the villagers tab puts the "...and one more in the morning" card: right after the
 * villagers, but never past the last slot of page 1. It used to come after every villager, so with
 * a full first page it sat on page 2 and the dev missed a coming villager (dev playtest 2026-10-29:
 * "Maybe it should always be shown on page 1 - pushing other villagers down"). Then it went first,
 * and the dev wanted it after them (2026-10-30: "should be in third spot, so it renders after 2
 * villagers").
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
