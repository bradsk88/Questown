package ca.bradj.questown.gui.town.status;

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * Where the villagers tab puts the "...and one more in the morning" card. It came after every
 * villager, so with a full first page it sat on page 2 and the dev missed a coming villager (dev
 * playtest 2026-10-29: "Maybe it should always be shown on page 1 - pushing other villagers down").
 */
final class ArrivalCard {

    private ArrivalCard() {
    }

    static <T> ImmutableList<T> order(List<T> villagers, T arrivalCard, boolean arriving) {
        if (!arriving) {
            return ImmutableList.copyOf(villagers);
        }
        return ImmutableList.<T>builder().add(arrivalCard).addAll(villagers).build();
    }
}
