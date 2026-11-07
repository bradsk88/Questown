package ca.bradj.questown.gui;

import java.util.Optional;

/**
 * The change-job panel's line: what to do next (insert a Block of Progress, or click Unlock) and
 * when the change happens (the next morning, or at once in creative or for a promised job).
 */
public record JobChangeText(String key, Optional<String> root, String whenKey) {

    public static JobChangeText of(
            boolean inserted,
            boolean creative,
            Optional<String> promisedRoot
    ) {
        boolean now = creative || promisedRoot.isPresent();
        String when = now ? "menu.unlock_root.when_now" : "menu.unlock_root.when_morning";
        if (promisedRoot.isPresent()) {
            String key = inserted ? "menu.unlock_root.click_unlock_promised" : "menu.unlock_root.insert_bop_promised";
            return new JobChangeText(key, promisedRoot, when);
        }
        String key = inserted ? "menu.unlock_root.click_unlock" : "menu.unlock_root.insert_bop";
        return new JobChangeText(key, Optional.empty(), when);
    }
}
