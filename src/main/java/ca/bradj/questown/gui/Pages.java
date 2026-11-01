package ca.bradj.questown.gui;

import java.util.List;

/**
 * Page count for the paged card screens. An empty list is one empty page, so the header reads
 * "Page 1 / 1", not "Page 1 / 0".
 */
public final class Pages {

    private Pages() {
    }

    public static int count(
            int items,
            int perPage
    ) {
        return Math.max(1, (int) Math.ceil((double) items / perPage));
    }

    /**
     * The items on one page: {@code perPage} items from {@code page * perPage}, or none past the
     * last page. Pinned by {@code PagesTest}.
     */
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
