package ca.bradj.questown.town.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/** Which of a new worker's starter requests go on the board: each once, as declared, skipping any another request (or the board) already covers. */
final class StarterRequests {

    private StarterRequests() {
    }

    /** @param covers whether {@code a} already asks for everything {@code b} does; false when they are equal */
    static <R> List<R> toPost(List<R> declared, BiPredicate<R, R> covers) {
        return toPost(declared, List.of(), covers);
    }

    /** As above, also skipping what the board already covers. */
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
