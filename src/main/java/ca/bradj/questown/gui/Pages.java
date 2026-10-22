package ca.bradj.questown.gui;

/**
 * Page count for the paged card screens. An empty list is one empty page, so the header reads
 * "Page 1 / 1", not "Page 1 / 0" (2026-10-22 playtest: the empty job board).
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
}
