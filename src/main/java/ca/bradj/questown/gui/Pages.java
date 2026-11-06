package ca.bradj.questown.gui;

import java.util.List;

/** Page counts for the card screens. An empty list is one empty page. */
public final class Pages {

    private Pages() {
    }

    public static int count(
            int items,
            int perPage
    ) {
        return Math.max(1, (int) Math.ceil((double) items / perPage));
    }

    /** The items on page {@code page}, or none past the last page. */
    public static <T> List<T> slice(
            List<T> items,
            int page,
            int perPage
    ) {
        int start = Math.min(page * perPage, items.size());
        int end = Math.min(start + perPage, items.size());
        return items.subList(start, end);
    }
}
