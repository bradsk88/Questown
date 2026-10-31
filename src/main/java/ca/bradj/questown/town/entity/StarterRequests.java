package ca.bradj.questown.town.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Which of a new worker's declared starter requests go on the board. Each request is posted as
 * declared (a tag stays a tag), once, and an item request is dropped when another declared request
 * covers it: the onboarding cook posts cook/extract's {@code #questown:villager_food}, not also
 * cook/mutton's {@code cooked_mutton}, so the board does not ask for mutton specifically while the
 * hunter brings beef (agent playtest r10 #11, dev decision 2026-10-23). A request the board already
 * covers is dropped too (dev playtest 2026-10-30: a new cook auto-posted cooked_porkchop under an
 * existing {@code #questown:villager_food}, "should check if that request is already being covered
 * by a tag").
 */
final class StarterRequests {

    private StarterRequests() {
    }

    /**
     * @param covers {@code covers(a, b)}: request {@code a} already asks for everything {@code b}
     *               asks for (a tag containing b's item). Must be false for {@code a.equals(b)}.
     */
    static <R> List<R> toPost(List<R> declared, BiPredicate<R, R> covers) {
        return toPost(declared, List.of(), covers);
    }

    /** As {@link #toPost(List, BiPredicate)}, also dropping what a request already on the board covers. */
    static <R> List<R> toPost(List<R> declared, List<R> onBoard, BiPredicate<R, R> covers) {
        List<R> out = new ArrayList<>();
        for (R r : declared) {
            if (out.contains(r) || isCoveredByAnother(r, declared, covers) || isCoveredByAnother(r, onBoard, covers)) {
                continue;
            }
            out.add(r);
        }
        return out;
    }

    private static <R> boolean isCoveredByAnother(R r, List<R> declared, BiPredicate<R, R> covers) {
        for (R other : declared) {
            if (!other.equals(r) && covers.test(other, r)) {
                return true;
            }
        }
        return false;
    }
}
